package io.github.jqssun.airplay.renderer

/** Opaque, per-player track key; names and formats come from the incoming source. */
data class VideoTrackOption(val type: Int, val key: String, val label: String = "", val selected: Boolean = false)
