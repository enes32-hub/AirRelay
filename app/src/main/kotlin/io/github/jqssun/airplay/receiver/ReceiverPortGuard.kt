package io.github.jqssun.airplay.receiver

import java.io.Closeable
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket

/** Preflight only. Native bind remains authoritative; a different process can race this check. */
object ReceiverPortGuard {
    // Fixed transport endpoints owned by the native receiver; the control port must never
    // collide with one of them (see native_bridge.cpp tcp_ports/udp_ports).
    const val MIRROR_PORT = 7100
    const val TIMING_PORT = 7011
    const val AUDIO_CONTROL_PORT = 6001
    const val AUDIO_DATA_PORT = 6000
    val reservedPorts = setOf(MIRROR_PORT, TIMING_PORT, AUDIO_CONTROL_PORT, AUDIO_DATA_PORT)

    data class Endpoint(val protocol: String, val port: Int) {
        override fun toString() = "$protocol $port"
    }
    // Must match native_bridge.cpp. No listener is moved behind the emulator bridge's back.
    // distinct(): the control port may legitimately equal a fixed transport port request,
    // and the guard must not collide with its own still-bound probe socket.
    fun endpoints(controlPort: Int) = listOf(Endpoint("TCP", controlPort), Endpoint("TCP", MIRROR_PORT),
        Endpoint("UDP", TIMING_PORT), Endpoint("UDP", AUDIO_CONTROL_PORT), Endpoint("UDP", AUDIO_DATA_PORT))
        .distinct()

    /** All probes stay bound until the complete set has been checked; no packets are sent. */
    fun firstUnavailable(endpoints: List<Endpoint>): Endpoint? {
        val held = mutableListOf<Closeable>()
        try {
            for (endpoint in endpoints) {
                try {
                    if (endpoint.protocol == "TCP") {
                        val socket = ServerSocket()
                        held += socket
                        // Allow a previous session's TIME_WAIT sockets, never another listener.
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(endpoint.port))
                    } else {
                        val socket = DatagramSocket(null)
                        held += socket
                        socket.reuseAddress = false
                        socket.bind(InetSocketAddress(endpoint.port))
                    }
                } catch (_: java.io.IOException) { return endpoint }
                catch (_: SecurityException) { return endpoint }
            }
            return null
        } finally { held.forEach { runCatching { it.close() } } }
    }
}
