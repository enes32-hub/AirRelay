package io.github.jqssun.airplay

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import io.github.jqssun.airplay.receiver.PictureFit
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.PlaybackSnapshot
import io.github.jqssun.airplay.ui.FittedReceiverSurface
import java.util.concurrent.atomic.AtomicReference

/** Real Media3 decoding into the production surface, not a drawn Canvas substitute. */
object DirectRotationProbe {
    fun run(test: Instrumentation): String {
        val server = TransitionPlaybackProbe.FixtureServer(test)
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val angle = mutableIntStateOf(0)
        val snapshot = AtomicReference<PlaybackSnapshot>()
        val error = AtomicReference<Int>()
        val player = AirPlayVideoPlayer(test.targetContext)
        var firstSurface: Surface? = null
        var replacements = 0
        var destroyed = 0
        player.onPlaybackInfo = { snapshot.set(it) }
        player.onError = { error.set(it) }
        test.runOnMainSync { activity.setContent {
            FittedReceiverSurface(16f / 9f, PictureFit.FIT, angle.intValue,
                { surface ->
                    if (firstSurface == null) firstSurface = surface
                    else if (firstSurface !== surface) replacements++
                    player.setSurface(surface)
                }, { destroyed++; player.clearSurface(it) }, stableTexture = true)
        } }
        fun await(reason: String, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (!predicate() && error.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(40)
            check(error.get() == null && predicate()) { "$reason; code=${error.get()} snapshot=${snapshot.get()}" }
        }
        fun shot() = checkNotNull(test.uiAutomation.takeScreenshot())
        fun sample(image: Bitmap, degrees: Int, u: Float, v: Float): Int {
            val aspect = if (degrees % 180 == 0) 16f / 9f else 9f / 16f
            val w = minOf(image.width.toFloat(), image.height * aspect)
            val h = w / aspect
            val (x, y) = when (degrees) {
                90 -> (1f - v) to u
                180 -> (1f - u) to (1f - v)
                270 -> v to (1f - u)
                else -> u to v
            }
            return image.getPixel(((image.width - w) / 2 + x * w).toInt(), ((image.height - h) / 2 + y * h).toInt())
        }
        var checks = 0
        try {
            player.play(server.url("clip.mp4"), 0f, 900)
            await("first decoded frame") { snapshot.get()?.firstFrame == true && snapshot.get().position >= 0.5f }
            player.setPlaying(false)
            test.waitForIdleSync(); SystemClock.sleep(350)
            val reference = shot()
            // Compare stable color regions, not codec/grid edges whose resampling changes
            // with a 90-degree resize. Keep broad spatial coverage and a strict match ratio.
            val points = (1..15).flatMap { x -> (1..15).map { y -> x / 16f to y / 16f } }.filter { (u, v) ->
                val center = sample(reference, 0, u, v)
                listOf(-.002f to 0f, .002f to 0f, 0f to -.002f, 0f to .002f).all { (du,dv) ->
                    val nearby = sample(reference, 0, u+du, v+dv)
                    kotlin.math.abs(Color.red(center)-Color.red(nearby)) < 18 &&
                    kotlin.math.abs(Color.green(center)-Color.green(nearby)) < 18 &&
                    kotlin.math.abs(Color.blue(center)-Color.blue(nearby)) < 18
                }
            }
            check(points.size >= 50) { "Too few stable reference regions: ${points.size}" }
            val colors = points.map { (u,v) -> sample(reference, 0, u,v) }
            check(colors.distinct().size > 5) { "Fixture is blank or unsuitable for rotation verification" }
            try {
                repeat(3) { cycle ->
                    for (degrees in listOf(90,180,270,0)) {
                        test.runOnMainSync { angle.intValue = degrees }
                        test.waitForIdleSync(); SystemClock.sleep(300)
                        val image = shot()
                        try {
                            val matches = points.zip(colors).count { (point, expected) ->
                                val actual = sample(image, degrees, point.first, point.second)
                                kotlin.math.abs(Color.red(actual)-Color.red(expected)) < 40 &&
                                kotlin.math.abs(Color.green(actual)-Color.green(expected)) < 40 &&
                                kotlin.math.abs(Color.blue(actual)-Color.blue(expected)) < 40
                            }
                            check(matches >= kotlin.math.ceil(points.size * .90).toInt()) { "Rotation $degrees cycle $cycle: only $matches/${points.size} stable decoded pixel samples matched" }
                            if (cycle == 0) {
                                val folder = java.io.File(test.targetContext.getExternalFilesDir(null), "direct-rotation").apply { mkdirs() }
                                java.io.File(folder, "$degrees.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                            }
                        } finally { image.recycle() }
                        check(replacements == 0 && destroyed == 0) { "Rotation replaced/destroyed decoder surface" }
                        checks++
                    }
                }
            } finally { reference.recycle() }
            player.play(server.url("live.m3u8"), 0f, 901)
            await("live frame") { snapshot.get()?.let { it.mediaId == 901L && it.firstFrame && it.rate > 0 } == true }
            val liveFrames = java.util.concurrent.atomic.AtomicInteger()
            test.runOnMainSync {
                val actual = player.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(player) as androidx.media3.exoplayer.ExoPlayer
                actual.setVideoFrameMetadataListener { _, _, _, _ -> liveFrames.incrementAndGet() }
            }
            repeat(12) {
                test.runOnMainSync { angle.intValue = (angle.intValue + 90) % 360 }
                SystemClock.sleep(250)
                check(error.get() == null && replacements == 0 && destroyed == 0)
            }
            await("live still advancing") { snapshot.get().rate > 0 && !snapshot.get().buffering }
            check(liveFrames.get() >= 15) { "Live decoder stopped submitting frames: ${liveFrames.get()}" }
            return "PASS: $checks decoded-frame pixel rotations + 12 live rotations (${liveFrames.get()} decoder frames); zero Surface replacements/destructions; no player error. Local HLS/MP4; iPhone and audible sync not validated."
        } finally {
            player.stop(); test.waitForIdleSync(); test.runOnMainSync { activity.finish() }; server.close()
        }
    }
}
