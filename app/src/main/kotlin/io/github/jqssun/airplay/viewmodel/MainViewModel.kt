package io.github.jqssun.airplay.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.Surface
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.audio.TrackInfo
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.diagnostics.DiagnosticStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject
import java.util.Locale

data class AudioDebug(
    val backlogMs: Int,
    val tunedCushionMs: Int,
    val trims: Int,
    val drops: Int,
    val silences: Int,
    val underruns: Int,
    val xrun: Int,
    val decodeMeanUs: Int,
    val decodeMaxUs: Int,
    val decodeHeld: Int,
    val decodeErrors: Int,
)

data class DebugInfo(
    val videoCodec: String = "",
    val videoRes: String = "",
    val videoFps: Int = 0,
    val videoBitrate: Long = 0,
    val videoFrames: Long = 0,
    val droppedFrames: Long = 0,
    val framePacingJitterUs: Long = 0,
    val audioCodec: String = "",
    val audioVolume: Int = 100,
    val audio: AudioDebug? = null,
    val connections: Int = 0,
) {
    // Locale.ROOT keeps the metrics panel consistent with StreamMetrics and avoids mixed
    // decimal separators under locales such as tr/de.
    val bitrateStr: String get() {
        val kbps = videoBitrate / 1000
        return if (kbps >= 1000) "${"%.1f".format(Locale.ROOT, kbps / 1000.0)} Mbps" else "$kbps Kbps"
    }
    val jitterStr: String get() {
        return if (framePacingJitterUs >= 1000) {
            "${"%.1f".format(Locale.ROOT, framePacingJitterUs / 1000.0)} ms"
        } else {
            "$framePacingJitterUs us"
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {

    private val deviceAccess = TrustedDevices(app.getSharedPreferences("trusted_airplay_devices", Context.MODE_PRIVATE))
    fun pairedDevices() = deviceAccess.devices()
    fun blockPairedDevice(key: String, blocked: Boolean) = deviceAccess.setBlocked(key, blocked)
    private val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val experience = app.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
    private val pendingSettings = PendingReceiverSettings(app)
    private val controller = ReceiverController()
    private val diagnostics = DiagnosticStore.get(app)
    private var serviceObservation: Job? = null
    private var pendingMirrorSurface: Surface? = null
    private var pendingPlaybackSurface: Surface? = null
    private var pendingUserStart = false
    private val _feedback = MutableStateFlow<UiFeedback?>(null)
    val feedback = _feedback.asStateFlow()
    private var feedbackSequence = 0L
    private fun feedback(kind: UiFeedbackKind) { _feedback.value = UiFeedback(++feedbackSequence, kind) }
    fun dismissFeedback(sequence: Long) { if (_feedback.value?.sequence == sequence) _feedback.value = null }
    fun dismissNotice() = controller.dismissNotice()
    private val _diagnosticsVisible = MutableStateFlow(false)
    fun setDiagnosticsVisible(visible: Boolean) { _diagnosticsVisible.value = visible }
    private val _receiverState = MutableStateFlow(ReceiverUiState(pendingSettings = pendingSettings.hasPending()))
    val receiverState = _receiverState.asStateFlow()
    private val _serviceBound = MutableStateFlow(false)
    val serviceBound = _serviceBound.asStateFlow()
    private val _setupComplete = MutableStateFlow(experience.getBoolean(UserExperiencePrefs.SETUP_COMPLETE, false))
    val setupComplete = _setupComplete.asStateFlow()
    private val _language = MutableStateFlow(experience.getString(UserExperiencePrefs.LANGUAGE, "system") ?: "system")
    val language = _language.asStateFlow()
    private val _reduceMotion = MutableStateFlow(experience.getBoolean(UserExperiencePrefs.REDUCE_MOTION, false))
    val reduceMotion = _reduceMotion.asStateFlow()
    private val _largeText = MutableStateFlow(experience.getBoolean(UserExperiencePrefs.LARGE_TEXT, false))
    val largeText = _largeText.asStateFlow()
    private val _theme = MutableStateFlow(AppearancePolicy.resolve(experience.getString(UserExperiencePrefs.THEME, "classic") ?: "classic"))
    val theme = _theme.asStateFlow()
    private val _showStreamMetrics = MutableStateFlow(experience.getBoolean(UserExperiencePrefs.SHOW_STREAM_METRICS, false))
    val showStreamMetrics = _showStreamMetrics.asStateFlow()
    fun setTheme(value: String) {
        if (value !in AppearancePolicy.themes) return
        experience.edit().putString(UserExperiencePrefs.THEME, value).apply()
        _theme.value = value
    }
    fun setShowStreamMetrics(value: Boolean) {
        experience.edit().putBoolean(UserExperiencePrefs.SHOW_STREAM_METRICS, value).apply()
        _showStreamMetrics.value = value
    }
    fun setLanguage(value: String) {
        if (!io.github.jqssun.airplay.receiver.AppLanguages.isSupportedSelection(value)) return
        experience.edit().putString(UserExperiencePrefs.LANGUAGE, value).apply()
        _language.value = value
        service?.refreshPresentationLanguage()
    }
    fun setReduceMotion(value: Boolean) {
        experience.edit().putBoolean(UserExperiencePrefs.REDUCE_MOTION, value).apply()
        _reduceMotion.value = value
    }
    fun setLargeText(value: Boolean) {
        experience.edit().putBoolean(UserExperiencePrefs.LARGE_TEXT, value).apply()
        _largeText.value = value
    }
    fun mayAutoStart(): Boolean = _setupComplete.value && _autoStart.value &&
        !experience.getBoolean(UserExperiencePrefs.USER_STOPPED, false)
    fun completeSetup(openAutomatically: Boolean, bootAutomatically: Boolean, background: Boolean) {
        setAutoStart(openAutomatically)
        setBootAutoStart(bootAutomatically)
        setRunInBackground(background)
        experience.edit().putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true).apply()
        _setupComplete.value = true
        startServer()
    }
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var service: AirPlayService? = null
    val dacpPlayer: Player? get() = service?.dacpPlayer

    // Remote (DACP) transport stays; the local volume/scan helpers had no caller after the
    // unreachable playback-control UI was removed.
    fun audioMuteToggle() { service?.dacpController?.muteToggle() }

    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val _pinCode = MutableStateFlow<String?>(null)
    val pinCode: StateFlow<String?> = _pinCode.asStateFlow()

    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect: StateFlow<Float> = _videoAspect.asStateFlow()

    private val _videoResolution = MutableStateFlow("")
    val videoResolution: StateFlow<String> = _videoResolution.asStateFlow()

    private val _serverName = MutableStateFlow(pendingSettings.string(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME)!!)
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    // settings
    private val _serverPort = MutableStateFlow(pendingSettings.int(Prefs.SERVER_PORT, Prefs.DEF_SERVER_PORT))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()
    private val _remoteVideoMaxBitrate = MutableStateFlow(pendingSettings.int(Prefs.REMOTE_VIDEO_MAX_BITRATE, Prefs.DEF_REMOTE_VIDEO_MAX_BITRATE))
    val remoteVideoMaxBitrate = _remoteVideoMaxBitrate.asStateFlow()

    private val _autoStart = MutableStateFlow(prefs.getBoolean(Prefs.AUTO_START, Prefs.DEF_AUTO_START))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _bootAutoStart = MutableStateFlow(prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START))
    val bootAutoStart: StateFlow<Boolean> = _bootAutoStart.asStateFlow()

    private val _runInBackground = MutableStateFlow(prefs.getBoolean(Prefs.RUN_IN_BACKGROUND, Prefs.DEF_RUN_IN_BACKGROUND))
    val runInBackground: StateFlow<Boolean> = _runInBackground.asStateFlow()

    private val _h265Enabled = MutableStateFlow(pendingSettings.boolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED))
    val h265Enabled: StateFlow<Boolean> = _h265Enabled.asStateFlow()

    private val _enforceSdr = MutableStateFlow(pendingSettings.boolean(Prefs.ENFORCE_SDR, Prefs.DEF_ENFORCE_SDR))
    val enforceSdr: StateFlow<Boolean> = _enforceSdr.asStateFlow()

    private val _keyAllowFrameDrop = MutableStateFlow(pendingSettings.boolean(Prefs.KEY_ALLOW_FRAME_DROP, Prefs.DEF_KEY_ALLOW_FRAME_DROP))
    val keyAllowFrameDrop: StateFlow<Boolean> = _keyAllowFrameDrop.asStateFlow()

    private val _realtimeDecoderPriority = MutableStateFlow(pendingSettings.boolean(Prefs.KEY_PRIORITY, Prefs.DEF_KEY_PRIORITY))
    val realtimeDecoderPriority: StateFlow<Boolean> = _realtimeDecoderPriority.asStateFlow()

    private val _lowLatency = MutableStateFlow(pendingSettings.boolean(Prefs.LOW_LATENCY, Prefs.DEF_LOW_LATENCY))
    val lowLatency: StateFlow<Boolean> = _lowLatency.asStateFlow()

    private val _operatingRate = MutableStateFlow(pendingSettings.string(Prefs.OPERATING_RATE, Prefs.DEF_OPERATING_RATE)!!)
    val operatingRate: StateFlow<String> = _operatingRate.asStateFlow()

    private val _scheduledOutputBufferRelease = MutableStateFlow(pendingSettings.boolean(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, Prefs.DEF_SCHEDULED_OUTPUT_BUFFER_RELEASE))
    val scheduledOutputBufferRelease: StateFlow<Boolean> = _scheduledOutputBufferRelease.asStateFlow()

    private val _benchmarkLog = MutableStateFlow(pendingSettings.boolean(Prefs.BENCHMARK_LOG, Prefs.DEF_BENCHMARK_LOG))
    val benchmarkLog: StateFlow<Boolean> = _benchmarkLog.asStateFlow()

    private val _alacEnabled = MutableStateFlow(pendingSettings.boolean(Prefs.ALAC_ENABLED, Prefs.DEF_ALAC_ENABLED))
    val alacEnabled: StateFlow<Boolean> = _alacEnabled.asStateFlow()

    private val _forceSwAlac = MutableStateFlow(pendingSettings.boolean(Prefs.FORCE_SW_ALAC, Prefs.DEF_FORCE_SW_ALAC))
    val forceSwAlac: StateFlow<Boolean> = _forceSwAlac.asStateFlow()

    private val _aacEnabled = MutableStateFlow(pendingSettings.boolean(Prefs.AAC_ENABLED, Prefs.DEF_AAC_ENABLED))
    val aacEnabled: StateFlow<Boolean> = _aacEnabled.asStateFlow()

    private val _resolution = MutableStateFlow(pendingSettings.string(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION)!!)
    val resolution: StateFlow<String> = _resolution.asStateFlow()

    private val _streamProfile = MutableStateFlow(pendingSettings.string(Prefs.STREAM_PROFILE, Prefs.DEF_STREAM_PROFILE))
    val streamProfile = _streamProfile.asStateFlow()
    fun setStreamProfile(key: String) {
        if (StreamProfile.entries.none { it.key == key }) return
        if (queueSetting(Prefs.STREAM_PROFILE, key)) _streamProfile.value = key
    }

    private val _maxFps = MutableStateFlow(pendingSettings.int(Prefs.MAX_FPS, Prefs.DEF_MAX_FPS))
    val maxFps: StateFlow<Int> = _maxFps.asStateFlow()

    private val _overscanned = MutableStateFlow(pendingSettings.boolean(Prefs.OVERSCANNED, Prefs.DEF_OVERSCANNED))
    val overscanned: StateFlow<Boolean> = _overscanned.asStateFlow()

    private val _requirePin = MutableStateFlow(pendingSettings.boolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN))
    val requirePin: StateFlow<Boolean> = _requirePin.asStateFlow()

    private val _allowNewConn = MutableStateFlow(pendingSettings.boolean(Prefs.ALLOW_NEW_CONN, Prefs.DEF_ALLOW_NEW_CONN))
    val allowNewConn: StateFlow<Boolean> = _allowNewConn.asStateFlow()

    private val _audioLatencyMs = MutableStateFlow(pendingSettings.int(Prefs.AUDIO_LATENCY_MS, Prefs.DEF_AUDIO_LATENCY_MS))
    val audioLatencyMs: StateFlow<Int> = _audioLatencyMs.asStateFlow()

    private val _idlePreview = MutableStateFlow(prefs.getBoolean(Prefs.IDLE_PREVIEW, Prefs.DEF_IDLE_PREVIEW))
    val idlePreview: StateFlow<Boolean> = _idlePreview.asStateFlow()

    private val _autoFullscreen = MutableStateFlow(prefs.getBoolean(Prefs.AUTO_FULLSCREEN, Prefs.DEF_AUTO_FULLSCREEN))
    val autoFullscreen: StateFlow<Boolean> = _autoFullscreen.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(Prefs.KEEP_SCREEN_ON, Prefs.DEF_KEEP_SCREEN_ON))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _advertiseVideo = MutableStateFlow(pendingSettings.boolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO))
    val advertiseVideo: StateFlow<Boolean> = _advertiseVideo.asStateFlow()

    private val _advertiseAudio = MutableStateFlow(pendingSettings.boolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO))
    val advertiseAudio: StateFlow<Boolean> = _advertiseAudio.asStateFlow()

    private val _launchOnConnect = MutableStateFlow(prefs.getBoolean(Prefs.LAUNCH_ON_CONNECT, Prefs.DEF_LAUNCH_ON_CONNECT))
    val launchOnConnect: StateFlow<Boolean> = _launchOnConnect.asStateFlow()

    // debug
    private val _debugEnabled = MutableStateFlow(prefs.getBoolean(Prefs.DEBUG_ENABLED, Prefs.DEF_DEBUG_ENABLED))
    val debugEnabled: StateFlow<Boolean> = _debugEnabled.asStateFlow()

    private val _developerOptions = MutableStateFlow(prefs.getBoolean(Prefs.DEVELOPER_OPTIONS, Prefs.DEF_DEVELOPER_OPTIONS))
    val developerOptions: StateFlow<Boolean> = _developerOptions.asStateFlow()

    private val _audioAutoBuffer = MutableStateFlow(pendingSettings.boolean(Prefs.AUDIO_AUTO_BUFFER, Prefs.DEF_AUDIO_AUTO_BUFFER))
    val audioAutoBuffer: StateFlow<Boolean> = _audioAutoBuffer.asStateFlow()

    private val _audioCushionMs = MutableStateFlow(pendingSettings.int(Prefs.AUDIO_CUSHION_MS, Prefs.DEF_AUDIO_CUSHION_MS))
    val audioCushionMs: StateFlow<Int> = _audioCushionMs.asStateFlow()

    private val _audioAdaptiveStep = MutableStateFlow(pendingSettings.int(Prefs.AUDIO_ADAPTIVE_STEP, Prefs.DEF_AUDIO_ADAPTIVE_STEP))
    val audioAdaptiveStep: StateFlow<Int> = _audioAdaptiveStep.asStateFlow()

    private val _oboeBufferFrames = MutableStateFlow(pendingSettings.int(Prefs.OBOE_BUFFER_FRAMES, Prefs.DEF_OBOE_BUFFER_FRAMES))
    val oboeBufferFrames: StateFlow<Int> = _oboeBufferFrames.asStateFlow()

    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

    // audio mode
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    private val _videoPlaybackActive = MutableStateFlow(false)
    val videoPlaybackActive: StateFlow<Boolean> = _videoPlaybackActive.asStateFlow()

    // video channel polling but /play not received yet
    private val _videoSessionPending = MutableStateFlow(false)
    val videoSessionPending: StateFlow<Boolean> = _videoSessionPending.asStateFlow()

    // airplay video transport overlay state

    // bumping the tick shows the overlay and restarts its auto-hide timer
    private val _videoOverlayTick = MutableStateFlow(0L)
    val videoOverlayTick: StateFlow<Long> = _videoOverlayTick.asStateFlow()

    private val _videoPositionMs = MutableStateFlow(0L)
    val videoPositionMs: StateFlow<Long> = _videoPositionMs.asStateFlow()

    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    private val _videoPlaybackAspect = MutableStateFlow(16f / 9f)
    val videoPlaybackAspect: StateFlow<Float> = _videoPlaybackAspect.asStateFlow()

    private val _videoPlaybackSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoPlaybackSize: StateFlow<Pair<Int, Int>?> = _videoPlaybackSize.asStateFlow()

    private val _videoDetails = MutableStateFlow(io.github.jqssun.airplay.service.VideoPlaybackInfo())
    val videoDetails = _videoDetails.asStateFlow()
    fun seekVideoPosition(positionMs: Long) { service?.seekVideoTo(positionMs) }
    fun selectVideoTrack(mediaId: Long, type: Int, key: String) { service?.airPlayVideoPlayer?.selectTrack(mediaId, type, key) }

    private val _videoBuffering = MutableStateFlow(false)
    val videoBuffering: StateFlow<Boolean> = _videoBuffering.asStateFlow()

    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()

    private val _videoLocation = MutableStateFlow<String?>(null)
    val videoLocation: StateFlow<String?> = _videoLocation.asStateFlow()

    // optimistic for instant icon feedback, re-synced from the delayed snapshot
    private val _videoPlaying = MutableStateFlow(true)
    val videoPlaying: StateFlow<Boolean> = _videoPlaying.asStateFlow()
    private var _videoActionAt = 0L

    // null = idle, -1 = unknown, else 0..100
    private val _videoDownloadProgress = MutableStateFlow<Int?>(null)
    val videoDownloadProgress: StateFlow<Int?> = _videoDownloadProgress.asStateFlow()

    // mirror the player's playback parameters; sender /rate commands reset speed to 1
    private val _videoSpeed = MutableStateFlow(1f)
    val videoSpeed: StateFlow<Float> = _videoSpeed.asStateFlow()
    private val _videoSkipSilence = MutableStateFlow(false)
    val videoSkipSilence: StateFlow<Boolean> = _videoSkipSilence.asStateFlow()
    private var _videoParamsActionAt = 0L

    // non-null while a scrub is in flight; committed once after a debounce
    private val _videoScrubPositionMs = MutableStateFlow<Long?>(null)
    val videoScrubPositionMs: StateFlow<Long?> = _videoScrubPositionMs.asStateFlow()
    private var _scrubJob: Job? = null
    private var _lastVideoPlaySeq = 0L

    private val _mirroringActive = MutableStateFlow(false)
    val mirroringActive: StateFlow<Boolean> = _mirroringActive.asStateFlow()

    private val _trackInfo = MutableStateFlow(TrackInfo())
    val trackInfo: StateFlow<TrackInfo> = _trackInfo.asStateFlow()


    // The service and UI share one bounded asynchronous sink. No per-line disk writes here.
    val logs = diagnostics.lines
    val logStorageError = diagnostics.storageError
    private val _logActionError = MutableStateFlow(false)
    val logActionError = _logActionError.asStateFlow()
    fun addLog(msg: String) = diagnostics.offer(msg)
    private val _logActionBusy = MutableStateFlow(false)
    val logActionBusy = _logActionBusy.asStateFlow()
    fun clearLogs() {
        if (_logActionBusy.value) return
        _logActionBusy.value = true
        _logActionError.value = false
        viewModelScope.launch {
            try { diagnostics.clear() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _logActionError.value = true }
            finally { _logActionBusy.value = false }
        }
    }
    fun dismissLogError() { _logActionError.value = false }
    fun exportLogs() {
        if (_logActionBusy.value) return
        _logActionBusy.value = true
        _logActionError.value = false
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val file = diagnostics.export()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val title = ctx.getString(io.github.jqssun.airplay.R.string.export_logs_chooser_title)
                ctx.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _logActionError.value = true }
            finally { _logActionBusy.value = false }
        }
    }

    private fun notifyPendingSettings() {
        controller.settingsChanged()
        _receiverState.value = _receiverState.value.copy(pendingSettings = pendingSettings.hasPending())
    }
    private fun queueSetting(key: String, value: Any): Boolean {
        if (service?.receiverState?.value?.starting == true || !pendingSettings.put(key, value)) {
            feedback(UiFeedbackKind.SETTINGS_BLOCKED)
            return false
        }
        notifyPendingSettings()
        return true
    }
    fun applyPendingSettings(endSessionConfirmed: Boolean = false): Boolean {
        if (!_setupComplete.value) { feedback(UiFeedbackKind.SETTINGS_BLOCKED); return false }
        val accepted = controller.applySettings(_serverName.value, endSessionConfirmed)
        if (accepted) experience.edit().putBoolean(UserExperiencePrefs.USER_STOPPED, false).apply()
        feedback(if (accepted) UiFeedbackKind.SETTINGS_RESTARTING else UiFeedbackKind.SETTINGS_BLOCKED)
        return accepted
    }
    fun retryDiscovery() { if (controller.retryDiscovery()) feedback(UiFeedbackKind.DISCOVERY_REQUESTED) }
    fun refreshDiscoveryOnResume() { service?.refreshDiscoveryOnResume() }
    fun cancelPairing() = controller.cancelPairing()
    fun discardPendingSettings() {
        if (service?.receiverState?.value?.starting == true || !pendingSettings.discard()) {
            feedback(UiFeedbackKind.SETTINGS_BLOCKED)
            return
        }
        _streamProfile.value = pendingSettings.string(Prefs.STREAM_PROFILE, Prefs.DEF_STREAM_PROFILE)
        _serverName.value = pendingSettings.string(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME)!!
        _serverPort.value = pendingSettings.int(Prefs.SERVER_PORT, Prefs.DEF_SERVER_PORT)
        _remoteVideoMaxBitrate.value = pendingSettings.int(Prefs.REMOTE_VIDEO_MAX_BITRATE, Prefs.DEF_REMOTE_VIDEO_MAX_BITRATE)
        _h265Enabled.value = pendingSettings.boolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED)
        _enforceSdr.value = pendingSettings.boolean(Prefs.ENFORCE_SDR, Prefs.DEF_ENFORCE_SDR)
        _keyAllowFrameDrop.value = pendingSettings.boolean(Prefs.KEY_ALLOW_FRAME_DROP, Prefs.DEF_KEY_ALLOW_FRAME_DROP)
        _realtimeDecoderPriority.value = pendingSettings.boolean(Prefs.KEY_PRIORITY, Prefs.DEF_KEY_PRIORITY)
        _lowLatency.value = pendingSettings.boolean(Prefs.LOW_LATENCY, Prefs.DEF_LOW_LATENCY)
        _operatingRate.value = pendingSettings.string(Prefs.OPERATING_RATE, Prefs.DEF_OPERATING_RATE)!!
        _scheduledOutputBufferRelease.value = pendingSettings.boolean(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, Prefs.DEF_SCHEDULED_OUTPUT_BUFFER_RELEASE)
        _benchmarkLog.value = pendingSettings.boolean(Prefs.BENCHMARK_LOG, Prefs.DEF_BENCHMARK_LOG)
        _alacEnabled.value = pendingSettings.boolean(Prefs.ALAC_ENABLED, Prefs.DEF_ALAC_ENABLED)
        _forceSwAlac.value = pendingSettings.boolean(Prefs.FORCE_SW_ALAC, Prefs.DEF_FORCE_SW_ALAC)
        _aacEnabled.value = pendingSettings.boolean(Prefs.AAC_ENABLED, Prefs.DEF_AAC_ENABLED)
        _resolution.value = pendingSettings.string(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION)!!
        _maxFps.value = pendingSettings.int(Prefs.MAX_FPS, Prefs.DEF_MAX_FPS)
        _overscanned.value = pendingSettings.boolean(Prefs.OVERSCANNED, Prefs.DEF_OVERSCANNED)
        _requirePin.value = pendingSettings.boolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN)
        _allowNewConn.value = pendingSettings.boolean(Prefs.ALLOW_NEW_CONN, Prefs.DEF_ALLOW_NEW_CONN)
        _audioLatencyMs.value = pendingSettings.int(Prefs.AUDIO_LATENCY_MS, Prefs.DEF_AUDIO_LATENCY_MS)
        _advertiseVideo.value = pendingSettings.boolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)
        _advertiseAudio.value = pendingSettings.boolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO)
        _audioAutoBuffer.value = pendingSettings.boolean(Prefs.AUDIO_AUTO_BUFFER, Prefs.DEF_AUDIO_AUTO_BUFFER)
        _audioCushionMs.value = pendingSettings.int(Prefs.AUDIO_CUSHION_MS, Prefs.DEF_AUDIO_CUSHION_MS)
        _audioAdaptiveStep.value = pendingSettings.int(Prefs.AUDIO_ADAPTIVE_STEP, Prefs.DEF_AUDIO_ADAPTIVE_STEP)
        _oboeBufferFrames.value = pendingSettings.int(Prefs.OBOE_BUFFER_FRAMES, Prefs.DEF_OBOE_BUFFER_FRAMES)
        notifyPendingSettings()
        feedback(UiFeedbackKind.SETTINGS_DISCARDED)
    }

    fun setServerPort(port: Int) { if (!ReceiverInput.validPort(port)) return; if (queueSetting(Prefs.SERVER_PORT, port)) _serverPort.value = port }
    fun setRemoteVideoMaxBitrate(value: Int) {
        if (value !in setOf(0, 4_000_000, 8_000_000, 12_000_000, 20_000_000, 40_000_000)) return
        if (queueSetting(Prefs.REMOTE_VIDEO_MAX_BITRATE, value)) _remoteVideoMaxBitrate.value = value
    }
    fun setServerName(name: String) {
        val compact = name.trim().replace(Regex("\\s+"), "-")
        if (!ReceiverInput.validName(compact)) return
        if (queueSetting(Prefs.SERVER_NAME, compact)) _serverName.value = compact
    }
    fun setAutoStart(v: Boolean) { _autoStart.value = v; prefs.edit().putBoolean(Prefs.AUTO_START, v).apply() }
    fun setBootAutoStart(v: Boolean) { _bootAutoStart.value = v; prefs.edit().putBoolean(Prefs.BOOT_AUTO_START, v).apply() }
    fun setRunInBackground(v: Boolean) { _runInBackground.value = v; prefs.edit().putBoolean(Prefs.RUN_IN_BACKGROUND, v).apply() }
    fun setH265Enabled(v: Boolean) { if (queueSetting(Prefs.H265_ENABLED, v)) _h265Enabled.value = v }
    fun setEnforceSdr(v: Boolean) { if (queueSetting(Prefs.ENFORCE_SDR, v)) _enforceSdr.value = v }
    fun setKeyAllowFrameDrop(v: Boolean) {
        if (queueSetting(Prefs.KEY_ALLOW_FRAME_DROP, v)) _keyAllowFrameDrop.value = v
    }
    fun setRealtimeDecoderPriority(v: Boolean) {
        if (queueSetting(Prefs.KEY_PRIORITY, v)) _realtimeDecoderPriority.value = v
    }
    fun setLowLatency(v: Boolean) {
        if (queueSetting(Prefs.LOW_LATENCY, v)) _lowLatency.value = v
    }
    fun setOperatingRate(v: String) { if (queueSetting(Prefs.OPERATING_RATE, v)) _operatingRate.value = v }
    fun setScheduledOutputBufferRelease(v: Boolean) {
        if (queueSetting(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, v)) _scheduledOutputBufferRelease.value = v
    }
    fun setBenchmarkLog(v: Boolean) {
        if (queueSetting(Prefs.BENCHMARK_LOG, v)) _benchmarkLog.value = v
    }
    fun setForceSwAlac(v: Boolean) { if (queueSetting(Prefs.FORCE_SW_ALAC, v)) _forceSwAlac.value = v }
    fun setAlacEnabled(v: Boolean) { if (queueSetting(Prefs.ALAC_ENABLED, v)) _alacEnabled.value = v }
    fun setAacEnabled(v: Boolean) { if (queueSetting(Prefs.AAC_ENABLED, v)) _aacEnabled.value = v }
    fun setResolution(v: String) { if (!ReceiverInput.validResolution(v)) return; if (queueSetting(Prefs.RESOLUTION, v)) _resolution.value = v }
    fun setMaxFps(v: Int) {
        if (!ReceiverInput.validFps(v)) return
        if (service?.receiverState?.value?.starting == true || !pendingSettings.putAll(
                mapOf(Prefs.MAX_FPS to v, Prefs.STREAM_PROFILE to StreamProfile.CUSTOM.key))) {
            feedback(UiFeedbackKind.SETTINGS_BLOCKED)
            return
        }
        _maxFps.value = v
        _streamProfile.value = StreamProfile.CUSTOM.key
        notifyPendingSettings()
    }
    fun setOverscanned(v: Boolean) { if (queueSetting(Prefs.OVERSCANNED, v)) _overscanned.value = v }
    fun setRequirePin(v: Boolean) { if (queueSetting(Prefs.REQUIRE_PIN, v)) _requirePin.value = v }
    fun setAllowNewConn(v: Boolean) { if (queueSetting(Prefs.ALLOW_NEW_CONN, v)) _allowNewConn.value = v }
    fun setAudioLatencyMs(v: Int) { if (queueSetting(Prefs.AUDIO_LATENCY_MS, v)) _audioLatencyMs.value = v }
    fun setIdlePreview(v: Boolean) { _idlePreview.value = v; prefs.edit().putBoolean(Prefs.IDLE_PREVIEW, v).apply() }
    fun setAutoFullscreen(v: Boolean) { _autoFullscreen.value = v; prefs.edit().putBoolean(Prefs.AUTO_FULLSCREEN, v).apply() }
    fun setKeepScreenOn(v: Boolean) { _keepScreenOn.value = v; prefs.edit().putBoolean(Prefs.KEEP_SCREEN_ON, v).apply() }
    fun setPlaybackPreference(v: Boolean): Boolean {
        val receiver = service ?: return false
        if (!receiver.setRemoteVideoPlaybackEnabled(v)) return false
        // Best-effort draft cleanup on this thread: remove() never blocks, so a settings lock
        // held by a durable start commit simply leaves the value in the draft, which the next
        // start re-applies idempotently.
        pendingSettings.remove(Prefs.ADVERTISE_VIDEO)
        _advertiseVideo.value = v
        notifyPendingSettings()
        return true
    }
    fun setAdvertiseVideo(v: Boolean) { if (queueSetting(Prefs.ADVERTISE_VIDEO, v)) _advertiseVideo.value = v }
    fun setAdvertiseAudio(v: Boolean) { if (queueSetting(Prefs.ADVERTISE_AUDIO, v)) _advertiseAudio.value = v }
    fun setLaunchOnConnect(v: Boolean) { _launchOnConnect.value = v; prefs.edit().putBoolean(Prefs.LAUNCH_ON_CONNECT, v).apply() }
    fun setDebugEnabled(v: Boolean) { _debugEnabled.value = v; prefs.edit().putBoolean(Prefs.DEBUG_ENABLED, v).apply() }
    fun setDeveloperOptions(v: Boolean) {
        _developerOptions.value = v
        prefs.edit().putBoolean(Prefs.DEVELOPER_OPTIONS, v).apply()
    }
    fun setAudioAutoBuffer(v: Boolean) {
        if (queueSetting(Prefs.AUDIO_AUTO_BUFFER, v)) _audioAutoBuffer.value = v
    }
    fun setAudioCushionMs(v: Int) {
        val value = v.coerceIn(1, 1000)
        if (queueSetting(Prefs.AUDIO_CUSHION_MS, value)) _audioCushionMs.value = value
    }
    fun setAudioAdaptiveStep(v: Int) {
        val value = v.coerceIn(0, Prefs.ADAPTIVE_PERCENTILES.size - 1)
        if (queueSetting(Prefs.AUDIO_ADAPTIVE_STEP, value)) _audioAdaptiveStep.value = value
    }
    fun setOboeBufferFrames(v: Int) {
        val value = v.coerceIn(0, 8192)
        if (queueSetting(Prefs.OBOE_BUFFER_FRAMES, value)) _oboeBufferFrames.value = value
    }

    // Event-driven state; the Activity no longer polls every 200 ms.
    fun bindService(svc: AirPlayService) {
        serviceObservation?.cancel()
        service = svc
        controller.attach(svc)
        svc.refreshDiscoveryOnResume()
        _serviceBound.value = true
        pendingMirrorSurface?.takeIf { it.isValid }?.let(svc::setVideoSurface)
        pendingPlaybackSurface?.takeIf { it.isValid }?.let(svc::setVideoPlaybackSurface)
        updateFromService()
        serviceObservation = viewModelScope.launch {
            merge(
                svc.receiverState.map { Unit }, svc.serverState.map { Unit },
                svc.connectionCount.map { Unit }, svc.videoAspect.map { Unit },
                svc.videoResolution.map { Unit }, svc.audioOnly.map { Unit },
                svc.videoPlaybackActive.map { Unit }, svc.videoPlaySeq.map { Unit },
                svc.videoPlaybackInfo.map { Unit }, svc.videoPlaybackAspect.map { Unit },
                svc.videoPlaybackSize.map { Unit }, svc.videoTitle.map { Unit },
                svc.videoLocation.map { Unit }, svc.mirroringActive.map { Unit },
                svc.trackInfo.map { Unit }, svc.videoDownloader.progress.map { Unit },
            ).collect { updateFromService() }
        }
        if (pendingUserStart) { pendingUserStart = false; startServer() }
    }

    fun unbindService() {
        serviceObservation?.cancel()
        serviceObservation = null
        service?.let { svc ->
            pendingMirrorSurface?.let(svc::clearVideoSurface)
            pendingPlaybackSurface?.let(svc::clearVideoPlaybackSurface)
        }
        controller.detach()
        service = null
        _serviceBound.value = false
        _pinCode.value = null
        _serverState.value = ServerState.STOPPED
        _connectionCount.value = 0
        _videoPlaybackActive.value = false
        _videoSessionPending.value = false
        _videoDetails.value = io.github.jqssun.airplay.service.VideoPlaybackInfo()
        _videoBuffering.value = false
        _videoPositionMs.value = 0L
        _videoDurationMs.value = 0L
        _videoPlaybackSize.value = null
        _videoTitle.value = ""
        _videoLocation.value = null
        _videoPlaying.value = false
        _videoSpeed.value = 1f
        _videoSkipSilence.value = false
        _videoDownloadProgress.value = null
        _videoActionAt = 0L
        _videoParamsActionAt = 0L
        _lastVideoPlaySeq = -1L
        _debugInfo.value = DebugInfo()
        _mirroringActive.value = false
        _audioOnly.value = false
        _trackInfo.value = TrackInfo()
        _scrubJob?.cancel()
        _videoScrubPositionMs.value = null
        _receiverState.value = ReceiverUiState(pendingSettings = pendingSettings.hasPending())
    }

    fun startServer() {
        if (!_setupComplete.value) return
        experience.edit().putBoolean(UserExperiencePrefs.USER_STOPPED, false).apply()
        if (service == null) pendingUserStart = true else controller.start(_serverName.value)
    }

    fun stopServer(userInitiated: Boolean = true) {
        pendingUserStart = false
        if (userInitiated) experience.edit().putBoolean(UserExperiencePrefs.USER_STOPPED, true).apply()
        controller.stop()
    }

    fun onSurfaceAvailable(surface: Surface) {
        pendingMirrorSurface = surface
        service?.setVideoSurface(surface)
    }
    fun onSurfaceDestroyed(surface: Surface) {
        if (pendingMirrorSurface === surface) pendingMirrorSurface = null
        service?.clearVideoSurface(surface)
    }
    fun onVideoPlaybackSurfaceAvailable(surface: Surface) {
        pendingPlaybackSurface = surface
        service?.setVideoPlaybackSurface(surface)
    }
    fun onVideoPlaybackSurfaceDestroyed(surface: Surface) {
        if (pendingPlaybackSurface === surface) pendingPlaybackSurface = null
        service?.clearVideoPlaybackSurface(surface)
    }

    fun toggleVideoPlayPause(showOverlay: Boolean = true) = setVideoPlaying(!_videoPlaying.value, showOverlay)

    fun setVideoPlaying(playing: Boolean, showOverlay: Boolean = true) {
        // pending session: there is no local media yet, nothing to toggle
        if (!_videoPlaybackActive.value) return
        val svc = service
        // Without a bound receiver nothing can reconcile an optimistic flip, so the UI must not
        // show a play/pause state that never reached the sender.
        if (svc == null || !_serviceBound.value) return
        _videoPlaying.value = playing
        _videoActionAt = SystemClock.elapsedRealtime()
        svc.setVideoPlaying(playing)
        if (showOverlay) showVideoOverlay()
    }

    // hold the target until the reported position catches up (hls rebuffer)
    private suspend fun _holdScrubUntilSettled(target: Long) {
        val deadline = SystemClock.elapsedRealtime() + SCRUB_SETTLE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(SCRUB_SETTLE_POLL_MS)
            if (abs(_videoPositionMs.value - target) <= SCRUB_SETTLE_EPSILON_MS) break
        }
        _videoScrubPositionMs.value = null
    }

    // drag-seek: live seeks under scrubbing mode, settle-hold on release
    fun startVideoScrub() {
        _scrubJob?.cancel()
        service?.setVideoScrubbing(true)
    }

    fun scrubVideoTo(positionMs: Long) {
        val duration = _videoDurationMs.value
        if (duration <= 0) return
        val target = positionMs.coerceIn(0L, duration)
        _videoScrubPositionMs.value = target
        service?.seekVideoTo(target)
    }

    fun endVideoScrub() {
        service?.setVideoScrubbing(false)
        _scrubJob?.cancel()
        _scrubJob = viewModelScope.launch {
            _holdScrubUntilSettled(_videoScrubPositionMs.value ?: return@launch)
        }
    }

    fun showVideoOverlay() {
        _videoOverlayTick.value++
    }

    fun stopVideoPlayback() {
        service?.stopVideoPlayback()
    }

    fun endCurrentStream() {
        service?.endCurrentStream()
    }

    fun seekVideoBy(deltaMs: Long) {
        service?.seekVideoBy(deltaMs)
    }

    fun dismissPin() { cancelPairing() }

    fun showPin(pin: String?) {
        _pinCode.value = pin
    }

    fun updateFromService() {
        service?.let {
            _receiverState.value = it.receiverState.value
            _pinCode.value = it.receiverState.value.pin
            _serverState.value = it.serverState.value
            _connectionCount.value = it.connectionCount.value
            _videoAspect.value = it.videoAspect.value
            _videoResolution.value = it.videoResolution.value
            _audioOnly.value = it.audioOnly.value
            val videoActive = it.videoPlaybackActive.value
            val playSeq = it.videoPlaySeq.value
            if (playSeq != _lastVideoPlaySeq) {
                // fresh /play (incl. back-to-back): drop stale overlay and scrub state
                _lastVideoPlaySeq = playSeq
                _videoPlaying.value = true
                _videoActionAt = 0
                _videoOverlayTick.value = 0
                _videoSpeed.value = 1f
                _videoSkipSilence.value = false
                _scrubJob?.cancel()
                _videoScrubPositionMs.value = null
            }
            _videoPlaybackActive.value = videoActive
            _videoSessionPending.value = it.videoSessionPending()
            _videoDownloadProgress.value = it.videoDownloader.progress.value
            if (videoActive) {
                val info = it.videoPlaybackInfo.value
                _videoDetails.value = info
                _videoPositionMs.value = info.positionMs
                _videoDurationMs.value = info.durationMs
                if (SystemClock.elapsedRealtime() - _videoParamsActionAt > VIDEO_ACTION_SYNC_HOLD_MS) {
                    _videoSpeed.value = info.speed
                    _videoSkipSilence.value = info.skipSilence
                }
                _videoBuffering.value = info.buffering
                _videoPlaybackAspect.value = it.videoPlaybackAspect.value
                _videoPlaybackSize.value = it.videoPlaybackSize.value
                // stream metadata first, dmap second; senders rarely provide either for video
                _videoTitle.value = it.videoTitle.value.ifEmpty { it.trackInfo.value.title }
                _videoLocation.value = it.videoLocation.value
                // stale snapshots must not overwrite a just-made optimistic action
                if (SystemClock.elapsedRealtime() - _videoActionAt > VIDEO_ACTION_SYNC_HOLD_MS) {
                    _videoPlaying.value = info.playing
                }
            } else {
                _videoPositionMs.value = 0
                _videoDurationMs.value = 0
                _videoBuffering.value = false
            }
            _mirroringActive.value = it.mirroringActive.value
            _trackInfo.value = it.trackInfo.value
        }
    }

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_showStreamMetrics, _diagnosticsVisible) { enabled, visible -> enabled && visible }
                .collectLatest { enabled ->
                while (enabled) {
                    service?.let { _debugInfo.value = it.collectDebugInfo() }
                    delay(1000)
                }
            }
        }
    }

    private companion object {
        // post-commit: show the target until within epsilon, give up after the timeout
        const val SCRUB_SETTLE_POLL_MS = 100L
        const val SCRUB_SETTLE_EPSILON_MS = 2_000L
        const val SCRUB_SETTLE_TIMEOUT_MS = 3_000L
        // raise if the play/pause icon flickers back right after a local toggle
        const val VIDEO_ACTION_SYNC_HOLD_MS = 700L
    }
}
