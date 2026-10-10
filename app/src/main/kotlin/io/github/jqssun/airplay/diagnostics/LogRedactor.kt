package io.github.jqssun.airplay.diagnostics

/** Conservative support log filtering. Never feed a redacted value back to the receiver. */
object LogRedactor {
    private val secret = Regex("(?i)(?<![a-z0-9])(pin|password|passphrase|authorization|token|secret|session[-_ ]?key|aes[-_ ]?key|private[-_ ]?key|active[-_ ]?remote)(?![a-z0-9])")
    private val url = Regex("(?i)\\b(?:https?|rtsp)://[^\\s]+")
    private val mac = Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b")
    private val ip = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    // A compressed IPv6 address contains ::; a full address has more than two colons.
    // In particular, do not mistake a support log's HH:mm:ss.SSS timestamp for IPv6.
    private val ipv6 = Regex("(?i)(?<![0-9a-z])(?:[0-9a-f:]*::[0-9a-f:.%a-z0-9-]*|(?:[0-9a-f]{1,4}:){3,}[0-9a-f:.%a-z0-9-]+)(?![0-9a-z])")
    fun redact(message: String): String {
        val line = message.take(4096).replace('\n', ' ').replace('\r', ' ')
            .filterNot { it.isISOControl() && it != '\t' }
        if (secret.containsMatchIn(line)) return "[sensitive diagnostic omitted]"
        if (line.startsWith("Track:") || line.startsWith("DACP:")) return "[media metadata omitted]"
        return ipv6.replace(ip.replace(mac.replace(url.replace(line, "[url]"), "[device]"), "[address]"), "[address]")
    }
}

/** Small bounded buffer. Caller owns synchronization; tests run without Android. */
class BoundedLogBuffer(private val maxLines: Int = 600) {
    init { require(maxLines > 0) }
    private val lines = java.util.ArrayDeque<String>()
    fun append(line: String) {
        if (lines.size == maxLines) lines.removeFirst()
        lines.addLast(line)
    }
    fun clear() = lines.clear()
    fun snapshot(): List<String> = lines.toList()
}
