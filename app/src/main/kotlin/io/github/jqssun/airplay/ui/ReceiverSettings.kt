package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.receiver.ReceiverInput
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import io.github.jqssun.airplay.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingSettingsPanel(viewModel: MainViewModel, onModalChange: (Boolean) -> Unit = {}) {
    var confirmRestart by remember { mutableStateOf(false) }
    val blocked = LocalBlockingOverlay.current
    SideEffect { onModalChange(confirmRestart && !blocked) }
    DisposableEffect(Unit) { onDispose { onModalChange(false) } }
    val state by viewModel.receiverState.collectAsStateWithLifecycle()
    val bound by viewModel.serviceBound.collectAsStateWithLifecycle()
    val setup by viewModel.setupComplete.collectAsStateWithLifecycle()
    LaunchedEffect(state.pin, state.pendingSettings) { if (state.pin != null || !state.pendingSettings) confirmRestart = false }
    if (!state.pendingSettings) return
    if (confirmRestart && !blocked) DialogInputScope {
        ConfirmReceiverAction(stringResource(R.string.restart_stream_title), stringResource(R.string.restart_stream_body),
            stringResource(R.string.p_apply_settings), onConfirm = { confirmRestart = false; viewModel.applyPendingSettings(true) },
            onCancel = { confirmRestart = false })
    }
    Column(Modifier.fillMaxWidth().background(AirRelayColors.AccentSoft, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.p_pending_title), style = MaterialTheme.typography.labelLarge, color = AirRelayColors.Accent)
        Text(stringResource(when {
            !setup -> R.string.p_setup_before_apply
            state.sessionBusy -> R.string.p_pending_busy
            else -> R.string.r2_pending_brief
        }), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvAction(stringResource(R.string.p_apply_settings), { if (state.sessionBusy) confirmRestart = true else viewModel.applyPendingSettings() },
                enabled = bound && setup && !state.starting, icon = Icons.Rounded.Refresh,
                compact = true, focusId = "pending.apply")
            TvAction(stringResource(R.string.r2_discard_settings), viewModel::discardPendingSettings,
                enabled = !state.starting, compact = true, focusId = "pending.discard")
        }
    }
}

private enum class SettingsCategory { CONNECTION, PLAYBACK, RECORDING, STARTUP, PERMISSIONS, APPEARANCE, ABOUT }

