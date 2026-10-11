package io.github.jqssun.airplay.ui

import android.content.pm.PackageManager
import android.view.KeyEvent as AndroidKey
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: MainViewModel, isInPip: Boolean = false,
    onSurfaceAvailable: (Surface) -> Unit, onSurfaceDestroyed: (Surface) -> Unit,
    onPip: () -> Unit = {}) {
    val state by viewModel.receiverState.collectAsStateWithLifecycle()
    var consumedFailureId by rememberSaveable { mutableLongStateOf(0L) }
    var observedFailureId by rememberSaveable { mutableLongStateOf(0L) }
    var showPlaybackHelp by rememberSaveable { mutableStateOf(false) }
    val savedName by viewModel.serverName.collectAsStateWithLifecycle()
    val remoteVideoEnabled by viewModel.advertiseVideo.collectAsStateWithLifecycle()
    val quickRecording = rememberQuickRecording()
    val homePrimary = remember { FocusRequester() }
    var showRecordings by remember { mutableStateOf(false) }
    val recording by io.github.jqssun.airplay.recording.RecordingService.status.collectAsStateWithLifecycle()
    var confirmMode by remember { mutableStateOf(false) }
    val metricsEnabled by viewModel.showStreamMetrics.collectAsStateWithLifecycle()
    val metrics by viewModel.debugInfo.collectAsStateWithLifecycle()
    var pictureFit by rememberSaveable { mutableStateOf(PictureFit.FIT) }
    var pictureRotation by rememberSaveable { mutableIntStateOf(0) }
    val bound by viewModel.serviceBound.collectAsStateWithLifecycle()
    val setupComplete by viewModel.setupComplete.collectAsStateWithLifecycle()
    val autoFullscreen by viewModel.autoFullscreen.collectAsStateWithLifecycle()
    val videoAspect by viewModel.videoAspect.collectAsStateWithLifecycle()
    val mediaActive by viewModel.videoPlaybackActive.collectAsStateWithLifecycle()
    val mediaAspect by viewModel.videoPlaybackAspect.collectAsStateWithLifecycle()
    val mediaPlaying by viewModel.videoPlaying.collectAsStateWithLifecycle()
    val mediaBuffering by viewModel.videoBuffering.collectAsStateWithLifecycle()
    val mediaDetails by viewModel.videoDetails.collectAsStateWithLifecycle()
    var trackMenu by remember(mediaDetails.mediaId, mediaActive) { mutableStateOf<Int?>(null) }
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(ReceiverPage.HOME) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(!setupComplete) }
    var confirmStop by remember { mutableStateOf(false) }
    var confirmMediaStop by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    var pageModal by remember { mutableStateOf(false) }
    var entryToken by remember { mutableIntStateOf(0) }
    val pageStates = rememberSaveableStateHolder()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val experience = remember(context) { context.getSharedPreferences(UserExperiencePrefs.NAME, android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(state.playbackFailureId) {
        if (state.playbackFailureId != 0L && state.playbackFailureId != observedFailureId) {
            observedFailureId = state.playbackFailureId
            showPlaybackHelp = !experience.getBoolean(UserExperiencePrefs.PLAYBACK_HELP_SEEN, false)
        }
    }
    val pipSupported = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    val feedbackText = feedback?.let { stringResource(when (it.kind) {
        UiFeedbackKind.SETTINGS_RESTARTING -> R.string.r2_settings_restarting
        UiFeedbackKind.SETTINGS_BLOCKED -> R.string.r2_settings_blocked
        UiFeedbackKind.SETTINGS_DISCARDED -> R.string.r2_settings_discarded
        UiFeedbackKind.DISCOVERY_REQUESTED -> R.string.r2_discovery_requested
    }) }
    LaunchedEffect(feedback?.sequence) {
        val message = feedback ?: return@LaunchedEffect
        if (feedbackText != null) snackbars.showSnackbar(feedbackText, duration = SnackbarDuration.Short)
        viewModel.dismissFeedback(message.sequence)
    }
    LaunchedEffect(setupComplete) { if (setupComplete) showSetup = false }
    LaunchedEffect(state.presentationHeld, state.pin, autoFullscreen) {
        if (state.presentationHeld && state.pin == null && autoFullscreen) fullScreen = true
        if (!state.presentationHeld) fullScreen = false
    }
    LaunchedEffect(mediaActive) { if (mediaActive) fullScreen = true }
    LaunchedEffect(state.presentationHeld, mediaActive, recording.active) {
        if (recording.active && !state.presentationHeld && !mediaActive) {
            context.startService(android.content.Intent(context, io.github.jqssun.airplay.recording.RecordingService::class.java)
                .setAction(io.github.jqssun.airplay.recording.RecordingService.STOP))
        }
    }
    LaunchedEffect(state.running, bound) {
        if ((!state.running && !state.starting) || !bound) { fullScreen = false; confirmStop = false; confirmMediaStop = false }
    }
    LaunchedEffect(state.pin) { if (state.pin != null) { confirmStop = false; confirmMediaStop = false; confirmQuit = false; confirmMode = false } }
    val showVideo = (fullScreen && (state.presentationHeld || mediaActive)) || isInPip
    DisposableEffect(showVideo, metricsEnabled, state.pin) {
        viewModel.setDiagnosticsVisible(showVideo && metricsEnabled && state.pin == null)
        onDispose { viewModel.setDiagnosticsVisible(false) }
    }
    val setupDialog = !setupComplete && showSetup
    val pendingFailure = state.playbackFailureId != 0L && state.playbackFailureId != observedFailureId
    val globalModal = showRecordings || state.pin != null || setupDialog || confirmStop || confirmMediaStop || confirmQuit || confirmMode || showPlaybackHelp || pendingFailure || trackMenu != null
    LaunchedEffect(setupComplete, bound, state.starting, state.running, globalModal, page) {
        if (setupComplete && bound && !state.starting && !globalModal && page == ReceiverPage.HOME)
            homePrimary.requestFocusUntilLanded()
    }
    val choosePage: (ReceiverPage) -> Unit = { page = it; entryToken++ }
    val requestStop: () -> Unit = { if (state.sessionBusy) confirmStop = true else viewModel.stopServer() }
    BackHandler(enabled = !showVideo && !isInPip && !globalModal && !pageModal && page != ReceiverPage.HOME) {
        choosePage(page.parent)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Never animate, re-key or put these surfaces inside page navigation.
        if (mediaActive) {
            key("remote-video-surface") {
                FittedReceiverSurface(mediaAspect, pictureFit, pictureRotation, viewModel::onVideoPlaybackSurfaceAvailable,
                    viewModel::onVideoPlaybackSurfaceDestroyed, stableTexture = true)
            }
        } else {
            key("mirror-surface") { FittedReceiverSurface(videoAspect, pictureFit, pictureRotation, onSurfaceAvailable, onSurfaceDestroyed) }
        }
        if (mediaActive && showVideo) DirectVideoSubtitles(mediaDetails.cues)
        if (trackMenu != null && mediaActive && state.pin == null) {
            io.github.jqssun.airplay.ui.theme.PlaybackDialogColors {
            VideoTrackDialog(trackMenu!!, mediaDetails.trackOptions,
                onChoose = { key -> viewModel.selectVideoTrack(mediaDetails.mediaId, trackMenu!!, key); trackMenu = null },
                onDismiss = { trackMenu = null })
            }
        }
        if (!isInPip) {
            if (showVideo) {
                // The player overlay stays on the dark brand palette while a picture is playing, so the
                // controls never glare over video; it is the same palette family as the rest of the app.
                io.github.jqssun.airplay.ui.theme.AirPlayTheme(selected = "classic") {
                ReceiverPlayerOverlay(
                    waiting = state.mediaErrorCode == null && (state.remotePreparing || if (mediaActive) mediaBuffering || !mediaDetails.firstFrame else !state.firstFrameSubmitted),
                    pictureFit = pictureFit, onPictureFit = { pictureFit = it },
                    pictureRotation = pictureRotation, onPictureRotation = { pictureRotation = (pictureRotation + 90) % 360 },
                    remoteVideoEnabled = remoteVideoEnabled, onMode = { confirmMode = true },
                    details = mediaDetails, onSeek = viewModel::seekVideoPosition, errorCode = state.mediaErrorCode,
                    failureId = state.playbackFailureId, hintConsumed = consumedFailureId == state.playbackFailureId,
                    onHintConsumed = { consumedFailureId = state.playbackFailureId },
                    networkMissing = state.network == LocalNetwork.UNAVAILABLE,
                    isMedia = mediaActive, mediaPlaying = mediaPlaying, modalOpen = globalModal,
                    onHome = { fullScreen = false; choosePage(ReceiverPage.HOME) },
                    onSettings = { fullScreen = false; choosePage(ReceiverPage.SETTINGS) },
                    onEnd = { confirmMediaStop = true },
                    onPlayPause = { viewModel.toggleVideoPlayPause(false) },
                    onTracks = { trackMenu = it },
                    recording = recording.active,
                    onRecord = {
                        if (recording.active) context.startService(android.content.Intent(context, io.github.jqssun.airplay.recording.RecordingService::class.java).setAction(io.github.jqssun.airplay.recording.RecordingService.STOP))
                        else quickRecording()
                    },
                    onRecordings = { showRecordings = true },
                    onPip = if (pipSupported) onPip else null)
                }
            } else {
                CompositionLocalProvider(LocalBlockingOverlay provides globalModal,
                    LocalTvInputEnabled provides (!globalModal && !pageModal)) {
                    ReceiverShell(page, choosePage, { confirmQuit = true }, homePrimary) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            pageStates.SaveableStateProvider(page.name) {
                                TvFocusRegion(enabled = !globalModal && !pageModal,
                                    defaultId = if (page == ReceiverPage.HOME) "home.primary" else null,
                                    restoreToken = entryToken, preferDefault = page == ReceiverPage.HOME) {
                                    PageEntrance(page) {
                                        when (page) {
                                            ReceiverPage.HOME -> ReceiverHome(state, savedName, bound,
                                                onStart = { if (setupComplete) viewModel.startServer() else showSetup = true },
                                                onStop = requestStop, onShowMirror = { fullScreen = true },
                                                primaryFocus = homePrimary, onHelp = { choosePage(ReceiverPage.HELP) }, onDismissNotice = viewModel::dismissNotice)
                                            ReceiverPage.RECORDINGS -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                                RecordingPanel(canRecord = false, canPlay = !state.sessionBusy && !mediaActive,
                                                    onModalChange = { pageModal = it })
                                            }
                                            ReceiverPage.HELP -> ReceiverHelp(viewModel)
                                            ReceiverPage.SETTINGS -> ReceiverSettings(viewModel,
                                                onAdvanced = { choosePage(ReceiverPage.ADVANCED) }, onModalChange = { pageModal = it })
                                            ReceiverPage.LOGS -> LogsScreen(viewModel, onModalChange = { pageModal = it })
                                            ReceiverPage.ADVANCED -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(stringResource(R.string.p_advanced_warning),
                                                    style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Warning)
                                                PendingSettingsPanel(viewModel) { pageModal = it }
                                                Box(Modifier.weight(1f)) { SettingsScreen(viewModel) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            NetworkLabel(state.network)
                        }
                    }
                }
                SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                    .widthIn(max = 600.dp))
            }
            if (showVideo && metricsEnabled && !globalModal &&
                (if (mediaActive) mediaDetails.firstFrame else state.firstFrameSubmitted)) {
                StreamMetrics(metrics, mediaActive, mediaDetails,
                    Modifier.align(Alignment.TopEnd).padding(18.dp))
            }
            // One global modal at a time. A PIN always takes priority over setup/stop prompts.
            io.github.jqssun.airplay.ui.theme.PlaybackDialogColors(enabled = showVideo && state.pin == null && !setupDialog) {
            when {
                state.pin != null -> PairingDialog(state.pin!!, viewModel::cancelPairing)
                setupDialog -> ReceiverSetupDialog(viewModel) { showSetup = false }
                showPlaybackHelp -> PlaybackHelpDialog {
                    experience.edit().putBoolean(UserExperiencePrefs.PLAYBACK_HELP_SEEN, true).apply()
                    showPlaybackHelp = false
                }
                showRecordings -> DialogInputScope {
                    ProductAlertDialog(onDismissRequest = { showRecordings = false },
                        title = { Text(stringResource(R.string.rec_options_title)) },
                        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                            RecordingPanel(onStarted = { showRecordings = false }, showLibrary = false)
                        } },
                        confirmButton = { TvAction(stringResource(R.string.rec_close), { showRecordings = false }) },
                        dismissButton = {})
                }
                confirmMode -> PlaybackChoiceDialog(remoteVideoEnabled,
                    onChoose = { if (viewModel.setPlaybackPreference(it)) confirmMode = false },
                    onDismiss = { confirmMode = false })
                confirmQuit -> ConfirmReceiverAction(stringResource(R.string.p_quit_title),
                    stringResource(R.string.p_quit_body), stringResource(R.string.p_quit),
                    onConfirm = { confirmQuit = false; context.mainActivity()?.quitApplication() },
                    onCancel = { confirmQuit = false })
                confirmStop -> ConfirmReceiverAction(stringResource(R.string.p_stop_title),
                    stringResource(R.string.p_stop_body), stringResource(R.string.p_stop_confirm),
                    onConfirm = { confirmStop = false; viewModel.stopServer() }, onCancel = { confirmStop = false })
                confirmMediaStop -> ConfirmReceiverAction(stringResource(R.string.p_media_end_title),
                    stringResource(R.string.p_media_end_body), stringResource(R.string.p_stop_confirm),
                    onConfirm = { confirmMediaStop = false; viewModel.endCurrentStream(); fullScreen = false },
                    onCancel = { confirmMediaStop = false })
            }
            }
        }
    }
}

