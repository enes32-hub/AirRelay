package io.github.jqssun.airplay

import android.app.Instrumentation
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaExtractor
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.jqssun.airplay.renderer.VideoRenderer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real local AVC encode -> production VideoRenderer -> SurfaceTexture -> EGL -> RGBX buffers.
 * This is not an AirPlay, JNI, audio, iPhone, TV panel or end-to-end latency acceptance test.
 * Call run(this) from an Instrumentation worker thread while no real video test is active.
 * No preferences, service state, network ports or production rendering options are changed.
 */
object SyntheticVideoProbe {
    private const val WIDTH = 320
    private const val HEIGHT = 192
    private const val FPS = 15
    private const val FRAME_COUNT = 36
    private const val MIME = "video/avc"

    fun startRecordingFeed(renderer: VideoRenderer, zeroPts: Boolean = false): java.io.Closeable {
        val encoded = encodePattern()
        renderer.selectDecoders(WIDTH, HEIGHT, FPS, false)
        renderer.setResolution(WIDTH, HEIGHT)
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val failure = AtomicReference<Throwable>()
        val thread = Thread {
            try {
                var count = 0L
                while (running.get()) for (frame in encoded.frames) {
                    if (!running.get()) break
                    renderer.feedFrame(frame.bytes, if (zeroPts) 0L else 1_000_000_000L + count++ * 1_000_000_000L / FPS, false)
                    SystemClock.sleep(1000L / FPS)
                }
            } catch (e: Throwable) { failure.set(e) }
        }.apply { start() }
        return java.io.Closeable { running.set(false); thread.join(3000); check(!thread.isAlive); failure.get()?.let { throw it } }
    }

    fun run(instrumentation: Instrumentation): JSONObject {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Video probe must run off the main thread" }
        val begun = SystemClock.elapsedRealtime()
        val encoded = encodePattern()
        check(encoded.frames.size >= FRAME_COUNT - 2) { "Encoder produced too few AVC frames" }
        val callbacks = ConcurrentHashMap<Long, AtomicInteger>()
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = PixelSink()
        val sessions = JSONArray()
        try {
            renderer.selectDecoders(WIDTH, HEIGHT, FPS, false)
            renderer.setResolution(WIDTH, HEIGHT)
            renderer.setSurface(sink.reader.surface)
            renderer.onFirstFrameSubmitted = { token ->
                callbacks.computeIfAbsent(token) { AtomicInteger() }.incrementAndGet()
            }
            // These are production defaults. The probe does not override timing/drop/codec settings.
            check(!renderer.scheduledOutputBufferRelease)
            repeat(3) { sessionIndex ->
                val token = 9_000L + sessionIndex
                val callbackCount = callbacks.computeIfAbsent(token) { AtomicInteger() }
                renderer.startSession(token)
                // Rebinding repaints the last cached texture in sessions 2/3, before any
                // new decoder input. It must not claim that the new session is live.
                renderer.clearSurface(sink.reader.surface)
                renderer.setSurface(sink.reader.surface)
                SystemClock.sleep(180)
                check(callbackCount.get() == 0) { "Cached texture falsely marked session $token live" }
                sink.beginWindow()
                val start = SystemClock.elapsedRealtime()
                for ((index, frame) in encoded.frames.withIndex()) {
                    val due = start + index * 1000L / FPS
                    val delay = due - SystemClock.elapsedRealtime()
                    if (delay > 0) SystemClock.sleep(delay)
                    // Replay original PTS on every session intentionally, including duplicate
                    // timestamps across decoder restarts. The encoded pattern itself moves.
                    renderer.feedFrame(frame.bytes, frame.ptsUs * 1000L, false)
                    check(SystemClock.elapsedRealtime() - start < 12_000) { "Decoder feed exceeded bounded probe duration" }
                    sink.failure.get()?.let {
                        throw AssertionError("RGBX sink failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}", it)
                    }
                }
                val deadline = SystemClock.elapsedRealtime() + 2_000
                while (SystemClock.elapsedRealtime() < deadline &&
                    (callbackCount.get() == 0 || sink.snapshot().frames < 12)) SystemClock.sleep(30)
                val pixels = sink.snapshot()
                check(callbackCount.get() == 1) { "Expected one real first-frame callback, received ${callbackCount.get()}" }
                sink.failure.get()?.let {
                    throw AssertionError("RGBX sink failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}", it)
                }
                check(pixels.frames >= 12) { "Only ${pixels.frames} real RGBX output buffers arrived" }
                check(pixels.signatures >= 3) { "Output did not contain a changing decoded pattern" }
                check(renderer.codecName.isNotBlank()) { "Production decoder did not start" }
                sessions.put(JSONObject().put("token", token).put("result", "PASS")
                    .put("firstFrameCallbacks", callbackCount.get()).put("rgbBuffers", pixels.frames)
                    .put("distinctPixelSignatures", pixels.signatures).put("decoder", renderer.codecName)
                    .put("encodedFramesFed", encoded.frames.size).put("inputDrops", renderer.droppedFrames)
                    .put("elapsedMs", SystemClock.elapsedRealtime() - start))
                renderer.stopSession()
            }
            return JSONObject().put("mode", "LOCAL_ENCODED_AVC").put("passed", true)
                .put("encoder", encoded.encoder).put("width", WIDTH).put("height", HEIGHT)
                .put("sourceFps", FPS).put("outputPixelFormat", "RGBX_8888").put("sessions", sessions)
                .put("elapsedMs", SystemClock.elapsedRealtime() - begun)
                .put("actualMediaCodecAndEgl", true).put("realAirPlayValidated", false)
                .put("audioValidated", false).put("panelScanoutValidated", false)
        } finally {
            renderer.onFirstFrameSubmitted = null
            try { renderer.release() } finally { sink.close() }
        }
    }

