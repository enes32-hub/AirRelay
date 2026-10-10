package io.github.jqssun.airplay.renderer

/** iOS can use a very large finite sentinel for an unspecified live position. */
object PlaybackStartPosition {
    fun milliseconds(seconds: Float): Long? =
        if (!seconds.isFinite() || seconds <= 0f || seconds >= 1_000_000_000_000f) null
        else (seconds.toDouble() * 1000).toLong()
}
