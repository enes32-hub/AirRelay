package io.github.jqssun.airplay.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.recording.RecordingService
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecordingPanel(onStarted: () -> Unit = {}, canRecord: Boolean = true, canPlay: Boolean = false,
    onModalChange: (Boolean) -> Unit = {}, showLibrary: Boolean = true) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val state by RecordingService.status.collectAsStateWithLifecycle()
    var starting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.active) { if (state.active && starting) { starting = false; onStarted() } }
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.getSharedPreferences("recording_options", android.content.Context.MODE_PRIVATE) }
    var quality by rememberSaveable { mutableIntStateOf(preferences.getInt("quality", 720)) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingDelete) { onModalChange(pendingDelete != null) }
    DisposableEffect(Unit) { onDispose { onModalChange(false) } }
    val files by produceState<List<io.github.jqssun.airplay.recording.RecordingEntry>?>(null, state, revision, showLibrary) {
        value = if (showLibrary) withContext(Dispatchers.IO) { io.github.jqssun.airplay.recording.RecordingLibrary.list(context) } else emptyList()
    }
    if (pendingDelete != null) ConfirmReceiverAction(stringResource(R.string.rec_delete),
        stringResource(R.string.rec_delete_confirm), stringResource(R.string.rec_delete), onConfirm = {
            val name = pendingDelete!!
            pendingDelete = null
            scope.launch {
                exporting = true
                val success = withContext(Dispatchers.IO) { io.github.jqssun.airplay.recording.RecordingLibrary.delete(context, name) }
                message = if (success) R.string.rec_deleted else R.string.rec_delete_failed
                revision++
                exporting = false
            }
        }, onCancel = { pendingDelete = null })
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            starting = true
            runCatching { ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java)
                .putExtra("consent", result.data).putExtra("quality", quality)) }
                .onFailure { message = R.string.rec_unavailable }
        }
    }
    fun requestCapture() {
        runCatching {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            // Only UID-filtered app audio uses this consent. No display pixels are captured.
            val request = if (android.os.Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else manager.createScreenCaptureIntent()
            consent.launch(request)
        }
            .onFailure { message = R.string.rec_unavailable }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) requestCapture() else message = R.string.rec_audio_permission
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val name = selected
        selected = null
        if (uri == null) message = R.string.rec_export_cancelled
        if (uri != null && name != null) scope.launch {
            exporting = true
            val success = withContext(Dispatchers.IO) { runCatching {
                val source = RecordingService.files(context).firstOrNull { it.name == name } ?: error("Missing recording")
                context.contentResolver.openOutputStream(uri, "wt")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: error("Cannot export")
            }.isSuccess }
            message = if (success) R.string.rec_exported else R.string.rec_export_failed
            exporting = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canRecord) {
        Text(stringResource(R.string.rec_description), color = AirRelayColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(720, 1080).forEach { value -> TvAction("${value}p", { quality = value; preferences.edit().putInt("quality", value).apply() },
                selected = quality == value, enabled = !state.active, compact = true, focusId = "record.quality.$value") }
        }
        TvAction(stringResource(if (state.active) R.string.rec_stop else R.string.rec_start), {
            message = 0
            if (state.active) context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.STOP))
            else if (android.os.Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            else requestCapture()
        }, focusId = "record.toggle")
        }
        if (!canRecord && !canPlay) Text(stringResource(R.string.rec_end_stream_first), color = AirRelayColors.Muted)
        val statusMessage = if (message != 0) message else state.message
        if (statusMessage != 0) Text(stringResource(statusMessage), color = AirRelayColors.Muted)
        if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (showLibrary && files == null) CircularProgressIndicator()
        if (showLibrary && files?.isEmpty() == true) Text(stringResource(R.string.rec_empty), color = AirRelayColors.Muted)
        files.orEmpty().forEach { entry ->
            val file = entry.file
            HorizontalDivider()
            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale).format(java.util.Date(entry.createdAt)),
                style = MaterialTheme.typography.titleMedium)
            Text(listOf(entry.durationMs?.let { ms ->
                val seconds = ms / 1000
                if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
                else "%d:%02d".format(seconds / 60, seconds % 60)
            } ?: stringResource(R.string.rec_duration_unknown),
                android.text.format.Formatter.formatFileSize(context, file.length()),
                entry.height?.let { "${it}p" } ?: "MP4").joinToString(" · "), color = AirRelayColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvAction(stringResource(R.string.rec_watch), {
                runCatching { context.startActivity(Intent(context, io.github.jqssun.airplay.recording.RecordingPlayerActivity::class.java)
                    .putExtra("recording", file.name)) }.onFailure { message = R.string.rec_play_failed }
            }, enabled = canPlay && !state.active && !exporting && pendingDelete == null, compact = true, focusId = "record.play.${file.name}")
            TvAction(stringResource(R.string.rec_delete), { pendingDelete = file.name },
                enabled = !exporting && pendingDelete == null, destructive = true, compact = true, focusId = "record.delete.${file.name}")
            }

            TvAction(stringResource(R.string.rec_export), {
                message = 0
                if (android.os.Build.VERSION.SDK_INT >= 29) scope.launch {
                    exporting = true
                    val success = withContext(Dispatchers.IO) { runCatching {
                        io.github.jqssun.airplay.recording.RecordingExport.toMovies(context, file)
                    }.isSuccess }
                    message = if (success) R.string.rec_exported_movies else R.string.rec_export_failed
                    exporting = false
                } else {
                    selected = file.name
                    runCatching { export.launch(file.name) }.onFailure { selected = null; message = R.string.rec_export_failed }
                }
            }, enabled = !exporting, compact = true, focusId = "record.export.${file.name}")
        }
    }
}

