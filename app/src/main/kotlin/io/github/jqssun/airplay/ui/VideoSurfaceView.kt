package io.github.jqssun.airplay.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
    val callbacks = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceAvailable(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, fmt: Int, w: Int, h: Int) {
                onSurfaceAvailable(holder.surface)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed(holder.surface)
            }
        }
    }

    AndroidView(factory = { ctx -> SurfaceView(ctx).also { it.holder.addCallback(callbacks) } }, modifier = modifier)
}

@Composable
private fun RotatableTextureView(onSurfaceAvailable: (Surface) -> Unit, onSurfaceDestroyed: (Surface) -> Unit,
    modifier: Modifier) {
    val listener = remember {
        object : TextureView.SurfaceTextureListener {
            private var output: Surface? = null
            override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                output = Surface(texture).also(onSurfaceAvailable)
            }
            override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                output?.let(onSurfaceAvailable)
            }
            override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                output?.let(onSurfaceDestroyed)
                output?.release()
                output = null
                return true
            }
            override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
        }
    }
    AndroidView(factory = { ctx -> TextureView(ctx).also { it.surfaceTextureListener = listener } }, modifier = modifier)
}