@Composable
fun ReceiverSettings(viewModel: MainViewModel, onAdvanced: () -> Unit, onModalChange: (Boolean) -> Unit = {}) {
    val name by viewModel.serverName.collectAsStateWithLifecycle()
    val auto by viewModel.autoStart.collectAsStateWithLifecycle()
    val boot by viewModel.bootAutoStart.collectAsStateWithLifecycle()
    val background by viewModel.runInBackground.collectAsStateWithLifecycle()
    val launchOnConnect by viewModel.launchOnConnect.collectAsStateWithLifecycle()
    val access = permissionAccess()
    val activity = access.activity
    val automaticDisplayAllowed = access.display
    val profile by viewModel.streamProfile.collectAsStateWithLifecycle()
    val pin by viewModel.requirePin.collectAsStateWithLifecycle()
    val takeover by viewModel.allowNewConn.collectAsStateWithLifecycle()
    val fullscreen by viewModel.autoFullscreen.collectAsStateWithLifecycle()
        val keepScreen by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val reduced by viewModel.reduceMotion.collectAsStateWithLifecycle()
    val largeText by viewModel.largeText.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val metrics by viewModel.showStreamMetrics.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(SettingsCategory.CONNECTION) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var disablePin by remember { mutableStateOf(false) }
    var allowTakeover by remember { mutableStateOf(false) }
    var restartModal by remember { mutableStateOf(false) }
    var devicePicker by remember { mutableStateOf(false) }
    val themeFocus = remember { FocusRequester() }
    var returnThemeFocus by rememberSaveable { mutableStateOf(false) }
    var themePicker by rememberSaveable { mutableStateOf(false) }
    val languageFocus = remember { FocusRequester() }
    var returnLanguageFocus by rememberSaveable { mutableStateOf(false) }
    var languagePicker by rememberSaveable { mutableStateOf(false) }
    val blocked = LocalBlockingOverlay.current
    val modal = !blocked && (editName || disablePin || allowTakeover || themePicker || languagePicker || restartModal || devicePicker)
    SideEffect { onModalChange(modal) }
    DisposableEffect(Unit) { onDispose { onModalChange(false) } }
    val settingsList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(theme, themePicker, returnThemeFocus, blocked) {
        if (returnThemeFocus && !themePicker && !blocked && category == SettingsCategory.APPEARANCE) {
            settingsList.scrollToItem(1)
            kotlinx.coroutines.delay(180)
            themeFocus.requestFocusUntilLanded()
            returnThemeFocus = false
        }
    }
    LaunchedEffect(language, languagePicker, returnLanguageFocus, blocked) {
        if (returnLanguageFocus && !languagePicker && !blocked && category == SettingsCategory.APPEARANCE) {
            settingsList.scrollToItem(2)
            kotlinx.coroutines.delay(180)
            languageFocus.requestFocusUntilLanded()
            returnLanguageFocus = false
        }
    }
    fun selectCategory(next: SettingsCategory) {
        category = next
        scope.launch { settingsList.scrollToItem(0) }
    }
    val categories = listOf(SettingsCategory.CONNECTION to R.string.p_section_connection,
        SettingsCategory.PLAYBACK to R.string.settings_playback, SettingsCategory.RECORDING to R.string.rec_options_title,
        SettingsCategory.STARTUP to R.string.p_section_startup, SettingsCategory.PERMISSIONS to R.string.permission_section,
        SettingsCategory.APPEARANCE to R.string.p_section_appearance,
        SettingsCategory.ABOUT to R.string.p_about)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageHeading(stringResource(R.string.p_settings_title), stringResource(R.string.r2_settings_intro))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val navigation: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (item, label) ->
                        TvAction(stringResource(label), { selectCategory(item) }, Modifier.fillMaxWidth(),
                            selected = category == item, focusId = "settings.category.${item.name}", compact = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    TvAction(stringResource(R.string.p_advanced), onAdvanced, Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Tune, focusId = "settings.advanced", compact = true)
                }
            }
            val settings: @Composable (Modifier) -> Unit = { modifier ->
                LazyColumn(modifier, state = settingsList, contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "pending") { PendingSettingsPanel(viewModel) { restartModal = it } }
                    when (category) {
                        SettingsCategory.CONNECTION -> {
                            item(key = "name") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.p_receiver_name), style = MaterialTheme.typography.labelMedium, color = AirRelayColors.Muted)
                                    TvAction(name, { editName = true }, Modifier.fillMaxWidth(), Icons.Rounded.Edit, focusId = "settings.name")
                                }
                            }
                            item(key = "pin") { TvPreference(stringResource(R.string.p_require_pin), stringResource(R.string.p_require_pin_desc), pin,
                                { if (it) viewModel.setRequirePin(true) else disablePin = true }, focusId = "settings.pin") }
                            item(key = "takeover") { TvPreference(stringResource(R.string.p_takeover), stringResource(R.string.p_takeover_desc), takeover,
                                { if (it) allowTakeover = true else viewModel.setAllowNewConn(false) }, focusId = "settings.takeover") }
                            item(key = "devices") { TvAction(stringResource(R.string.access_devices), { devicePicker = true }, Modifier.fillMaxWidth(), compact = true) }
                        }
                        SettingsCategory.PLAYBACK -> {
                            item(key = "stream-profile") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.stream_profile_title), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.stream_profile_hint), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                                    listOf("balanced" to R.string.stream_profile_balanced,
                                        "quality" to R.string.stream_profile_quality,
                                        "responsive" to R.string.stream_profile_responsive,
                                        "custom" to R.string.stream_profile_custom
                                    ).plus(if (profile == "stable") listOf("stable" to R.string.stream_profile_stable) else emptyList()).forEach { (key, label) ->
                                        TvAction(stringResource(label), { viewModel.setStreamProfile(key) },
                                            Modifier.fillMaxWidth(), selected = profile == key,
                                            focusId = "settings.profile.$key", compact = true)
                                    }
                                    if (profile == "custom") TvAction(stringResource(R.string.custom_advanced_shortcut), onAdvanced,
                                        Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune, compact = true, focusId = "settings.custom-advanced")
                                    Text(stringResource(when (profile) {
                                        "quality" -> R.string.profile_quality_detail
                                        "responsive", "stable" -> R.string.profile_responsive_detail
                                        "custom" -> R.string.profile_custom_detail
                                        else -> R.string.profile_balanced_detail
                                    }), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                                }
                            }
                            item(key = "fullscreen") { TvPreference(stringResource(R.string.p_fullscreen), stringResource(R.string.p_fullscreen_desc), fullscreen,
                                viewModel::setAutoFullscreen, focusId = "settings.fullscreen") }
                            item(key = "keep-screen") { TvPreference(stringResource(R.string.p_keep_screen), stringResource(R.string.p_keep_screen_desc), keepScreen,
                                viewModel::setKeepScreenOn, focusId = "settings.keep-screen") }
                            item(key = "stream-metrics") { TvPreference(stringResource(R.string.stream_metrics),
                                stringResource(R.string.stream_metrics_desc), metrics, viewModel::setShowStreamMetrics,
                                focusId = "settings.metrics") }
                        }
                        SettingsCategory.RECORDING -> {
                            item(key = "recording-quality") { RecordingQualitySetting() }
                        }
                        SettingsCategory.STARTUP -> {
                            item(key = "auto") { TvPreference(stringResource(R.string.p_setup_open), stringResource(R.string.p_setup_open_desc), auto, viewModel::setAutoStart, focusId = "settings.auto") }
                            item(key = "boot") { TvPreference(stringResource(R.string.p_setup_boot), stringResource(R.string.p_setup_boot_desc), boot, viewModel::setBootAutoStart, focusId = "settings.boot") }
                            item(key = "background") { TvPreference(stringResource(R.string.p_setup_background), stringResource(R.string.p_setup_background_desc), background, viewModel::setRunInBackground, focusId = "settings.background") }
                            item(key = "automatic-display") {
                                TvPreference(stringResource(R.string.setting_launch_on_connect), stringResource(R.string.setting_launch_on_connect_desc),
                                    launchOnConnect, viewModel::setLaunchOnConnect, focusId = "settings.automatic-display")
                                if (launchOnConnect && !automaticDisplayAllowed) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.setting_launch_on_connect_no_permission), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Warning)
                                    Spacer(Modifier.height(8.dp))
                                    TvAction(stringResource(R.string.p_automatic_display_permission), { activity?.requestAutomaticDisplayPermission() },
                                        enabled = activity != null, compact = true, focusId = "settings.display-permission")
                                }
                            }
                        }
                        SettingsCategory.PERMISSIONS -> {
                            item(key = "notification-permission") {
                                PermissionManagementCard(stringResource(R.string.permission_notifications_label),
                                    stringResource(R.string.permission_notifications_body), access.notifications, "settings.permission.notifications") {
                                    activity?.openNotificationSettings()
                                }
                            }
                            if (needsDisplayPermission) item(key = "display-permission") {
                                PermissionManagementCard(stringResource(R.string.permission_display_label),
                                    stringResource(R.string.permission_display_body), access.display, "settings.permission.display") {
                                    activity?.openAutomaticDisplaySettings()
                                }
                            }
                            item(key = "network-permission") { PermissionNetworkNote() }
                            item(key = "automatic-permissions") {
                                Text(stringResource(R.string.permission_automatic_body), style = MaterialTheme.typography.bodySmall,
                                    color = AirRelayColors.Muted, modifier = Modifier.padding(16.dp))
                            }
                        }
                        SettingsCategory.APPEARANCE -> {
                            item(key = "themes") {
                                val effective = io.github.jqssun.airplay.receiver.AppearancePolicy.resolve(theme)
                                val currentLabel = when (effective) {
                                    "classic" -> R.string.theme_classic
                                    "light" -> R.string.theme_light
                                    else -> R.string.theme_dark
                                }
                                TvAction("${stringResource(R.string.appearance_theme)}: ${stringResource(currentLabel)}",
                                    { themePicker = true }, Modifier.fillMaxWidth().focusRequester(themeFocus), selected = true,
                                    compact = false, focusId = "settings.theme")
                            }
                            item(key = "language") {
                                val label = io.github.jqssun.airplay.receiver.AppLanguages.choices
                                    .firstOrNull { it.first == language }?.second ?: stringResource(R.string.p_language_system)
                                TvAction("${stringResource(R.string.p_language)}: $label", { languagePicker = true },
                                    Modifier.fillMaxWidth().focusRequester(languageFocus), compact = false,
                                    focusId = "settings.language", icon = Icons.Rounded.Language)
                            }
                            item(key = "motion") { TvPreference(stringResource(R.string.p_reduce_motion), stringResource(R.string.p_reduce_motion_desc), reduced, viewModel::setReduceMotion, focusId = "settings.motion") }
                            item(key = "text") { TvPreference(stringResource(R.string.p_large_text), stringResource(R.string.p_large_text_desc), largeText, viewModel::setLargeText, focusId = "settings.text") }
                        }
                        SettingsCategory.ABOUT -> {
                            item(key = "about") { AboutProductContent() }
                        }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                navigation(Modifier.width(178.dp).fillMaxHeight().verticalScroll(rememberScrollState()).focusGroup().padding(3.dp))
                settings(Modifier.weight(1f).fillMaxHeight().focusGroup())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).focusGroup().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { (item, label) -> TvAction(stringResource(label), { selectCategory(item) }, selected = category == item,
                        compact = true, focusId = "settings.category.${item.name}") }
                    TvAction(stringResource(R.string.p_advanced), onAdvanced, compact = true, focusId = "settings.advanced")
                }
                settings(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    if (!blocked) DialogInputScope {
        when {
            devicePicker -> DeviceAccessDialog(viewModel) { devicePicker = false }
            editName -> NameEditor(name, onSave = { viewModel.setServerName(it); editName = false }, onCancel = { editName = false })
            themePicker -> ThemePicker(theme, onSelect = { returnThemeFocus = true; viewModel.setTheme(it); themePicker = false }, onCancel = { returnThemeFocus = true; themePicker = false })
            languagePicker -> LanguagePicker(language, onSelect = {
                returnLanguageFocus = true; viewModel.setLanguage(it); languagePicker = false
            }, onCancel = { returnLanguageFocus = true; languagePicker = false })
            disablePin -> ConfirmReceiverAction(stringResource(R.string.p_disable_pin_title), stringResource(R.string.p_disable_pin_body),
                stringResource(R.string.p_disable_pin_confirm), onConfirm = { viewModel.setRequirePin(false); disablePin = false }, onCancel = { disablePin = false })
            allowTakeover -> ConfirmReceiverAction(stringResource(R.string.r2_takeover_title), stringResource(R.string.r2_takeover_body),
                stringResource(R.string.r2_takeover_confirm), onConfirm = { viewModel.setAllowNewConn(true); allowTakeover = false }, onCancel = { allowTakeover = false })
        }
    }
}

