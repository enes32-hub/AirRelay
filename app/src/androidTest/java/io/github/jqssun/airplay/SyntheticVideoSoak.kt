package io.github.jqssun.airplay

import android.app.Instrumentation
import android.os.Debug
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * In-process soak for the video pipeline.
 *
 * Why this exists: the per-run instrumentation modes (`tvHevcOnly`, `presentationOnly`, ...) each
 * start a fresh app process, so resource counters cannot be compared across runs — the process is
 * gone before the next measurement. This mode loops inside one process and reports open file
 * descriptors, thread count and PSS before and after, which is what makes leak detection automatic:
 * `SyntheticVideoProbe.runTvHevc` restarts the decoder and the GL pipeline once per round, the exact
 * path that leaked one external texture and one shader program per restart before that was fixed.
 *
 * Run it with:
 *
 *     adb shell am force-stop com.android.tv.settings          # clear the TV's stuck Settings task
 *     adb shell input keyevent KEYCODE_WAKEUP                  # the probe needs a live surface
 *     adb shell am instrument -w -e soakHevcRounds 10 \
 *         com.airrelay.receiver.test/io.github.jqssun.airplay.IntegrationInstrumentation
 */
object SyntheticVideoSoak {

    fun run(instrumentation: Instrumentation, rounds: Int): JSONObject {
        val before = sample()
        val perRound = JSONArray()
        var passed = 0
        var failed = 0
        for (round in 1..rounds) {
            val entry = JSONObject().put("round", round)
            try {
                val result = SyntheticVideoProbe.runTvHevc(instrumentation)
                entry.put("passed", result.optBoolean("passed", false))
                entry.put("rounds", result.optJSONArray("rounds")?.length() ?: 0)
                passed++
            } catch (failure: Throwable) {
                failed++
                entry.put("passed", false)
                entry.put("error", failure.message?.take(200) ?: failure.javaClass.simpleName)
            }
            perRound.put(entry)
        }
        val after = sample()
        return JSONObject()
            .put("passed", failed == 0)
            .put("rounds", rounds)
            .put("passedRounds", passed)
            .put("failedRounds", failed)
            .put("fdsBefore", before.fds).put("fdsAfter", after.fds).put("fdDelta", after.fds - before.fds)
            .put("threadsBefore", before.threads).put("threadsAfter", after.threads)
            .put("threadDelta", after.threads - before.threads)
            .put("pssKbBefore", before.pssKb).put("pssKbAfter", after.pssKb)
            .put("pssDeltaKb", after.pssKb - before.pssKb)
            .put("fdKindsBefore", descriptorKinds(before.kinds))
            .put("fdKindsAfter", descriptorKinds(after.kinds))
            .put("perRound", perRound)
    }

    /**
     * Categorises open descriptors by what they point at. This separates a graphics/codec leak
     * (dmabuf, sync_file, gralloc) from test-harness churn (sockets, pipes, plain files), which is
     * the difference between a product defect and an artefact of starting an Activity per round.
     */
    private fun descriptorKinds(kinds: Map<String, Int>): JSONObject =
        JSONObject().also { json -> kinds.forEach { (kind, count) -> json.put(kind, count) } }

    /**
     * Control run for [run]: starts and finishes the probe's host Activity (same `SurfaceView`,
     * same keep-screen-on flag) the same number of times, but **decodes nothing**.
     *
     * Subtracting this from [run] separates test-harness churn — an Activity, a SurfaceView and an
     * EGL context per probe call — from growth caused by the decoder and GL pipeline restarts, which
     * is the part that would matter for the product.
     */
    fun runActivityChurn(instrumentation: Instrumentation, rounds: Int): JSONObject {
        val before = sample()
        val perRound = JSONArray()
        var failures = 0
        for (round in 1..rounds) {
            val entry = JSONObject().put("round", round)
            var activity: android.app.Activity? = null
            try {
                activity = instrumentation.startActivitySync(
                    android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                val host = activity
                val view = android.view.SurfaceView(host)
                instrumentation.runOnMainSync {
                    host.window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    host.setContentView(view)
                }
                val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                while (!view.holder.surface.isValid &&
                    android.os.SystemClock.elapsedRealtime() < deadline) {
                    android.os.SystemClock.sleep(20)
                }
                entry.put("surfaceValid", view.holder.surface.isValid)
                android.os.SystemClock.sleep(1500)
            } catch (failure: Throwable) {
                failures++
                entry.put("error", failure.message?.take(120) ?: failure.javaClass.simpleName)
            } finally {
                instrumentation.runOnMainSync { runCatching { activity?.finish() } }
            }
            perRound.put(entry)
        }
        val after = sample()
        return JSONObject()
            .put("mode", "activity-churn")
            .put("passed", failures == 0)
            .put("rounds", rounds)
            .put("failedRounds", failures)
            .put("fdsBefore", before.fds).put("fdsAfter", after.fds).put("fdDelta", after.fds - before.fds)
            .put("threadsBefore", before.threads).put("threadsAfter", after.threads)
            .put("threadDelta", after.threads - before.threads)
            .put("pssKbBefore", before.pssKb).put("pssKbAfter", after.pssKb)
            .put("pssDeltaKb", after.pssKb - before.pssKb)
            .put("fdKindsBefore", descriptorKinds(before.kinds))
            .put("fdKindsAfter", descriptorKinds(after.kinds))
            .put("perRound", perRound)
    }

    private data class Counters(val fds: Int, val threads: Int, val pssKb: Int, val kinds: Map<String, Int>)

    private fun sample(): Counters {
        val links = File("/proc/self/fd").listFiles().orEmpty().mapNotNull { link ->
            runCatching { android.system.Os.readlink(link.path) }.getOrNull()
        }
        val fds = links.size
        val threads = runCatching {
            Regex("Threads:\\s*(\\d+)").find(File("/proc/self/status").readText())
                ?.groupValues?.get(1)?.toIntOrNull() ?: -1
        }.getOrDefault(-1)
        val memory = Debug.MemoryInfo()
        runCatching { Debug.getMemoryInfo(memory) }
        return Counters(fds, threads, memory.totalPss, links.groupingBy(::classify).eachCount())
    }

    private fun classify(target: String): String = when {
        target.startsWith("socket:") -> "socket"
        target.startsWith("pipe:") -> "pipe"
        target.contains("dmabuf") -> "dmabuf"
        target.contains("sync_file") || target.contains("sync_timeline") -> "sync_file"
        target.contains("gralloc") || target.contains("memfd:") -> "gralloc/memfd"
        target.contains("eventfd") || target.contains("eventpoll") -> "eventfd"
        target.startsWith("anon_inode:") -> "anon_inode"
        target.startsWith("/dev/") -> "/dev"
        target.startsWith("/") -> "file"
        else -> "other"
    }
}
