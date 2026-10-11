package io.github.jqssun.airplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.ui.theme.AirRelayColors

@Composable
fun TvAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, primary: Boolean = false, enabled: Boolean = true,
    selected: Boolean = false, focusId: String? = null, compact: Boolean = false,
    destructive: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val hovered by interactions.collectIsHoveredAsState()
    val reduced = LocalReducedMotion.current
    val usable = enabled && LocalTvInputEnabled.current
    val activity = LocalControlActivity.current
    val pressChanged = LocalControlPressed.current
    LaunchedEffect(pressed) { pressChanged(pressed); if (pressed) activity() }
    DisposableEffect(Unit) { onDispose { pressChanged(false) } }
    val scale = animateFloatAsState(when {
        reduced || !usable -> 1f
        pressed -> .98f
        focused -> 1.015f
        else -> 1f
    }, tween(if (reduced) 0 else if (pressed) 70 else 130), label = "action feedback")
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    val fill = when {
        focused -> AirRelayColors.Focus
        (primary || selected) && hovered -> AirRelayColors.Hover
        primary -> AirRelayColors.Accent
        selected -> AirRelayColors.SelectedFill
        focused || hovered -> AirRelayColors.Elevated
        else -> AirRelayColors.ButtonSurface
    }
    val ink = when {
        focused -> AirRelayColors.OnAccent
        primary -> MaterialTheme.colorScheme.onPrimary
        destructive -> AirRelayColors.Danger
        selected -> AirRelayColors.SelectedInk
        else -> AirRelayColors.Text
    }
    Row(modifier.tvFocusId(focusId, usable)
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = if (usable) 1f else .48f }
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) activity() }
        .semantics { this.selected = selected }
        .heightIn(min = if (compact) 44.dp else 50.dp).clip(shape).background(fill)
        .border(if (focused) 2.dp else 1.dp,
            if (focused) AirRelayColors.Focus else if (selected) AirRelayColors.Accent.copy(alpha = .55f)
            else if (primary) Color.Transparent else AirRelayColors.Border.copy(alpha = .7f), shape)
        .clickable(enabled = usable, role = Role.Button, interactionSource = interactions,
            indication = null,
            onClick = { activity(); onClick() })
        .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 9.dp else 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(if (compact) 19.dp else 21.dp), tint = ink); Spacer(Modifier.width(9.dp)) }
        Text(text, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = ink, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvPreference(title: String, description: String, value: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, focusId: String? = null, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val usable = LocalTvInputEnabled.current
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().tvFocusId(focusId, usable)
        .onFocusChanged { focused = it.isFocused }.clip(shape)
        .background(if (focused || pressed) AirRelayColors.Elevated else AirRelayColors.Panel)
        .border(if (focused) 2.dp else 1.dp, if (focused) AirRelayColors.Focus else AirRelayColors.Border.copy(alpha = .6f), shape)
        .toggleable(value = value, enabled = usable, role = Role.Switch,
            interactionSource = interactions, indication = null, onValueChange = onChange)
        .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
            }
        }
        Spacer(Modifier.width(20.dp))
        Switch(checked = value, onCheckedChange = null, modifier = Modifier.focusProperties { canFocus = false })
    }
}

@Composable
fun PageHeading(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
    }
}

@Composable
fun ReceiverBrand(modifier: Modifier = Modifier) {
    Row(modifier.width(180.dp).height(52.5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Image(painterResource(if (AirRelayColors.Text.luminance() < 0.5f)
            R.drawable.airrelay_logo_light else R.drawable.airrelay_logo_vector),
            contentDescription = null, modifier = Modifier.size(44.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Text(stringResource(R.string.app_name), color = AirRelayColors.Text,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun phaseText(phase: ReceiverPhase): String = stringResource(when (phase) {
    ReceiverPhase.STOPPED -> R.string.p_state_stopped
    ReceiverPhase.STARTING -> R.string.p_state_starting
    ReceiverPhase.NETWORK_WAIT -> R.string.p_state_network
    ReceiverPhase.DISCOVERY_WAIT -> R.string.p_state_discovery
    ReceiverPhase.DISCOVERY_ERROR -> R.string.p_state_discovery_error
    ReceiverPhase.READY -> R.string.p_state_ready
    ReceiverPhase.CONNECTING -> R.string.p_state_connecting
    ReceiverPhase.PAIRING -> R.string.p_state_pairing
    ReceiverPhase.WAITING_VIDEO -> R.string.p_state_video_wait
    ReceiverPhase.MIRRORING -> R.string.p_state_mirroring
    ReceiverPhase.AUDIO_CONNECTED -> R.string.p_state_audio
    ReceiverPhase.MEDIA_PLAYING -> R.string.p_state_media
    ReceiverPhase.ERROR -> R.string.p_state_error
})

@Composable
fun ReceiverStatus(state: ReceiverUiState, modifier: Modifier = Modifier) {
    val label = if (state.running && state.mediaErrorCode != null)
        stringResource(if (state.mediaErrorCode == 5003) R.string.access_waiting_screen else R.string.access_video_issue) else phaseText(state.phase)
    val color = when (state.phase) {
        ReceiverPhase.READY, ReceiverPhase.MIRRORING, ReceiverPhase.MEDIA_PLAYING, ReceiverPhase.AUDIO_CONNECTED -> AirRelayColors.Success
        ReceiverPhase.ERROR, ReceiverPhase.DISCOVERY_ERROR -> AirRelayColors.Danger
        ReceiverPhase.NETWORK_WAIT, ReceiverPhase.STARTING, ReceiverPhase.DISCOVERY_WAIT,
        ReceiverPhase.CONNECTING, ReceiverPhase.PAIRING, ReceiverPhase.WAITING_VIDEO -> AirRelayColors.Warning
        else -> AirRelayColors.Muted
    }
    Row(modifier.semantics { stateDescription = label; liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun NetworkLabel(network: LocalNetwork, modifier: Modifier = Modifier) {
    val icon = when (network) {
        LocalNetwork.WIFI -> Icons.Rounded.Wifi
        LocalNetwork.ETHERNET -> Icons.Rounded.SettingsEthernet
        else -> Icons.Rounded.Router
    }
    val text = stringResource(when (network) {
        LocalNetwork.WIFI -> R.string.p_network_wifi
        LocalNetwork.ETHERNET -> R.string.p_network_ethernet
        LocalNetwork.UNKNOWN -> R.string.p_network_unknown
        LocalNetwork.UNAVAILABLE -> R.string.p_network_unavailable
        LocalNetwork.PERMISSION_DENIED -> R.string.p_network_permission
    })
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = AirRelayColors.Muted)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
    }
}

@Composable
fun InfoPanel(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Row(modifier.fillMaxWidth().background(AirRelayColors.Elevated, RoundedCornerShape(14.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top) {
        Icon(if (warning) Icons.Rounded.Info else Icons.Rounded.CheckCircle, null, Modifier.size(21.dp),
            tint = if (warning) AirRelayColors.Warning else AirRelayColors.Link)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
    }
}

