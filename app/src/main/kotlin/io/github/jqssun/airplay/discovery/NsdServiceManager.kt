package io.github.jqssun.airplay.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.jqssun.airplay.receiver.Advertisement
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Publishes the receiver's Bonjour records from our own multicast socket.
 *
 * Android's NSD service is deliberately not used: on this class of Android TV box the platform
 * forwards NSD registrations to the Wi-Fi chipset's mDNS offload engine, and that engine fails there.
 * The system log shows `wifimdnsoffload: setWakePorts: exit:-95` and
 * `NsdManagerOffloadEngine ... onOffloadServiceUpdated [0, 0, -124, ...]` while
 * `NsdManager.registerService` still reports success, so the receiver announced "ready to connect"
 * with no `_airplay._tcp` or `_raop._tcp` record on the network and phones could not find it.
 *
 * [MdnsResponder] broadcasts the records itself, and readiness is now derived from facts we can
 * observe: [Advertisement.REGISTERED] means the announcement datagrams were handed to the network
 * stack, and [Advertisement.CONFIRMED] means another device queried us and we answered.
 */
class NsdServiceManager(
    private val ctx: Context,
    private val onResult: (Result) -> Unit = {},
) {
    enum class Kind { RAOP, AIRPLAY }
    data class Result(val kind: Kind, val state: Advertisement,
        val actualName: String? = null, val errorCode: Int? = null)

    private var multicastLock: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val deadlines = ConcurrentHashMap<Kind, Runnable>()
    private val responders = ConcurrentHashMap<Kind, MdnsResponder>()
    @Volatile private var announcedSince = ConcurrentHashMap<Kind, Long>()
    @Volatile private var released = false

    fun acquireMulticastLock() {
        // Ethernet-only TVs may not provide a Wi-Fi service. Our own sockets still work;
        // only the Wi-Fi radio needs this lock to keep receiving multicast.
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifi.createMulticastLock("airplay_mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun registerRaop(serviceName: String, port: Int, txtRecords: Map<String, String>) =
        register(Kind.RAOP, serviceName, "_raop._tcp", port, txtRecords)

    fun registerAirplay(serviceName: String, port: Int, txtRecords: Map<String, String>) =
        register(Kind.AIRPLAY, serviceName, "_airplay._tcp", port, txtRecords)

    private fun cancelDeadline(kind: Kind) {
        deadlines.remove(kind)?.let { main.removeCallbacks(it) }
    }

    private fun register(kind: Kind, name: String, type: String, port: Int, txt: Map<String, String>) {
        if (released) return
        onResult(Result(kind, Advertisement.REGISTERING))
        cancelDeadline(kind)
        responders.remove(kind)?.stop()
        announcedSince.remove(kind)

        val address = lanAddress()
        if (address == null) {
            Log.e(TAG, "$kind: no local IPv4 address to advertise")
            onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_PLATFORM))
            return
        }
        val host = hostLabel(name)
        val responder = MdnsResponder(address)
        val started = runCatching {
            responder.start(MdnsResponder.Service(type = type, name = name, host = host, port = port, txt = txt))
        }.getOrElse {
            Log.e(TAG, "$kind: responder could not start: ${it.javaClass.simpleName} ${it.message}")
            false
        }
        if (!started) {
            Log.e(TAG, "$kind: no multicast socket available")
            onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_PLATFORM))
            return
        }
        responders[kind] = responder
        watch(kind, responder, name)
    }

    /**
     * Readiness is only reported after the records actually left this process, and the stronger
     * state is reported once a device on the network asks for them. Both are polled on the main
     * thread so callbacks stay on the same thread the UI already observes.
     */
    private fun watch(kind: Kind, responder: MdnsResponder, name: String) {
        var waited = 0L
        var reportedConfirmed = false
        lateinit var poll: Runnable
        poll = Runnable {
            if (released || responders[kind] !== responder) return@Runnable
            when {
                responder.answeredQueries > 0 && !reportedConfirmed -> {
                    reportedConfirmed = true
                    Log.i(TAG, "$kind confirmed by a network query")
                    onResult(Result(kind, Advertisement.CONFIRMED, name))
                }
                responder.announced && announcedSince[kind] == null -> {
                    announcedSince[kind] = System.currentTimeMillis()
                    Log.i(TAG, "$kind announced on the network (${responder.answeredQueries} queries answered)")
                    onResult(Result(kind, Advertisement.REGISTERED, name))
                }
                responder.lastError != null && waited >= ANNOUNCEMENT_TIMEOUT_MS &&
                    announcedSince[kind] == null -> {
                    Log.e(TAG, "$kind announcement failed: ${responder.lastError}")
                    onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_TIMEOUT))
                    return@Runnable
                }
                waited >= ANNOUNCEMENT_TIMEOUT_MS && announcedSince[kind] == null -> {
                    Log.e(TAG, "$kind announcement never left the process")
                    onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_TIMEOUT))
                    return@Runnable
                }
            }
            waited += POLL_INTERVAL_MS
            main.postDelayed(poll, POLL_INTERVAL_MS)
        }
        deadlines[kind] = poll
        main.postDelayed(poll, POLL_INTERVAL_MS)
    }

    fun unregisterAll() {
        // Invalidate first: a late poll must never make a stopped receiver look ready again.
        Kind.values().forEach { kind ->
            cancelDeadline(kind)
            responders.remove(kind)?.stop()
            announcedSince.remove(kind)
        }
        onResult(Result(Kind.AIRPLAY, Advertisement.IDLE))
        onResult(Result(Kind.RAOP, Advertisement.IDLE))
    }

    fun release() {
        released = true
        unregisterAll()
        multicastLock?.let { if (it.isHeld) runCatching { it.release() } }
        multicastLock = null
    }

    /** IPv4 address the receiver is reachable on; Wi-Fi first, then any other live interface. */
    private fun lanAddress(): InetAddress? {
        val candidates = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
        return candidates.asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
    }

    /**
     * Stable, unique-on-the-LAN host label for the SRV target of both services. RAOP names carry a
     * device prefix ("5ADA...@AirRelay-4BBD7345"), so the prefix is dropped: both services then
     * share one clean hostname instead of embedding the device address twice.
     */
    private fun hostLabel(serviceName: String): String {
        val device = serviceName.substringAfter('@', serviceName)
        val slug = device.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return when {
            slug.isEmpty() -> "airrelay-receiver"
            slug.startsWith("airrelay") -> slug
            else -> "airrelay-$slug"
        }
    }

    companion object {
        private const val TAG = "NsdServiceManager"
        const val ERROR_PERMISSION = -1
        const val ERROR_PLATFORM = -2
        const val ERROR_TIMEOUT = -3
        private const val ANNOUNCEMENT_TIMEOUT_MS = 6_000L
        private const val POLL_INTERVAL_MS = 250L
    }
}
