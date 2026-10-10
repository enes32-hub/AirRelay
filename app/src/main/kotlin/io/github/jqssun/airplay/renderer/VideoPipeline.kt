package io.github.jqssun.airplay.renderer

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import io.github.jqssun.airplay.receiver.FirstFrameGate

// decodes into an app-owned SurfaceTexture that outlives display surface
// gl thread blits it to any surface attached, so fullscreen toggles re-point display without restarting codec
class VideoPipeline {
    @Volatile
    private var recordingTarget: io.github.jqssun.airplay.recording.CleanRecording.Target? = null
    private var recordWindow = EGL14.EGL_NO_SURFACE
    private var recordDirty = false
    private var recordDetachPending = false
    private var recordLastNs = 0L
    @Volatile
    private var recordingFailed = false

    /**
     * Requests a recording-target change.
     *
     * Never throws and never blocks unboundedly. Attaching returns immediately; detaching
     * waits at most [RECORD_DETACH_TIMEOUT_MS] for the GL thread to destroy the recording
     * EGL surface, because the encoder that owns that surface must not be stopped before
     * the detach completes. A timeout returns false: the GL thread finishes the detach on
     * its next pass, and a swap against an already-released surface is handled as a caught
     * recording failure, so the caller can still proceed without crashing.
     */
    fun setRecordingTarget(target: io.github.jqssun.airplay.recording.CleanRecording.Target?): Boolean =
        synchronized(lock) {
            if (recordingTarget === target && !recordDirty) return@synchronized true
            recordingTarget = target
            recordDirty = true
            recordingFailed = false
            lock.notifyAll()
            if (target != null) return@synchronized true
            val deadline = android.os.SystemClock.elapsedRealtime() + RECORD_DETACH_TIMEOUT_MS
            while (running && recordDetachPending) {
                val left = deadline - android.os.SystemClock.elapsedRealtime()
                if (left <= 0) {
                    Log.w(TAG, "Recording surface detach still pending after ${RECORD_DETACH_TIMEOUT_MS}ms")
                    return@synchronized false
                }
                lock.wait(left)
            }
            true
        }

    private val lock = Object()
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile var failed = false; private set
    @Volatile var onPipelineFailure: (() -> Unit)? = null
    @Volatile var onFailure: ((Long) -> Unit)? = null
    @Volatile var submittedFrames = 0L; private set
    @Volatile var lastSubmissionNs = 0L; private set

    private var egl: EglCore? = null
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var winW = 0
    private var winH = 0

    private var oesTex = 0
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private var uTexMatrix = 0
    private val texMatrix = FloatArray(16)
    private var hasFrame = false
    // Observation only; provenance comes from decoder output, never the current GL session.
    private val presentationGate = FirstFrameGate()
    private var consumedFrame: FirstFrameGate.DecoderFrame? = null
    @Volatile var onFirstFrameSubmitted: ((Long) -> Unit)? = null
    fun beginPresentationObservation(token: Long) { presentationGate.begin(token) }
    fun endPresentationObservation() { presentationGate.invalidate() }
    fun awaitFreshSubmission() { presentationGate.awaitFreshSubmission() }
    fun presentationSession() = presentationGate.decoderSession()
    fun observeDecoderOutput(session: FirstFrameGate.Session?, ptsUs: Long, surfaceTimestampNs: Long) {
        presentationGate.recordDecoderOutput(session, ptsUs, surfaceTimestampNs)
    }

    private var surfaceTexture: SurfaceTexture? = null
    var inputSurface: Surface? = null; private set

    @Volatile private var frameAvailable = false
    private var pendingDisplay: Surface? = null
    private var displayDirty = false
    private var sizeDirty = false
    @Volatile private var videoW = 0
    @Volatile private var videoH = 0

    /**
     * Starts the GL thread and waits until its input SurfaceTexture exists.
     * Returns false when the pipeline is no longer running or GL initialisation failed,
     * so the caller can report a presentation failure instead of silently restarting.
     */
    fun start(): Boolean = synchronized(lock) {
        if (running) return@synchronized true
        frameAvailable = false
        hasFrame = false
        consumedFrame = null
        displayDirty = true
        running = true
        failed = false
        thread = Thread({ _loop() }, "VideoPipeline").also { it.start() }
        while (inputSurface == null && running && !failed) lock.wait()
        inputSurface != null && !failed
    }

    fun setDisplaySurface(surface: Surface?) = synchronized(lock) {
        pendingDisplay = surface
        displayDirty = true
        lock.notifyAll()
    }

    fun setVideoSize(w: Int, h: Int) = synchronized(lock) {
        videoW = w
        videoH = h
        sizeDirty = true
        lock.notifyAll()
    }

