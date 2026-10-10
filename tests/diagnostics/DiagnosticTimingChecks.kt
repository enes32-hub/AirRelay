import io.github.jqssun.airplay.diagnostics.DiagnosticIngressQueue
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Host regression checks for receipt timing and bounded diagnostic batching; no Android timing emulation. */
fun main() {
    var wall = 1_000L
    var monotonic = 50_000_000L
    val queue = DiagnosticIngressQueue(2, { wall }, { monotonic })
    val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    var checks = 0
    fun checkCase(name: String, action: () -> Unit) {
        action()
        checks++
        println("PASS: $name")
    }

    checkCase("delayed batch preserves distinct event receipt times") {
        queue.offer("control connected")
        wall = 1_250
        monotonic = 300_000_000
        queue.offer("first frame")
        // Simulate a busy IO worker: the clock at dequeue is unrelated to either event.
        wall = 10_000
        monotonic = 9_050_000_000
        val events = queue.drain()
        check(events.map { it.wallTimeMillis } == listOf(1_000L, 1_250L))
        check(events.map { it.monotonicTimeNanos } == listOf(50_000_000L, 300_000_000L))
        check(events[0].format(clock) == "00:00:01.000 [mono_ms=50] control connected")
        check(events[1].format(clock) == "00:00:01.250 [mono_ms=300] first frame")
    }

    checkCase("wall-clock correction never reorders a batch or changes elapsed monotonic time") {
        wall = 20_000
        monotonic = 10_000_000_000
        queue.offer("before correction")
        wall = 5_000
        monotonic = 10_080_000_000
        queue.offer("after correction")
        val events = queue.drain()
        check(events.map { it.message } == listOf("before correction", "after correction"))
        check(events.map { it.wallTimeMillis } == listOf(20_000L, 5_000L))
        check(events[1].monotonicTimeNanos - events[0].monotonicTimeNanos == 80_000_000L)
    }

    checkCase("overflow keeps accepted records and reports the first omitted event time once") {
        wall = 30_000
        monotonic = 20_000_000_000
        queue.offer("accepted one")
        queue.offer("accepted two")
        wall = 30_080
        monotonic = 20_080_000_000
        queue.offer("password=never retained")
        wall = 30_090
        monotonic = 20_090_000_000
        queue.offer("fourth event")
        val events = queue.drain()
        check(events.size == 3)
        check(events.take(2).map { it.message } == listOf("accepted one", "accepted two"))
        check(events.last().message == "[diagnostics rate limited: 2 messages omitted]")
        check(events.last().wallTimeMillis == 30_080L)
        check(events.last().monotonicTimeNanos == 20_080_000_000L)
        check(queue.drain().isEmpty())
        queue.offer("after drain")
        check(queue.drain().single().message == "after drain")
    }

    checkCase("queued records are redacted and bounded before they can be flushed") {
        queue.offer("authorization: do-not-retain-this")
        queue.offer("x".repeat(10_000))
        val events = queue.drain()
        check(events.first().message == "[sensitive diagnostic omitted]")
        check(events.last().message.length == 4096)
    }

    checkCase("clear discards both queued events and pending rate-limit notice") {
        repeat(10) { queue.offer("pre-clear event") }
        queue.clear()
        check(queue.drain().isEmpty())
        queue.offer("post-clear event")
        check(queue.drain().single().message == "post-clear event")
    }

    checkCase("sustained overload stays bounded across batches") {
        repeat(3) {
            repeat(10_000) { queue.offer("ordinary event") }
            val events = queue.drain()
            check(events.size == 3)
            check(events.last().message == "[diagnostics rate limited: 9998 messages omitted]")
        }
    }

    println("Diagnostic timing checks: $checks passed")
}
