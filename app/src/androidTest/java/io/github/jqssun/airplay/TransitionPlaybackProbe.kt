package io.github.jqssun.airplay

import android.app.Instrumentation
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.AudioSelection
import io.github.jqssun.airplay.renderer.PlaybackSnapshot
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

/** Local HTTP/Media3 test. Does not claim sender protocol or audible output validation. */
object TransitionPlaybackProbe {
    fun run(test: Instrumentation, soakSeconds: Int = 12): JSONObject {
        val server = FixtureServer(test)
        val thread = HandlerThread("TransitionPixelSink").apply { start() }
        val sink = ImageReader.newInstance(320, 180, android.graphics.ImageFormat.YUV_420_888, 3)
        val received = java.util.concurrent.atomic.AtomicInteger()
        val sinkFailure = AtomicReference<Throwable>()
        sink.setOnImageAvailableListener({ reader ->
            try { reader.acquireLatestImage()?.let { it.close(); received.incrementAndGet() } }
            catch (failure: Throwable) { sinkFailure.set(failure) }
        }, Handler(thread.looper))
        val snapshot = AtomicReference<PlaybackSnapshot>()
        val error = AtomicReference<Int>()
        val player = AirPlayVideoPlayer(test.targetContext)
        player.onPlaybackInfo = { snapshot.set(it) }
        player.onError = { error.set(it) }
        player.setSurface(sink.surface)
        fun waitFor(predicate: () -> Boolean) {
            val limit = SystemClock.elapsedRealtime() + 10_000
            while (!predicate() && error.get() == null && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(30)
            sinkFailure.get()?.let { throw it }
            check(error.get() == null) { "Media3 error ${error.get()}" }
            check(predicate()) { "Media3 fixture timed out: ${snapshot.get()}" }
        }
        fun actual(): ExoPlayer = player.javaClass.getDeclaredField("player").apply { isAccessible = true }.get(player) as ExoPlayer
        try {
            var oldListener: Player.Listener? = null
            for (id in 1L..10L) {
                player.play(server.url("clip.mp4?item=$id"), 0f, id)
                waitFor { snapshot.get()?.let { it.mediaId == id && it.firstFrame && it.seekable } == true }
                test.runOnMainSync {
                    oldListener?.onPlaybackStateChanged(Player.STATE_ENDED)
                    oldListener = player.javaClass.getDeclaredField("activeListener").apply { isAccessible = true }.get(player) as Player.Listener
                }
                player.scrub(2f)
                waitFor { (snapshot.get()?.position ?: 0f) >= 1.8f }
                check(snapshot.get().mediaId == id && snapshot.get().duration > 0)
            }
            player.play(server.url("master.m3u8"), 0f, 11)
            waitFor { snapshot.get()?.let { it.mediaId == 11L && it.firstFrame } == true }
            player.selectAudio(AudioSelection(null, "tr", null, null))
            var selected = false
            waitFor {
                test.runOnMainSync {
                    selected = actual().currentTracks.groups.any { group ->
                        (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).language == "tr" }
                    }
                }
                selected
            }
            player.selectAudio(AudioSelection(null, "en", null, null))
            waitFor {
                test.runOnMainSync {
                    selected = actual().currentTracks.groups.any { group ->
                        (0 until group.length).any { group.isTrackSelected(it) && group.getTrackFormat(it).language == "en" }
                    }
                }
                selected
            }
            player.play(server.url("live.m3u8"), 0f, 12)
            waitFor { snapshot.get()?.let { it.mediaId == 12L && it.live && it.firstFrame } == true }
            val liveFrames = received.get()
            val memory = org.json.JSONArray()
            val end = SystemClock.elapsedRealtime() + soakSeconds * 1000L
            var lastFrames = liveFrames
            while (SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(minOf(30_000L, end - SystemClock.elapsedRealtime()).coerceAtLeast(1))
                val info = android.os.Debug.MemoryInfo()
                android.os.Debug.getMemoryInfo(info)
                memory.put(JSONObject().put("elapsedMs", SystemClock.elapsedRealtime()).put("pssKb", info.totalPss)
                    .put("nativeHeapBytes", android.os.Debug.getNativeHeapAllocatedSize()).put("frames", received.get()))
                java.io.File(test.targetContext.filesDir, "soak-progress.json").writeText(memory.toString())
                check(error.get() == null) { "Playback error during soak: ${error.get()}" }
                check(received.get() > lastFrames) { "No decoded frames during soak interval" }
                lastFrames = received.get()
            }
            val growthKb = memory.getJSONObject(memory.length() - 1).getInt("pssKb") - memory.getJSONObject(0).getInt("pssKb")
            check(growthKb < 96 * 1024) { "Memory grew by $growthKb KB; investigate retention" }
            sinkFailure.get()?.let { throw it }
            check(received.get() - liveFrames > 30) { "Live decoder stopped producing frames" }
            check(error.get() == null && snapshot.get().live && snapshot.get().rate > 0)
            check(server.liveFetches >= 2) { "Live manifest was not refreshed" }
            return JSONObject().put("passed", true).put("consecutiveVideos", 10)
                .put("alternateAudioSelections", 2).put("livePlaylistFetches", server.liveFetches)
                .put("soakSeconds", soakSeconds).put("memorySamples", memory).put("pssGrowthKb", growthKb)
                .put("senderProtocolValidated", false).put("audibleOutputValidated", false)
        } finally {
            player.stop()
            test.waitForIdleSync()
            sink.close(); thread.quitSafely(); thread.join(); server.close()
        }
    }

