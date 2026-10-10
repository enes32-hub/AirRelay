package io.github.jqssun.airplay.renderer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import io.github.jqssun.airplay.renderer.DecoderSelector.Companion.videoCaps
import io.github.jqssun.airplay.receiver.FirstFrameGate

class VideoRenderer(ctx: Context) {

    private val lock = Object()
    private val pipeline = VideoPipeline().also { io.github.jqssun.airplay.recording.CleanRecording.register("mirror", it) }
    var onPresentationFailure: ((Long) -> Unit)?
        get() = pipeline.onFailure
        set(value) { pipeline.onFailure = value }
    private var lastPipelineRetryNs = 0L
    var onFirstFrameSubmitted: ((Long) -> Unit)?
        get() = pipeline.onFirstFrameSubmitted
        set(value) { pipeline.onFirstFrameSubmitted = value }
    val selector = DecoderSelector(ctx)
    private var avcDecoder: MediaCodecInfo? = null
    private var hevcDecoder: MediaCodecInfo? = null
    private var maxFps = 0
    private var codec: MediaCodec? = null
    private var decoderObservationSession: FirstFrameGate.Session? = null
    // Volatile: the display hand-off below must not queue behind the media lock.
    @Volatile private var displaySurface: Surface? = null
    private var currentH265 = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var firstFrameQueued = false
    private val accessUnits = AnnexBAccessUnit()
    // Bumped by startSession/stopSession/release. A codec restart performs its blocking GL
    // work without the media lock, then re-checks this before committing.
    private var sessionGeneration = 0L
    private var parameterCodec: Boolean? = null
    private var configuredMaxWidth = 0
    private var configuredMaxHeight = 0
    private var configuredAdaptive = false
    private var reconfigureOnSizeChange = false
    private var codecStartedAtNs = 0L
    private var queuedSinceOutput = 0
    @Volatile var decodedFrames = 0L; private set
    val submittedFrames: Long get() = pipeline.submittedFrames
    val outputAgeMs: Long get() = _lastOutputFrameNs.takeIf { it != 0L }
        ?.let { (System.nanoTime() - it) / 1_000_000L } ?: -1L
    val surfaceAgeMs: Long get() = pipeline.lastSubmissionNs.takeIf { it != 0L }
        ?.let { (System.nanoTime() - it) / 1_000_000L } ?: -1L
    private val residence = DecoderResidence()
    var staleFrameThresholdMs = 180L
    @Volatile var decoderResidenceMs = -1L; private set
    @Volatile var coalescedOutputs = 0L; private set
    private var lastPresentedAtNs = 0L
    @Volatile var lateOutputFrames = 0L; private set

    // stats
    @Volatile var fps = 0; private set
    @Volatile var bitrateBps = 0L; private set
    @Volatile var frameCount = 0L; private set
    @Volatile var codecName = ""; private set
    @Volatile var droppedFrames = 0L; private set
    @Volatile var framePacingJitterUs = 0L; private set

    var enforceSdr = true
    var keyAllowFrameDrop = true
    // AirPlay mirroring timestamps can jump when iOS re-anchors its clock.  TV
    // mirroring therefore renders decoded frames immediately by default; audio
    // keeps its own NTP-synchronised timeline in the native engine.
    var scheduledOutputBufferRelease = false
    var benchmarkLog = false
    var benchmarkLogCallback: ((String) -> Unit)? = null
    private var _framesThisSec = 0
    private var _bytesThisSec = 0L
    private var _lastStatReset = 0L
    private val _frameIntervalsNs = LongArray(120)
    private var _frameIntervalIdx = 0
    private var _frameIntervalCount = 0
    private var _lastOutputFrameNs = 0L
    // anchors that map decoder PTS (us) to System.nanoTime() for scheduled rendering
    private var _ptsBaseUs = Long.MIN_VALUE
    private var _wallBaseNs = 0L

