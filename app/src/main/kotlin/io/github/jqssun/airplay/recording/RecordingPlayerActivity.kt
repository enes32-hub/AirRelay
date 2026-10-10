package io.github.jqssun.airplay.recording

import android.app.Activity
import android.os.Bundle
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jqssun.airplay.R

/** Local recordings have their own player lifecycle; they never take over the AirPlay player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecordingPlayerActivity : Activity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(io.github.jqssun.airplay.receiver.AppLanguages.context(newBase))
    }
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private var position = 0L
    private var playing = true
    private var revealKey: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getLong("position") ?: 0L
        playing = savedInstanceState?.getBoolean("playing") ?: true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            controllerShowTimeoutMs = 5000
        }
        view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
            ?.setTimeBarMinUpdateInterval(1000)
        view.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
            isFocusable = true
            setKeyTimeIncrement(10_000)
            addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                override fun onScrubStart(bar: androidx.media3.ui.TimeBar, position: Long) { view.controllerShowTimeoutMs = 0 }
                override fun onScrubMove(bar: androidx.media3.ui.TimeBar, position: Long) = Unit
                override fun onScrubStop(bar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) { view.controllerShowTimeoutMs = 5000 }
            })
        }
        setContentView(view)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val timeBar = view.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
        if (view.isControllerFullyVisible && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && timeBar?.hasFocus() == false) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) timeBar.requestFocus()
            return true
        }
        if (timeBar?.hasFocus() == true && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN)
                view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
            return true
        }
        if (event.keyCode == revealKey) {
            if (event.action == android.view.KeyEvent.ACTION_UP) revealKey = null
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible &&
            event.keyCode in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
            revealKey = event.keyCode
            view.showController()
            view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        val file = RecordingService.files(this).firstOrNull { it.name == intent.getStringExtra("recording") }
        if (file == null) { Toast.makeText(this, R.string.rec_play_failed, Toast.LENGTH_LONG).show(); finish(); return }
        val local = ExoPlayer.Builder(this).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        player = local
        view.player = local
        local.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@RecordingPlayerActivity, R.string.rec_play_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        })
        local.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        local.seekTo(position)
        local.prepare()
        local.playWhenReady = playing
        view.showController()
        view.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("position", player?.currentPosition ?: position)
        outState.putBoolean("playing", player?.playWhenReady ?: playing)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        player?.let { position = it.currentPosition; playing = it.playWhenReady; it.release() }
        view.player = null
        player = null
        super.onStop()
    }
}
