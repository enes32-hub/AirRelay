package io.github.jqssun.airplay.renderer

/** Decoder-pressure ceiling, layered on top of Media3 bandwidth adaptation. */
class SmartVideoQuality {
    var constrained = false
        private set
    private var lastPressureMs: Long? = null
    private var healthyPlaybackMs = 0L
    private var lastTickMs: Long? = null

    fun dropped(nowMs: Long, count: Int, intervalMs: Long): Boolean {
        // Do not react to an isolated dropped frame or a long cumulative report.
        if (count < 12 || intervalMs <= 0 || intervalMs > 120_000 || count * 1_000L < intervalMs * 4) return false
        lastPressureMs = nowMs
        healthyPlaybackMs = 0
        lastTickMs = nowMs
        if (constrained) return false
        constrained = true
        return true
    }

    fun tick(nowMs: Long, advancing: Boolean): Boolean {
        val previous = lastTickMs
        lastTickMs = nowMs
        if (!constrained) return false
        // Pausing/buffering is not evidence that the decoder can sustain higher quality.
        if (!advancing) healthyPlaybackMs = 0
        else if (previous != null && nowMs >= previous) healthyPlaybackMs += (nowMs - previous).coerceAtMost(1_000)
        if (healthyPlaybackMs < 60_000 || nowMs - (lastPressureMs ?: nowMs) < 60_000) return false
        reset()
        return true
    }

    fun reset() {
        constrained = false
        healthyPlaybackMs = 0
        lastPressureMs = null
        lastTickMs = null
    }
}