    fun runResolutionChanges(instrumentation: Instrumentation): JSONObject {
        val sizes = listOf(498 to 1080, 1920 to 1080, 498 to 1080, 1920 to 886, 498 to 1080)
        val sources = sizes.distinct().associateWith { (w, h) -> encodePattern(w, h, 24) }
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = PixelSink()
        val results = JSONArray()
        try {
            renderer.selectDecoders(1920, 1080, FPS, false)
            renderer.setSurface(sink.reader.surface)
            renderer.startSession(9999)
            repeat(3) { round ->
                for ((w, h) in sizes) {
                    renderer.setResolution(w, h)
                    sink.beginWindow()
                    val source = sources.getValue(w to h)
                    val start = SystemClock.elapsedRealtime()
                    for ((index, frame) in source.frames.withIndex()) {
                        // Mirror continues while a remote player owns the visible view.
                        // Reattach without reconnecting/restarting the mirror decoder.
                        if (index == 8) renderer.clearSurface(sink.reader.surface)
                        if (index == 12) renderer.setSurface(sink.reader.surface)
                        val wait = start + index * 1000L / FPS - SystemClock.elapsedRealtime()
                        if (wait > 0) SystemClock.sleep(wait)
                        renderer.feedFrame(frame.bytes, frame.ptsUs * 1000, false)
                    }
                    SystemClock.sleep(150)
                    sink.failure.get()?.let { throw it }
                    val pixels = sink.snapshot()
                    check(pixels.frames >= 6 && pixels.signatures >= 2) { "Transition $w x $h stalled: $pixels" }
                    check(renderer.droppedFrames == 0L) { "Decoder input dropped during size transition" }
                    results.put(JSONObject().put("round", round).put("width", w).put("height", h)
                        .put("frames", pixels.frames).put("signatures", pixels.signatures))
                }
            }
            return JSONObject().put("passed", true).put("legs", results)
        } finally { renderer.release(); sink.close() }
    }