@Composable
private fun ReceiverShell(page: ReceiverPage, onPage: (ReceiverPage) -> Unit, onQuit: () -> Unit,
    homePrimary: FocusRequester, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxSize().background(AirRelayColors.Background)) {
                Column(Modifier.width(194.dp).fillMaxHeight().background(AirRelayColors.Rail)
            .padding(horizontal = 18.dp, vertical = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReceiverBrand(Modifier.fillMaxWidth())
            Spacer(Modifier.height(38.dp))
            listOf(Triple(ReceiverPage.HOME, R.string.p_home, Icons.Rounded.Cast),
                Triple(ReceiverPage.RECORDINGS, R.string.rec_title, Icons.Rounded.VideoLibrary),
                Triple(ReceiverPage.SETTINGS, R.string.p_settings, Icons.Rounded.Settings),
                Triple(ReceiverPage.LOGS, R.string.tab_logs, Icons.Rounded.Description),
                Triple(ReceiverPage.HELP, R.string.p_help, Icons.Rounded.HelpOutline)).forEach { (target, label, icon) ->
                TvAction(stringResource(label), { onPage(target) }, Modifier.fillMaxWidth(), icon = icon,
                    selected = page.tab == target, compact = true, focusId = "nav.${target.name.lowercase()}")
            }
            Spacer(Modifier.height(24.dp))
            TvAction(stringResource(R.string.p_quit), onQuit, Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PowerSettingsNew, compact = true, focusId = "nav.quit")
        }
        
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp, end = 32.dp,
            top = 32.dp, bottom = 18.dp)) {
            
            key("receiver-pages") { content() }
        }
    }
}

