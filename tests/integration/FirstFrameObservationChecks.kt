import io.github.jqssun.airplay.receiver.FirstFrameGate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

// Exercises production bookkeeping on the host. SurfaceTexture/MediaCodec/EGL are
// intentionally not faked: Android presentation and real stream tests remain separate.
private var passed = 0
private fun case(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS $name") }
    catch (error: Throwable) { throw AssertionError("FAIL $name", error) }
}

private fun FirstFrameGate.output(ptsUs: Long, timestampNs: Long = ptsUs * 1000L) {
    recordDecoderOutput(decoderSession(), ptsUs, timestampNs)
}

fun main() {
    case("texture without a decoder output never proves a frame") {
        val gate = FirstFrameGate(); gate.begin(1)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
    }
    case("matching decoded PTS reports exactly one successful submission") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val frame = gate.frameForTexture(10_000)
        check(frame?.presentationTimeUs == 10L)
        check(gate.acceptSubmission(frame)); check(!gate.acceptSubmission(frame))
    }
    case("queued old buffer cannot inherit the new session at texture consumption") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        gate.invalidate(); gate.begin(2)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        gate.output(20)
        check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("cached old texture redraw stays old after display rebind") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val cached = gate.frameForTexture(10_000)
        gate.invalidate(); gate.begin(2)
        check(!gate.acceptSubmission(cached))
        check(gate.frameForTexture(10_000) == cached)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
    }
    case("frame consumed before display attachment can later prove current session") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val cached = gate.frameForTexture(10_000)
        // No swap occurred while the display was absent, so no submission was accepted.
        check(gate.acceptSubmission(cached))
    }
    case("old decoder output arriving after begin retains decoder generation") {
        val gate = FirstFrameGate(); gate.begin(1)
        val oldDecoder = gate.decoderSession()
        gate.begin(2)
        gate.recordDecoderOutput(oldDecoder, 10, 10_000)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        gate.output(20); check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("rapid restart is safe even when an external token is reused") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val stale = gate.frameForTexture(10_000)
        gate.invalidate(); gate.begin(1)
        check(!gate.acceptSubmission(stale))
        gate.output(20); check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("cross-session duplicate timestamp is ambiguous until texture advances") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        gate.begin(2); gate.output(10)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        gate.output(20); check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("cached timestamp cannot be relabeled by a newly queued matching output") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val stale = gate.frameForTexture(10_000)
        gate.begin(2); gate.output(10)
        check(gate.frameForTexture(10_000) == stale)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        gate.output(20); check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("duplicate timestamps within one decoder session remain attributable") {
        val gate = FirstFrameGate(); gate.begin(1)
        repeat(1000) { gate.output(10) }
        check(gate.acceptSubmission(gate.frameForTexture(10_000)))
    }
    case("scheduled-release surface timestamp is matched while original PTS is retained") {
        val gate = FirstFrameGate(); gate.begin(1)
        gate.output(10, 99_000_000_000L)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        val frame = gate.frameForTexture(99_000_000_000L)
        check(frame?.presentationTimeUs == 10L)
        check(gate.acceptSubmission(frame))
    }
    case("coalesced texture callbacks discard skipped provenance without growing forever") {
        val gate = FirstFrameGate(); gate.begin(1)
        repeat(5000) { batch ->
            repeat(8) { gate.output((batch * 8 + it + 1).toLong()) }
            check(gate.frameForTexture((batch * 8L + 8) * 1000)?.session == gate.decoderSession())
        }
        check(gate.acceptSubmission(gate.frameForTexture(40_000_000)))
    }
    case("nonmonotonic media PTS is matched in decoder release order") {
        val gate = FirstFrameGate(); gate.begin(1)
        gate.output(400); gate.output(100); gate.output(250)
        check(gate.frameForTexture(100_000)?.presentationTimeUs == 100L)
        check(gate.acceptSubmission(gate.frameForTexture(250_000)))
    }
    case("untrusted decoder output with a colliding timestamp cannot prove current stream") {
        val gate = FirstFrameGate()
        gate.output(10)
        gate.begin(1); gate.output(10)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
    }
    case("observation overflow fails closed across restart until input surface replacement") {
        val gate = FirstFrameGate(); gate.begin(1)
        repeat(1024) { gate.output(it.toLong()) }
        gate.begin(2); gate.output(10)
        check(!gate.acceptSubmission(gate.frameForTexture(10_000)))
        gate.resetSurfaceObservation()
        gate.output(20); check(gate.acceptSubmission(gate.frameForTexture(20_000)))
    }
    case("late accepted callback token does not change when the next session begins") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val frame = gate.frameForTexture(10_000)!!
        check(gate.acceptSubmission(frame))
        gate.begin(2)
        check(frame.session!!.token == 1L)
        check(!gate.acceptSubmission(frame))
    }
    case("concurrent callbacks for one decoded frame accept once") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val frame = gate.frameForTexture(10_000)
        val start = CountDownLatch(1); val count = AtomicInteger()
        val threads = List(24) { Thread { start.await(); if (gate.acceptSubmission(frame)) count.incrementAndGet() } }
        threads.forEach { it.start() }; start.countDown(); threads.forEach { it.join() }
        check(count.get() == 1)
    }
    case("remote return rejects cached and queued frames but accepts new decoder output") {
        val gate = FirstFrameGate(); gate.begin(1); gate.output(10)
        val old = gate.frameForTexture(10_000)
        check(gate.acceptSubmission(old))
        gate.output(20)
        gate.awaitFreshSubmission()
        check(!gate.acceptSubmission(old))
        check(!gate.acceptSubmission(gate.frameForTexture(20_000)))
        gate.output(30)
        check(gate.acceptSubmission(gate.frameForTexture(30_000)))
    }
    println("FIRST FRAME OBSERVATION: $passed passed; Android decoder, EGL and iPhone flow not exercised.")
}
