package io.github.jqssun.airplay

import android.app.Instrumentation
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.PlaybackSnapshot
import java.util.concurrent.atomic.AtomicReference

object AdaptiveQualityProbe {
    fun run(test: Instrumentation): String {
        val server = TransitionPlaybackProbe.FixtureServer(test)
        val thread = HandlerThread("abr-pixels").apply { start() }
        val sink = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.YUV_420_888, 3)
        sink.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(thread.looper))
        val snapshot = AtomicReference<PlaybackSnapshot>()
        val failure = AtomicReference<Int>()
        val player = AirPlayVideoPlayer(test.targetContext)
        player.onPlaybackInfo = { snapshot.set(it) }
        player.onError = { failure.set(it) }
        player.setSurface(sink.surface)
        fun awaitHeight(height: Int) {
            val until = SystemClock.elapsedRealtime() + 90000
            while (SystemClock.elapsedRealtime() < until && failure.get() == null) {
                val state = snapshot.get()
                if (state?.firstFrame == true && state.videoHeight == height && state.trackOptions.any { it.key == "auto" && it.selected }) return
                SystemClock.sleep(100)
            }
            error("Adaptive height $height not reached; actual=${snapshot.get()?.videoHeight} error=${failure.get()}")
        }
        try {
            server.segmentBytesPerSecond = 2_000_000
            player.play(server.url("quality-master.m3u8"), 0f, 333)
            awaitHeight(1080)
            server.segmentBytesPerSecond = 200_000
            awaitHeight(720)
            server.segmentBytesPerSecond = 2_000_000
            awaitHeight(1080)
            return "PASS local multi-variant HLS, real decoder, throttled HTTP: automatic 1080 -> 720 -> 1080 without manual track selection. Not a YouTube-only feature.\n"
        } finally {
            player.stop(); test.waitForIdleSync(); sink.close(); thread.quitSafely(); thread.join(); server.close()
        }
    }
}