    class FixtureServer(private val test: Instrumentation) : AutoCloseable {
        private val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        @Volatile private var running = true
        @Volatile var liveFetches = 0
        @Volatile var segmentBytesPerSecond = 0
        private val epoch = SystemClock.elapsedRealtime()
        private val worker = Thread {
            while (running) {
                val client = try { socket.accept() } catch (_: Exception) { break }
                client.use { runCatching { serve(it) } }
            }
        }.apply { start() }
        fun url(path: String) = "http://127.0.0.1:${socket.localPort}/$path"
        private fun serve(client: Socket) {
            client.soTimeout = 3000
            val input = client.getInputStream().bufferedReader()
            val request = input.readLine() ?: return
            val path = request.split(' ')[1].substringBefore('?').removePrefix("/")
            var range: Long? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range: bytes=", true)) range = line.substringAfter('=').substringBefore('-').toLongOrNull()
            }
            val data = if (path == "quality-master.m3u8") {
                ("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\nquality-high.m3u8\n" +
                    "#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720\nquality-low.m3u8\n").toByteArray()
            } else if (path == "quality-high.m3u8" || path == "quality-low.m3u8") {
                val sequence = (SystemClock.elapsedRealtime() - epoch) / 2000
                val segment = path.removeSuffix(".m3u8") + ".ts"
                ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:$sequence\n#EXT-X-DISCONTINUITY-SEQUENCE:$sequence\n" +
                    (sequence..sequence + 3).joinToString("") { "#EXT-X-DISCONTINUITY\n#EXTINF:2.0,\n$segment?n=$it\n" }).toByteArray()
            } else if (path == "live.m3u8") {
                liveFetches++
                val sequence = (SystemClock.elapsedRealtime() - epoch) / 5000
                ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:5\n#EXT-X-MEDIA-SEQUENCE:$sequence\n#EXT-X-DISCONTINUITY-SEQUENCE:$sequence\n" +
                    (sequence..sequence + 2).joinToString("") { "#EXT-X-DISCONTINUITY\n#EXTINF:5.0,\nlive.ts?n=$it\n" }).toByteArray()
            } else test.context.assets.open("media/$path").use { it.readBytes() }
            val offset = (range ?: 0).coerceIn(0, data.size.toLong()).toInt()
            val type = when { path.endsWith("m3u8") -> "application/vnd.apple.mpegurl"; path.endsWith("vtt") -> "text/vtt"; path.endsWith("ts") -> "video/mp2t"; else -> "video/mp4" }
            val headers = "HTTP/1.1 ${if (range != null) "206 Partial Content" else "200 OK"}\r\nContent-Type: $type\r\nContent-Length: ${data.size - offset}\r\n" +
                (if (range != null) "Content-Range: bytes $offset-${data.size - 1}/${data.size}\r\n" else "") + "Connection: close\r\n\r\n"
            client.getOutputStream().apply {
                write(headers.toByteArray()); flush()
                if (segmentBytesPerSecond > 0 && path.endsWith(".ts")) {
                    var cursor = offset
                    while (cursor < data.size && running) {
                        val count = minOf(8192, data.size - cursor)
                        write(data, cursor, count); flush(); cursor += count
                        SystemClock.sleep((count * 1000L / segmentBytesPerSecond.coerceAtLeast(1)).coerceAtLeast(1))
                    }
                } else { write(data, offset, data.size - offset); flush() }
            }
        }
        override fun close() { running = false; socket.close(); worker.join(3500) }
    }
}
