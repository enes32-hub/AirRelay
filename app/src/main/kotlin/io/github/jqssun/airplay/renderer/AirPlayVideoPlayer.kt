package io.github.jqssun.airplay.renderer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import java.util.concurrent.atomic.AtomicLong
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.datasource.HttpDataSource
import io.github.jqssun.airplay.receiver.StreamProfile

// rate is 0 while buffering; speed is the configured rate regardless of pause state
// native reads the effective rate, overlay reads playWhenReady + speed + skipSilence
data class PlaybackSnapshot(
    val position: Float,
    val duration: Float,
    val rate: Float,
    val ready: Boolean,
    val playWhenReady: Boolean,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val buffering: Boolean = false,
    val mediaId: Long = 0,
    val firstFrame: Boolean = false,
    val live: Boolean = false,
    val seekable: Boolean = false,
    val seekStartMs: Long = 0,
    val seekEndMs: Long = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val nominalBitrate: Int = 0,
    val sourceFrameRate: Float = 0f,
    val bufferedMs: Long = 0,
    val trackOptions: List<VideoTrackOption> = emptyList(),
    val cues: List<androidx.media3.common.text.Cue> = emptyList(),
)

// exoplayer calls stay on the main thread; native only reads the onPlaybackInfo snapshot
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AirPlayVideoPlayer(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private val videoPipeline = VideoPipeline().also { pipeline ->
        io.github.jqssun.airplay.recording.CleanRecording.register("direct", pipeline)
        pipeline.onPipelineFailure = {
            val token = generation.get()
            mainHandler.post { if (generation.get() == token && player != null) onError?.invoke(5001) }
        }
    }
    private var pendingSurface: Surface? = null
    private val generation = AtomicLong()
    private var mediaId = 0L
    private var firstFrame = false
    private var activeListener: Player.Listener? = null
    private var selection: AudioSelection? = null
    private var cues: List<androidx.media3.common.text.Cue> = emptyList()
    private val trackGroups = java.util.IdentityHashMap<androidx.media3.common.TrackGroup, Long>()
    private var nextTrackGroup = 0L

    private fun options(p: ExoPlayer): List<VideoTrackOption> = buildList {
        trackGroups.keys.removeAll { old -> p.currentTracks.groups.none { it.mediaTrackGroup === old } }
        for (type in listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)) {
            val groups = p.currentTracks.groups.filter { it.type == type }
            if (groups.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }) continue
            val overridden = p.trackSelectionParameters.overrides.keys.any { it.type == type }
            val disabled = type in p.trackSelectionParameters.disabledTrackTypes
            if (type == C.TRACK_TYPE_VIDEO) add(VideoTrackOption(type, "auto", selected = !overridden))
            if (type == C.TRACK_TYPE_TEXT) add(VideoTrackOption(type, "off", selected = disabled || groups.none { it.isSelected }))
            groups.forEach { group ->
                val groupId = trackGroups.getOrPut(group.mediaTrackGroup) { ++nextTrackGroup }
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    val f = group.getTrackFormat(index)
                    val label = listOfNotNull(f.label?.takeIf { it.isNotBlank() },
                        f.language?.takeIf { it != "und" && it.isNotBlank() },
                        if (f.height > 0) "${f.width} × ${f.height}" else null,
                        if (f.bitrate > 0) "${f.bitrate / 1000} kb/s" else null,
                        if (f.channelCount > 0) "${f.channelCount} ch" else null).distinct().joinToString(" · ")
                    add(VideoTrackOption(type, "${generation.get()}:$groupId:$index", label.ifEmpty { "${index + 1}" },
                        !disabled && group.isTrackSelected(index) && (type != C.TRACK_TYPE_VIDEO || overridden)))
                }
            }
        }
    }

    fun selectTrack(id: Long, type: Int, key: String) = current {
        val p = player ?: return@current
        if (id != mediaId || options(p).none { it.type == type && it.key == key }) return@current
        val params = p.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_AUDIO) selection = null
        params.setTrackTypeDisabled(type, key == "off")
        if (key != "auto" && key != "off") {
            val group = p.currentTracks.groups.firstOrNull { g ->
                g.type == type && (0 until g.length).any { i ->
                    "${generation.get()}:${trackGroups[g.mediaTrackGroup]}:$i" == key && g.isTrackSupported(i)
                }
            } ?: return@current
            val index = key.substringAfterLast(':').toInt()
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        p.trackSelectionParameters = params.build()
        if (type == C.TRACK_TYPE_TEXT && key == "off") cues = emptyList()
        _reportPlaybackInfo()
    }
    private var audioStarted = false
    private var audioWaitReported = false
    private var prepareStartedMs = 0L
    private var liveRecoveryAttempts = 0
    private var lastLiveRecoveryMs = Long.MIN_VALUE
    @Volatile var streamProfile = StreamProfile.CUSTOM
    @Volatile var maxVideoBitrateBps = 0
    var onError: ((Int) -> Unit)? = null

    private fun current(action: () -> Unit): Boolean {
        val token = generation.get()
        return mainHandler.post { if (token == generation.get()) action() }
    }

    fun selectAudio(value: AudioSelection) = current {
        selection = value
        applyAudioSelection()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyAudioSelection() {
        val wanted = selection ?: return
        val p = player ?: return
        val candidates = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { index -> group to index }
        }
        val matching = candidates.filter { (group, index) ->
            val f = group.getTrackFormat(index)
            wanted.matches(f.id, f.language, f.label, group.mediaTrackGroup.id)
        }
        if (matching.size != 1) {
            Log.i(TAG, "Audio selection unresolved: media=$mediaId matches=${matching.size} tracks=${candidates.size}")
            return
        }
        val (group, index) = matching.single()
        if (group.isTrackSelected(index)) return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)).build()
        Log.i(TAG, "Audio selection applied: media=$mediaId")
    }

    var onPlaybackInfo: ((PlaybackSnapshot) -> Unit)? = null
    var onVideoSize: ((width: Int, height: Int, aspect: Float) -> Unit)? = null
    var onTitle: ((String?) -> Unit)? = null
    var onEnded: (() -> Unit)? = null

    private val _reportTick = object : Runnable {
        override fun run() {
            _reportPlaybackInfo()
            mainHandler.postDelayed(this, REPORT_INTERVAL_MS)
        }
    }

    private fun listener(p: ExoPlayer, token: Long) = object : Player.Listener {
        private fun valid() = generation.get() == token && player === p
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            if (!valid()) return
            // Avoid signed content URLs from exception messages in persistent diagnostics.
            Log.w(TAG, "Playback error: media=$mediaId code=${error.errorCode}")
            val http = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            if (http != null) Log.w(TAG, "HTTP playback failure: media=$mediaId status=${http.responseCode}")
            val now = SystemClock.elapsedRealtime()
            val recoverable = http?.responseCode == 404 ||
                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
            if (p.isCurrentMediaItemLive && recoverable && liveRecoveryAttempts < 2 &&
                (liveRecoveryAttempts == 0 || now - lastLiveRecoveryMs >= 30_000)) {
                liveRecoveryAttempts++
                lastLiveRecoveryMs = now
                Log.w(TAG, "Live recovery: media=$mediaId attempt=$liveRecoveryAttempts status=${http?.responseCode}")
                mainHandler.postDelayed({
                    if (valid()) {
                        firstFrame = false
                        p.seekToDefaultPosition()
                        p.prepare()
                        _reportPlaybackInfo()
                    }
                }, 1_000)
                return
            }
            onError?.invoke(http?.responseCode ?: error.errorCode)
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (!valid()) return
            _reportPlaybackInfo()
            if (state == Player.STATE_ENDED) onEnded?.invoke()
        }
        override fun onRenderedFirstFrame() {
            if (!valid()) return
            firstFrame = true
            Log.i(TAG, "First video frame: media=$mediaId")
            _reportPlaybackInfo()
        }
        override fun onTracksChanged(tracks: Tracks) {
            if (!valid()) return
            applyAudioSelection()
            _reportPlaybackInfo()
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            Log.i(TAG, "Audio tracks: media=$mediaId groups=${audio.size} selected=${audio.count { it.isSelected }} elapsedMs=${SystemClock.elapsedRealtime() - prepareStartedMs}")
        }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { if (valid()) _reportPlaybackInfo() }
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            if (!valid()) return
            cues = if (C.TRACK_TYPE_TEXT in p.trackSelectionParameters.disabledTrackTypes) emptyList() else cueGroup.cues.toList()
            _reportPlaybackInfo()
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (valid()) onTitle?.invoke(mediaMetadata.title?.toString())
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (!valid() || videoSize.height == 0) return
            val width = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
            videoPipeline.setVideoSize(width, videoSize.height)
            onVideoSize?.invoke(width, videoSize.height, width.toFloat() / videoSize.height)
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun audioDiagnostics(p: ExoPlayer, token: Long) = object : AnalyticsListener {
        private fun valid() = generation.get() == token && player === p
        override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                                 mediaLoadData: MediaLoadData, error: java.io.IOException, wasCanceled: Boolean) {
            if (!valid() || wasCanceled) return
            val http = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
            // Keep signed URLs, cookies, and header values out of diagnostics.
            val stage = when (mediaLoadData.dataType) {
                C.DATA_TYPE_MANIFEST -> "manifest"
                C.DATA_TYPE_MEDIA -> "segment"
                C.DATA_TYPE_DRM -> "drm"
                else -> "other"
            }
            Log.w(TAG, "Media load failed: media=$mediaId stage=$stage status=${http?.responseCode} " +
                "elapsedMs=${loadEventInfo.loadDurationMs} bytes=${loadEventInfo.bytesLoaded} type=${error.javaClass.simpleName}")
        }
        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                                              initializedTimestampMs: Long, initializationDurationMs: Long) {
            if (valid()) Log.i(TAG, "Audio decoder ready: media=$mediaId decoder=$decoderName initMs=$initializationDurationMs elapsedMs=${SystemClock.elapsedRealtime() - prepareStartedMs}")
        }
        override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
            if (!valid()) return
            audioStarted = true
            // AudioTrack advancing is evidence of Android playout, not proof that
            // the Windows host speaker is audible. Do not log signed media URLs.
            Log.i(TAG, "Audio playout advancing: media=$mediaId elapsedMs=${SystemClock.elapsedRealtime() - prepareStartedMs} positionMs=${p.currentPosition}")
        }
        override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int,
                                    bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
            if (valid()) Log.w(TAG, "Audio underrun: media=$mediaId bufferMs=$bufferSizeMs sinceFeedMs=$elapsedSinceLastFeedMs")
        }
        override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
            if (valid()) Log.w(TAG, "Audio sink error: media=$mediaId type=${audioSinkError.javaClass.simpleName}")
        }
    }

    fun play(location: String, startPositionSeconds: Float, id: Long = 0L) : Boolean {
        val token = generation.incrementAndGet()
        return mainHandler.post {
            if (generation.get() != token) return@post
            _stopInternal(reportStopped = false)
            mediaId = id
            firstFrame = false
            selection = null
            audioStarted = false
            audioWaitReported = false
            prepareStartedMs = SystemClock.elapsedRealtime()
            liveRecoveryAttempts = 0
            lastLiveRecoveryMs = Long.MIN_VALUE
            val sources = DefaultMediaSourceFactory(context).setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
                override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val http = generateSequence<Throwable>(info.exception) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                    // Repeating an identical denied request cannot refresh sender
                    // credentials. Return promptly to the existing mirror session.
                    if (http?.responseCode == 401 || http?.responseCode == 403) return C.TIME_UNSET
                    return super.getRetryDelayMsFor(info)
                }
            })
            val p = ExoPlayer.Builder(context).setMediaSourceFactory(sources).build()
            // ABR chooses only variants present in the source. No sender encoder
            // control is implied, and audio-language overrides remain separate.
            val profile = streamProfile
            val constraints = p.trackSelectionParameters.buildUpon()
                .setMaxVideoBitrate(RemoteVideoPolicy.bitrateCeiling(profile, maxVideoBitrateBps))
            if (profile != StreamProfile.CUSTOM) constraints.setMaxVideoSize(profile.width, profile.height)
            p.trackSelectionParameters = constraints.build()
            player = p
            p.addAnalyticsListener(audioDiagnostics(p, token))
            activeListener = listener(p, token).also { p.addListener(it) }
            if (!videoPipeline.start()) {
                // Reported instead of crashing on a null input surface.
                Log.w(TAG, "Video pipeline failed to start for direct playback")
                runCatching { p.release() }
                player = null
                onError?.invoke(5001)
                return@post
            }
            videoPipeline.setDisplaySurface(pendingSurface)
            val inputSurface = videoPipeline.inputSurface
            if (inputSurface == null) {
                Log.w(TAG, "Video pipeline has no input surface for direct playback")
                runCatching { p.release() }
                player = null
                onError?.invoke(5001)
                return@post
            }
            p.setVideoSurface(inputSurface)
            val item = MediaItem.fromUri(location)
            // An absent/invalid position must preserve ExoPlayer's live default position.
            val startMs = PlaybackStartPosition.milliseconds(startPositionSeconds)
            if (startMs != null)
                p.setMediaItem(item, startMs)
            else p.setMediaItem(item)
            p.playWhenReady = true
            p.prepare()
            onPlaybackInfo?.invoke(PlaybackSnapshot(0f, 0f, 0f, false, true, mediaId = mediaId, buffering = true))
            mainHandler.postDelayed(_reportTick, REPORT_INTERVAL_MS)
        }
    }

    fun scrub(positionSeconds: Float) = current {
        val p = player ?: return@current
        if (!p.isCurrentMediaItemSeekable || !positionSeconds.isFinite()) {
            Log.i(TAG, "Sender seek not applied: media=$mediaId seekable=${p.isCurrentMediaItemSeekable}")
            return@current
        }
        val end = p.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: return@current
        p.seekTo((positionSeconds * 1000).toLong().coerceIn(0, end))
        Log.i(TAG, "Sender seek applied: media=$mediaId targetSeconds=$positionSeconds")
    }

    // coalesces the seek spam from drag-seeking
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun setScrubbing(enabled: Boolean) = current {
        player?.isScrubbingModeEnabled = enabled
    }

    fun setRate(rate: Float) = current {
        if (!rate.isFinite() || rate < 0f || rate > 4f) return@current
        val p = player ?: return@current
        if (rate <= 0f) {
            p.playWhenReady = false
        } else {
            p.playbackParameters = PlaybackParameters(rate.coerceAtLeast(0.1f))
            p.playWhenReady = true
        }
    }

    // local-only: the sender self-syncs from its next /playback-info poll
    fun setPlaying(playing: Boolean) = current {
        player?.playWhenReady = playing
    }

    // local speed toggle: unlike setRate it must not resume a paused player
    fun setSpeed(speed: Float) = current {
        if (!speed.isFinite() || speed <= 0f || speed > 4f) return@current
        player?.playbackParameters = PlaybackParameters(speed.coerceAtLeast(0.1f))
    }

    fun setSkipSilence(enabled: Boolean) = current {
        player?.skipSilenceEnabled = enabled
    }

    fun seekBy(deltaMs: Long) = current {
        val p = player ?: return@current
        if (!p.isCurrentMediaItemSeekable) return@current
        var target = (p.currentPosition + deltaMs).coerceAtLeast(0)
        val duration = p.duration
        if (duration != C.TIME_UNSET) {
            target = target.coerceAtMost(duration)
        }
        p.seekTo(target)
    }

    fun setSurface(surface: Surface) = mainHandler.post {
        pendingSurface = surface
        videoPipeline.setDisplaySurface(surface)
    }

    // no-op if a newer surface already replaced this one
    fun clearSurface(surface: Surface) = mainHandler.post {
        if (pendingSurface !== surface) return@post
        pendingSurface = null
        videoPipeline.setDisplaySurface(null)
    }

    fun stop(): Boolean {
        val token = generation.incrementAndGet()
        return mainHandler.post { if (generation.get() == token) _stopInternal(reportStopped = true) }
    }

    private fun _stopInternal(reportStopped: Boolean) {
        mainHandler.removeCallbacks(_reportTick)
        player?.let {
            activeListener?.let { listener -> it.removeListener(listener) }
            it.release()
        }
        player = null
        videoPipeline.release()
        cues = emptyList()
        trackGroups.clear()
        activeListener = null
        // duration=-1 is the "video finished" sentinel for the playback-info handler
        if (reportStopped) {
            onPlaybackInfo?.invoke(PlaybackSnapshot(0f, -1f, 0f, false, false, mediaId = mediaId))
        }
    }

    private fun _reportPlaybackInfo() {
        val p = player ?: return
        val elapsed = SystemClock.elapsedRealtime() - prepareStartedMs
        if (!audioStarted && !audioWaitReported && elapsed >= 3000 && p.playWhenReady &&
            p.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }) {
            audioWaitReported = true
            Log.w(TAG, "Audio startup pending: media=$mediaId elapsedMs=$elapsed state=${p.playbackState} videoFrame=$firstFrame positionMs=${p.currentPosition} bufferedMs=${p.totalBufferedDuration} suppression=${p.playbackSuppressionReason} volume=${p.volume}")
        }
        val durationMs = p.duration
        val position = p.currentPosition / 1000f
        // 0 = live/unknown (TIME_UNSET); a real value only exists for vod
        val duration = if (durationMs == C.TIME_UNSET) 0f else durationMs / 1000f
        val rate = if (p.playWhenReady && p.playbackState == Player.STATE_READY) p.playbackParameters.speed else 0f
        // readyToPlay = the timeline is established, NOT exoplayer's buffering state: for vod that means the duration is known; for live there is no duration so being playable is enough. the sender holds its timeline (and /play) until this, so it must not go true early
        val ready = if (p.isCurrentMediaItemLive) p.playbackState == Player.STATE_READY
                    else durationMs != C.TIME_UNSET
        onPlaybackInfo?.invoke(
            PlaybackSnapshot(
                position = position,
                duration = duration,
                rate = rate,
                ready = ready,
                playWhenReady = p.playWhenReady,
                speed = p.playbackParameters.speed,
                skipSilence = p.skipSilenceEnabled,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                mediaId = mediaId, firstFrame = firstFrame,
                live = p.isCurrentMediaItemLive,
                seekable = p.isCurrentMediaItemSeekable && durationMs != C.TIME_UNSET && durationMs > 0,
                seekEndMs = if (durationMs == C.TIME_UNSET) 0 else durationMs.coerceAtLeast(0),
                videoWidth = (p.videoFormat?.width ?: 0).coerceAtLeast(0),
                videoHeight = (p.videoFormat?.height ?: 0).coerceAtLeast(0),
                videoCodec = p.videoFormat?.sampleMimeType.orEmpty(),
                audioCodec = p.audioFormat?.sampleMimeType.orEmpty(),
                nominalBitrate = (p.videoFormat?.bitrate ?: 0).coerceAtLeast(0),
                sourceFrameRate = (p.videoFormat?.frameRate ?: 0f).coerceAtLeast(0f),
                bufferedMs = p.totalBufferedDuration.coerceAtLeast(0),
                trackOptions = options(p), cues = cues,
            )
        )
    }

    companion object {
        private const val TAG = "AirPlayVideoPlayer"
        private const val REPORT_INTERVAL_MS = 250L
    }
}
