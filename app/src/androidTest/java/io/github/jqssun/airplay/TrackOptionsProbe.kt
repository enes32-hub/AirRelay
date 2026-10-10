package io.github.jqssun.airplay

import android.app.Instrumentation
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.jqssun.airplay.ui.DirectVideoSubtitles
import io.github.jqssun.airplay.ui.VideoTrackDialog
import io.github.jqssun.airplay.ui.theme.AirPlayTheme
import androidx.media3.common.C
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.PlaybackSnapshot
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

object TrackOptionsProbe {
    fun run(test: Instrumentation): JSONObject {
        val server = TransitionPlaybackProbe.FixtureServer(test)
        val thread = HandlerThread("TrackPixelSink").apply { start() }
        val sink = ImageReader.newInstance(320, 180, android.graphics.ImageFormat.YUV_420_888, 3)
        sink.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(thread.looper))
        val state = AtomicReference<PlaybackSnapshot>()
        val displayedCues = mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList())
        val displayedTracks = mutableStateOf<List<io.github.jqssun.airplay.renderer.VideoTrackOption>>(emptyList())
        val menu = mutableStateOf<Int?>(null)
        val chosen = AtomicReference<String>()
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        test.runOnMainSync { activity.setContent {
            AirPlayTheme(selected = "dark") {
                Box(Modifier.fillMaxSize().background(Color.Black)) { DirectVideoSubtitles(displayedCues.value) }
                menu.value?.let { type -> VideoTrackDialog(type, displayedTracks.value,
                    onChoose = { chosen.set(it); menu.value = null }, onDismiss = { menu.value = null }) }
            }
        } }
        fun subtitlePixels(name: String): Int {
            test.waitForIdleSync()
            SystemClock.sleep(200)
            val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                val folder = java.io.File(test.targetContext.filesDir, "track-options").apply { mkdirs() }
                java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                var count = 0
                for (y in bitmap.height * 2 / 3 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) > 180 && android.graphics.Color.blue(pixel) > 180) count++
                }
                return count
            } finally { bitmap.recycle() }
        }
        val error = AtomicReference<Int>()
        val player = AirPlayVideoPlayer(test.targetContext)
        player.onPlaybackInfo = { state.set(it); displayedCues.value = it.cues; displayedTracks.value = it.trackOptions }
        player.onError = { error.set(it) }
        player.setSurface(sink.surface)
        fun waitFor(reason: String, predicate: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15000
            while (!predicate() && error.get() == null && SystemClock.elapsedRealtime() < end) SystemClock.sleep(30)
            check(error.get() == null && predicate()) { "$reason: error=${error.get()} state=${state.get()}" }
        }
        try {
            player.play(server.url("quality-master.m3u8"), 0f, 101)
            waitFor("quality catalog") { state.get()?.let { it.mediaId == 101L && it.firstFrame && it.trackOptions.count { o -> o.type == C.TRACK_TYPE_VIDEO } >= 3 } == true }
            val video = state.get().trackOptions.filter { it.type == C.TRACK_TYPE_VIDEO && it.key != "auto" }
            for ((height, option) in listOf(720, 1080).map { h -> h to video.first { it.label.contains("$h") } }) {
                player.selectTrack(101, C.TRACK_TYPE_VIDEO, option.key)
                waitFor("selected resolution $height") { state.get()?.let { it.videoHeight == height && it.trackOptions.any { o -> o.key == option.key && o.selected } } == true }
            }
            player.selectTrack(101, C.TRACK_TYPE_VIDEO, "auto")
            waitFor("automatic quality") { state.get().trackOptions.any { it.key == "auto" && it.selected } }
            player.play(server.url("tracks-master.m3u8"), 0f, 102)
            waitFor("audio and caption catalog") { state.get()?.let { it.mediaId == 102L && it.firstFrame && it.trackOptions.any { o -> o.type == C.TRACK_TYPE_TEXT && o.key != "off" } } == true }
            player.setPlaying(false)
            for (language in listOf("tr", "en")) {
                val option = state.get().trackOptions.first { it.type == C.TRACK_TYPE_AUDIO && it.label.contains(language) }
                player.selectTrack(102, C.TRACK_TYPE_AUDIO, option.key)
                waitFor("audio $language") { state.get().trackOptions.any { it.key == option.key && it.selected } }
            }
            val subtitle = state.get().trackOptions.first { it.type == C.TRACK_TYPE_TEXT && it.key != "off" }
            player.selectTrack(102, C.TRACK_TYPE_TEXT, subtitle.key)
            player.scrub(0.5f)
            player.setPlaying(true)
            waitFor("decoded subtitle cue") { state.get().cues.any { it.text?.contains("AirRelay subtitle test") == true } }
            player.setPlaying(false)
            val subtitleVisible = subtitlePixels("subtitles-on")
            check(subtitleVisible > 30) { "Decoded subtitle not visible on screen" }
            player.selectTrack(102, C.TRACK_TYPE_TEXT, "off")
            waitFor("subtitles off") { state.get().cues.isEmpty() && state.get().trackOptions.any { it.key == "off" && it.selected } }
            check(subtitlePixels("subtitles-off") < subtitleVisible / 2) { "Subtitle stayed on screen after disabling" }
            for (type in listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
                chosen.set(null)
                val options = state.get().trackOptions.filter { it.type == type }
                val expected = (options.firstOrNull { it.selected } ?: options.first()).key
                test.runOnMainSync { menu.value = type }
                subtitlePixels("menu-$type")
                SystemClock.sleep(300)
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
                waitFor("TV remote selects initial track $type") { chosen.get() == expected && menu.value == null }
                test.runOnMainSync { menu.value = type }
                test.waitForIdleSync()
                SystemClock.sleep(300)
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                waitFor("TV remote dismisses menu $type") { menu.value == null }
            }
            player.selectTrack(101, C.TRACK_TYPE_VIDEO, video.first().key)
            player.selectTrack(102, C.TRACK_TYPE_TEXT, "nonexistent")
            test.waitForIdleSync()
            check(state.get().mediaId == 102L && state.get().cues.isEmpty())
            player.stop()
            waitFor("stop clears tracks") { state.get().duration < 0 && state.get().trackOptions.isEmpty() && state.get().cues.isEmpty() }
            return JSONObject().put("passed", true).put("actualResolutions", "720,1080")
                .put("audioLanguages", "tr,en").put("subtitleCueDecoded", true)
                .put("automaticAndOff", true).put("staleSelectionRejected", true)
                .put("subtitlePixelsVerified", true).put("remoteMenus", 3)
                .put("scope", "Local HTTP HLS + real Media3 decoder; not iPhone/YouTube acceptance")
        } finally {
            player.stop(); test.waitForIdleSync(); sink.close(); thread.quitSafely(); thread.join(); server.close()
            test.runOnMainSync { activity.finish() }
        }
    }
}
