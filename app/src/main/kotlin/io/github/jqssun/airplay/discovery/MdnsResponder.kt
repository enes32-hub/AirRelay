package io.github.jqssun.airplay.discovery

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Publishes the receiver's Bonjour services ourselves instead of asking Android's NSD service.
 *
 * Why this exists: on several Android TV boxes the platform hands NSD registrations to the Wi-Fi
 * chipset's mDNS offload engine, and that engine fails there — the system log shows
 * `wifimdnsoffload: setWakePorts: exit:-95` and `NsdManagerOffloadEngine ... E` while
 * `NsdManager.registerService` still reports success. The receiver then looks ready in its own UI
 * while no `_airplay._tcp` or `_raop._tcp` record ever reaches the network, so an iPhone cannot see
 * it. Broadcasting the records from our own multicast socket bypasses that path completely, and it
 * also lets us tell the truth in the UI: [announced] means this socket actually put the records on
 * the wire, and [answeredQueries] means another device asked for them and we replied.
 *
 * The record set is the standard one: PTR for the service type, SRV for the instance, TXT for the
 * service attributes and an A record for the hostname in the SRV target.
 */
internal class MdnsResponder(private val address: InetAddress) {

    data class Service(val type: String, val name: String, val host: String, val port: Int,
        val txt: Map<String, String>) {
        val instance get() = "$name.$type.local"
        val typeFqdn get() = "$type.local"
        val target get() = "$host.local"
    }

    @Volatile private var service: Service? = null
    @Volatile private var socket: MulticastSocket? = null
    @Volatile private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    /** Set once the announcement datagrams were handed to the network stack. */
    @Volatile var announced: Boolean = false
        private set

    /** Monotonic count of queries from other devices that this responder answered. */
    @Volatile var answeredQueries: Int = 0
        private set

    @Volatile var lastError: String? = null
        private set

