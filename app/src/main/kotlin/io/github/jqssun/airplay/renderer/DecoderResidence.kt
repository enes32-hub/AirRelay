package io.github.jqssun.airplay.renderer

/** Monotonic decoder residence only. Does not invent sender time when NTP is unavailable. */
class DecoderResidence {
    private data class Entry(val ptsUs: Long, val queuedNs: Long)
    private val pending = ArrayDeque<Entry>()
    fun queued(ptsUs: Long, nowNs: Long) {
        if (pending.size >= 256) pending.removeFirst()
        pending.addLast(Entry(ptsUs, nowNs))
    }
    fun output(ptsUs: Long, nowNs: Long): Long? {
        val index = pending.indexOfFirst { it.ptsUs == ptsUs }
        if (index < 0) return null
        val entry = pending.removeAt(index)
        return ((nowNs - entry.queuedNs).coerceAtLeast(0) / 1_000_000)
    }
    fun clear() = pending.clear()
}