    fun setResolution(w: Int, h: Int) = synchronized(lock) {
        if (w <= 0 || h <= 0) return@synchronized
        if (w != videoWidth || h != videoHeight) {
            Log.i(TAG, "Resolution $videoWidth x $videoHeight -> $w x $h")
            accessUnits.clear()
            if (codec != null && (reconfigureOnSizeChange || !configuredAdaptive || w > configuredMaxWidth || h > configuredMaxHeight)) {
                Log.i(TAG, "Resolution outside configured decoder envelope; awaiting configured keyframe")
                stopCodec()
            }
        }
        videoWidth = w
        videoHeight = h
        pipeline.setVideoSize(w, h)
    }

    // The display hand-off only touches the pipeline (which takes its own brief lock) and a
    // volatile field, so a surface rebind from the UI never waits for a decode pass to end.
    // doesn't restart codec; decoder renders into pipeline's own persistent surface
    fun setSurface(surface: Surface) {
        displaySurface = surface
        pipeline.setDisplaySurface(surface)
    }

    fun clearSurface(surface: Surface) {
        if (displaySurface !== surface) return
        displaySurface = null
        pipeline.setDisplaySurface(null)
    }

    fun awaitFreshSubmission() = pipeline.awaitFreshSubmission()

    fun selectDecoders(w: Int, h: Int, fps: Int, h265: Boolean): Boolean = synchronized(lock) {
        avcDecoder = selector.avc()
        hevcDecoder = if (h265) selector.hevc(avcDecoder, w, h, fps) else null
        maxFps = fps
        Log.i(TAG, "decoders: avc=${avcDecoder?.name} hevc=${hevcDecoder?.name}")
        hevcDecoder != null
    }

    // unknown limits default to 1080p
    fun maxResolution(): Pair<Int, Int> =
        listOfNotNull(avcDecoder?.let { it to DecoderSelector.AVC }, hevcDecoder?.let { it to DecoderSelector.HEVC })
            .map { (info, mime) ->
                runCatching { info.videoCaps(mime).let { it.supportedWidths.upper to it.supportedHeights.upper } }
                    .getOrDefault(1920 to 1080)
            }
            .reduceOrNull { (w1, h1), (w2, h2) -> minOf(w1, w2) to minOf(h1, h2) } ?: (1920 to 1080)

    // codec per mirror session; pipeline persists across sessions
    fun startSession(observationToken: Long = 0L) = synchronized(lock) {
        lastPipelineRetryNs = 0L
        sessionGeneration++
        stopCodec()
        accessUnits.clear()
        _resetStats()
        pipeline.beginPresentationObservation(observationToken)
    }

    fun stopSession() = synchronized(lock) {
        sessionGeneration++
        pipeline.endPresentationObservation()
        stopCodec()
    }

    private fun _resetStats() {
        fps = 0; bitrateBps = 0; frameCount = 0; codecName = ""
        droppedFrames = 0; framePacingJitterUs = 0
        lateOutputFrames = 0
        coalescedOutputs = 0
        decodedFrames = 0
        _framesThisSec = 0; _bytesThisSec = 0
    }

    private fun _updateStats(size: Int) {
        val now = System.currentTimeMillis()
        if (now - _lastStatReset >= 1000) {
            fps = _framesThisSec
            bitrateBps = _bytesThisSec * 8
            framePacingJitterUs = _computeFramePacingJitterUs()
            _framesThisSec = 0
            _bytesThisSec = 0
            _lastStatReset = now
            if (benchmarkLog) _emitBenchmarkLine()
        }
        _framesThisSec++
        _bytesThisSec += size
        frameCount++
    }

    private fun _emitBenchmarkLine() {
        val msg = "fps=$fps bitrate=${bitrateBps / 1000}kbps " +
            "jitter=${framePacingJitterUs}us frames=$frameCount " +
            "dropped=$droppedFrames codec=$codecName " +
            "res=${videoWidth}x${videoHeight}"
        Log.i(BENCH_TAG, msg)
        benchmarkLogCallback?.invoke(msg)
    }