    fun start(service: Service): Boolean {
        stop()
        this.service = service
        announced = false
        answeredQueries = 0
        lastError = null
        val sock = try {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(5353))
                timeToLive = 4
                networkInterface = lanInterface()
                joinGroup(InetSocketAddress(InetAddress.getByName(GROUP), PORT), lanInterface())
            }
        } catch (failure: Exception) {
            // Some boxes refuse a second listener on 5353. Announcing from an ephemeral port still
            // publishes the records; only answering unicast queries is lost, and that is reported.
            lastError = "bind:${failure.javaClass.simpleName}"
            Log.w(TAG, "mDNS bind on 5353 failed: ${failure.message}")
            runCatching {
                MulticastSocket().apply {
                    reuseAddress = true
                    timeToLive = 4
                    networkInterface = lanInterface()
                    joinGroup(InetSocketAddress(InetAddress.getByName(GROUP), PORT), lanInterface())
                }
            }.getOrElse {
                lastError = "socket:${it.javaClass.simpleName}"
                Log.e(TAG, "mDNS socket unavailable: ${it.message}")
                return false
            }
        }
        socket = sock
        running.set(true)
        worker = Thread({ loop(sock) }, "mdns-responder").apply { isDaemon = true; start() }
        Log.i(TAG, "mDNS responder started for ${service.typeFqdn} as ${service.instance}")
        return true
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        worker?.let { runCatching { it.join(500) } }
        worker = null
        service = null
        announced = false
    }

    private fun loop(sock: MulticastSocket) {
        var sinceAnnounce = 0L
        var sinceRefresh = 0L
        val buffer = ByteArray(4096)
        while (running.get()) {
            // Unsolicited announcements: three quick ones so a browsing phone sees us immediately,
            // then a periodic refresh that also keeps the Wi-Fi radio awake for multicast.
            if (sinceAnnounce <= 0L) {
                sendAnnouncement(sock)
                sinceAnnounce = if (++burst < 3) 1_000L else 60_000L
            }
            try {
                sock.soTimeout = 500
                val packet = DatagramPacket(buffer, buffer.size)
                sock.receive(packet)
                sinceRefresh = 0L
                val query = parse(packet.data, packet.length)
                if (query != null && matches(query)) {
                    sendResponse(sock, packet.address, packet.port, query.unicastPreferred)
                    answeredQueries++
                    Log.i(TAG, "answered ${query.name} from ${packet.address.hostAddress}")
                }
            } catch (_: java.net.SocketTimeoutException) {
                sinceAnnounce -= 500
                sinceRefresh += 500
            } catch (failure: Exception) {
                if (running.get()) {
                    lastError = "receive:${failure.javaClass.simpleName}"
                    Log.w(TAG, "mDNS receive failed: ${failure.message}")
                    Thread.sleep(500)
                }
            }
        }
    }

    private var burst = 0

    private fun sendAnnouncement(sock: MulticastSocket) {
        val svc = service ?: return
        val payload = records(svc, includeQuestion = false)
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(GROUP), PORT)
        runCatching { sock.send(packet) }
            .onSuccess { announced = true }
            .onFailure {
                lastError = "send:${it.javaClass.simpleName}"
                Log.w(TAG, "mDNS announcement failed: ${it.message}")
            }
    }

    private fun sendResponse(sock: MulticastSocket, to: InetAddress, port: Int, unicast: Boolean) {
        val svc = service ?: return
        val payload = records(svc, includeQuestion = false)
        val target = if (unicast) InetSocketAddress(to, port) else InetSocketAddress(InetAddress.getByName(GROUP), PORT)
        runCatching { sock.send(DatagramPacket(payload, payload.size, target)) }
            .onSuccess { announced = true }
            .onFailure { lastError = "reply:${it.javaClass.simpleName}" }
    }

    private fun records(svc: Service, includeQuestion: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        // Response header: id 0, flags 0x8400 (authoritative response), no questions, 4 answers.
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0x00, 0, 0, 0, if (includeQuestion) 1 else 0,
            0, 4, 0, 0))
        out.write(name(svc.typeFqdn))
        out.write(short(PTR)); out.write(short(CLASS_IN)); out.write(int(4500))
        val ptr = name(svc.instance)
        out.write(short(ptr.size)); out.write(ptr)

        out.write(name(svc.instance))
        out.write(short(SRV)); out.write(short(CLASS_IN)); out.write(int(4500))
        val srv = ByteArrayOutputStream().apply {
            write(short(0)); write(short(0)); write(short(svc.port)); write(name(svc.target))
        }.toByteArray()
        out.write(short(srv.size)); out.write(srv)

        out.write(name(svc.instance))
        out.write(short(TXT)); out.write(short(CLASS_IN)); out.write(int(4500))
        val txt = ByteArrayOutputStream().apply {
            svc.txt.forEach { (key, value) ->
                val entry = "$key=$value".toByteArray(Charsets.UTF_8)
                if (entry.size <= 255) { write(entry.size); write(entry) }
            }
        }.toByteArray()
        out.write(short(txt.size)); out.write(txt)

        out.write(name(svc.target))
        out.write(short(A)); out.write(short(CLASS_IN)); out.write(int(120))
        out.write(short(4)); out.write(address.address)
        return out.toByteArray()
    }

    private data class Query(val name: String, val unicastPreferred: Boolean)

    private fun parse(data: ByteArray, length: Int): Query? {
        if (length < 12) return null
        val questions = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (questions <= 0) return null
        var offset = 12
        val labels = StringBuilder()
        while (offset < length) {
            val len = data[offset].toInt() and 0xFF
            if (len == 0) { offset++; break }
            if (len and 0xC0 != 0) { offset += 2; break }
            if (offset + 1 + len > length) return null
            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(data, offset + 1, len, Charsets.UTF_8))
            offset += 1 + len
        }
        if (offset + 4 > length) return null
        val qclass = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        return Query(labels.toString(), qclass and 0x8000 != 0)
    }

    private fun matches(query: Query): Boolean {
        val svc = service ?: return false
        val name = query.name.lowercase()
        return name == svc.typeFqdn.lowercase() || name == svc.instance.lowercase() ||
            name == "_services._dns-sd._udp.local" || name.endsWith(".local") && name.contains(svc.type)
    }

    private fun lanInterface(): NetworkInterface? = NetworkInterface.getNetworkInterfaces().toList()
        .firstOrNull { iface -> iface.isUp && !iface.isLoopback && iface.inetAddresses.toList()
            .any { !it.isLoopbackAddress && it is java.net.Inet4Address } }

    private fun name(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        value.trim('.').split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && bytes.size <= 63) { out.write(bytes.size); out.write(bytes) }
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun short(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
    private fun int(value: Int) =
        byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    companion object {
        private const val TAG = "MdnsResponder"
        private const val GROUP = "224.0.0.251"
        private const val PORT = 5353
        private const val CLASS_IN = 0x0001
        private const val PTR = 12
        private const val TXT = 16
        private const val SRV = 33
        private const val A = 1
    }
}
