import io.github.jqssun.airplay.receiver.*

fun main() {
    var checks = 0
    fun expect(value: Boolean) { check(value); checks++ }
    val ready = ReceiverUiState(running = true, connections = 1, airplay = Advertisement.REGISTERED)
    val screenAudio = ready.observeAudioFormat(true)
    expect(screenAudio.phase == ReceiverPhase.WAITING_VIDEO && !screenAudio.audioConnected)
    val live = screenAudio.observeMirrorStart().copy(firstFrameSubmitted = true)
    expect(live.phase == ReceiverPhase.MIRRORING)
    val interrupted = live.observeMirrorEnd()
    expect(interrupted.phase == ReceiverPhase.WAITING_VIDEO && !interrupted.firstFrameSubmitted)
    expect(interrupted.observeAudioFormat(false).phase == ReceiverPhase.WAITING_VIDEO)
    expect(interrupted.observeMirrorStart().phase == ReceiverPhase.WAITING_VIDEO)
    expect(ready.observeMirrorStart().observeMirrorEnd().phase == ReceiverPhase.WAITING_VIDEO)
    expect(ready.observeMirrorEnd().phase == ReceiverPhase.CONNECTING)
    val ended = interrupted.sessionEnded(ReceiverNotice.NONE)
    expect(!ended.screenSession && !ended.mirrorRequested && !ended.audioConnected)
    expect(ended.phase == ReceiverPhase.READY)
    val music = ended.copy(connections = 1).observeAudioFormat(false)
    expect(music.phase == ReceiverPhase.AUDIO_CONNECTED)
    expect(music.observeMirrorEnd().phase == ReceiverPhase.AUDIO_CONNECTED)
    expect(music.observeAudioFormat(true).phase == ReceiverPhase.WAITING_VIDEO)
    val next = live.observeMirrorEnd().observeMirrorStart(12)
    expect(next.observeFirstFrame(11).phase == ReceiverPhase.WAITING_VIDEO)
    expect(next.observeFirstFrame(12).phase == ReceiverPhase.MIRRORING)
    expect(next.sessionEnded().observeFirstFrame(12).firstFrameSubmitted == false)
    val failed = next.copy(mediaErrorCode = 403)
    expect(failed.observeFirstFrame(11).mediaErrorCode == 403)
    expect(failed.observeFirstFrame(12).mediaErrorCode == null)
    expect(failed.copy(remotePreparing = true).observeFirstFrame(12).mediaErrorCode == 403)
    println("PASS: $checks screen-session observation checks")
}
