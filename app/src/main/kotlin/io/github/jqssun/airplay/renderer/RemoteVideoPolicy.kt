package io.github.jqssun.airplay.renderer

import io.github.jqssun.airplay.receiver.StreamProfile

/** An ABR variant ceiling, never a control over the mirroring sender's encoder. */
object RemoteVideoPolicy {
    private val choices = setOf(4_000_000, 8_000_000, 12_000_000, 20_000_000, 40_000_000)
    fun bitrateCeiling(profile: StreamProfile, preference: Int): Int =
        preference.takeIf { it in choices } ?: when (profile) {
            StreamProfile.RESPONSIVE, StreamProfile.STABLE -> 4_000_000
            StreamProfile.QUALITY -> 12_000_000
            StreamProfile.BALANCED -> 8_000_000
            StreamProfile.CUSTOM -> Int.MAX_VALUE
        }
}
