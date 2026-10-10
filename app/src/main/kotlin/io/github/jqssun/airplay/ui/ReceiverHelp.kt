package io.github.jqssun.airplay.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import io.github.jqssun.airplay.viewmodel.MainViewModel

@Composable
fun ReceiverHelp(viewModel: MainViewModel) {
    val state by viewModel.receiverState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var settingsFailed by remember { mutableStateOf(false) }
    val topics = listOf(
        R.string.p_help_find to R.string.p_help_find_body,
        R.string.p_help_pin to R.string.p_help_pin_body,
        R.string.p_help_sound to R.string.p_help_sound_body,
        R.string.help_modes_title to R.string.help_modes_body,
        R.string.help_video_title to R.string.help_video_body,
        R.string.help_network_title to R.string.help_network_body,
        R.string.help_tracks_title to R.string.help_tracks_body,
        R.string.help_record_title to R.string.help_record_body,
    )
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(4.dp)) {
        item {
            PageHeading(stringResource(R.string.p_help_title), stringResource(R.string.p_help_body))
        }
        items(topics.indices.toList(), key = { it }) { index ->
            val (title, body) = topics[index]
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvAction(stringResource(title), { selected = if (selected == index) -1 else index },
                    Modifier.fillMaxWidth(),
                    icon = if (selected == index) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    selected = selected == index, focusId = "help.topic.$index")
                if (selected == index) {
                    InfoPanel(stringResource(body))
                    if (index == 0) {
                        TvAction(stringResource(if (state.canRetryDiscovery) R.string.p_retry_discovery else R.string.r2_retry_unavailable),
                            viewModel::retryDiscovery, enabled = state.canRetryDiscovery, icon = Icons.Rounded.Refresh,
                            focusId = "help.retry")
                        ReceiverStatus(state)
                    }
                }
            }
        }
        item {
            TvAction(stringResource(R.string.p_open_network_settings), {
                settingsFailed = runCatching {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.recoverCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isFailure
            }, icon = Icons.Rounded.SettingsEthernet, focusId = "help.network")
            if (settingsFailed) InfoPanel(stringResource(R.string.p_network_settings_failed), warning = true)
        }

    }
}