    /** Fault injection: lost texture observations must not poison the next decoder. */
    fun runObservationRecovery(instrumentation: Instrumentation): JSONObject {
        val source = encodePattern()
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = PixelSink()
        val callbacks = ConcurrentHashMap<Long, AtomicInteger>()
        try {
            renderer.selectDecoders(WIDTH, HEIGHT, FPS, false)
            renderer.setResolution(WIDTH, HEIGHT)
            renderer.setSurface(sink.reader.surface)
            renderer.onFirstFrameSubmitted = { callbacks.computeIfAbsent(it) { AtomicInteger() }.incrementAndGet() }
            repeat(2) { index ->
                val token = 12000L + index
                renderer.startSession(token)
                sink.beginWindow()
                val start = SystemClock.elapsedRealtime()
                source.frames.forEachIndexed { n, frame ->
                    val wait = start + n * 1000L / FPS - SystemClock.elapsedRealtime()
                    if (wait > 0) SystemClock.sleep(wait)
                    renderer.feedFrame(frame.bytes, frame.ptsUs * 1000, false)
                }
                SystemClock.sleep(200)
                check(callbacks[token]?.get() == 1) { "Reconnect token $token never submitted first frame after lost observations" }
                check(sink.snapshot().frames >= 12 && sink.snapshot().signatures >= 3)
                sink.failure.get()?.let { throw it }
                if (index == 0) {
                    // Model a lost/stalled texture consumer without exposing test hooks in production.
                    val field = VideoRenderer::class.java.getDeclaredField("pipeline").apply { isAccessible = true }
                    val pipeline = field.get(renderer) as io.github.jqssun.airplay.renderer.VideoPipeline
                    repeat(300) { pipeline.observeDecoderOutput(pipeline.presentationSession(), 90_000_000L + it, 90_000_000_000L + it * 1000) }
                }
                renderer.stopSession()
            }
            return JSONObject().put("passed", true).put("faultInjection", "300 unconsumed decoder observations")
                .put("actualDecodedReconnect", true).put("realIPhoneValidated", false)
        } finally { renderer.release(); sink.close() }
    }

    /** An exception on the actual GL thread must report failure and recover
     * on a new configured frame without ending the presentation session. */
    fun runPipelineFailureRecovery(instrumentation: Instrumentation): JSONObject {
        val source = encodePattern()
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = PixelSink()
        val submitted = AtomicInteger()
        val failures = AtomicInteger()
        try {
            renderer.selectDecoders(WIDTH, HEIGHT, FPS, false)
            renderer.setResolution(WIDTH, HEIGHT)
            renderer.setSurface(sink.reader.surface)
            renderer.startSession(13000)
            renderer.onPresentationFailure = { if (it == 13000L) failures.incrementAndGet() }
            renderer.onFirstFrameSubmitted = {
                if (submitted.incrementAndGet() == 1) error("Injected GL-thread failure")
            }
            repeat(3) { round ->
                sink.beginWindow()
                val start = SystemClock.elapsedRealtime()
                source.frames.forEachIndexed { index, frame ->
                    val delay = start + index * 1000L / FPS - SystemClock.elapsedRealtime()
                    if (delay > 0) SystemClock.sleep(delay)
                    renderer.feedFrame(frame.bytes, (frame.ptsUs + round * 3_000_000L) * 1000, false)
                }
            }
            SystemClock.sleep(150)
            check(failures.get() == 1) { "Expected exactly one pipeline failure; got ${failures.get()}" }
            check(submitted.get() >= 2) { "No fresh frame after pipeline restart" }
            check(sink.snapshot().frames >= 8 && sink.snapshot().signatures >= 2) { "Recovered video did not move" }
            return JSONObject().put("passed", true).put("failures", failures.get())
                .put("freshSubmissions", submitted.get()).put("sameSession", true)
        } finally { renderer.release(); sink.close() }
    }

    private data class EncodedFrame(val bytes: ByteArray, val ptsUs: Long)