@Composable
private fun PageEntrance(page: ReceiverPage, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val progress = remember(page) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(page, reduced) { if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(160)) }
    // Only the incoming page exists: no crossfade with two simultaneous focus trees.
    Box(Modifier.fillMaxSize().graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 5.dp.toPx()
    }) { content() }
}

@Composable
internal fun FittedReceiverSurface(aspect: Float, pictureFit: PictureFit, rotation: Int,
    onAvailable: (Surface) -> Unit, onDestroyed: (Surface) -> Unit, stableTexture: Boolean = false) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val rotated = rotation % 180 != 0
        val (targetWidth, targetHeight) = with(density) {
            pictureFit.size(maxWidth.toPx(), maxHeight.toPx(), if (rotated) 1f / aspect.coerceAtLeast(.01f) else aspect)
        }
        // Direct playback keeps one TextureView/Surface at every angle, including zero.
        // Rotating must not detach a live MediaCodec from its output surface.
        VideoSurfaceView(onAvailable, onDestroyed, applyAspectRatio = false, useTextureView = stableTexture || rotation % 360 != 0,
            modifier = with(density) {
                Modifier.requiredSize((if (rotated) targetHeight else targetWidth).toDp(),
                    (if (rotated) targetWidth else targetHeight).toDp())
                    .graphicsLayer { rotationZ = rotation.toFloat() }
            })
    }
}

