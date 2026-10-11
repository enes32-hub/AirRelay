package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.ui.theme.AirRelayColors

@Composable
fun ReceiverHome(state: ReceiverUiState, savedName: String, bound: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onShowMirror: () -> Unit, onHelp: () -> Unit,
    modifier: Modifier = Modifier, primaryFocus: FocusRequester = remember { FocusRequester() }, onDismissNotice: () -> Unit = {}) {
    val helpFocus = remember { FocusRequester() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp && LocalDensity.current.fontScale < 1.5f
        val compactGuide = maxHeight < 380.dp
        val showClassicScene = maxWidth >= 640.dp && LocalDensity.current.fontScale < 1.2f
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    HomeIdentity(state, savedName, bound, onStart, onStop, onShowMirror, onHelp,
                        onDismissNotice, primaryFocus, helpFocus, Modifier.weight(1f))
                    if (showClassicScene)
                        ReceiverScene(Modifier.width(230.dp).height(200.dp).padding(top = 60.dp))
                }
                androidx.compose.material3.HorizontalDivider(color = AirRelayColors.Border)
                if (LocalDensity.current.fontScale < 1.2f) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.weight(1f)) { GuideStep("1", R.string.p_step1, R.string.r2_step1) }
                    Box(Modifier.weight(1f)) { GuideStep("2", R.string.p_step2, R.string.r2_step2) }
                    Box(Modifier.weight(1f)) { GuideStep("3", R.string.p_step3, R.string.r2_step3) }
                } else {
                    GuideStep("1", R.string.p_step1, R.string.r2_step1)
                    GuideStep("2", R.string.p_step2, R.string.r2_step2)
                    GuideStep("3", R.string.p_step3, R.string.r2_step3)
                }
                TvAction(stringResource(R.string.r2_cannot_find), onHelp, compact = true,
                    modifier = Modifier.focusRequester(helpFocus), focusId = "home.help")
            }
        
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeIdentity(state: ReceiverUiState, savedName: String, bound: Boolean,
    onStart: () -> Unit, onStop: () -> Unit, onShowMirror: () -> Unit, onHelp: () -> Unit,
    onDismissNotice: () -> Unit, primaryFocus: FocusRequester, helpFocus: FocusRequester, modifier: Modifier = Modifier) {
    val largerText = LocalDensity.current.fontScale >= 1.2f
    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(if (largerText) 12.dp else 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(stringResource(R.string.r2_hero), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.r2_hero_body), style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)
            .border(1.dp, AirRelayColors.Border.copy(alpha = .75f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = if (largerText) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (state.running) R.string.p_pick_name else R.string.p_name_when_started),
                style = MaterialTheme.typography.labelSmall, color = AirRelayColors.Muted)
            // The full advertised name is essential to choosing the right receiver. Never ellipsize it.
            Text(if (state.running) state.advertisedName.ifEmpty { savedName } else savedName,
                style = MaterialTheme.typography.headlineMedium, color = AirRelayColors.Text)
            ReceiverStatus(state)
            Text(if (state.running && state.mediaErrorCode != null) stringResource(R.string.access_return_stream)
                else if (state.portConflict != null) stringResource(R.string.p_port_conflict, state.portConflict) else stringResource(when (state.phase) {
                ReceiverPhase.STOPPED -> R.string.p_stopped_note
                ReceiverPhase.ERROR -> R.string.p_start_error_note
                ReceiverPhase.NETWORK_WAIT -> R.string.p_network_note
                ReceiverPhase.STARTING -> R.string.r2_starting_body
                ReceiverPhase.DISCOVERY_WAIT, ReceiverPhase.DISCOVERY_ERROR -> R.string.p_discovery_note
                ReceiverPhase.WAITING_VIDEO -> R.string.p_video_wait_body
                ReceiverPhase.MIRRORING, ReceiverPhase.MEDIA_PLAYING -> R.string.r2_active_body
                ReceiverPhase.CONNECTING, ReceiverPhase.PAIRING -> R.string.r2_connecting_body
                ReceiverPhase.AUDIO_CONNECTED -> R.string.r2_audio_body
                else -> R.string.p_ready_note
            }), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
        }
        val action = state.homePrimaryAction()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvAction(stringResource(when {
                state.starting -> R.string.p_state_starting
                action == HomePrimaryAction.VIEW -> R.string.p_show_mirror
                action == HomePrimaryAction.START -> R.string.p_start
                else -> R.string.r2_connection_guide
            }), onClick = {
                when (action) {
                    HomePrimaryAction.START -> onStart()
                    HomePrimaryAction.VIEW -> onShowMirror()
                    HomePrimaryAction.GUIDE -> onHelp()
                }
            }, icon = if (action == HomePrimaryAction.GUIDE) Icons.Rounded.HelpOutline else Icons.Rounded.Cast,
                primary = true, enabled = !state.starting && (bound || action == HomePrimaryAction.GUIDE),
                modifier = Modifier.focusRequester(primaryFocus), focusId = "home.primary")
            if (state.running) TvAction(stringResource(R.string.p_stop), onStop,
                modifier = Modifier.focusProperties { right = helpFocus }, icon = Icons.Rounded.Stop, focusId = "home.stop", destructive = true)
        }
        val notice = when (state.notice) {
            ReceiverNotice.CONNECTION_LOST -> R.string.p_notice_lost
            ReceiverNotice.CONNECTION_RESET -> R.string.p_notice_reset
            ReceiverNotice.PAIRING_CANCELLED -> R.string.p_notice_cancelled
            ReceiverNotice.PAIRING_EXPIRED -> R.string.p_notice_expired
            ReceiverNotice.NETWORK_CHANGED -> R.string.p_notice_network
            else -> null
        }
        if (notice != null) {
            Text(stringResource(notice), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Warning)
            TvAction(stringResource(R.string.r2_notice_dismiss), onDismissNotice, compact = true, focusId = "home.notice")
        }
        if (state.pendingSettings) Text(stringResource(R.string.p_pending_title),
            style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Accent)
        if (state.raop == Advertisement.FAILED) Text(stringResource(R.string.p_audio_warning),
            style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Warning)
    }
}

@Composable
private fun GuideStep(number: String, title: Int, body: Int) {
    // Explanatory content remains accessible to screen readers, but is not a dead D-pad stop.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(AirRelayColors.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = AirRelayColors.Accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
        }
    }
}
