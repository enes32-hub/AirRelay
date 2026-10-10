import io.github.jqssun.airplay.receiver.PictureFit
import kotlin.math.abs

fun main() {
    var checks = 0
    for ((w,h) in listOf(1920f to 1080f, 3840f to 2160f)) {
        for (ratio in listOf(3840f/1772f, 498f/1080f, 16f/9f, 4f/3f)) {
            val (fw,fh) = PictureFit.FIT.size(w,h,ratio)
            check(fw <= w + .01f && fh <= h + .01f)
            check(abs(fw-w) < .01f || abs(fh-h) < .01f)
            check(abs(fw/fh-ratio) < .001f)
            val (cw,ch) = PictureFit.FILL.size(w,h,ratio)
            check(cw >= w - .01f && ch >= h - .01f)
            check(abs(cw/ch-ratio) < .001f)
            check(PictureFit.STRETCH.size(w,h,ratio) == (w to h))
            checks += 6
        }
    }
    check(PictureFit.FIT.size(1920f,1080f,Float.NaN) == (1920f to 1080f)); checks++
    var mode = PictureFit.FIT
    for (expected in listOf(PictureFit.FILL, PictureFit.STRETCH, PictureFit.FIT)) {
        mode = mode.next(); check(mode == expected); checks++
    }
    println("PASS: $checks picture fit geometry and cycle checks")
}