@Composable
private fun ReceiverPlayerOverlay(waiting: Boolean, networkMissing: Boolean, isMedia: Boolean,
    pictureFit: PictureFit, onPictureFit: (PictureFit) -> Unit,
    pictureRotation: Int, onPictureRotation: () -> Unit,
    remoteVideoEnabled: Boolean, onMode: () -> Unit,
    mediaPlaying: Boolean, modalOpen: Boolean, onHome: () -> Unit, onSettings: () -> Unit, onEnd: () -> Unit,
    recording: Boolean, onRecord: () -> Unit, onRecordings: () -> Unit,
    onPlayPause: () -> Unit, onPip: (() -> Unit)?, onTracks: (Int) -> Unit,
    details: io.github.jqssun.airplay.service.VideoPlaybackInfo,
    onSeek: (Long) -> Unit, errorCode: Int?, failureId: Long, hintConsumed: Boolean, onHintConsumed: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    var hidingUp by remember { mutableStateOf(false) }
    fun hideAboveControls() { hidingUp = true; visible = false }
    var seeking by remember(details.mediaId) { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }
    val wakeGuard = remember { RemoteWakeGuard() }
    val rootFocus = remember { FocusRequester() }
    val modeAction = remember { FocusRequester() }
    var modeFocused by remember { mutableStateOf(false) }
    val centerAction = remember { FocusRequester() }
    var lastAction by remember { mutableStateOf<FocusRequester?>(null) }
    var returningFromDialog by remember { mutableStateOf(false) }
    var centerFocused by remember { mutableStateOf(false) }
    val seekFocus = remember { FocusRequester() }
    var modeHint by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(failureId, modalOpen) {
        if (failureId != 0L && !hintConsumed && !modalOpen) {
            visible = true
            withFrameNanos { }
            modeAction.requestFocusUntilLanded(isFocused = { modeFocused })
            onHintConsumed() // Consume once per failure, not per visit to the controls.
            try {
                if (reducedMotion) { modeHint = true; delay(6000) }
                else repeat(5) { modeHint = true; delay(600); modeHint = false; delay(600) }
            } finally { modeHint = false }
        }
    }
    val accessibility = LocalAccessibilityManager.current
    val timeout = accessibility?.calculateRecommendedTimeoutMillis(5000L,
        containsIcons = true, containsText = true, containsControls = true) ?: 5000L
    LaunchedEffect(visible, interaction, waiting, modalOpen, networkMissing, pressed, seeking, timeout, errorCode, modeHint) {
        if (errorCode == null && OverlayPolicy.mayHide(visible, waiting, modalOpen, networkMissing, pressed || seeking || modeHint)) {
            delay(timeout); visible = false
        }
    }
    LaunchedEffect(visible, modalOpen, details.mediaId, errorCode, isMedia) {
        if (modalOpen) { returningFromDialog = true; wakeGuard.reset() }
        else if (visible && (errorCode != null || failureId != 0L && !hintConsumed)) modeAction.requestFocusUntilLanded(isFocused = { modeFocused })
        else if (visible && returningFromDialog) {
            returningFromDialog = false
            runCatching { (lastAction ?: centerAction).requestFocusUntilLanded() }
                .onFailure { centerAction.requestFocusUntilLanded(isFocused = { centerFocused }) }
        }
        else if (visible) centerAction.requestFocusUntilLanded(isFocused = { centerFocused })
        else rootFocus.requestFocusUntilLanded(isFocused = { rootFocused })
    }
    LaunchedEffect(networkMissing) { if (networkMissing) visible = true }
    LaunchedEffect(errorCode) { if (errorCode != null) visible = true }
    BackHandler(enabled = !modalOpen) { if (visible && errorCode == null) visible = false else onHome() }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (modalOpen) return@onPreviewKeyEvent false
        val code = event.nativeKeyEvent.keyCode
        if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
        val controlKey = code == AndroidKey.KEYCODE_DPAD_CENTER || code == AndroidKey.KEYCODE_ENTER ||
            code == AndroidKey.KEYCODE_NUMPAD_ENTER || code == AndroidKey.KEYCODE_DPAD_UP ||
            code == AndroidKey.KEYCODE_DPAD_DOWN || code == AndroidKey.KEYCODE_DPAD_LEFT || code == AndroidKey.KEYCODE_DPAD_RIGHT
        val down = event.type == KeyEventType.KeyDown
        if (code == AndroidKey.KEYCODE_DPAD_UP && hidingUp) {
            if (down && event.nativeKeyEvent.repeatCount == 0) hidingUp = false
            else { if (!down) hidingUp = false; return@onPreviewKeyEvent true }
        }
        val decision = wakeGuard.event(code, down, event.nativeKeyEvent.repeatCount, controlKey, visible)
        if (controlKey && down) interaction++
        if (decision.reveal) visible = true
        if (decision.consume) return@onPreviewKeyEvent true
        false // Volume and media keys remain owned by Android/the existing media session.
    }.focusRequester(rootFocus).onFocusChanged { rootFocused = it.isFocused }
        .focusable(enabled = !visible && !modalOpen)
        .pointerInput(modalOpen) { detectTapGestures { if (!modalOpen) { visible = true; interaction++ } } }) {
        if (waiting) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(44.dp), color = AirRelayColors.Accent)
        }
        if (errorCode != null) Text(when (errorCode) {
            401, 403 -> stringResource(R.string.transition_access_denied)
            5001 -> stringResource(R.string.transition_picture_interrupted)
            5002 -> stringResource(R.string.transition_transport_interrupted)
            5003 -> stringResource(R.string.playback_return_to_screen)
            else -> stringResource(R.string.transition_playback_error, errorCode)
        },
            Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp))
        if (visible) CompositionLocalProvider(LocalControlActivity provides { interaction++ },
            LocalControlPressed provides { pressed = it }, LocalTvInputEnabled provides !modalOpen,
            LocalCapsuleFocused provides { lastAction = it }) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, AirRelayColors.Background.copy(alpha = .78f))))
                .padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMedia && details.seekable && details.seekEndMs > details.seekStartMs) {
                    ReceiverSeekBar(details, onSeek, { seeking = it; interaction++ }, enabled = !modalOpen,
                        focus = seekFocus, onExit = { centerAction.requestFocus() }, onHide = { hideAboveControls() })
                } else if (isMedia && details.live) {
                    Text(stringResource(R.string.transition_live), color = Color.White)
                }
                if (networkMissing) Text(stringResource(R.string.p_network_note), color = AirRelayColors.Warning)
                Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 1100.dp)
                    .background(Brush.linearGradient(listOf(AirRelayColors.Muted.copy(alpha = .16f),
                        AirRelayColors.Panel.copy(alpha = .60f))), RoundedCornerShape(52.dp))
                    .border(1.dp, AirRelayColors.Border.copy(alpha = .20f), RoundedCornerShape(52.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.align(Alignment.CenterHorizontally)
                    .horizontalScroll(rememberScrollState()).focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (!modalOpen && event.nativeKeyEvent.keyCode == AndroidKey.KEYCODE_DPAD_UP) {
                            if (event.type == KeyEventType.KeyDown) {
                                if (isMedia && details.seekable && details.seekEndMs > details.seekStartMs) seekFocus.requestFocus()
                                else hideAboveControls()
                            }
                            true
                        } else false
                    }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                    Row(if (isMedia) Modifier.width((if (onPip != null) 388.dp else 324.dp) * fontScale) else Modifier,
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CapsuleAction(stringResource(R.string.playback_choice_title), onMode, Icons.Rounded.Cast,
                            Modifier.focusRequester(modeAction).onFocusChanged { modeFocused = it.isFocused },
                            selected = modeHint, wide = true, focusId = "player.mode")
                        CapsuleAction(stringResource(when (pictureFit) {
                            PictureFit.FIT -> R.string.scale_best_fit
                            PictureFit.FILL -> R.string.scale_fill
                            PictureFit.STRETCH -> R.string.scale_stretch
                        }), { onPictureFit(pictureFit.next()) }, Icons.Rounded.AspectRatio, wide = true, focusId = "player.fit")
                        CapsuleAction(stringResource(R.string.picture_rotate, pictureRotation), onPictureRotation, Icons.Rounded.RotateRight,
                            if (!isMedia) Modifier.focusRequester(centerAction).onFocusChanged { centerFocused = it.isFocused } else Modifier,
                            wide = true, focusId = "player.rotate")
                        CapsuleAction(stringResource(if (recording) R.string.rec_stop else R.string.rec_start), onRecord,
                            if (recording) Icons.Rounded.Stop else Icons.Rounded.FiberManualRecord,
                            destructive = recording, selected = recording, focusId = "player.record")
                        if (onPip != null) CapsuleAction(stringResource(R.string.cd_pip), onPip, Icons.Rounded.PictureInPictureAlt)
                    }
                    if (isMedia) {
                        Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = .14f)))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (details.seekable && details.seekEndMs > details.seekStartMs)
                                CapsuleAction("−" + stringResource(R.string.seconds_short, 10), { onSeek((details.positionMs - 10_000).coerceIn(details.seekStartMs, details.seekEndMs)) },
                                    Icons.Rounded.Replay10, focusId = "player.rewind")
                            CapsuleAction(stringResource(if (mediaPlaying) R.string.p_pause else R.string.p_play), onPlayPause,
                                if (mediaPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                Modifier.focusRequester(centerAction).onFocusChanged { centerFocused = it.isFocused },
                                prominent = true, focusId = "player.play")
                            if (details.seekable && details.seekEndMs > details.seekStartMs)
                                CapsuleAction("+" + stringResource(R.string.seconds_short, 10), { onSeek((details.positionMs + 10_000).coerceIn(details.seekStartMs, details.seekEndMs)) },
                                    Icons.Rounded.Forward10, focusId = "player.forward")
                        }
                        Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = .14f)))
                    }
                    Row(if (isMedia) Modifier.width((if (onPip != null) 388.dp else 324.dp) * fontScale) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                        if (isMedia) {
                            CapsuleAction(stringResource(R.string.track_subtitles), { onTracks(androidx.media3.common.C.TRACK_TYPE_TEXT) }, Icons.Rounded.Subtitles)
                            CapsuleAction(stringResource(R.string.track_audio), { onTracks(androidx.media3.common.C.TRACK_TYPE_AUDIO) }, Icons.Rounded.Audiotrack)
                            CapsuleAction(stringResource(R.string.track_quality), { onTracks(androidx.media3.common.C.TRACK_TYPE_VIDEO) }, Icons.Rounded.HighQuality)
                        }
                        CapsuleAction(stringResource(R.string.p_settings), onSettings, Icons.Rounded.Settings, wide = !isMedia, focusId = "player.settings")
                        CapsuleAction(stringResource(R.string.p_stop_confirm), onEnd, Icons.Rounded.Stop,
                            destructive = true, wide = !isMedia, focusId = "player.end")
                    }
                }
                }

            }
        }
    }
}

