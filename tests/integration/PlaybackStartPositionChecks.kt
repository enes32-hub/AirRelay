import io.github.jqssun.airplay.renderer.PlaybackStartPosition

fun main() {
    check(PlaybackStartPosition.milliseconds(4f) == 4000L)
    check(PlaybackStartPosition.milliseconds(29.125f) == 29125L)
    check(PlaybackStartPosition.milliseconds(0f) == null)
    check(PlaybackStartPosition.milliseconds(-1f) == null)
    check(PlaybackStartPosition.milliseconds(Float.NaN) == null)
    check(PlaybackStartPosition.milliseconds(Float.POSITIVE_INFINITY) == null)
    check(PlaybackStartPosition.milliseconds(9.223372e12f) == null)
    println("PASS: 7 playback start position checks, including observed iOS live sentinel")
}
