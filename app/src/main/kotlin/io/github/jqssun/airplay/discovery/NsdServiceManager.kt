package io.github.jqssun.airplay.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import io.github.jqssun.airplay.receiver.Advertisement

/** Same names, ports, types and TXT records; adds only lifecycle-safe result observation. */
class NsdServiceManager(
    private val ctx: Context,
    private val onResult: (Result) -> Unit = {},
) {
    enum class Kind { RAOP, AIRPLAY }
    data class Result(val kind: Kind, val state: Advertisement,
        val actualName: String? = null, val errorCode: Int? = null)
    private val nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val deadlines = ConcurrentHashMap<Kind, Runnable>()
    @Volatile private var raopRegistration: NsdManager.RegistrationListener? = null
    @Volatile private var airplayRegistration: NsdManager.RegistrationListener? = null
    @Volatile private var released = false

    fun acquireMulticastLock() {
        // Ethernet-only TVs may not provide a Wi-Fi service. Android NSD and
        // the receiver sockets still work; only the Wi-Fi radio needs this lock.
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
        lateinit var listener: NsdManager.RegistrationListener
        lateinit var deadline: Runnable
        // Only this registration owns this deadline. A racing old callback must never
        // remove the replacement registration's observation task for the same service kind.
        fun cancelOwnDeadline() {
            if (deadlines.remove(kind, deadline)) main.removeCallbacks(deadline)
        }
        fun current() = !released && listener ===
            (if (kind == Kind.RAOP) raopRegistration else airplayRegistration)
        listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                if (!current()) return
                cancelOwnDeadline()
                Log.i(TAG, "$kind registered")
                onResult(Result(kind, Advertisement.REGISTERED, info.serviceName))
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                if (!current()) return
                cancelOwnDeadline()
                Log.e(TAG, "$kind registration failed: $code")
                onResult(Result(kind, Advertisement.FAILED, errorCode = code))
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                if (!current()) return
                cancelOwnDeadline()
                onResult(Result(kind, Advertisement.IDLE))
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                // An obsolete listener must not change current readiness, but its failed
                // withdrawal is still useful evidence when a sender shows a stale receiver.
                Log.w(TAG, "$kind withdrawal failed: $code")
                if (!current()) return
                cancelOwnDeadline()
                onResult(Result(kind, Advertisement.FAILED, errorCode = code))
            }
        }
        if (kind == Kind.RAOP) raopRegistration = listener else airplayRegistration = listener
        onResult(Result(kind, Advertisement.REGISTERING))
        cancelDeadline(kind)
        // UI observation deadline only. It neither changes TXT data nor tears down a connection.
        deadline = Runnable {
            if (deadlines.remove(kind, deadline) && current())
                onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_TIMEOUT))
        }
        deadlines[kind] = deadline
        main.postDelayed(deadline, REGISTRATION_OBSERVATION_MS)
        try {
            val info = NsdServiceInfo().apply {
                serviceName = name
                serviceType = type
                this.port = port
                txt.forEach { (key, value) -> setAttribute(key, value) }
            }
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: SecurityException) {
            cancelOwnDeadline()
            onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_PERMISSION))
        } catch (_: RuntimeException) {
            cancelOwnDeadline()
            onResult(Result(kind, Advertisement.FAILED, errorCode = ERROR_PLATFORM))
        }
    }
    fun unregisterAll() {
        // Invalidate first: late callbacks must never make a stopped/replaced receiver look ready.
        val raop = raopRegistration; val airplay = airplayRegistration
        raopRegistration = null; airplayRegistration = null
        Kind.values().forEach(::cancelDeadline)
        raop?.let { runCatching { nsdManager.unregisterService(it) }
            .onFailure { Log.w(TAG, "RAOP withdrawal request failed: ${it.javaClass.simpleName}") } }
        airplay?.let { runCatching { nsdManager.unregisterService(it) }
            .onFailure { Log.w(TAG, "AIRPLAY withdrawal request failed: ${it.javaClass.simpleName}") } }
    }
    fun release() {
        released = true
        Kind.values().forEach(::cancelDeadline)
        unregisterAll()
        multicastLock?.let { if (it.isHeld) runCatching { it.release() } }
        multicastLock = null
    }
    companion object {
        private const val TAG = "NsdServiceManager"
        const val ERROR_PERMISSION = -1
        const val ERROR_PLATFORM = -2
        const val ERROR_TIMEOUT = -3
        private const val REGISTRATION_OBSERVATION_MS = 15_000L
    }
}