@Composable
private fun ReceiverSeekBar(details: io.github.jqssun.airplay.service.VideoPlaybackInfo,
    onSeek: (Long) -> Unit, onEditing: (Boolean) -> Unit, enabled: Boolean,
    focus: FocusRequester, onExit: () -> Unit, onHide: () -> Unit) {
    var draft by remember(details.mediaId) { mutableStateOf<Long?>(null) }
    var focused by remember { mutableStateOf(false) }
    val start = details.seekStartMs
    val end = details.seekEndMs
    val position = (draft ?: (details.positionMs / 1000L * 1000L)).coerceIn(start, end)
    fun cancel() { draft = null; onEditing(false) }
    BackHandler(enabled = enabled && draft != null) { cancel() }
    DisposableEffect(details.mediaId) { onDispose { onEditing(false) } }
    Column(Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
        if (!enabled) return@onPreviewKeyEvent false
        val key = event.nativeKeyEvent.keyCode
        if (key == AndroidKey.KEYCODE_DPAD_UP) {
            if (event.type == KeyEventType.KeyDown) { cancel(); onHide() }
            return@onPreviewKeyEvent true
        }
        if (key == AndroidKey.KEYCODE_DPAD_DOWN) {
            if (event.type == KeyEventType.KeyDown) { cancel(); onExit() }
            return@onPreviewKeyEvent true
        }
        if (key !in listOf(AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_DPAD_RIGHT,
                AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_NUMPAD_ENTER)) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            when (key) {
                AndroidKey.KEYCODE_DPAD_LEFT -> { draft = (position - 10_000).coerceIn(start, end); onEditing(true) }
                AndroidKey.KEYCODE_DPAD_RIGHT -> { draft = (position + 10_000).coerceIn(start, end); onEditing(true) }
                else -> { draft?.let { onSeek(it.coerceIn(start, end)) }; cancel() }
            }
        }
        true
    }.onFocusChanged { focused = it.isFocused; if (!it.hasFocus) cancel() }
        .semantics {
            // Screen readers get the position, the full range and the seek step; without this the
            // hand-rolled control was silent for TalkBack.
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = position.toFloat(),
                range = start.toFloat()..end.coerceAtLeast(start + 1L).toFloat())
            stateDescription = "${playbackTime(position)} / ${playbackTime(end)}"
        }
        .focusable(enabled)
        .border(2.dp, if (focused) AirRelayColors.Accent else Color.Transparent)
        .background(if (draft != null) AirRelayColors.Elevated else Color.Transparent).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackTime(position), color = AirRelayColors.Text)
            Text(if (details.live) stringResource(R.string.transition_live) else playbackTime(end), color = AirRelayColors.Text)
        }
        LinearProgressIndicator(progress = { (position - start).toFloat() / (end - start).coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = AirRelayColors.Accent,
            trackColor = Color.White.copy(alpha = .24f))
        if (focused) Text(stringResource(R.string.transition_seek_hint), style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

private fun playbackTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun PlaybackHelpDialog(onDismiss: () -> Unit) = DialogInputScope {
    ProductAlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_help_title)) },
        text = { Text(stringResource(R.string.playback_help_body)) },
        confirmButton = { TvAction(stringResource(R.string.playback_help_confirm), onDismiss, primary = true) }, dismissButton = {})
}

@Composable
private fun PlaybackChoiceDialog(direct: Boolean, onChoose: (Boolean) -> Unit, onDismiss: () -> Unit) = DialogInputScope {
    ProductAlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playback_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(false, true).forEach { value ->
                    Column(Modifier.fillMaxWidth().background(AirRelayColors.Panel, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TvAction(stringResource(if (value) R.string.playback_direct else R.string.playback_mirror),
                            { onChoose(value) }, Modifier.fillMaxWidth(), selected = direct == value,
                            icon = if (direct == value) Icons.Rounded.CheckCircle else Icons.Rounded.Cast)
                        Text(stringResource(if (value) R.string.playback_direct_description else R.string.playback_mirror_description),
                            style = MaterialTheme.typography.bodyMedium, color = AirRelayColors.Muted)
                    }
                }
                Text(stringResource(R.string.playback_switch_hint), style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
            }
        }, confirmButton = {}, dismissButton = { TvAction(stringResource(R.string.p_cancel), onDismiss) })
}