    /** Same real decoder/EGL workload for before/after throughput comparison;
     * ImageReader readback is not an iPhone end-to-end latency measurement. */
    fun runThroughput(instrumentation: Instrumentation, targetFps: Int = 60, software: Boolean = false): JSONObject {
        val source = encodePattern(498, 1080, 90)
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = PixelSink(1920, 1080)
        val rounds = JSONArray()
        try {
            renderer.selectDecoders(1920, 1080, targetFps, false)
            if (software) {
                val info = checkNotNull(renderer.selector.software(MIME, 1920, 1080))
                VideoRenderer::class.java.getDeclaredField("avcDecoder").apply { isAccessible = true }.set(renderer, info)
            }
            renderer.setResolution(498, 1080)
            renderer.setSurface(sink.reader.surface)
            repeat(3) { round ->
                renderer.startSession(14000L + round)
                sink.beginWindow()
                val start = SystemClock.elapsedRealtime()
                val feedTimes = ArrayList<Long>()
                val sourceClockMs = System.currentTimeMillis()
                source.frames.forEachIndexed { index, frame ->
                    val wait = start + index * 1000L / targetFps - SystemClock.elapsedRealtime()
                    if (wait > 0) SystemClock.sleep(wait)
                    val before = SystemClock.elapsedRealtime()
                    renderer.feedFrame(frame.bytes, (sourceClockMs + index * 1000L / targetFps) * 1_000_000L, false)
                    feedTimes.add(SystemClock.elapsedRealtime() - before)
                }
                val elapsed = SystemClock.elapsedRealtime() - start
                SystemClock.sleep(250)
                sink.failure.get()?.let { throw it }
                val pixels = sink.snapshot()
                check(pixels.frames >= 12 && pixels.signatures >= 3)
                val sorted = feedTimes.sorted()
                rounds.put(JSONObject().put("round", round).put("feedElapsedMs", elapsed)
                    .put("targetElapsedMs", source.frames.size * 1000 / targetFps).put("framesFed", source.frames.size)
                    .put("rgbFrames", pixels.frames).put("inputDrops", renderer.droppedFrames)
                    .put("decoder", renderer.codecName)
                    .put("feedP50Ms", sorted[sorted.size/2]).put("feedP95Ms", sorted[sorted.size*95/100]))
                renderer.stopSession()
            }
            return JSONObject().put("rounds", rounds).put("realIPhoneLatencyMeasured", false)
        } finally { renderer.release(); sink.close() }
    }
    private data class EncodedPattern(val encoder: String, val frames: List<EncodedFrame>)

