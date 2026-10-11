package io.github.jqssun.airplay.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.receiver.ReceiverInput
import io.github.jqssun.airplay.receiver.StreamProfile
import io.github.jqssun.airplay.viewmodel.MainViewModel
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val inputsEnabled = LocalTvInputEnabled.current
    val h265Enabled by viewModel.h265Enabled.collectAsStateWithLifecycle()
    val enforceSdr by viewModel.enforceSdr.collectAsStateWithLifecycle()
    val alacEnabled by viewModel.alacEnabled.collectAsStateWithLifecycle()
    val aacEnabled by viewModel.aacEnabled.collectAsStateWithLifecycle()
    val resolution by viewModel.resolution.collectAsStateWithLifecycle()
    val advertiseAudio by viewModel.advertiseAudio.collectAsStateWithLifecycle()
    val launchOnConnect by viewModel.launchOnConnect.collectAsStateWithLifecycle()
    val maxFps by viewModel.maxFps.collectAsStateWithLifecycle()
    val fpsLimit = 120
    val overscanned by viewModel.overscanned.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val remoteVideoMaxBitrate by viewModel.remoteVideoMaxBitrate.collectAsStateWithLifecycle()
    val audioLatencyMs by viewModel.audioLatencyMs.collectAsStateWithLifecycle()
    val forceSwAlac by viewModel.forceSwAlac.collectAsStateWithLifecycle()
    val developerOptions by viewModel.developerOptions.collectAsStateWithLifecycle()
    val keyAllowFrameDrop by viewModel.keyAllowFrameDrop.collectAsStateWithLifecycle()
    val realtimeDecoderPriority by viewModel.realtimeDecoderPriority.collectAsStateWithLifecycle()
    val lowLatency by viewModel.lowLatency.collectAsStateWithLifecycle()
    val operatingRate by viewModel.operatingRate.collectAsStateWithLifecycle()
    val scheduledOutputBufferRelease by viewModel.scheduledOutputBufferRelease.collectAsStateWithLifecycle()
    val audioAutoBuffer by viewModel.audioAutoBuffer.collectAsStateWithLifecycle()
    val audioCushionMs by viewModel.audioCushionMs.collectAsStateWithLifecycle()
    val audioAdaptiveStep by viewModel.audioAdaptiveStep.collectAsStateWithLifecycle()
    val oboeBufferFrames by viewModel.oboeBufferFrames.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        SectionHeader(stringResource(R.string.section_server))


        SettingTextField(
            label = stringResource(R.string.setting_server_port),
            focusKey = "setting_server_port",
            value = serverPort.toString(),
            onCommit = { viewModel.setServerPort(it.toInt()) },
            range = 1..65535
        )



        SectionHeader(stringResource(R.string.section_display))

        SettingChips(
            title = stringResource(R.string.setting_remote_bitrate),
            description = stringResource(R.string.setting_remote_bitrate_desc),
            value = remoteVideoMaxBitrate.toString(),
            options = listOf("0" to stringResource(R.string.chip_auto), "4000000" to "4 Mbps",
                "8000000" to "8 Mbps", "12000000" to "12 Mbps", "20000000" to "20 Mbps", "40000000" to "40 Mbps"),
            onValueChange = { value -> value.toIntOrNull()?.let(viewModel::setRemoteVideoMaxBitrate) }
        )


        SettingResolution(
            value = resolution,
            onValueChange = { viewModel.setResolution(it) }
        )

        SettingChipField(
            title = stringResource(R.string.setting_max_fps),
            focusKey = "setting_max_fps",
            description = stringResource(R.string.setting_max_fps_desc, fpsLimit),
            value = maxFps.coerceIn(1, fpsLimit).toString(),
            presets = listOf(24, 30, 60, 90, 120).map { it.toString() to it.toString() },
            placeholder = stringResource(R.string.setting_max_fps_placeholder, fpsLimit),
            keyboardType = KeyboardType.Number,
            validate = { it.toIntOrNull()?.let { fps -> fps in 1..fpsLimit } == true },
            onValueChange = { it.toIntOrNull()?.takeIf { fps -> fps in 1..fpsLimit }?.let(viewModel::setMaxFps) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_overscanned),
            focusKey = "setting_overscanned",
            description = stringResource(R.string.setting_overscanned_desc),
            checked = overscanned,
            onCheckedChange = { viewModel.setOverscanned(it) }
        )

        SectionHeader(stringResource(R.string.section_decode))

        SettingSwitch(
            title = stringResource(R.string.setting_h265),
            focusKey = "setting_h265",
            description = stringResource(R.string.setting_h265_desc),
            checked = h265Enabled,
            onCheckedChange = { viewModel.setH265Enabled(it) }
        )

        SettingSwitch(
            title = stringResource(R.string.setting_sw_alac),
            focusKey = "setting_sw_alac",
            description = stringResource(R.string.setting_sw_alac_desc),
            checked = forceSwAlac,
            onCheckedChange = { viewModel.setForceSwAlac(it) }
        )

        SectionHeader(stringResource(R.string.section_developer))

        SettingSwitch(
            title = stringResource(R.string.setting_developer_options),
            focusKey = "setting_developer_options",
            description = stringResource(R.string.setting_developer_options_desc),
            checked = developerOptions,
            onCheckedChange = { viewModel.setDeveloperOptions(it) }
        )

        if (developerOptions) {



            SettingSwitch(
                title = stringResource(R.string.setting_advertise_audio),
                focusKey = "setting_advertise_audio",
                description = stringResource(R.string.setting_advertise_audio_desc),
                checked = advertiseAudio,
                onCheckedChange = { viewModel.setAdvertiseAudio(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_alac),
                focusKey = "setting_alac",
                description = stringResource(R.string.setting_alac_desc),
                checked = alacEnabled,
                onCheckedChange = { viewModel.setAlacEnabled(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_aac),
                focusKey = "setting_aac",
                description = stringResource(R.string.setting_aac_desc),
                checked = aacEnabled,
                onCheckedChange = { viewModel.setAacEnabled(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_key_allow_frame_drop),
                focusKey = "setting_key_allow_frame_drop",
                description = stringResource(R.string.setting_key_allow_frame_drop_desc),
                checked = keyAllowFrameDrop,
                onCheckedChange = { viewModel.setKeyAllowFrameDrop(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_enforce_sdr),
                focusKey = "setting_enforce_sdr",
                description = stringResource(R.string.setting_enforce_sdr_desc),
                checked = enforceSdr,
                onCheckedChange = { viewModel.setEnforceSdr(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_realtime_decoder_priority),
                focusKey = "setting_realtime_decoder_priority",
                description = stringResource(R.string.setting_realtime_decoder_priority_desc),
                checked = realtimeDecoderPriority,
                onCheckedChange = { viewModel.setRealtimeDecoderPriority(it) }
            )

            SettingChips(
                title = stringResource(R.string.setting_operating_rate),
                description = stringResource(R.string.setting_operating_rate_desc),
                value = operatingRate,
                options = listOf(
                    Prefs.AUTO to stringResource(R.string.chip_auto),
                    Prefs.ON to stringResource(R.string.chip_on),
                    Prefs.OFF to stringResource(R.string.chip_off),
                ),
                onValueChange = { viewModel.setOperatingRate(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_low_latency),
                focusKey = "setting_low_latency",
                description = stringResource(R.string.setting_low_latency_desc),
                checked = lowLatency,
                onCheckedChange = { viewModel.setLowLatency(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_scheduled_output_buffer_release),
                focusKey = "setting_scheduled_output_buffer_release",
                description = stringResource(R.string.setting_scheduled_output_buffer_release_desc),
                checked = scheduledOutputBufferRelease,
                onCheckedChange = { viewModel.setScheduledOutputBufferRelease(it) }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_audio_delay),
                focusKey = "setting_audio_delay",
                description = stringResource(R.string.setting_audio_delay_desc),
                checked = audioLatencyMs >= 0,
                onCheckedChange = { viewModel.setAudioLatencyMs(if (it) 250 else -1) }
            )

            if (audioLatencyMs >= 0) {
                var sliderVal by remember(audioLatencyMs) { mutableFloatStateOf(audioLatencyMs.toFloat()) }
                ListItem(
                    headlineContent = {
                        Slider(
                            enabled = inputsEnabled,
                            value = sliderVal,
                            onValueChange = { sliderVal = it },
                            onValueChangeFinished = { viewModel.setAudioLatencyMs(sliderVal.roundToInt()) },
                            valueRange = 0f..1000f,
                            steps = 19,
                            modifier = Modifier.tvFocusId("advanced.audio-delay", inputsEnabled).dpadFocus().dpadAdjust(
                                onLeft = { if (inputsEnabled) viewModel.setAudioLatencyMs((audioLatencyMs - 50).coerceIn(0, 1000)) },
                                onRight = { if (inputsEnabled) viewModel.setAudioLatencyMs((audioLatencyMs + 50).coerceIn(0, 1000)) }
                            )
                        )
                    },
                    trailingContent = { Text(stringResource(R.string.audio_delay_value, sliderVal.roundToInt())) }
                )
            }

            SettingSwitch(
                title = stringResource(R.string.setting_audio_auto_buffer),
                focusKey = "setting_audio_auto_buffer",
                description = stringResource(R.string.setting_audio_auto_buffer_desc),
                checked = audioAutoBuffer,
                onCheckedChange = { viewModel.setAudioAutoBuffer(it) }
            )

            if (audioAutoBuffer) {
                val maxStep = Prefs.ADAPTIVE_PERCENTILES.size - 1
                var stepVal by remember(audioAdaptiveStep) { mutableFloatStateOf(audioAdaptiveStep.toFloat()) }
                ListItem(
                    headlineContent = {
                        Slider(
                            enabled = inputsEnabled,
                            value = stepVal,
                            onValueChange = { stepVal = it },
                            onValueChangeFinished = { viewModel.setAudioAdaptiveStep(stepVal.roundToInt()) },
                            valueRange = 0f..maxStep.toFloat(),
                            steps = maxStep - 1,
                            modifier = Modifier.tvFocusId("advanced.audio-adaptive", inputsEnabled).dpadFocus().dpadAdjust(
                                onLeft = { if (inputsEnabled) viewModel.setAudioAdaptiveStep((audioAdaptiveStep - 1).coerceIn(0, maxStep)) },
                                onRight = { if (inputsEnabled) viewModel.setAudioAdaptiveStep((audioAdaptiveStep + 1).coerceIn(0, maxStep)) }
                            )
                        )
                    },
                    trailingContent = {
                        val step = stepVal.roundToInt().coerceIn(0, maxStep)
                        Text(stringResource(R.string.audio_adaptive_value,
                            Prefs.ADAPTIVE_PERCENTILES[step],
                            stringArrayResource(R.array.audio_adaptive_step_names)[step]))
                    }
                )
            } else {
                SettingTextField(
                    label = stringResource(R.string.setting_audio_cushion_ms),
                    focusKey = "setting_audio_cushion_ms",
                    value = audioCushionMs.toString(),
                    onCommit = { viewModel.setAudioCushionMs(it.toInt()) },
                    description = stringResource(R.string.setting_audio_cushion_ms_desc),
                    range = 1..1000
                )
            }

            SettingTextField(
                label = stringResource(R.string.setting_oboe_buffer_frames),
                focusKey = "setting_oboe_buffer_frames",
                value = oboeBufferFrames.toString(),
                onCommit = { viewModel.setOboeBufferFrames(it.toInt()) },
                description = stringResource(R.string.setting_oboe_buffer_frames_desc),
                range = 0..8192
            )



        }
    }
}

private fun canAutoLaunch(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(ctx)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingResolution(
    value: String,
    onValueChange: (String) -> Unit
) {
    val presets = listOf(
        Prefs.AUTO to stringResource(R.string.chip_auto),
        "1280x720" to "1280x720",
        "1920x1080" to "1920x1080",
        "3840x2160" to "3840x2160"
    )
    val devicePresets = listOf(
        "portrait" to stringResource(R.string.chip_device_portrait),
        "landscape" to stringResource(R.string.chip_device_landscape)
    )
    val isPreset = (presets + devicePresets).any { it.first == value }
    var editing by rememberSaveable { mutableStateOf(false) }
    val parts = if (!isPreset && value.contains("x")) value.split("x", limit = 2) else listOf("", "")
    var width by rememberSaveable(value) { mutableStateOf(if (isPreset) "" else parts[0]) }
    var height by rememberSaveable(value) { mutableStateOf(if (isPreset) "" else parts.getOrElse(1) { "" }) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.setting_resolution)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.setting_resolution_desc))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable
                    fun presetChip(key: String, label: String) = FilterChip(
                        enabled = LocalTvInputEnabled.current,
                        selected = value == key && !editing,
                        onClick = {
                            editing = false
                            width = ""; height = ""
                            onValueChange(key)
                        },
                        label = { Text(label) },
                        modifier = Modifier.dpadFocus()
                    )
                    presets.forEach { (key, label) -> presetChip(key, label) }
                    FilterChip(
                        enabled = LocalTvInputEnabled.current,
                        selected = !isPreset || editing,
                        onClick = { editing = true },
                        label = { Text(stringResource(R.string.chip_custom)) },
                        modifier = Modifier.dpadFocus()
                    )
                    devicePresets.forEach { (key, label) -> presetChip(key, label) }
                }
                if (editing || !isPreset) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvManualTextField(
                            enabled = LocalTvInputEnabled.current,
                            value = width,
                            onValueChange = { width = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.setting_resolution_width)) },
                            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        TvManualTextField(
                            enabled = LocalTvInputEnabled.current,
                            value = height,
                            onValueChange = { height = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            label = { Text(stringResource(R.string.setting_resolution_height)) },
                            keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = doneAndHide(valid = ReceiverInput.validResolution("${width}x${height}")) {
                                val w = width.toIntOrNull()
                                val h = height.toIntOrNull()
                                if (w != null && h != null && ReceiverInput.validResolution("${w}x${h}")) {
                                    onValueChange("${w}x${h}")
                                    editing = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val valid = ReceiverInput.validResolution("${width}x${height}")
                    if (!valid) Text(stringResource(R.string.r2_invalid_resolution), color = MaterialTheme.colorScheme.error)
                    TvAction(stringResource(R.string.p_save), {
                        onValueChange("${width.toInt()}x${height.toInt()}"); editing = false
                    }, enabled = valid, compact = true, focusId = "advanced.resolution.save")
                }
            }
        }
    )
}

@Composable
private fun doneAndHide(valid: Boolean = true, commit: () -> Unit): KeyboardActions {
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions(onDone = { if (valid) { commit(); keyboard?.hide() } })
}

@Composable
private fun SettingTextField(label: String, value: String, onCommit: (String) -> Unit,
    description: String? = null, range: IntRange? = null, focusKey: String? = null) {
    // Focus identity must not depend on localized text: switching UI language used to invalidate
    // the remembered id, and two settings with the same translated label collided.
    val key = focusKey ?: label
    var text by rememberSaveable(value) { mutableStateOf(value) }
    val valid = if (range == null) text.isNotBlank() else text.toIntOrNull()?.let { it in range } == true
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvManualTextField(value = text, onValueChange = { text = if (range == null) it.take(96) else it.filter { c -> c.isDigit() }.take(6) },
            label = { Text(label) }, singleLine = true, isError = !valid, enabled = LocalTvInputEnabled.current,
            supportingText = {
                when {
                    !valid && range != null -> Text(stringResource(R.string.r2_invalid_number, range.first, range.last))
                    description != null -> Text(description)
                }
            }, keyboardOptions = KeyboardOptions(keyboardType = if (range == null) KeyboardType.Text else KeyboardType.Number, imeAction = ImeAction.Done, showKeyboardOnFocus = false),
            keyboardActions = KeyboardActions(onDone = { if (valid) { if (text != value) onCommit(text); keyboard?.hide() } }),
            modifier = Modifier.fillMaxWidth().tvFocusId("advanced.field.$key", LocalTvInputEnabled.current))
        TvAction(stringResource(R.string.p_save), { onCommit(text) }, enabled = valid && text != value,
            compact = true, focusId = "advanced.save.$key")
    }
}

