package io.github.jqssun.airplay.discovery

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArraySet
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
    @Volatile private var boundToMdnsPort = false
    private val running = AtomicBoolean(false)
    private val replyLock = Any()
    private var lastReplyAtMs = Long.MIN_VALUE
    private var lastReplyKey: String? = null

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
        burst = 0
        boundToMdnsPort = false
        synchronized(replyLock) {
            lastReplyAtMs = Long.MIN_VALUE
            lastReplyKey = null
        }
        val iface = lanInterface()
        var bound5353 = false
        val sock = try {
            MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
                bound5353 = true
                timeToLive = 4
                if (iface != null) networkInterface = iface
                joinGroup(InetSocketAddress(InetAddress.getByName(GROUP), PORT), iface)
            }
        } catch (failure: Exception) {
            // Some boxes refuse a second listener on 5353. Announcing from an ephemeral port still
            // publishes the records; sibling responders bound on 5353 forward queries here as well.
            lastError = "bind:${failure.javaClass.simpleName}"
            Log.w(TAG, "mDNS bind on 5353 failed: ${failure.message}")
            runCatching {
                MulticastSocket().apply {
                    reuseAddress = true
                    timeToLive = 4
                    if (iface != null) networkInterface = iface
                    joinGroup(InetSocketAddress(InetAddress.getByName(GROUP), PORT), iface)
                }
            }.getOrElse {
                lastError = "socket:${it.javaClass.simpleName}"
                Log.e(TAG, "mDNS socket unavailable: ${it.message}")
                return false
            }
        }
        boundToMdnsPort = bound5353
        socket = sock
        running.set(true)
        activeResponders.add(this)
        worker = Thread({ loop(sock) }, "mdns-responder").apply { isDaemon = true; start() }
        Log.i(TAG, "mDNS responder started for ${service.typeFqdn} as ${service.instance} (port5353=$bound5353)")
        return true
    }

    fun stop() {
        activeResponders.remove(this)
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        worker?.let { runCatching { it.join(500) } }
        worker = null
        service = null
        announced = false
        boundToMdnsPort = false
    }

    private fun loop(sock: MulticastSocket) {
        var nextAnnounceAtMs = System.nanoTime() / 1_000_000L
        val buffer = ByteArray(4096)
        try {
            while (running.get()) {
                // Unsolicited announcements: six quick ones on startup so a browsing phone sees us
                // immediately, then a 10s periodic refresh (or 1s retry on transient send error).
                val nowMs = System.nanoTime() / 1_000_000L
                if (nowMs >= nextAnnounceAtMs) {
                    val delayMs = if (sendAnnouncement(sock)) {
                        if (++burst < 6) 1_000L else 10_000L
                    } else {
                        1_000L
                    }
                    nextAnnounceAtMs = (System.nanoTime() / 1_000_000L) + delayMs
                }
                val remainingMs = (nextAnnounceAtMs - (System.nanoTime() / 1_000_000L)).coerceIn(50L, 500L)
                try {
                    sock.soTimeout = remainingMs.toInt()
                    val packet = DatagramPacket(buffer, buffer.size)
                    sock.receive(packet)
                    val queries = parseQuestions(packet.data, packet.length)
                    if (queries.isNotEmpty()) {
                        queries.firstOrNull(::matches)?.let { matched ->
                            answerIfNeeded(packet.address, packet.port, matched.unicastPreferred, matched.name)
                        }
                        for (peer in activeResponders) {
                            if (peer !== this) {
                                peer.onPeerPacket(queries, packet.address, packet.port)
                            }
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Scheduled wakeups are evaluated against monotonic time at loop top.
                } catch (failure: Exception) {
                    if (running.get()) {
                        lastError = "receive:${failure.javaClass.simpleName}"
                        Log.w(TAG, "mDNS receive failed: ${failure.message}")
                        Thread.sleep(500)
                    }
                }
            }
        } finally {
            activeResponders.remove(this)
        }
    }

    private var burst = 0

    private fun onPeerPacket(queries: List<Query>, fromAddress: InetAddress, fromPort: Int) {
        if (!running.get()) return
        val matched = queries.firstOrNull(::matches) ?: return
        answerIfNeeded(fromAddress, fromPort, matched.unicastPreferred, matched.name)
    }

    private fun answerIfNeeded(fromAddress: InetAddress, fromPort: Int, unicastPreferred: Boolean, matchedName: String) {
        val sock = socket ?: return
        if (!running.get() || service == null) return
        val nowMs = System.nanoTime() / 1_000_000L
        val dedupKey = "${fromAddress.hostAddress}:$fromPort:${matchedName.lowercase()}"
        synchronized(replyLock) {
            if (lastReplyKey == dedupKey && lastReplyAtMs != Long.MIN_VALUE && nowMs - lastReplyAtMs < REPLY_DEDUP_MS) {
                return
            }
            lastReplyKey = dedupKey
            lastReplyAtMs = nowMs
        }
        val useUnicast = unicastPreferred && boundToMdnsPort
        if (sendResponse(sock, fromAddress, fromPort, useUnicast)) {
            answeredQueries++
            Log.i(TAG, "answered $matchedName from ${fromAddress.hostAddress}")
        }
    }

    private fun sendAnnouncement(sock: MulticastSocket): Boolean {
        val svc = service ?: return false
        val payload = records(svc, includeQuestion = false)
        val packet = DatagramPacket(payload, payload.size, InetAddress.getByName(GROUP), PORT)
        return runCatching { sock.send(packet) }
            .onSuccess { announced = true }
            .onFailure {
                lastError = "send:${it.javaClass.simpleName}"
                Log.w(TAG, "mDNS announcement failed: ${it.message}")
            }
            .isSuccess
    }

    private fun sendResponse(sock: MulticastSocket, to: InetAddress, port: Int, unicast: Boolean): Boolean {
        val svc = service ?: return false
        val payload = records(svc, includeQuestion = false)
        val target = if (unicast) InetSocketAddress(to, port) else InetSocketAddress(InetAddress.getByName(GROUP), PORT)
        return runCatching { sock.send(DatagramPacket(payload, payload.size, target)) }
            .onSuccess { announced = true }
            .onFailure {
                lastError = "reply:${it.javaClass.simpleName}"
                Log.w(TAG, "mDNS response failed: ${it.message}")
            }
            .isSuccess
    }

    private fun records(svc: Service, includeQuestion: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        // DNS header field order is ID | flags | QDCOUNT | ANCOUNT | NSCOUNT | ARCOUNT, and all four
        // records this responder writes are answers. Publishing them in the authority section (the
        // 0/4 layout used before) made the packet claim "no answers", which iOS rejects: the receiver
        // was announced on the wire yet never listed under Screen Mirroring.
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0x00, 0, 0, 0, 4, 0, 0, 0, 0))
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

    private fun parseQuestions(data: ByteArray, length: Int): List<Query> {
        if (length < 12) return emptyList()
        if ((data[2].toInt() and 0x80) != 0) return emptyList()
        val questions = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        if (questions <= 0) return emptyList()
        val count = questions.coerceAtMost(32)
        val parsed = ArrayList<Query>(count)
        var offset = 12
        repeat(count) {
            if (offset >= length) return parsed
            val decoded = readDnsName(data, length, offset) ?: return parsed
            val qname = decoded.first
            offset = decoded.second
            if (offset + 4 > length) return parsed
            val qclass = ((data[offset + 2].toInt() and 0xFF) shl 8) or (data[offset + 3].toInt() and 0xFF)
            if (qname.isNotEmpty()) {
                parsed.add(Query(qname, (qclass and 0x8000) != 0))
            }
            offset += 4
        }
        return parsed
    }

    private fun readDnsName(data: ByteArray, length: Int, startOffset: Int): Pair<String, Int>? {
        val labels = StringBuilder()
        var pos = startOffset
        var nextOffset = -1
        var jumped = false
        var hops = 0
        while (pos < length) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) nextOffset = pos + 1
                return labels.toString() to nextOffset
            }
            if ((len and 0xC0) == 0xC0) {
                if (pos + 1 >= length) return null
                if (++hops > 16) return null
                if (!jumped) {
                    nextOffset = pos + 2
                    jumped = true
                }
                val pointer = ((len and 0x3F) shl 8) or (data[pos + 1].toInt() and 0xFF)
                if (pointer < 12 || pointer >= length) return null
                pos = pointer
                continue
            }
            if ((len and 0xC0) != 0) return null
            if (pos + 1 + len > length) return null
            if (labels.isNotEmpty()) labels.append('.')
            labels.append(String(data, pos + 1, len, Charsets.UTF_8))
            pos += 1 + len
        }
        return null
    }

    private fun matches(query: Query): Boolean {
        val svc = service ?: return false
        val name = query.name.lowercase().trimEnd('.')
        val typeFqdn = svc.typeFqdn.lowercase()
        return name == typeFqdn ||
            name == svc.instance.lowercase() ||
            name == svc.target.lowercase() ||
            name == "_services._dns-sd._udp.local" ||
            name.endsWith("._sub.$typeFqdn")
    }

    private fun lanInterface(): NetworkInterface? {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
        return interfaces.firstOrNull { iface -> iface.inetAddresses.toList().any { it == address } }
            ?: interfaces.firstOrNull { iface -> iface.inetAddresses.toList()
                .any { !it.isLoopbackAddress && it is java.net.Inet4Address } }
    }

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
        private val activeResponders = CopyOnWriteArraySet<MdnsResponder>()
        private const val TAG = "MdnsResponder"
        private const val GROUP = "224.0.0.251"
        private const val PORT = 5353
        private const val REPLY_DEDUP_MS = 80L
        private const val CLASS_IN = 0x0001
        private const val PTR = 12
        private const val TXT = 16
        private const val SRV = 33
        private const val A = 1
    }
}