@Composable
private fun NameEditor(name: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    val valid = ReceiverInput.validName(draft)
    val field = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { field.requestFocusUntilLanded(isFocused = { focused }) }
    ProductAlertDialog(onDismissRequest = onCancel, title = { Text(stringResource(R.string.p_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvManualTextField(value = draft, onValueChange = { draft = it.take(96) },
                    label = { Text(stringResource(R.string.p_name_label)) }, singleLine = true,
                    isError = !valid, modifier = Modifier.fillMaxWidth().focusRequester(field).onFocusChanged { focused = it.isFocused },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, showKeyboardOnFocus = false),
                    keyboardActions = KeyboardActions(onDone = { if (valid) { if (draft != name) onSave(draft) else onCancel() } }))
                Text(stringResource(if (valid) R.string.p_receiver_name_desc else R.string.p_name_error), style = MaterialTheme.typography.bodySmall,
                    color = if (valid) AirRelayColors.Muted else MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TvAction(stringResource(R.string.p_save), { onSave(draft) }, primary = true, enabled = valid && draft != name) },
        dismissButton = { TvAction(stringResource(R.string.p_cancel), onCancel) })
}

@Composable
private fun ThemePicker(current: String, onSelect: (String) -> Unit, onCancel: () -> Unit) {
    val choices = listOf(
        "classic" to R.string.theme_classic,
        "light" to R.string.theme_light,
        "dark" to R.string.theme_dark,
    )
    ProductAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.appearance_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (key, label) ->
                    TvAction(stringResource(label), { onSelect(key) }, Modifier.fillMaxWidth(),
                        selected = current == key, compact = true, focusId = "settings.theme.dialog.$key")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TvAction(stringResource(R.string.p_cancel), onCancel) }
    )
}