    fun release() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        thread?.join()
        thread = null
    }

    private fun _loop() {
        try {
            egl = EglCore()
            _initGl()
        Matrix.setIdentityM(texMatrix, 0)
        synchronized(lock) {
            inputSurface = Surface(surfaceTexture)
            lock.notifyAll()
        }
        while (true) {
            var newDisplay: Surface? = null
            var displayChanged = false
            var resize = false
            var doFrame = false
            var detachRecordWindow = false
            synchronized(lock) {
                while (running && !frameAvailable && !displayDirty && !sizeDirty && !recordDirty) {
                    if (recordingTarget != null && hasFrame) { lock.wait(34); break }
                    lock.wait()
                }
                if (recordDirty) {
                    // The GL work runs outside the lock: an encoder that pushes back on
                    // eglSwapBuffers must not block a display rebind coming from the UI.
                    recordDirty = false
                    recordDetachPending = true
                    detachRecordWindow = true
                }
                if (running && sizeDirty) { resize = true; sizeDirty = false }
                if (running && displayDirty) {
                    newDisplay = pendingDisplay
                    displayChanged = true
                    displayDirty = false
                }
                if (running && frameAvailable) {
                    frameAvailable = false
                    doFrame = true
                }
            }
            if (!running) break
            if (detachRecordWindow) {
                egl?.makeCurrent()
                if (recordWindow != EGL14.EGL_NO_SURFACE) runCatching { egl?.destroySurface(recordWindow) }
                recordWindow = EGL14.EGL_NO_SURFACE
                recordLastNs = 0L
                synchronized(lock) { recordDetachPending = false; lock.notifyAll() }
            }
            if (resize && videoW > 0 && videoH > 0) surfaceTexture?.setDefaultBufferSize(videoW, videoH)
            if (displayChanged) _bindDisplay(newDisplay)
            if (doFrame) _consumeAndDraw()
            _recordFrame()
        }
        } catch (e: Exception) {
            failed = true
            runCatching { onPipelineFailure?.invoke() }
            presentationGate.awaitFreshSubmission()
            Log.e(TAG, "Display pipeline failed: ${e.javaClass.simpleName}")
            // Product-state callback only. Never acquire a codec lock from here.
            presentationGate.decoderSession()?.let { session ->
                runCatching { onFailure?.invoke(session.token) }
                    .onFailure { Log.w(TAG, "Display failure notification failed") }
            }
        } finally {
            runCatching { _releaseGl() }.onFailure { Log.w(TAG, "GL cleanup failed: ${it.javaClass.simpleName}") }
            synchronized(lock) { running = false; lock.notifyAll() }
        }
    }

    private fun _bindDisplay(surface: Surface?) {
        val egl = egl ?: return
        if (window != EGL14.EGL_NO_SURFACE) {
            egl.makeCurrent()
            egl.destroySurface(window)
            window = EGL14.EGL_NO_SURFACE
        }
        if (surface == null || !surface.isValid) return
        window = egl.windowSurface(surface)
        check(window != EGL14.EGL_NO_SURFACE) { "Display creation failed" }
        check(egl.makeCurrent(window)) { "Display surface unavailable" }
        winW = egl.query(window, EGL14.EGL_WIDTH)
        winH = egl.query(window, EGL14.EGL_HEIGHT)
        // an idle source sends no new frames, so repaint last one or new surface stays black
        if (hasFrame) _render()
    }

    private fun _consumeAndDraw() {
        val st = surfaceTexture ?: return
        val egl = egl ?: return
        if (window == EGL14.EGL_NO_SURFACE) {
            // no display: keep consuming so decoder doesn't stall
            check(egl.makeCurrent()) { "Offscreen context unavailable" }
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            consumedFrame = presentationGate.frameForTexture(st.timestamp)
            hasFrame = true
            return
        }
        check(egl.makeCurrent(window)) { "Display context unavailable" }
        st.updateTexImage()
        consumedFrame = presentationGate.frameForTexture(st.timestamp)
        st.getTransformMatrix(texMatrix)
        hasFrame = true
        _render()
    }

    // Recording state read here is either volatile or owned by this GL thread, so no
    // encoder backpressure can ever be held against the pipeline lock.
    private fun _recordFrame() {
        val target = recordingTarget ?: return
        val core = egl ?: return
        if (recordingFailed || !hasFrame || videoW <= 0 || videoH <= 0) return
        val now = System.nanoTime()
        if (recordLastNs != 0L && now - recordLastNs < 33_000_000L) return
        try {
            if (recordWindow == EGL14.EGL_NO_SURFACE) recordWindow = core.windowSurface(target.surface)
            check(recordWindow != EGL14.EGL_NO_SURFACE && core.makeCurrent(recordWindow))
            GLES20.glViewport(0, 0, target.width, target.height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            // Fit the unrotated source without stretching; controls and UI transforms are excluded.
            val scale = minOf(target.width.toFloat() / videoW, target.height.toFloat() / videoH)
            val w = (videoW * scale).toInt().coerceAtLeast(1)
            val h = (videoH * scale).toInt().coerceAtLeast(1)
            GLES20.glViewport((target.width - w) / 2, (target.height - h) / 2, w, h)
            _drawTexture()
            // MediaRecorder discards timestamps before its monotonic start time.
            core.presentationTime(recordWindow, now)
            check(core.swap(recordWindow))
            target.firstFrameNs.compareAndSet(0L, now)
            recordLastNs = now
        } catch (e: Exception) {
            recordingFailed = true
            Log.w(TAG, "Clean recording output failed: ${e.javaClass.simpleName}")
            target.failed()
        } finally { core.makeCurrent() }
    }

    private fun _drawTexture() {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, POS)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    private fun _render() {
        // surfaceChanged rebinds the display and refreshes winW/winH. Querying
        // EGL here adds two synchronous guest/host GPU round trips to EVERY frame.
        if (winW <= 0 || winH <= 0) return
        check(egl?.makeCurrent(window) == true)
        GLES20.glViewport(0, 0, winW, winH)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, POS)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, TEX)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        val submitted = egl?.swap(window) == true
        // Treat a lost EGL window as a pipeline failure. The renderer rebuilds
        // with the retained display on a later configured keyframe, even when
        // the activity never emits a second surfaceChanged callback.
        check(submitted) { "Display submission failed" }
        submittedFrames++
        lastSubmissionNs = System.nanoTime()
        val frame = consumedFrame
        if (submitted && presentationGate.acceptSubmission(frame)) {
            Log.i(TAG, "First frame submitted: session=${frame?.session?.token}")
            // Callback must remain non-blocking: only publishes product state, no JNI calls.
            frame?.session?.let { onFirstFrameSubmitted?.invoke(it.token) }
        }
    }

    private fun _initGl() {
        presentationGate.resetSurfaceObservation()
        consumedFrame = null
        program = _buildProgram()
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTex = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(oesTex).also {
            if (videoW > 0 && videoH > 0) it.setDefaultBufferSize(videoW, videoH)
            it.setOnFrameAvailableListener {
                synchronized(lock) { frameAvailable = true; lock.notifyAll() }
            }
        }
    }

    private fun _buildProgram(): Int {
        val vs = _shader(GLES20.GL_VERTEX_SHADER, VERT)
        val fs = _shader(GLES20.GL_FRAGMENT_SHADER, FRAG)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
        }
    }

    private fun _shader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "shader compile failed: ${GLES20.glGetShaderInfoLog(it)}")
        }
    }

    private fun _releaseGl() {
        // Invalidate cached images before a subsequent decoder starts, even if
        // its PTS is identical to the previous decoder's final timestamp.
        hasFrame = false
        consumedFrame = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        // Releasing the SurfaceTexture does not free the GPU driver objects created for it. Both
        // the external texture and the shader program must be deleted here, while the EGL context
        // is still current; otherwise every decoder restart leaks one texture and one program.
        // Measured on the TV before this fix: 19 mirror sessions grew open fds from 103 to 206,
        // threads from 34 to 44 and PSS by 33 MB, with the growth dominated by sync_file, dmabuf
        // and gralloc fds.
        if (oesTex != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0) }
            oesTex = 0
        }
        if (program != 0) {
            runCatching { GLES20.glDeleteProgram(program) }
            program = 0
        }
        egl?.let {
            if (recordWindow != EGL14.EGL_NO_SURFACE) runCatching { it.destroySurface(recordWindow) }
            recordWindow = EGL14.EGL_NO_SURFACE
            if (window != EGL14.EGL_NO_SURFACE) runCatching { it.destroySurface(window) }
            runCatching { it.close() }
        }
        egl = null
        window = EGL14.EGL_NO_SURFACE
    }

    companion object {
        private const val TAG = "VideoPipeline"
        // Bounded, non-throwing wait for the recording EGL surface to be destroyed.
        private const val RECORD_DETACH_TIMEOUT_MS = 2500L

        private val POS = _fb(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val TEX = _fb(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

        private fun _fb(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(a); position(0) }

        private const val VERT =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTex;\n" +
            "uniform mat4 uTexMatrix;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "  vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;\n" +
            "}\n"

        private const val FRAG =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform samplerExternalOES sTex;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTex, vTex);\n" +
            "}\n"
    }
}
