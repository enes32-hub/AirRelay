package io.github.jqssun.airplay.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import io.github.jqssun.airplay.viewmodel.MainViewModel

@Composable
fun LogsScreen(viewModel: MainViewModel, onModalChange: (Boolean) -> Unit = {}) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val storageError by viewModel.logStorageError.collectAsStateWithLifecycle()
    val actionError by viewModel.logActionError.collectAsStateWithLifecycle()
    val busy by viewModel.logActionBusy.collectAsStateWithLifecycle()
    val debugEnabled by viewModel.debugEnabled.collectAsStateWithLifecycle()
    val debug by viewModel.debugInfo.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var follow by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var displayed by remember { mutableStateOf(logs) }
    var initialized by remember { mutableStateOf(logs.isNotEmpty()) }
    var export by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val blocked = LocalBlockingOverlay.current
    SideEffect { onModalChange(!blocked && (export || clear)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setDiagnosticsVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        viewModel.setDiagnosticsVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setDiagnosticsVisible(false)
            onModalChange(false)
        }
    }
    // Freeze the reading snapshot. Appending/rotating logs must never replace a focused line.
    LaunchedEffect(logs, follow, reading) {
        if ((follow && !reading) || !initialized) {
            displayed = logs
            initialized = logs.isNotEmpty()
            if (follow && displayed.isNotEmpty() && !list.isScrollInProgress) list.scrollToItem(displayed.lastIndex)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageHeading(stringResource(R.string.p_logs_title), stringResource(R.string.p_logs_body))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvAction(stringResource(if (busy) R.string.r2_working else R.string.p_logs_export), { export = true },
                icon = Icons.Rounded.Share, enabled = !busy, compact = true, focusId = "logs.export")
            TvAction(stringResource(R.string.p_logs_clear), { clear = true }, icon = Icons.Rounded.DeleteOutline,
                enabled = !busy, compact = true, focusId = "logs.clear")
            TvAction(stringResource(R.string.r2_refresh_snapshot), { displayed = logs; initialized = true },
                icon = Icons.Rounded.Refresh, compact = true, focusId = "logs.refresh")
            TvAction(stringResource(R.string.p_follow_logs), { follow = !follow }, selected = follow,
                compact = true, focusId = "logs.follow")
        }
        Text(stringResource(if (follow && !reading) R.string.r2_logs_live else R.string.r2_logs_frozen),
            style = MaterialTheme.typography.labelSmall, color = AirRelayColors.Muted)
        if (storageError || actionError) Text(stringResource(R.string.p_logs_error), color = AirRelayColors.Warning)
        if (debugEnabled) Text(stringResource(R.string.p_technical_metrics) + "\n" +
            "${debug.videoFps} fps · ${debug.bitrateStr} · ${debug.videoRes}",
            style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
        if (displayed.isEmpty()) Text(stringResource(R.string.p_logs_empty), color = AirRelayColors.Muted)
        LazyColumn(Modifier.weight(1f).onFocusChanged { reading = it.hasFocus }.focusGroup(), state = list,
            contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(displayed, key = { index, _ -> index }) { index, line ->
                Text(line, Modifier.fillMaxWidth().tvFocusId("logs.line.$index", LocalTvInputEnabled.current)
                    .focusRing(RoundedCornerShape(6.dp)).focusable(enabled = LocalTvInputEnabled.current).padding(6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
    if (!blocked) when {
        export -> ConfirmReceiverAction(stringResource(R.string.p_logs_export_title), stringResource(R.string.p_logs_export_body),
            stringResource(R.string.p_logs_export), onConfirm = { export = false; viewModel.exportLogs() }, onCancel = { export = false })
        clear -> ConfirmReceiverAction(stringResource(R.string.p_logs_clear_title), stringResource(R.string.p_logs_clear_body),
            stringResource(R.string.p_clear_confirm), onConfirm = { clear = false; displayed = emptyList(); viewModel.clearLogs() }, onCancel = { clear = false })
    }
}
