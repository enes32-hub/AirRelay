package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import io.github.jqssun.airplay.viewmodel.MainViewModel

@Composable
fun PairingDialog(pin: String, onCancel: () -> Unit) = DialogInputScope {
    val cancel = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(pin) { cancel.requestFocusUntilLanded(isFocused = { focused }) }
    ProductDialog(onDismissRequest = onCancel, properties = DialogProperties(
        dismissOnClickOutside = false, usePlatformDefaultWidth = false,
        securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(.9f), shape = RoundedCornerShape(28.dp),
            color = AirRelayColors.Panel) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.r2_pair_eyebrow), style = MaterialTheme.typography.labelSmall, color = AirRelayColors.Accent)
                Text(stringResource(R.string.p_pair_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.p_pair_body), style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pin.forEach { digit ->
                        Box(Modifier.weight(1f).heightIn(min = 86.dp).background(AirRelayColors.Background, RoundedCornerShape(16.dp))
                            .border(1.dp, AirRelayColors.Accent.copy(alpha = .45f), RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center) {
                            Text(digit.toString(), fontSize = if (pin.length > 6) 30.sp else 48.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = AirRelayColors.Text)
                        }
                    }
                }
                Text(stringResource(R.string.p_pair_safety), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                TvAction(stringResource(R.string.p_pair_cancel), onCancel,
                    Modifier.focusRequester(cancel).onFocusChanged { focused = it.isFocused }, destructive = true)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiverSetupDialog(viewModel: MainViewModel, onDismiss: () -> Unit) = DialogInputScope {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf(true) }
    var boot by rememberSaveable { mutableStateOf(false) }
    var background by rememberSaveable { mutableStateOf(true) }
    val access = permissionAccess()
    val allowed = when (step) { 1 -> access.notifications; 2 -> access.display; else -> true }
    ProductDialog(onDismissRequest = { if (step > 0) step-- else onDismiss() }, properties = DialogProperties(
        dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth(.92f), shape = RoundedCornerShape(28.dp), color = AirRelayColors.Panel) {
          key(step) {
            TvFocusRegion(enabled = true, defaultId = "setup.next") {
                Column(Modifier.heightIn(max = 480.dp).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        ReceiverBrand()
                        Text(stringResource(R.string.permission_step, step + 1), style = MaterialTheme.typography.labelMedium, color = AirRelayColors.Muted)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { index -> Box(Modifier.weight(1f).height(3.dp)
                            .background(if (index <= step) AirRelayColors.Accent else AirRelayColors.Border, RoundedCornerShape(2.dp))) }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(when (step) {
                            0 -> R.string.permission_welcome
                            1 -> R.string.permission_notifications_title
                            2 -> R.string.permission_display_title
                            else -> R.string.permission_ready_title
                        }), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(when (step) {
                            0 -> R.string.permission_intro
                            1 -> R.string.permission_notifications_body
                            2 -> R.string.permission_display_body
                            else -> R.string.permission_ready_body
                        }), style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
                        when (step) {
                            0 -> PermissionNetworkNote()
                            1, 2 -> {
                                PermissionStatus(allowed)
                                if (!allowed) {
                                    if (step == 2) Text(stringResource(R.string.permission_display_instruction),
                                        style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Text)
                                    Text(stringResource(if (step == 1) R.string.permission_notifications_skip else R.string.permission_display_skip),
                                        style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                                }
                            }
                            3 -> {
                                TvPreference(stringResource(R.string.p_setup_open), stringResource(R.string.p_setup_open_desc), open, { open = it }, focusId = "setup.auto", compact = true)
                                TvPreference(stringResource(R.string.p_setup_boot), stringResource(R.string.p_setup_boot_desc), boot, { boot = it }, focusId = "setup.boot", compact = true)
                                TvPreference(stringResource(R.string.p_setup_background), stringResource(R.string.p_setup_background_desc), background, { background = it }, focusId = "setup.background", compact = true)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvAction(stringResource(when {
                            step == 3 -> R.string.p_setup_start
                            allowed -> R.string.permission_continue
                            step == 1 -> R.string.permission_notifications_grant
                            else -> R.string.permission_display_grant
                        }), {
                            when {
                                step == 3 -> {
                                    // The OS permission gates this feature; granting it later must
                                    // not also require discovering a silently disabled preference.
                                    viewModel.setLaunchOnConnect(true)
                                    viewModel.completeSetup(open, boot, background)
                                }
                                allowed -> step++
                                step == 1 -> access.activity?.requestNotificationPermission()
                                else -> access.activity?.requestAutomaticDisplayPermission()
                            }
                        }, primary = true, focusId = "setup.next")
                        if (!allowed) TvAction(stringResource(R.string.permission_skip), { step++ }, compact = true, focusId = "setup.skip")
                        if (step > 0) TvAction(stringResource(R.string.permission_back), { step-- }, compact = true, focusId = "setup.back")
                    }
                }
            }
          }
        }
    }
}

@Composable
fun ConfirmReceiverAction(title: String, body: String, confirmLabel: String,
    onConfirm: () -> Unit, onCancel: () -> Unit) = DialogInputScope {
    val safe = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { safe.requestFocusUntilLanded(isFocused = { focused }) }
    ProductAlertDialog(onDismissRequest = onCancel, shape = RoundedCornerShape(26.dp), containerColor = AirRelayColors.Panel,
        title = { Text(title) }, text = { Text(body) },
        confirmButton = { TvAction(confirmLabel, onConfirm) },
        dismissButton = { TvAction(stringResource(R.string.p_cancel), onCancel,
            Modifier.focusRequester(safe).onFocusChanged { focused = it.isFocused }, primary = true) })
}