    /** Long-GOP HEVC through the real TV decoder; no sender or captured user media. */
    fun runTvHevc(instrumentation: Instrumentation): JSONObject {
        val frames = ArrayList<EncodedFrame>()
        val extractor = MediaExtractor()
        try {
            instrumentation.context.assets.openFd("media/hevc-portrait.mp4").use { asset ->
                extractor.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "video/hevc"
            }
            val format = extractor.getTrackFormat(track)
            val config = annexB(bytes(checkNotNull(format.getByteBuffer("csd-0")).duplicate()))
            extractor.selectTrack(track)
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0); buffer.limit(size)
                val data = annexB(bytes(buffer))
                frames.add(EncodedFrame(if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    config + data else data, extractor.sampleTime))
                extractor.advance()
            }
        } finally { extractor.release() }
        check(frames.size == 240)
        val renderer = VideoRenderer(instrumentation.targetContext)
        val sink = TvPixelSink(instrumentation)
        val rounds = JSONArray()
        try {
            check(renderer.selectDecoders(3840, 2160, 120, true)) { "No hardware HEVC decoder selected" }
            renderer.setResolution(996, 2160)
            renderer.setSurface(sink.surface)
            repeat(3) { round ->
                renderer.startSession(15000L + round)
                sink.beginWindow()
                val start = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis() + 90L
                frames.forEachIndexed { index, frame ->
                    if (round == 1 && index == 1) {
                        // Model a slow initial TV compositor without dropping sender data.
                        val field = VideoRenderer::class.java.getDeclaredField("pipeline").apply { isAccessible = true }
                        val pipeline = field.get(renderer)
                        val monitor = checkNotNull(pipeline.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(pipeline))
                        val held = java.util.concurrent.CountDownLatch(1)
                        Thread {
                            synchronized(monitor) { held.countDown(); SystemClock.sleep(850) }
                        }.start()
                        check(held.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    }
                    val wait = start + frame.ptsUs / 1000 - SystemClock.elapsedRealtime()
                    if (wait > 0) SystemClock.sleep(wait)
                    if (index == 120) sink.beginWindow()
                    renderer.feedFrame(frame.bytes, wall * 1_000_000 + frame.ptsUs * 1000, true)
                    if (index % 12 == 11) sink.sample()
                    check(SystemClock.elapsedRealtime() - start < 15_000) { "HEVC feed stalled" }
                }
                SystemClock.sleep(200)
                sink.failure.get()?.let { throw it }
                val pixels = sink.snapshot()
                val result = JSONObject().put("round", round).put("lastHalfPixelSamples", pixels.frames)
                    .put("lastHalfDistinctImages", pixels.signatures).put("inputDrops", renderer.droppedFrames)
                    .put("decoder", renderer.codecName).put("elapsedMs", SystemClock.elapsedRealtime() - start)
                rounds.put(result)
                check(pixels.frames >= 6 && pixels.signatures >= 3) { "HEVC output froze: $result" }
                check(renderer.droppedFrames == 0L) { "Compressed HEVC reference frame dropped: $result" }
                renderer.stopSession()
            }
            return JSONObject().put("passed", true).put("fixture", "synthetic 996x2160 HEVC 60fps, single GOP")
                .put("requestedFps", 120).put("rounds", rounds).put("realIPhoneValidated", false)
        } finally { renderer.release(); sink.close() }
    }

    /** PixelCopy handles the TV's RGB10 window format without forcing a renderer format. */
    private class TvPixelSink(private val instrumentation: Instrumentation) : AutoCloseable {
        private val activity = instrumentation.startActivitySync(android.content.Intent(
            instrumentation.targetContext, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        private lateinit var view: android.view.SurfaceView
        private val thread = HandlerThread("TvPixelCopy").apply { start() }
        private val bitmap = android.graphics.Bitmap.createBitmap(WIDTH, HEIGHT, android.graphics.Bitmap.Config.ARGB_8888)
        private var samples = 0
        private val signatures = HashSet<Long>()
        val failure = AtomicReference<Throwable?>()
        val surface get() = view.holder.surface
        init {
            instrumentation.runOnMainSync {
                // The probe needs a valid surface for its whole run: without this the screen can
                // sleep mid-probe and the SurfaceView never becomes valid, which used to look like
                // a decoder freeze ("HEVC output froze") instead of a display problem.
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                view = android.view.SurfaceView(activity)
                activity.setContentView(view)
            }
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (!surface.isValid && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
            check(surface.isValid)
        }
        fun beginWindow() { samples = 0; signatures.clear() }
        fun snapshot() = PixelSnapshot(samples, signatures.size)
        fun sample() {
            val done = java.util.concurrent.CountDownLatch(1)
            val result = AtomicInteger(-1)
            android.view.PixelCopy.request(surface, bitmap, { result.set(it); done.countDown() }, Handler(thread.looper))
            check(done.await(2, java.util.concurrent.TimeUnit.SECONDS)) { "TV pixel copy timed out" }
            if (result.get() == android.view.PixelCopy.ERROR_SOURCE_NO_DATA) return
            check(result.get() == android.view.PixelCopy.SUCCESS) { "TV pixel copy failed: ${result.get()}" }
            var signature = 17L
            for (y in intArrayOf(24,72,120,168)) for (x in intArrayOf(20,60,100,140,180,220,260,300)) {
                val pixel = bitmap.getPixel(x,y)
                val light = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                signature = signature * 31 + light / 32
            }
            samples++; signatures.add(signature)
        }
        override fun close() {
            instrumentation.runOnMainSync { activity.finish() }
            thread.quitSafely(); thread.join(1000); bitmap.recycle()
        }
    }

    private fun encodePattern(width: Int = WIDTH, height: Int = HEIGHT, frameCount: Int = FRAME_COUNT): EncodedPattern {
        val WIDTH = width
        val HEIGHT = height
        val FRAME_COUNT = frameCount
        val encoder = MediaCodec.createEncoderByType(MIME)
        var started = false
        try {
            val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start(); started = true
            val result = ArrayList<EncodedFrame>()
            val info = MediaCodec.BufferInfo()
            var config = ByteArray(0)
            var inputFrame = 0
            var inputEos = false
            var outputEos = false
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!outputEos && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEos) {
                    val index = encoder.dequeueInputBuffer(2_000)
                    if (index >= 0) {
                        val ptsUs = 1_000_000L + inputFrame * 1_000_000L / FPS
                        if (inputFrame == FRAME_COUNT) {
                            encoder.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            val image = checkNotNull(encoder.getInputImage(index)) { "Encoder has no flexible YUV input image" }
                            fillPattern(image, inputFrame++)
                            encoder.queueInputBuffer(index, 0, WIDTH * HEIGHT * 3 / 2, ptsUs, 0)
                        }
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, 2_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = encoder.outputFormat
                        config = listOf("csd-0", "csd-1").mapNotNull { key ->
                            output.getByteBuffer(key)?.let { annexB(bytes(it.duplicate())) }
                        }.fold(ByteArray(0)) { acc, data -> acc + data }
                    }
                    index >= 0 -> {
                        if (info.size > 0) {
                            val buffer = checkNotNull(encoder.getOutputBuffer(index)).duplicate()
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val data = annexB(bytes(buffer))
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                config = data
                            } else {
                                val keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                check(result.isNotEmpty() || keyframe) { "AVC stream did not start with a keyframe" }
                                check(config.isNotEmpty()) { "AVC configuration was not emitted" }
                                result.add(EncodedFrame(if (keyframe) config + data else data, info.presentationTimeUs))
                            }
                        }
                        outputEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            check(outputEos) { "Local AVC encoder exceeded 20 second deadline" }
            return EncodedPattern(encoder.name, result)
        } finally {
            if (started) runCatching { encoder.stop() }
            encoder.release()
        }
    }

    private fun fillPattern(image: Image, frame: Int) {
        check(image.format == ImageFormat.YUV_420_888 && image.planes.size == 3)
        image.planes.forEachIndexed { component, plane ->
            val width = if (component == 0) image.width else image.width / 2
            val height = if (component == 0) image.height else image.height / 2
            val buffer = plane.buffer
            val start = buffer.position()
            for (y in 0 until height) for (x in 0 until width) {
                val value = if (component != 0) 128 else {
                    if ((x / 40 + y / 48 + frame / 3) % 4 < 2) 220 else 32
                }
                buffer.put(start + y * plane.rowStride + x * plane.pixelStride, value.toByte())
            }
        }
    }

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.get(it) }

    /** Android normally emits Annex B; accept explicit four-byte NAL lengths as well. */
    private fun annexB(data: ByteArray): ByteArray {
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte()) {
            if (data[2] == 0.toByte() && data[3] == 1.toByte()) return data
            if (data[2] == 1.toByte()) return byteArrayOf(0) + data
        }
        val output = ByteArrayOutputStream()
        var offset = 0
        while (offset + 4 <= data.size) {
            val size = ByteBuffer.wrap(data, offset, 4).int
            check(size > 0 && size <= data.size - offset - 4) { "Unsupported AVC buffer framing" }
            output.write(byteArrayOf(0, 0, 0, 1)); output.write(data, offset + 4, size)
            offset += size + 4
        }
        check(offset == data.size && output.size() > 0) { "Incomplete AVC NAL buffer" }
        return output.toByteArray()
    }

    private data class PixelSnapshot(val frames: Int, val signatures: Int)
    private class PixelSink(private val width: Int = WIDTH, private val height: Int = HEIGHT) : AutoCloseable {
        // The production EglCore requests RGB8 without alpha. Its emulator EGL config
        // produces native RGBX (0x2), which ImageReader must match exactly on API 36.
        val reader: ImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBX_8888, 3)
        val failure = AtomicReference<Throwable?>()
        private val thread = HandlerThread("SyntheticVideoPixelSink").apply { start() }
        private val lock = Any()
        private var frames = 0
        private val signatures = HashSet<Long>()

        init {
            reader.setOnImageAvailableListener({ available ->
                try {
                    val image = available.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        check(image.width == width && image.height == height)
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val start = buffer.position()
                        var signature = 17L
                        // Quantized luminance avoids counting tiny codec rounding changes as motion.
                        for (y in intArrayOf(24, 72, 120, 168)) for (x in intArrayOf(20, 60, 100, 140, 180, 220, 260, 300)) {
                            val offset = start + (y * height / HEIGHT) * plane.rowStride + (x * width / WIDTH) * plane.pixelStride
                            val luminance = (0..2).sumOf { buffer.get(offset + it).toInt() and 255 } / 3
                            signature = signature * 31 + luminance / 32
                        }
                        synchronized(lock) { frames++; signatures.add(signature) }
                    } finally { image.close() }
                } catch (problem: Throwable) {
                    if (failure.compareAndSet(null, problem)) Log.e("SyntheticVideoProbe", "RGBX output validation failed", problem)
                }
            }, Handler(thread.looper))
        }
        fun beginWindow() = synchronized(lock) { frames = 0; signatures.clear() }
        fun snapshot() = synchronized(lock) { PixelSnapshot(frames, signatures.size) }
        override fun close() {
            reader.setOnImageAvailableListener(null, null)
            thread.quitSafely(); thread.join(1_000)
            reader.close()
        }
    }
}
