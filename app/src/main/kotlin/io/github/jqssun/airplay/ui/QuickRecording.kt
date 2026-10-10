package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.recording.RecordingService

/** Starts with saved quality. Android's audio-capture consent remains mandatory. */
@Composable
fun rememberQuickRecording(): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(false) }
    fun error(id: Int) { pending = false; android.widget.Toast.makeText(context, id, android.widget.Toast.LENGTH_LONG).show() }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val quality = context.getSharedPreferences("recording_options", Context.MODE_PRIVATE).getInt("quality", 720)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)
                .putExtra("consent", result.data).putExtra("quality", if (quality == 1080) 1080 else 720)) }
                .onFailure { error(R.string.rec_unavailable) }
        }
    }
    val capture: () -> Unit = {
        runCatching {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            consent.launch(if (android.os.Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                else manager.createScreenCaptureIntent())
        }.onFailure { error(R.string.rec_unavailable) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) capture() else error(R.string.rec_audio_permission)
    }
    return {
        if (!pending) {
            pending = true
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.RECORD_AUDIO)
            else capture()
        }
    }
}

@Composable
fun RecordingQualitySetting() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("recording_options", Context.MODE_PRIVATE) }
    var quality by remember { mutableIntStateOf(prefs.getInt("quality", 720)) }
    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.rec_options_title))
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(androidx.compose.ui.unit.Dp(12f))) {
        listOf(720, 1080).forEach { value ->
            TvAction("${value}p", { quality = value; prefs.edit().putInt("quality", value).apply() }, selected = quality == value, compact = true)
        }
    }
}
