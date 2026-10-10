package io.github.jqssun.airplay

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import io.github.jqssun.airplay.recording.CleanRecording
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.PlaybackSnapshot
import io.github.jqssun.airplay.renderer.VideoRenderer
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Decoded AVC and Media3 -> production GL -> real AVC MP4, under an opaque UI overlay. */
object CleanRecordingProbe {
    @Suppress("DEPRECATION")
    fun run(test: Instrumentation): String {
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        test.runOnMainSync { activity.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Magenta)) {
                Text("CONTROLS / SETTINGS / RECORDING — MUST NEVER BE IN THE MP4")
            }
        } }
        test.waitForIdleSync(); SystemClock.sleep(1000)
        val server = TransitionPlaybackProbe.FixtureServer(test)
        val mirror = VideoRenderer(test.targetContext)
        val direct = AirPlayVideoPlayer(test.targetContext)
        val snapshot = AtomicReference<PlaybackSnapshot>()
        val error = AtomicReference<Int>()
        direct.onPlaybackInfo = { snapshot.set(it) }
        direct.onError = { error.set(it) }
        mirror.startSession(3500)
        val feed = SyntheticVideoProbe.startRecordingFeed(mirror)
        val folder = File(test.targetContext.cacheDir, "clean-recording-probe").apply { mkdirs() }
        fun await(predicate: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 15000
            while (!predicate() && error.get() == null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50)
            check(error.get() == null && predicate()) { "Direct decode failed: ${error.get()} ${snapshot.get()}" }
        }
        val results = StringBuilder()
        try {
            for (height in listOf(720, 1080)) {
                val width = height * 16 / 9
                val file = File(folder, "clean-$height.mp4")
                var pausedUs = 0L
                val encoder = MediaRecorder()
                var attached = false
                try {
                    encoder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    encoder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    encoder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    encoder.setVideoSize(width, height)
                    encoder.setVideoFrameRate(30)
                    encoder.setVideoEncodingBitRate(4_000_000)
                    encoder.setOutputFile(file.absolutePath)
                    encoder.prepare(); encoder.start()
                    CleanRecording.select("mirror")
                    val failure = AtomicReference<Boolean>(false)
                    CleanRecording.attach(CleanRecording.Target(encoder.surface, width, height, System.nanoTime()) { failure.set(true) })
                    attached = true
                    SystemClock.sleep(2500)
                    // Same MP4, new producer. The old EGL producer must detach first.
                    CleanRecording.select("direct")
                    direct.play(server.url("clip.mp4"), 0f, height.toLong())
                    await { snapshot.get()?.let { it.mediaId == height.toLong() && it.firstFrame && it.position >= 1f } == true }
                    direct.setPlaying(false)
                    test.waitForIdleSync()
                    SystemClock.sleep(3000) // A paused source must still extend recording duration.
                    pausedUs = (snapshot.get().position * 1_000_000).toLong()
                    if (height == 1080) {
                        val startPss = android.os.Debug.getPss()
                        repeat(10) {
                            CleanRecording.select("mirror"); SystemClock.sleep(150)
                            CleanRecording.select("direct"); SystemClock.sleep(150)
                        }
                        SystemClock.sleep(30000)
                        val growth = android.os.Debug.getPss() - startPss
                        check(growth < 96 * 1024) { "Recording memory growth: ${growth}KB" }
                        results.append("PASS ten producer switches + 30s paused soak; PSS delta ${growth}KB (not a multi-hour leak proof).\n")
                    }
                    check(!failure.get()) { "Recording EGL failed" }
                    CleanRecording.detach(); attached = false
                    encoder.stop()
                } finally {
                    if (attached) CleanRecording.detach()
                    encoder.release()
                    direct.stop(); test.waitForIdleSync()
                }
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(file.absolutePath)
                    val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                    check(duration >= 6000) { "Paused recording truncated: $duration ms" }
                    check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt() == height)
                    for (time in listOf(1_000_000L, (duration - 500) * 1000)) {
                        val frame = checkNotNull(metadata.getFrameAtTime(time, MediaMetadataRetriever.OPTION_CLOSEST))
                        try {
                            var magenta = 0; var nonBlack = 0; var gray = 0
                            for (x in 1..19) for (y in 1..19) {
                                val c = frame.getPixel(frame.width * x / 20, frame.height * y / 20)
                                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                                if (r > 200 && b > 200 && g < 60) magenta++
                                if (r + g + b > 100) nonBlack++
                                if (kotlin.math.abs(r-g) < 18 && kotlin.math.abs(g-b) < 18) gray++
                            }
                            check(magenta < 250 && nonBlack > 40) { "UI or blank video recorded: magenta=$magenta nonBlack=$nonBlack" }
                            if (time == 1_000_000L) check(gray > 330) { "Mirror fixture contaminated by UI" }
                            else {
                                val originalFile = File(folder, "reference.mp4")
                                test.context.assets.open("media/clip.mp4").use { input -> originalFile.outputStream().use { input.copyTo(it) } }
                                val originalReader = MediaMetadataRetriever()
                                try {
                                    originalReader.setDataSource(originalFile.absolutePath)
                                    val original = checkNotNull(originalReader.getFrameAtTime(pausedUs, MediaMetadataRetriever.OPTION_CLOSEST))
                                    try {
                                        val scale = minOf(frame.width.toFloat()/original.width, frame.height.toFloat()/original.height)
                                        val w = original.width*scale; val h=original.height*scale
                                        var matches = 0
                                        for (x in 1..19) for (y in 1..19) {
                                            val expected = original.getPixel(original.width*x/20, original.height*y/20)
                                            val actual = frame.getPixel(((frame.width-w)/2+w*x/20).toInt(), ((frame.height-h)/2+h*y/20).toInt())
                                            if (kotlin.math.abs(Color.red(actual)-Color.red(expected)) < 60 &&
                                                kotlin.math.abs(Color.green(actual)-Color.green(expected)) < 60 &&
                                                kotlin.math.abs(Color.blue(actual)-Color.blue(expected)) < 60) matches++
                                        }
                                        check(matches >= 280) { "Recorded source differs from original: $matches/361 samples" }
                                        results.append("PASS original video pixel comparison: $matches/361.\n")
                                    } finally { original.recycle() }
                                } finally { originalReader.release(); originalFile.delete() }
                            }
                        } finally { frame.recycle() }
                    }
                    results.append("PASS ${height}p real AVC mirror + Media3 source switch; opaque magenta UI excluded; paused duration ${duration}ms.\n")
                } finally { metadata.release(); file.delete() }
            }
            return results.toString()
        } finally {
            CleanRecording.detach(); feed.close(); mirror.release(); direct.stop()
            test.waitForIdleSync(); server.close(); test.runOnMainSync { activity.finish() }
        }
    }
}
