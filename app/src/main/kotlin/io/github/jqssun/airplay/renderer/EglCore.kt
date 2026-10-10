package io.github.jqssun.airplay.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

class EglCore : AutoCloseable {

    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val config: EGLConfig
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        try {
        check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1)) { "EGL initialization failed" }
        val attribs = intArrayOf(
            0x3142, 1, // EGL_RECORDABLE_ANDROID: encoder-compatible window buffers
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, IntArray(1), 0)) { "EGL config unavailable" }
        config = configs[0] ?: error("no egl config")
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "EGL context unavailable" }
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        check(pbuffer != EGL14.EGL_NO_SURFACE) { "EGL buffer unavailable" }
        check(makeCurrent()) { "EGL context activation failed" }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun makeCurrent(surface: EGLSurface = pbuffer) = EGL14.eglMakeCurrent(display, surface, surface, context)

    fun windowSurface(surface: Surface): EGLSurface =
        EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)

    fun destroySurface(surface: EGLSurface) = EGL14.eglDestroySurface(display, surface)

    fun presentationTime(surface: EGLSurface, ns: Long) = android.opengl.EGLExt.eglPresentationTimeANDROID(display, surface, ns)

    fun swap(surface: EGLSurface) = EGL14.eglSwapBuffers(display, surface)

    fun query(surface: EGLSurface, what: Int): Int {
        val v = IntArray(1)
        EGL14.eglQuerySurface(display, surface, what, v, 0)
        return v[0]
    }

    override fun close() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        pbuffer = EGL14.EGL_NO_SURFACE
        context = EGL14.EGL_NO_CONTEXT
        EGL14.eglTerminate(display)
    }
}
