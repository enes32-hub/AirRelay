package io.github.jqssun.airplay.receiver

/** Product observations only; no transport restart or codec/audio reconfiguration. */
fun ReceiverUiState.observeAudioFormat(usingScreen: Boolean): ReceiverUiState {
    val screen = screenSession || usingScreen
    return copy(screenSession = screen, mirrorRequested = mirrorRequested || usingScreen,
        audioConnected = !screen && connections > 0)
}

fun ReceiverUiState.observeMirrorStart(token: Long = -1L): ReceiverUiState = copy(
    presentationSession = token,
    screenSession = true, mirrorRequested = true, presentationHeld = true, mediaErrorCode = null, firstFrameSubmitted = false,
    audioConnected = false, mediaPending = false, notice = ReceiverNotice.NONE)

fun ReceiverUiState.observeMirrorEnd(): ReceiverUiState = copy(
    presentationSession = -1L,
    mirrorRequested = screenSession && connections > 0,
    firstFrameSubmitted = false,
    audioConnected = audioConnected && !screenSession)

fun ReceiverUiState.observeFirstFrame(token: Long): ReceiverUiState =
    if (token >= 0 && token == presentationSession && mirrorRequested)
        copy(firstFrameSubmitted = true, mediaErrorCode = if (!mediaPlaying && !remotePreparing) null else mediaErrorCode) else this
