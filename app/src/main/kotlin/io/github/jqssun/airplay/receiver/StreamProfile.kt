package io.github.jqssun.airplay.receiver

/** Receiver-advertised ceilings, not a promise about the sender's actual bitrate or FPS. */
enum class StreamProfile(val key: String, val width: Int, val height: Int, val fps: Int, val staleMs: Long) {
    CUSTOM("custom", 0, 0, 0, 180),
    BALANCED("balanced", 1920, 1080, 30, 180),
    QUALITY("quality", 1920, 1080, 30, 250),
    RESPONSIVE("responsive", 1280, 720, 60, 100),
    STABLE("stable", 1280, 720, 30, 200);

    fun size(w: Int, h: Int): Pair<Int, Int> {
        if (this == CUSTOM) return w to h
        return if (w < h) height to width else width to height
    }
    fun frameLimit(requested: Int): Int {
        val target = if (this == CUSTOM) requested else fps
        return target.coerceIn(1, 120)
    }
    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}
