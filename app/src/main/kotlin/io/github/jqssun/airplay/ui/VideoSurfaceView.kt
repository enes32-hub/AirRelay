package io.github.jqssun.airplay.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// shared surface for mirroring (VideoRenderer) and airplay video (AirPlayVideoPlayer)
@Composable
fun VideoSurfaceView(
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: (Surface) -> Unit,
    aspectRatio: Float = 16f / 9f,
    // false = caller sizes the surface (content-scale modes)
    applyAspectRatio: Boolean = true,
    // SurfaceView is fastest for normal playback. TextureView is required when
    // the receiver applies a visual rotation to its output surface.
    useTextureView: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sizedModifier = if (applyAspectRatio) {
        modifier.aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 1f).fillMaxSize()
    } else modifier
    key(useTextureView) {
        if (useTextureView) RotatableTextureView(onSurfaceAvailable, onSurfaceDestroyed, sizedModifier)
        else ReceiverSurfaceView(onSurfaceAvailable, onSurfaceDestroyed, sizedModifier)
    }
}

@Composable
private fun ReceiverSurfaceView(onSurfaceAvailable: (Surface) -> Unit, onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier) {
    val latestAvailable by rememberUpdatedState(onSurfaceAvailable)
    val latestDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    val callbacks = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                latestAvailable(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, fmt: Int, w: Int, h: Int) {
                latestAvailable(holder.surface)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                latestDestroyed(holder.surface)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { view ->
                view.holder.addCallback(callbacks)
                view.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if ((right - left != oldRight - oldLeft) || (bottom - top != oldBottom - oldTop)) {
                        v.post {
                            if (v.isAttachedToWindow) {
                                view.holder.surface?.takeIf { it.isValid }?.let(latestAvailable)
                            }
                        }
                    }
                }
            }
        },
        update = { view -> view.requestLayout() },
        modifier = modifier
    )
}

@Composable
private fun RotatableTextureView(onSurfaceAvailable: (Surface) -> Unit, onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier) {
    val latestAvailable by rememberUpdatedState(onSurfaceAvailable)
    val latestDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    val outputHolder = remember { arrayOfNulls<Surface>(1) }
    val listener = remember {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                outputHolder[0] = Surface(texture).also(latestAvailable)
            }
            override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                outputHolder[0]?.takeIf { it.isValid }?.let(latestAvailable)
            }
            override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                outputHolder[0]?.let(latestDestroyed)
                outputHolder[0]?.release()
                outputHolder[0] = null
                return true
            }
            override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
        }
    }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { view ->
                view.surfaceTextureListener = listener
                view.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if ((right - left != oldRight - oldLeft) || (bottom - top != oldBottom - oldTop)) {
                        v.post {
                            if (v.isAttachedToWindow && view.isAvailable) {
                                outputHolder[0]?.takeIf { it.isValid }?.let(latestAvailable)
                            }
                        }
                    }
                }
            }
        },
        update = { view -> view.requestLayout() },
        modifier = modifier
    )
}