@Composable
private fun SettingChipField(
    title: String,
    description: String,
    value: String,
    presets: List<Pair<String, String>>,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> Boolean = { it.isNotBlank() },
    onValueChange: (String) -> Unit,
    focusKey: String? = null
) {
    val isPreset = presets.any { it.first == value }
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(value) { mutableStateOf(if (isPreset) "" else value) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Spacer(Modifier.height(8.dp))
                ChipRow(if (editing) "" else value, presets, { editing = false; text = ""; onValueChange(it) }) {
                    FilterChip(
                        enabled = LocalTvInputEnabled.current,
                        selected = !isPreset || editing,
                        onClick = { editing = true },
                        label = { Text(stringResource(R.string.chip_custom)) },
                        modifier = Modifier.dpadFocus()
                    )
                }
                if (editing || !isPreset) {
                    Spacer(Modifier.height(8.dp))
                    TvManualTextField(
                            enabled = LocalTvInputEnabled.current,
                        value = text,
                        onValueChange = { text = it.take(12) },
                        singleLine = true,
                        isError = !validate(text),
                        label = { Text(placeholder) },
                        keyboardOptions = KeyboardOptions(showKeyboardOnFocus = false,
                            keyboardType = keyboardType,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = doneAndHide(valid = validate(text)) {
                            if (validate(text)) {
                                onValueChange(text)
                                editing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().tvFocusId("advanced.custom.${focusKey ?: title}", LocalTvInputEnabled.current)
                    )
                    TvAction(stringResource(R.string.p_save), { onValueChange(text); editing = false },
                        enabled = validate(text), compact = true, focusId = "advanced.custom.save.${focusKey ?: title}")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                        enabled = LocalTvInputEnabled.current,
                selected = value == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.dpadFocus()
            )
        }
        trailing()
    }
}

@Composable
private fun SettingChips(
    title: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Spacer(Modifier.height(8.dp))
                ChipRow(value, options, onValueChange)
            }
        }
    )
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    focusKey: String? = null) {
    TvPreference(title, description, checked, onCheckedChange,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
        focusId = "advanced.toggle.${focusKey ?: title}")
}