    /** A codec (re)start decided under the media lock, committed after the unlocked GL work. */
    private class PendingCodecStart(val h265: Boolean, val data: ByteArray, val ntpTimeNs: Long,
                                    val generation: Long, val width: Int, val height: Int)

    fun feedFrame(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {
        // The blocking part of a codec (re)start is the GL pipeline restart: it joins the
        // render thread and waits for a fresh SurfaceTexture. Decide under the lock, perform
        // that work without the lock, then commit under the lock again. A surface rebind, a
        // session end or a stop therefore never queues behind a decoder restart.
        var pending: PendingCodecStart? = null
        synchronized(lock) {
            _updateStats(data.size)
            if (videoWidth == 0 || videoHeight == 0) return
            if (pipeline.failed) {
                stopCodec()
                val now = System.nanoTime()
                // Retry only on configured keyframes, at most once per second.
                // A failed driver must not create an unbounded restart loop.
                if (lastPipelineRetryNs != 0L && now - lastPipelineRetryNs < 1_000_000_000L) return
            }

            if (parameterCodec != isH265) { accessUnits.clear(); parameterCodec = isH265 }
            val (keyframe, configuredData) = accessUnits.prepare(data, isH265)
            if (codec == null || isH265 != currentH265) {
                // a stale reference frame decodes to corruption, so wait for a keyframe to (re)start
                if (!keyframe) {
                    if (codec != null) stopCodec()
                    return
                }
                stopCodec()
                pending = PendingCodecStart(isH265, configuredData, ntpTimeNs,
                    sessionGeneration, videoWidth, videoHeight)
            } else {
                _feedAndDrain(configuredData, ntpTimeNs)
                return
            }
        }

        val start = pending ?: return
        // Unlocked: the GL lifecycle work that used to hold the media lock.
        lastPipelineRetryNs = System.nanoTime()
        // A released decoder may leave buffers and ambiguous PTS in its producer queue.
        // Give each decoder a fresh input SurfaceTexture, preserving the attached display
        // and session token. Display-only rebinds still keep it.
        pipeline.release()
        val started = pipeline.start()
        pipeline.setVideoSize(start.width, start.height)

        synchronized(lock) {
            if (start.generation != sessionGeneration) return
            if (!started) {
                // GL initialisation failed. Report it instead of silently restarting on the
                // next keyframe with no input surface.
                Log.w(TAG, "Video pipeline failed to start; awaiting a fresh configured keyframe")
                reportPresentationFailure()
                return
            }
            if (start.width != videoWidth || start.height != videoHeight) {
                pipeline.setVideoSize(videoWidth, videoHeight)
            }
            try {
                if (!startCodecLocked(start.h265)) return
                _feedAndDrain(start.data, start.ntpTimeNs)
            } catch (e: Exception) {
                Log.w(TAG, "Codec error, resetting", e)
                reportPresentationFailure()
                stopCodec()
            }
        }
    }

    /**
     * Codec input, drain and the "accepts input but produces no output" watchdog.
     * The caller holds [lock].
     */
    private fun _feedAndDrain(configuredData: ByteArray, ntpTimeNs: Long) {
        try {
            _feedToCodec(configuredData, ntpTimeNs)
            drainOutput()
            // Input FPS alone must never hide a decoder that accepts packets
            // but no longer produces pictures. Idle/paused sources don't feed
            // packets and therefore don't trigger this check.
            val lastProgress = _lastOutputFrameNs.takeIf { it != 0L } ?: codecStartedAtNs
            if (codec != null && queuedSinceOutput >= 30 && System.nanoTime() - lastProgress > 2_000_000_000L) {
                Log.w(TAG, "Decoder accepts input without output; awaiting a fresh configured keyframe")
                reportPresentationFailure()
                stopCodec()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Codec error, resetting", e)
            reportPresentationFailure()
            stopCodec()
        }
    }

    private fun _feedToCodec(data: ByteArray, ntpTimeNs: Long) {
        val c = codec ?: return
        // dropping a frame desyncs decoder until the next keyframe, but source would only send one on (re)connect
        // A TV decoder/compositor may need several hundred milliseconds to warm
        // up. Queueing the first compressed packet does not mean warm-up finished.
        val warmingUp = System.nanoTime() - codecStartedAtNs < 2_000_000_000L
        val retries = if (!firstFrameQueued || warmingUp) FIRST_FEED_RETRIES else FEED_RETRIES
        repeat(retries) {
            // Return completed output slots before waiting for an input slot.
            drainOutput()
            val idx = c.dequeueInputBuffer(FEED_WAIT_US)
            if (idx >= 0) {
                val buf = c.getInputBuffer(idx)
                if (buf == null) {
                    // The slot was already dequeued. Returning here would leak it, and the
                    // "no output" watchdog would stay silent because queuedSinceOutput never
                    // advances, leaving the decoder stalled forever. Treat it as a failure.
                    Log.w(TAG, "Decoder returned a null input buffer; resetting and awaiting a configured keyframe")
                    reportPresentationFailure()
                    stopCodec()
                    return
                }
                buf.clear()
                buf.put(data)
                c.queueInputBuffer(idx, 0, data.size, ntpTimeNs / 1000, 0)
                residence.queued(ntpTimeNs / 1000, System.nanoTime())
                firstFrameQueued = true
                queuedSinceOutput++
                return
            }
            drainOutput()
        }
        droppedFrames++
        Log.w(TAG, "Decoder input queue full; dropping frame. drops=$droppedFrames")
        // A single missing HEVC reference can suppress output for the entire GOP.
        // Do not keep accepting dependent frames and report a false healthy FPS.
        Log.w(TAG, "Compressed reference frame lost; resetting and awaiting configured keyframe")
        reportPresentationFailure()
        stopCodec()
    }

    private fun reportPresentationFailure() {
        pipeline.awaitFreshSubmission()
        pipeline.presentationSession()?.let { session ->
            runCatching { onPresentationFailure?.invoke(session.token) }
        }
    }

    /**
     * Codec-only start. The caller holds [lock] and has already restarted the GL pipeline
     * outside it, so this must never perform blocking GL lifecycle work.
     * Returns false when the pipeline has no input surface yet.
     */
    private fun startCodecLocked(h265: Boolean): Boolean {
        val s = pipeline.inputSurface ?: return false
        currentH265 = h265
        val mime = if (h265) DecoderSelector.HEVC else DecoderSelector.AVC
        val info = (if (h265) hevcDecoder else avcDecoder) ?: error("no decoder selected for $mime")

        firstFrameQueued = false
        try {
            _startWithLadder(info, mime, s, h265)
        } catch (e: Exception) {
            // strict hw decoders reject configs beyond their real limits
            val sw = selector.software(mime, videoWidth, videoHeight) ?: throw e
            Log.w(TAG, "Hardware decoder failed, trying software fallback", e)
            _startWithLadder(sw, mime, s, h265)
        }
        Log.i(TAG, "Video codec started: $mime ${videoWidth}x${videoHeight} ($codecName)")
        return true
    }

    private fun _startWithLadder(info: MediaCodecInfo, mime: String, s: Surface, h265: Boolean) {
        var tryNum = 0
        while (true) {
            val format = _format(mime, info)
            val more = selector.lowLatencyOptions(format, info, mime, tryNum)
            try {
                _startDecoder(MediaCodec.createByCodecName(info.name), format, s, h265)
                return
            } catch (e: Exception) {
                if (!more) throw e
                Log.w(TAG, "configure try $tryNum failed: $format", e)
                tryNum++
            }
        }
    }

    private fun _format(mime: String, info: MediaCodecInfo) = MediaFormat.createVideoFormat(mime, videoWidth, videoHeight).apply {
        setInteger(MediaFormat.KEY_FRAME_RATE, maxFps)
        configuredAdaptive = selector.adaptive(info, mime)
        // Keep Goldfish's original adaptive buffer allocation, but don't reuse
        // its codec across iPhone size changes (stale crop/width bug).
        reconfigureOnSizeChange = info.name.startsWith("c2.goldfish", ignoreCase = true)
        val edge = maxOf(1920, videoWidth, videoHeight)
        val squareSupported = runCatching { info.videoCaps(mime).isSizeSupported(edge, edge) }.getOrDefault(false)
        configuredMaxWidth = if (configuredAdaptive && squareSupported) edge else videoWidth
        configuredMaxHeight = if (configuredAdaptive && squareSupported) edge else videoHeight
        if (configuredAdaptive) {
            setInteger(MediaFormat.KEY_MAX_WIDTH, configuredMaxWidth)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, configuredMaxHeight)
        }
        // Sender-controlled sizes can be arbitrarily large; compute in Long so the hint can
        // never overflow Int, then clamp to a sane encoder-side ceiling.
        val maxInputSize = (configuredMaxWidth.toLong() * configuredMaxHeight.toLong() * 3L / 4L)
            .coerceIn(1024L * 1024L, Int.MAX_VALUE.toLong())
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize.toInt())
        if (enforceSdr) {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, if (keyAllowFrameDrop) 1 else 0)
        }
    }

    private fun _startDecoder(c: MediaCodec, format: MediaFormat, surface: Surface, h265: Boolean) {
        try {
            c.configure(format, surface, null, 0)
            c.start()
        } catch (e: Exception) {
            try { c.release() } catch (_: Exception) {}
            throw e
        }
        codec = c
        codecStartedAtNs = System.nanoTime()
        queuedSinceOutput = 0
        // A later startSession cannot relabel output still queued in this decoder.
        decoderObservationSession = pipeline.presentationSession()
        codecName = (if (h265) "H.265" else "H.264") + " (${c.name})"
    }

    private fun stopCodec() {
        residence.clear()
        decoderResidenceMs = -1L
        queuedSinceOutput = 0
        codecStartedAtNs = 0L
        _frameIntervalIdx = 0
        _frameIntervalCount = 0
        _lastOutputFrameNs = 0L
        _ptsBaseUs = Long.MIN_VALUE
        _wallBaseNs = 0L
        lastPresentedAtNs = 0L
        codec?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        codec = null
        decoderObservationSession = null
    }

    private fun drainOutput() {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var pendingIndex = -1
        var pendingPts = 0L
        var pendingResidence = -1L
        while (true) {
            val idx = c.dequeueOutputBuffer(info, 0)
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(TAG, "Decoder output format changed: ${c.outputFormat}")
                continue
            }
            if (idx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) continue
            if (idx < 0) break
            decodedFrames++
            queuedSinceOutput = 0
            _recordOutputFrameTime()
            val age = residence.output(info.presentationTimeUs, System.nanoTime()) ?: -1L
            if (!scheduledOutputBufferRelease) {
                if (pendingIndex >= 0) {
                    c.releaseOutputBuffer(pendingIndex, false)
                    coalescedOutputs++
                }
                pendingIndex = idx
                pendingPts = info.presentationTimeUs
                pendingResidence = age
            } else {
                presentOutput(c, idx, info.presentationTimeUs, age)
            }
        }
        if (pendingIndex >= 0) presentOutput(c, pendingIndex, pendingPts, pendingResidence)
    }

    private fun presentOutput(c: MediaCodec, idx: Int, ptsUs: Long, residenceMs: Long) {
            decoderResidenceMs = residenceMs
            // Native mirroring PTS is mapped to local CLOCK_REALTIME. During
            // overload, don't make the GPU present every obsolete decoded frame
            // and push back on the TCP reader. Reference decoding is preserved.
            // Keep a 10fps preview while catching up; never suppress a still
            // image solely because it is old. Synthetic/foreign clock PTS and
            // explicitly scheduled mode keep their existing behavior.
            val wallAgeMs = System.currentTimeMillis() - ptsUs / 1000L
            val ageMs = if (ptsUs > 0 && wallAgeMs in -10_000L..60_000L) wallAgeMs else residenceMs
            val nowNs = System.nanoTime()
            if (!scheduledOutputBufferRelease && ageMs in staleFrameThresholdMs..60_000L &&
                lastPresentedAtNs != 0L && nowNs - lastPresentedAtNs < 100_000_000L) {
                c.releaseOutputBuffer(idx, false)
                lateOutputFrames++
                return
            }
            lastPresentedAtNs = nowNs
            if (scheduledOutputBufferRelease) {
                // schedule frame at VSYNC matching its NTP presentation time
                if (_ptsBaseUs == Long.MIN_VALUE) {
                    _ptsBaseUs = ptsUs
                    _wallBaseNs = System.nanoTime()
                }
                val releaseTimeNs = _wallBaseNs + (ptsUs - _ptsBaseUs) * 1000L
                pipeline.observeDecoderOutput(decoderObservationSession, ptsUs, releaseTimeNs)
                c.releaseOutputBuffer(idx, releaseTimeNs)
            } else if (ptsUs <= 0L) {
                // Missing sender PTS must not label every SurfaceTexture buffer as
                // the same cached frame. Release immediately at monotonic "now";
                // keep the original media PTS for diagnostics, never schedule ahead.
                val releaseTimeNs = System.nanoTime()
                pipeline.observeDecoderOutput(decoderObservationSession, ptsUs, releaseTimeNs)
                c.releaseOutputBuffer(idx, releaseTimeNs)
            } else {
                pipeline.observeDecoderOutput(decoderObservationSession, ptsUs, ptsUs * 1000L)
                c.releaseOutputBuffer(idx, true)
            }
    }

    fun release() {
        synchronized(lock) {
            // Invalidate any in-flight unlocked restart before the GL thread is joined.
            sessionGeneration++
            pipeline.endPresentationObservation()
            stopCodec()
            _resetStats()
        }
        // The GL thread join happens without the media lock, so a surface rebind or a
        // session-end path never waits behind teardown.
        pipeline.release()
    }

    private fun _recordOutputFrameTime() {
        val now = System.nanoTime()
        if (_lastOutputFrameNs > 0) {
            _frameIntervalsNs[_frameIntervalIdx % _frameIntervalsNs.size] = now - _lastOutputFrameNs
            _frameIntervalIdx++
            _frameIntervalCount++
        }
        _lastOutputFrameNs = now
    }

    private fun _computeFramePacingJitterUs(): Long {
        val count = _frameIntervalCount.coerceAtMost(_frameIntervalsNs.size)
        if (count < 2) return 0

        var sum = 0.0
        var sumSq = 0.0
        for (i in 0 until count) {
            val interval = _frameIntervalsNs[i].toDouble()
            sum += interval
            sumSq += interval * interval
        }
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return (kotlin.math.sqrt(variance.coerceAtLeast(0.0)) / 1000.0).toLong()
    }

    companion object {
        private const val TAG = "VideoRenderer"
        private const val BENCH_TAG = "BENCHMARK"
        // A completed output can become available just after drainOutput().
        // A 20ms input wait then delays returning that slot and caps throughput
        // below a 60fps sender. Poll output frequently, with the same total budget.
        private const val FEED_WAIT_US = 1_000L
        // This is a bounded timeout, not a per-frame delay. Available input/output
        // slots are processed immediately. Shed obsolete decoded output instead
        // of breaking the compressed stream on a brief hardware scheduling stall.
        private const val FEED_RETRIES = 1000
        private const val FIRST_FEED_RETRIES = 1500
    }
}
