package io.github.jqssun.airplay

import android.app.Instrumentation
import android.os.SystemClock
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

object HttpFailureProbe {
    fun run(test: Instrumentation): String {
        val requests = AtomicInteger()
        val error = AtomicInteger()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { client ->
                        client.soTimeout = 3000
                        val input = client.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { }
                        requests.incrementAndGet()
                        client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                } catch (_: java.io.IOException) { }
            }
        }.apply { start() }
        val player = AirPlayVideoPlayer(test.targetContext)
        player.onError = { error.set(it) }
        try {
            player.play("http://127.0.0.1:${server.localPort}/denied.m3u8", 0f, 100)
            val deadline = SystemClock.elapsedRealtime() + 8000
            while (error.get() == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            check(error.get() == 403) { "Expected HTTP 403; received ${error.get()}" }
            SystemClock.sleep(1500)
            check(requests.get() == 1) { "Denied request repeated ${requests.get()} times" }
            // A new media item must remain playable after a denied source.
            val fixture = TransitionPlaybackProbe.FixtureServer(test)
            try {
                val ready = java.util.concurrent.atomic.AtomicBoolean()
                error.set(0)
                player.onPlaybackInfo = { if (it.mediaId == 101L && it.ready) ready.set(true) }
                player.play(fixture.url("clip.mp4"), 0f, 101)
                val nextDeadline = SystemClock.elapsedRealtime() + 10000
                while (!ready.get() && error.get() == 0 && SystemClock.elapsedRealtime() < nextDeadline) SystemClock.sleep(25)
                check(ready.get() && error.get() == 0) { "Next video not ready: ${error.get()}" }
                // Malformed sender rates must not throw on the main looper.
                player.setRate(Float.NaN)
                player.setRate(Float.POSITIVE_INFINITY)
                player.setSpeed(Float.NaN)
                player.setSpeed(-1f)
                test.waitForIdleSync()
                check(error.get() == 0) { "Invalid playback rate interrupted media" }
            } finally { fixture.close() }
            return "PASS HTTP_403: single request; exact error; next video ready; invalid rates ignored"
        } finally {
            player.stop(); test.waitForIdleSync(); server.close(); worker.join(4000)
        }
    }
}