@Composable
private fun LanguagePicker(current: String, onSelect: (String) -> Unit, onCancel: () -> Unit) {
    val choices = listOf("system" to stringResource(R.string.p_language_system)) +
        io.github.jqssun.airplay.receiver.AppLanguages.choices
    val selected = choices.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocusUntilLanded() }
    ProductAlertDialog(onDismissRequest = onCancel, title = { Text(stringResource(R.string.p_language)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), state = list,
                contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(choices.size, key = { choices[it].first }) { index ->
                    val (code, label) = choices[index]
                    TvAction(label, { onSelect(code) }, Modifier.fillMaxWidth().then(
                        if (index == selected) Modifier.focusRequester(initialFocus) else Modifier),
                        selected = code == current, compact = true, focusId = "settings.language.dialog.$code")
                }
            }
        }, confirmButton = {}, dismissButton = { TvAction(stringResource(R.string.p_cancel), onCancel) })
}

@Composable
private fun DeviceAccessDialog(viewModel: MainViewModel, onClose: () -> Unit) {
    var devices by remember { mutableStateOf(viewModel.pairedDevices()) }
    var failed by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.requestFocusUntilLanded() }
    ProductAlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.access_devices)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.access_hint))
                if (devices.isEmpty()) Text(stringResource(R.string.access_empty))
                devices.forEach { device ->
                    TvPreference(device.name.ifBlank { stringResource(R.string.access_device) } + " · " + device.id,
                        stringResource(if (device.blocked) R.string.access_blocked else R.string.access_allowed), device.blocked,
                        { blocked ->
                            failed = !viewModel.blockPairedDevice(device.key, blocked)
                            devices = viewModel.pairedDevices()
                        })
                }
                if (failed) Text(stringResource(R.string.access_save_failed), color = AirRelayColors.Danger)
            }
        }, confirmButton = { TvAction(stringResource(R.string.access_close), onClose, Modifier.focusRequester(closeFocus)) }, dismissButton = {})
}
