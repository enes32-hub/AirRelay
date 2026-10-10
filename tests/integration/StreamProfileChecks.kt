import io.github.jqssun.airplay.receiver.StreamProfile
import io.github.jqssun.airplay.renderer.DecoderResidence

fun main() {
    check(StreamProfile.from("unknown") == StreamProfile.CUSTOM)
    check(StreamProfile.CUSTOM.size(498,1080) == (498 to 1080))
    check(StreamProfile.RESPONSIVE.size(498,1080) == (720 to 1280))
    check(StreamProfile.QUALITY.size(1920,1080) == (1920 to 1080))
    check(StreamProfile.RESPONSIVE.frameLimit(30) == 60)
    check(StreamProfile.CUSTOM.frameLimit(120) == 120)
    check(StreamProfile.CUSTOM.frameLimit(90) == 90)
    check(StreamProfile.CUSTOM.frameLimit(121) == 120)
    check(StreamProfile.CUSTOM.frameLimit(24) == 24)
    val residence = DecoderResidence()
    residence.queued(0, 1_000_000)
    residence.queued(0, 2_000_000)
    check(residence.output(0, 11_000_000) == 10L)
    check(residence.output(0, 12_000_000) == 10L)
    check(residence.output(0, 13_000_000) == null)
    residence.queued(9, 1_000_000)
    residence.clear()
    check(residence.output(9, 99_000_000) == null)
    repeat(300) { residence.queued(it.toLong(),it.toLong()) }
    check(residence.output(0,1_000_000) == null)
    check(residence.output(299,1_000_000) == 0L)
    println("PASS: profiles, manual FPS up to 120, duplicate/zero PTS, stale generation and bounded residence observations")
}
