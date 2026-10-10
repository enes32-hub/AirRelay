package io.github.jqssun.airplay.recording

import android.view.Surface
import io.github.jqssun.airplay.renderer.VideoPipeline

/** Routes only decoded video textures; never a window, Compose view or system display. */
object CleanRecording {
    data class Target(val surface: Surface, val width: Int, val height: Int,
                      val startedNs: Long, val failed: () -> Unit) {
        val firstFrameNs = java.util.concurrent.atomic.AtomicLong(0)
    }
    private val lock = Any()
    private val sources = mutableMapOf<String, java.lang.ref.WeakReference<VideoPipeline>>()
    private var selected = "mirror"
    private var target: Target? = null

    /**
     * The pipeline's target change is a bounded wait for the GL thread. It must never run
     * while this object's lock is held, or a UI thread calling [select] would queue behind
     * it. Every mutation therefore computes its intent under the lock and talks to the
     * pipeline afterwards.
     */
    fun register(source: String, pipeline: VideoPipeline): Boolean {
        var previous: VideoPipeline? = null
        var current: Target? = null
        synchronized(lock) {
            previous = sources.put(source, java.lang.ref.WeakReference(pipeline))?.get()
            if (source == selected) current = target
        }
        val previousPipeline = previous
        val attachTo = current
        var ok = true
        if (previousPipeline != null && previousPipeline !== pipeline) {
            ok = previousPipeline.setRecordingTarget(null) && ok
        }
        if (attachTo != null) ok = pipeline.setRecordingTarget(attachTo) && ok
        return ok
    }

    fun select(source: String): Boolean {
        var previous: VideoPipeline? = null
        var next: VideoPipeline? = null
        var current: Target? = null
        synchronized(lock) {
            if (selected == source) return true
            previous = sources[selected]?.get()
            selected = source
            next = sources[source]?.get()
            current = target
        }
        val previousPipeline = previous
        val nextPipeline = next
        val attachTo = current
        var ok = true
        if (previousPipeline != null && previousPipeline !== nextPipeline) {
            ok = previousPipeline.setRecordingTarget(null) && ok
        }
        if (nextPipeline != null && attachTo != null) ok = nextPipeline.setRecordingTarget(attachTo) && ok
        return ok
    }

    /** False when a target is already attached; never throws. */
    fun attach(value: Target): Boolean {
        var pipeline: VideoPipeline? = null
        synchronized(lock) {
            if (target != null) return false
            target = value
            pipeline = sources[selected]?.get()
        }
        return pipeline?.setRecordingTarget(value) ?: true
    }

    fun detach(): Boolean {
        var pipeline: VideoPipeline? = null
        synchronized(lock) {
            pipeline = sources[selected]?.get()
            target = null
        }
        return pipeline?.setRecordingTarget(null) ?: true
    }
}
