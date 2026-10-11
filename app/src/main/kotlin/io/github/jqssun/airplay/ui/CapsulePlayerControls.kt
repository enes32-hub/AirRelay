package io.github.jqssun.airplay.ui

import io.github.jqssun.airplay.ui.theme.AirRelayColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalCapsuleFocused = compositionLocalOf<(FocusRequester) -> Unit> { {} }

/** Only the icon is filled: labels sit outside the circular hit-target artwork. */
@Composable
internal fun CapsuleAction(text: String, onClick: () -> Unit, icon: ImageVector,
    modifier: Modifier = Modifier, focusId: String? = null, selected: Boolean = false,
    destructive: Boolean = false, prominent: Boolean = false, wide: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val activity = LocalControlActivity.current
    val pressChanged = LocalControlPressed.current
    val usable = LocalTvInputEnabled.current
    val focus = remember { FocusRequester() }
    val focusedAction = LocalCapsuleFocused.current
    LaunchedEffect(pressed) { pressChanged(pressed); if (pressed) activity() }
    DisposableEffect(Unit) { onDispose { pressChanged(false) } }
    val active = focused || selected
    val size = if (prominent) 58.dp else 44.dp
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier.width((if (wide) 84.dp else 60.dp) * fontScale)
        .heightIn(min = 82.dp).tvFocusId(focusId, usable).focusRequester(focus)
        .onFocusChanged { focused = it.isFocused; if (focused) { activity(); focusedAction(focus) } }
        .semantics { this.selected = selected }
        .clickable(enabled = usable, role = Role.Button, interactionSource = interactions,
            indication = null, onClick = { focusedAction(focus); activity(); onClick() })
        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Box(Modifier.size(size)
            .background(when {
                active && destructive -> AirRelayColors.Danger
                active -> AirRelayColors.Accent
                pressed -> Color.White.copy(alpha = .25f)
                prominent -> Color.White.copy(alpha = .17f)
                else -> Color.White.copy(alpha = .055f)
            }, CircleShape)
            .border(1.dp, Color.White.copy(alpha = if (active) .75f else .09f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(if (prominent) 29.dp else 23.dp), tint = when {
                active && destructive -> AirRelayColors.OnAccent
                active -> AirRelayColors.OnAccent
                destructive -> AirRelayColors.Danger
                else -> AirRelayColors.Text
            })
        }
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp,
            lineHeight = 12.sp, letterSpacing = 0.sp),
            color = if (active) AirRelayColors.Text else AirRelayColors.Muted, textAlign = TextAlign.Center)
    }
}
