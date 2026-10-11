package io.github.jqssun.airplay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import io.github.jqssun.airplay.MainActivity
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.realDisplaySize
import io.github.jqssun.airplay.audio.DacpController
import io.github.jqssun.airplay.audio.DacpPlayer
import io.github.jqssun.airplay.audio.DmapParser
import io.github.jqssun.airplay.audio.TrackInfo
import io.github.jqssun.airplay.audio.VolumeBroadcast
import io.github.jqssun.airplay.bridge.LogListener
import io.github.jqssun.airplay.bridge.NativeBridge
import io.github.jqssun.airplay.bridge.RaopCallbackHandler
import io.github.jqssun.airplay.discovery.NsdServiceManager
import io.github.jqssun.airplay.download.VideoDownloader
import io.github.jqssun.airplay.renderer.AirPlayVideoPlayer
import io.github.jqssun.airplay.renderer.AudioRenderer
import io.github.jqssun.airplay.renderer.VideoRenderer
import io.github.jqssun.airplay.viewmodel.DebugInfo
import java.net.NetworkInterface
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.diagnostics.DiagnosticStore
import io.github.jqssun.airplay.diagnostics.LogRedactor
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import io.github.jqssun.airplay.receiver.StartRequestGate

data class VideoPlaybackInfo(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = true,
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
    val trackOptions: List<io.github.jqssun.airplay.renderer.VideoTrackOption> = emptyList(),
    val cues: List<androidx.media3.common.text.Cue> = emptyList(),
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AirPlayService : LifecycleService(), RaopCallbackHandler, LogListener {

    @Volatile private var nativeHandle = 0L
    private val _receiverState = MutableStateFlow(ReceiverUiState())
    val receiverState = _receiverState.asStateFlow()
    private val startRequests = StartRequestGate()
    private var startJob: Job? = null
    private val pendingSettings by lazy { PendingReceiverSettings(this) }
    private val diagnostics by lazy { DiagnosticStore.get(this) }
    private var networkMonitor: LocalNetworkMonitor? = null
    private var discoveryGeneration = 0L
    private val discoveryResumePolicy = io.github.jqssun.airplay.discovery.DiscoveryResumePolicy()
    private val discoveryWakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) refreshDiscoveryOnResume()
        }
    }

    /** Wake/foreground recovery for stale sender discovery, without replacing native sockets. */
    fun refreshDiscoveryOnResume() {
        val state = _receiverState.value
        if (discoveryResumePolicy.shouldRefresh(SystemClock.elapsedRealtime(),
                !stopping && nativeHandle != 0L && state.running && !state.starting,
                state.network == LocalNetwork.WIFI || state.network == LocalNetwork.ETHERNET,
                state.sessionBusy || state.remotePreparing,
                state.airplay == Advertisement.REGISTERING || state.raop == Advertisement.REGISTERING)) {
            log("Discovery refresh: idle foreground/wake")
            refreshDiscoveryRegistration()
        }
    }
    private val refreshNetworkDiscovery = Runnable {
        if (!stopping && nativeHandle != 0L && _serverState.value == ServerState.RUNNING &&
            _receiverState.value.network in setOf(LocalNetwork.WIFI, LocalNetwork.ETHERNET)) {
            refreshDiscoveryRegistration()
        }
    }
    private var registeredPort = 0
    @Volatile private var stopping = false
    // Serialises clean-recording source switches off the caller's thread: each switch may
    // wait up to the pipeline's bounded detach, which must never run on the UI thread or on
    // the native HTTP thread.
    private val recordingSourceExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "recording-source")
    }
    @Volatile private var callbackGeneration = 0L
    private var incomingPresentationRequested = false
    private var desiredVideoSurface: Surface? = null
    private val mirrorTokens = AtomicLong()
    @Volatile private var mirrorToken = -1L
    private var pinExpiry: Runnable? = null
    private val pendingVideoExpiry = Runnable {
        _receiverState.update { if (stopping) it else it.copy(mediaPending = videoSessionPending()) }
    }

    fun dismissNotice() {
        _receiverState.update { if (it.notice == ReceiverNotice.START_FAILED || it.notice == ReceiverNotice.DISCOVERY_FAILED) it
            else it.copy(notice = ReceiverNotice.NONE) }
    }

    fun refreshPendingSettings() {
        _receiverState.update { it.copy(pendingSettings = pendingSettings.hasPending()) }
    }

    private fun newDiscoveryManager(): NsdServiceManager {
        val generation = ++discoveryGeneration
        return NsdServiceManager(this) { result ->
            _mainHandler.post {
                if (generation != discoveryGeneration || stopping) return@post
                diagnostics.offer("Discovery ${result.kind}: ${result.state}; error=${result.errorCode ?: 0}")
                _receiverState.update { old ->
                    val next = when (result.kind) {
                        NsdServiceManager.Kind.AIRPLAY -> old.copy(
                            airplay = result.state,
                            advertisedName = result.actualName ?: old.advertisedName,
                            discoveryErrorCode = result.errorCode,
                        )
                        NsdServiceManager.Kind.RAOP -> old.copy(raop = result.state)
                    }
                    next.copy(notice = when {
                        result.state == Advertisement.FAILED -> ReceiverNotice.DISCOVERY_FAILED
                        next.airplay == Advertisement.REGISTERED &&
                            next.raop != Advertisement.FAILED && old.notice == ReceiverNotice.DISCOVERY_FAILED -> ReceiverNotice.NONE
                        else -> next.notice
                    })
                }
            }
        }
    }

    /** Re-advertise the EXISTING endpoint; never restarts the native receiver or media. */
    fun retryDiscovery() {
        if (!_receiverState.value.canRetryDiscovery || stopping || nativeHandle == 0L) return
        refreshDiscoveryRegistration()
    }

    private fun refreshDiscoveryRegistration() {
        nsdManager?.release()
        nsdManager = newDiscoveryManager()
        try {
            nsdManager?.acquireMulticastLock()
            registerCurrentServices()
        } catch (_: RuntimeException) {
            _receiverState.update { it.copy(airplay = Advertisement.FAILED,
                notice = ReceiverNotice.DISCOVERY_FAILED) }
        }
    }

    private fun registerCurrentServices() {
        discoveryResumePolicy.registrationStarted(SystemClock.elapsedRealtime())
        val raopTxt = NativeBridge.nativeGetRaopTxtRecords(nativeHandle) ?: emptyMap()
        val airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(nativeHandle) ?: emptyMap()
        val raopName = NativeBridge.nativeGetRaopServiceName(nativeHandle) ?: "AirPlay"
        val name = NativeBridge.nativeGetServerName(nativeHandle)
            ?: prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME) ?: Prefs.DEF_SERVER_NAME
        _receiverState.update { it.copy(airplay = Advertisement.REGISTERING,
            raop = if (prefs.getBoolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO))
                Advertisement.REGISTERING else Advertisement.NOT_REQUESTED,
            discoveryErrorCode = null) }
        if (prefs.getBoolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO)) {
            nsdManager?.registerRaop(raopName, registeredPort, raopTxt)
        }
        nsdManager?.registerAirplay(name, registeredPort, airplayTxt)
    }

    /** There is no native per-client cancel API. Close the receiver, not just its PIN window. */
    fun cancelPairing(expired: Boolean = false) {
        if (_receiverState.value.pin == null) return
        getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(UserExperiencePrefs.USER_STOPPED, true).apply()
        stopServer()
        _receiverState.update { it.copy(notice = if (expired) ReceiverNotice.PAIRING_EXPIRED
            else ReceiverNotice.PAIRING_CANCELLED) }
    }
    private var nsdManager: NsdServiceManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED

    val videoRenderer = VideoRenderer(this)
    val audioRenderer = AudioRenderer()
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val airPlayVideoPlayer by lazy { AirPlayVideoPlayer(this) }
    val videoDownloader by lazy { VideoDownloader(this) }

    // hls urls point at the native httpd, valid only while the session lives
    private val _videoLocation = MutableStateFlow<String?>(null)
    val videoLocation = _videoLocation.asStateFlow()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    }
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState = _serverState.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount = _connectionCount.asStateFlow()

    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect = _videoAspect.asStateFlow()

    private val _videoResolution = MutableStateFlow("")
    val videoResolution = _videoResolution.asStateFlow()

    private val _audioOnly = MutableStateFlow(false)
    val audioOnly = _audioOnly.asStateFlow()

    private val _videoPlaybackActive = MutableStateFlow(false)
    val videoPlaybackActive = _videoPlaybackActive.asStateFlow()

    private val _videoPlaybackInfo = MutableStateFlow(VideoPlaybackInfo())
    val videoPlaybackInfo = _videoPlaybackInfo.asStateFlow()

    // bumped per /play so the ui resets transport state on back-to-back plays too
    private val _videoPlaySeq = MutableStateFlow(0L)
    val videoPlaySeq = _videoPlaySeq.asStateFlow()

    private val _videoPlaybackAspect = MutableStateFlow(16f / 9f)
    val videoPlaybackAspect = _videoPlaybackAspect.asStateFlow()

    private val _videoPlaybackSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoPlaybackSize = _videoPlaybackSize.asStateFlow()

    // container/manifest metadata
    private val _videoTitle = MutableStateFlow("")
    val videoTitle = _videoTitle.asStateFlow()

    // recent /playback-info polls with no playback = pending video; polls start ~1s before /play
    @Volatile private var _lastVideoPollAt = 0L
    @Volatile private var _videoPollSuppressed = false

    fun videoSessionPending(): Boolean =
        !_videoPlaybackActive.value && !_audioOnly.value && !_mirroringActive.value &&
            !_videoPollSuppressed &&
            SystemClock.elapsedRealtime() - _lastVideoPollAt < VIDEO_POLL_PENDING_TIMEOUT_MS

    // set once mirroring reports a real size; stops with session
    private val _mirroringActive = MutableStateFlow(false)
    val mirroringActive = _mirroringActive.asStateFlow()

    private val _trackInfo = MutableStateFlow(TrackInfo())
    val trackInfo = _trackInfo.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _playing = MutableStateFlow(true)
    val playing = _playing.asStateFlow()

    @Volatile private var _progressBaseMs = 0L
    @Volatile private var _progressBaseTime = 0L

    fun currentPositionMs(): Long {
        if (_progressBaseTime == 0L || !_playing.value) return _positionMs.value
        val elapsed = SystemClock.elapsedRealtime() - _progressBaseTime
        return (_progressBaseMs + elapsed).coerceIn(0, _durationMs.value)
    }

    var dacpController: DacpController? = null
        private set
    lateinit var dacpPlayer: DacpPlayer
        private set
    private val _mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var _coverArtBytes: ByteArray? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaReceiver: BroadcastReceiver? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var _pendingVolEchoes = 0
    // senders park volume at -30 as route closes; session must not leave device silenced
    private var _preZeroIdx = -1
    @Volatile private var _senderFrac = -1f
    private var _volSyncTarget = -1f
    private var _volSyncHint = 0
    private var _volSyncDir = 0
    private var _volSyncSteps = 0
    private val _volSyncTimeout = Runnable { _volSyncEnd() }

    var logCallback: ((String) -> Unit)? = null

    @Volatile private var _lastPin: String? = null
    var pinCallback: ((String?) -> Unit)? = null
        set(value) {
            field = value
            // ui replay only: binding the activity must not mint a new native pin
            value?.invoke(_lastPin)
        }

    private fun log(msg: String) {
        val safe = LogRedactor.redact(msg)
        Log.i(TAG, safe)
        diagnostics.offer(safe)
        logCallback?.invoke(safe)
    }

    override fun onLog(msg: String) {
        val safe = LogRedactor.redact(msg)
        diagnostics.offer(safe)
        logCallback?.invoke(safe)
    }

    inner class LocalBinder : Binder() {
        val service: AirPlayService
            get() = this@AirPlayService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return LocalBinder()
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, discoveryWakeReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        createNotificationChannel()
        refreshPendingSettings()
        networkMonitor = LocalNetworkMonitor(this) { network ->
            _receiverState.update { old -> old.copy(network = network,
                notice = if (old.running && old.network != LocalNetwork.UNKNOWN && old.network != network)
                    ReceiverNotice.NETWORK_CHANGED else old.notice) }
            _mainHandler.removeCallbacks(refreshNetworkDiscovery)
            if (_serverState.value == ServerState.RUNNING && !stopping) {
                if (network == LocalNetwork.UNAVAILABLE || network == LocalNetwork.PERMISSION_DENIED) {
                    // Withdraw discovery only; native transports own their actual session lifetime.
                    discoveryGeneration++
                    nsdManager?.release()
                    nsdManager = null
                    _receiverState.update { it.copy(airplay = Advertisement.IDLE, raop = Advertisement.IDLE) }
                } else if (network == LocalNetwork.WIFI || network == LocalNetwork.ETHERNET) {
                    // Includes same-type LAN/address changes. Coalesce capability/link callbacks.
                    _mainHandler.postDelayed(refreshNetworkDiscovery, 500)
                }
            }
        }.also { it.start() }
        videoRenderer.onFirstFrameSubmitted = { token ->
            if (!stopping && token == mirrorToken) {
                _receiverState.update { if (!stopping && token == mirrorToken) it.observeFirstFrame(token) else it }
            }
        }
        videoRenderer.onPresentationFailure = { token ->
            if (!stopping && token == mirrorToken) _receiverState.update { state ->
                if (!stopping && state.presentationSession == token && state.screenSession &&
                    !state.mediaPlaying && !state.remotePreparing)
                    state.copy(mediaErrorCode = 5001, firstFrameSubmitted = false) else state
            }
        }
        dacpController = DacpController(this)
        dacpPlayer = DacpPlayer(
            mainLooper,
            dacp = { dacpController },
            snapshot = {
                DacpPlayer.Snapshot(
                    track = _trackInfo.value,
                    artworkData = _coverArtBytes,
                    durationMs = _durationMs.value,
                    playing = _playing.value,
                    active = _audioOnly.value && _connectionCount.value > 0,
                )
            },
            positionMs = ::currentPositionMs,
            setPlaying = ::_setPlaying,
        )
        mediaSession = MediaSessionCompat(this, "AirPlay").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (_videoPlaybackActive.value) {
                        airPlayVideoPlayer.setPlaying(true)
                        return
                    }
                    _setPlaying(true)
                    dacpController?.play()
                }
                override fun onPause() {
                    if (_videoPlaybackActive.value) {
                        airPlayVideoPlayer.setPlaying(false)
                        return
                    }
                    _setPlaying(false)
                    dacpController?.pause()
                }
                override fun onStop() {
                    if (_videoPlaybackActive.value) stopVideoPlayback()
                }
                override fun onFastForward() {
                    if (_videoPlaybackActive.value) airPlayVideoPlayer.seekBy(VIDEO_SEEK_STEP_MS)
                }
                override fun onRewind() {
                    if (_videoPlaybackActive.value) airPlayVideoPlayer.seekBy(-VIDEO_SEEK_STEP_MS)
                }
                override fun onSeekTo(pos: Long) {
                    if (_videoPlaybackActive.value) airPlayVideoPlayer.scrub(pos / 1000f)
                }
                override fun onSkipToNext() {
                    if (!_videoPlaybackActive.value) dacpController?.nextItem()
                }
                override fun onSkipToPrevious() {
                    if (!_videoPlaybackActive.value) dacpController?.prevItem()
                }
            })
        }
        mediaReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_PLAY_PAUSE -> togglePlayPause()
                    ACTION_NEXT -> dacpController?.nextItem()
                    ACTION_PREV -> dacpController?.prevItem()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
        }
        ContextCompat.registerReceiver(this, mediaReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getIntExtra(VolumeBroadcast.EXTRA_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
                val idx = intent.getIntExtra(VolumeBroadcast.EXTRA_VALUE, -1)
                val prev = intent.getIntExtra(VolumeBroadcast.EXTRA_PREV_VALUE, -1)
                if (idx < 0 || prev < 0 || idx == prev) return
                if (_pendingVolEchoes > 0) { _pendingVolEchoes--; return }
                if (_connectionCount.value == 0) return
                val target = idx.toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                Log.d(TAG, "local volume $prev -> $idx, sync sender to $target")
                _preZeroIdx = -1
                val active = _volSyncTarget >= 0f
                _volSyncTarget = target
                _volSyncHint = if (idx > prev) 1 else -1
                _volSyncDir = 0
                _volSyncSteps = 0
                if (!active) _volSyncStep()
            }
        }
        ContextCompat.registerReceiver(this, volumeReceiver, IntentFilter(VolumeBroadcast.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED)

        airPlayVideoPlayer.onVideoSize = { width, height, aspect ->
            _videoPlaybackAspect.value = aspect
            _videoPlaybackSize.value = width to height
        }
        airPlayVideoPlayer.onTitle = { _videoTitle.value = it ?: "" }
        airPlayVideoPlayer.onEnded = { _endVideoPlayback("AirPlay Video stopped (player)") }
        airPlayVideoPlayer.onError = { code ->
            _endVideoPlayback("AirPlay Video failed: code=$code", code)
        }
        airPlayVideoPlayer.onPlaybackInfo = snapshot@{ snapshot ->
            if (snapshot.mediaId != _videoPlaySeq.value.toLong()) return@snapshot
            if (nativeHandle != 0L) {
                NativeBridge.nativeUpdatePlaybackInfo(nativeHandle, snapshot.position, snapshot.duration, snapshot.rate, snapshot.ready)
            }
            if (_videoPlaybackActive.value) {
                if (snapshot.rate > 0 && (snapshot.position * 1000).toLong() != _videoPlaybackInfo.value.positionMs)
                    lastRemoteProgressAt = SystemClock.elapsedRealtime()
                _updateVideoPlaybackState(snapshot.position, snapshot.rate)
                _videoPlaybackInfo.value = VideoPlaybackInfo(
                    positionMs = (snapshot.position * 1000).toLong(),
                    durationMs = if (snapshot.duration > 0f) (snapshot.duration * 1000).toLong() else 0L,
                    playing = snapshot.playWhenReady,
                    speed = snapshot.speed,
                    skipSilence = snapshot.skipSilence,
                    buffering = snapshot.buffering,
                    mediaId = snapshot.mediaId, firstFrame = snapshot.firstFrame,
                    live = snapshot.live, seekable = snapshot.seekable,
                    seekStartMs = snapshot.seekStartMs, seekEndMs = snapshot.seekEndMs,
                    videoWidth = snapshot.videoWidth, videoHeight = snapshot.videoHeight,
                    videoCodec = snapshot.videoCodec, audioCodec = snapshot.audioCodec,
                    nominalBitrate = snapshot.nominalBitrate, sourceFrameRate = snapshot.sourceFrameRate,
                    bufferedMs = snapshot.bufferedMs,
                    trackOptions = snapshot.trackOptions, cues = snapshot.cues,
                )
            }
        }
        lifecycleScope.launch {
            prefs.audioConfigFlow()
                .debounce(AUDIO_CONFIG_DEBOUNCE_MS)
                .collect { audioRenderer.updateConfig(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_START_SERVER) {
            try { promoteToForeground() }
            catch (e: RuntimeException) {
                log("Foreground startup rejected: ${e.javaClass.simpleName}")
                _failStart()
                return START_NOT_STICKY
            }
            val name = prefs.getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME) ?: Prefs.DEF_SERVER_NAME
            startServer(name, ensureServiceStarted = false)
            // Preferences and any preceding teardown are awaited; failure stops this service in _failStart().
        }
        return START_NOT_STICKY
    }

    fun startServer(name: String) {
        startServer(name, ensureServiceStarted = true)
    }

    private fun startServer(name: String, ensureServiceStarted: Boolean) {
        if (_serverState.value == ServerState.RUNNING || _receiverState.value.starting) return
        val request = startRequests.begin()
        _receiverState.update { it.sessionEnded(ReceiverNotice.NONE).copy(starting = true, portConflict = null) }
        startJob = lifecycleScope.launch {
            try {
                // Durable preference commits are disk I/O, not UI work. Native startup remains
                // on its existing thread with exactly its existing call sequence and parameters.
                val committed = withContext(Dispatchers.IO) { pendingSettings.applyAtReceiverStart() }
                // A prior service instance may still own the fixed listener ports. Await
                // its process-lived teardown without blocking the Android main thread.
                val cleanup = synchronized(nativeCleanupLock) { nativeCleanupTail }
                cleanup?.await()
                if (!startRequests.isCurrent(request)) return@launch
                if (!committed) {
                    log("Unable to commit receiver settings")
                    _failStart()
                    return@launch
                }
                stopping = false
                incomingPresentationRequested = false
                refreshPendingSettings()
                val requestedPort = prefs.getInt(Prefs.SERVER_PORT, Prefs.DEF_SERVER_PORT).coerceIn(1, 65535)
                val conflict = withContext(Dispatchers.IO) {
                    ReceiverPortGuard.firstUnavailable(ReceiverPortGuard.endpoints(requestedPort))
                }
                if (!startRequests.isCurrent(request)) return@launch
                val startupPort = ReceiverPortPolicy.nativePort(requestedPort,
                    ReceiverPortPolicy.isEmulator(Build.HARDWARE, Build.FINGERPRINT, Build.MODEL), conflict != null)
                if (startupPort == null) {
                    log("Receiver listener unavailable: $conflict; no service advertised")
                    _failStart()
                    _receiverState.update { it.copy(portConflict = conflict.toString()) }
                    return@launch
                }
                if (startupPort == 0) log("Receiver port coexistence: $conflict busy; native automatic ports selected")
                val committedName = prefs.getString(Prefs.SERVER_NAME, name) ?: name
                startWithExistingConfiguration(committedName, ensureServiceStarted, startupPort)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                if (startRequests.isCurrent(request)) {
                    log("Receiver start failed at the Android boundary")
                    _failStart()
                }
            } catch (_: LinkageError) {
                if (startRequests.isCurrent(request)) {
                    log("Receiver native libraries unavailable")
                    _failStart()
                }
            } finally {
                // A cancelled coroutine cannot interrupt SharedPreferences.commit(). Reflect any
                // completed draft cleanup without reviving the cancelled native start request.
                refreshPendingSettings()
                if (startRequests.isCurrent(request)) startJob = null
            }
        }
    }

    private fun startWithExistingConfiguration(name: String, ensureServiceStarted: Boolean, startupPort: Int) {
        val effectiveName = name.ifBlank { Prefs.DEF_SERVER_NAME }
        desiredVideoSurface?.takeIf { it.isValid }?.let(videoRenderer::setSurface)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airplay:server").apply { acquire() }

        nsdManager = newDiscoveryManager().apply { acquireMulticastLock() }

        val hwAddr = getHwAddr()
        val keyFile = filesDir.resolve("airplay.pem").absolutePath
        val nohold = prefs.getBoolean(Prefs.ALLOW_NEW_CONN, Prefs.DEF_ALLOW_NEW_CONN)
        val requirePin = prefs.getBoolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN)

        // oboe's OpenSL ES backend (pre-AAudio devices, API < 27) can't discover native
        // rate / burst size itself; feed it AudioManager values so low-latency buffer
        // sizing works there
        // see: https://github.com/google/oboe/blob/main/docs/GettingStarted.md#obtaining-optimal-latency
        NativeBridge.nativeSetDefaultStreamValues(
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0,
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
        )
        nativeHandle = NativeBridge.nativeInit(this, hwAddr, effectiveName, keyFile, nohold, requirePin)
        if (nativeHandle == 0L) {
            log("Native init failed")
            _failStart()
            return
        }
        audioRenderer.attachEngine(nativeHandle)

        // apply settings from preferences
        val maxFps = effectiveMirrorFps()
        val overscanned = prefs.getBoolean(Prefs.OVERSCANNED, Prefs.DEF_OVERSCANNED)
        val audioLatencyMs = prefs.getInt(Prefs.AUDIO_LATENCY_MS, Prefs.DEF_AUDIO_LATENCY_MS)
        val (reqW, reqH) = _displaySize(clamp = false)
        val h265 = videoRenderer.selectDecoders(reqW, reqH, maxFps, prefs.getBoolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED))
        val alac = prefs.getBoolean(Prefs.ALAC_ENABLED, Prefs.DEF_ALAC_ENABLED)
        val aac = prefs.getBoolean(Prefs.AAC_ENABLED, Prefs.DEF_AAC_ENABLED)

        videoRenderer.staleFrameThresholdMs = streamProfile().staleMs
        log("Stream profile=${streamProfile().key} requested=${reqW}x${reqH} fpsLimit=$maxFps")
        videoRenderer.enforceSdr = prefs.getBoolean(Prefs.ENFORCE_SDR, Prefs.DEF_ENFORCE_SDR)
        videoRenderer.keyAllowFrameDrop = prefs.getBoolean(Prefs.KEY_ALLOW_FRAME_DROP, Prefs.DEF_KEY_ALLOW_FRAME_DROP)
        videoRenderer.selector.maxOperatingRate = when (prefs.getString(Prefs.OPERATING_RATE, Prefs.DEF_OPERATING_RATE)) {
            Prefs.ON -> true; Prefs.OFF -> false; else -> null
        }
        videoRenderer.benchmarkLog = false // No persistent technical/per-frame logs in the user product.
        videoRenderer.benchmarkLogCallback = { msg -> onLog(msg) }
        videoRenderer.scheduledOutputBufferRelease = prefs.getBoolean(Prefs.SCHEDULED_OUTPUT_BUFFER_RELEASE, Prefs.DEF_SCHEDULED_OUTPUT_BUFFER_RELEASE)
        NativeBridge.nativeSetH265Enabled(nativeHandle, h265)
        NativeBridge.nativeSetCodecs(nativeHandle, alac, aac)
        // Screen mirroring is a per-stream choice: every receiver start advertises direct
        // playback again, and only an in-stream switch turns mirroring on for that stream.
        prefs.edit().putBoolean(Prefs.ADVERTISE_VIDEO, true).apply()
        val advertiseAudio = prefs.getBoolean(Prefs.ADVERTISE_AUDIO, Prefs.DEF_ADVERTISE_AUDIO)
        NativeBridge.nativeSetHlsEnabled(nativeHandle, true)
        NativeBridge.nativeSetLang(nativeHandle, "", "", resources.configuration.locales.toLanguageTags().replace(',', ':'))
        NativeBridge.nativeSetAudioEnabled(nativeHandle, advertiseAudio)
        NativeBridge.nativeSetPlist(nativeHandle, "maxFPS", maxFps)
        NativeBridge.nativeSetPlist(nativeHandle, "overscanned", if (overscanned) 1 else 0)
        if (audioLatencyMs >= 0) {
            NativeBridge.nativeSetPlist(nativeHandle, "audio_delay_micros", audioLatencyMs * 1000)
        }

        // set display params
        lastOrientation = resources.configuration.orientation
        val (w, h) = _displaySize()
        videoRenderer.setResolution(w, h)
        _videoResolution.value = "${w}x${h}"
        _videoAspect.value = w.toFloat() / h
        NativeBridge.nativeSetDisplaySize(nativeHandle, w, h, maxFps)

        val requestedPort = startupPort
        val port = NativeBridge.nativeStart(nativeHandle, requestedPort)
        if (port < 0) {
            log("Failed to start on port $requestedPort")
            _failStart()
            return
        }

        // Same native TXT/name/port contract; readiness waits for Android registration results.
        registeredPort = port
        _receiverState.update { it.copy(advertisedName = effectiveName) }
        registerCurrentServices()

        _serverState.value = ServerState.RUNNING
        _receiverState.update { it.copy(running = true, starting = false) }
        if (ensureServiceStarted) {
            ContextCompat.startForegroundService(this, Intent(this, AirPlayService::class.java))
        }
        promoteToForeground()
        log("Server started on port $port")
    }

    private fun _orientationFollowsDevice(): Boolean =
        prefs.getString(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION) == Prefs.AUTO

    private fun _displaySize(clamp: Boolean = true): Pair<Int, Int> {
        val res = prefs.getString(Prefs.RESOLUTION, Prefs.DEF_RESOLUTION)!!
        val portrait = when (res) {
            "portrait" -> true
            "landscape" -> false
            else -> resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        val (rawW, rawH) = realDisplaySize()
        val device = if (portrait != rawH >= rawW) rawH to rawW else rawW to rawH
        val profile = streamProfile()
        if (profile != io.github.jqssun.airplay.receiver.StreamProfile.CUSTOM) {
            val (pw, ph) = profile.size(device.first, device.second)
            if (!clamp) return pw to ph
            val (mw, mh) = videoRenderer.maxResolution()
            return pw.coerceAtMost(mw) to ph.coerceAtMost(mh)
        }
        if (res == "portrait" || res == "landscape") return device
        val (w, h) = if (res.contains("x")) {
            res.split("x").let { it[0].toInt() to it[1].toInt() }
        } else device
        if (!clamp) return w to h
        // strict decoders black-screen past their limits; advertised size is only upper bound for senders
        val (maxW, maxH) = videoRenderer.maxResolution()
        return w.coerceAtMost(maxW) to h.coerceAtMost(maxH)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == lastOrientation) return
        lastOrientation = newConfig.orientation
        if (nativeHandle == 0L || _serverState.value != ServerState.RUNNING) return
        if (!_orientationFollowsDevice()) return
        val (w, h) = _displaySize()
        NativeBridge.nativeSetDisplaySize(nativeHandle, w, h, effectiveMirrorFps())
        log("Advertising ${w}x${h} from next session")
    }

    private fun _failStart() {
        stopping = true
        callbackGeneration++
        discoveryGeneration++
        clearPin()
        mirrorToken = -1L
        val retiredHandle = nativeHandle
        nativeHandle = 0L
        enqueueNativeCleanup(retiredHandle, stopFirst = false)
        nsdManager?.release()
        nsdManager = null
        wakeLock?.release()
        wakeLock = null
        _serverState.value = ServerState.ERROR
        _receiverState.update { it.sessionEnded(ReceiverNotice.START_FAILED).copy(
            running = false, starting = false, airplay = Advertisement.IDLE, raop = Advertisement.IDLE) }
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    fun stopServer() {
        startRequests.cancel()
        startJob?.cancel()
        startJob = null
        stopping = true
        incomingPresentationRequested = false
        callbackGeneration++
        discoveryGeneration++
        mirrorToken = -1L
        clearPin()
        _mainHandler.removeCallbacksAndMessages(null)
        val retiredHandle = nativeHandle
        nativeHandle = 0L
        enqueueNativeCleanup(retiredHandle, stopFirst = true)
        dacpController?.reset()
        nsdManager?.release()
        nsdManager = null
        wakeLock?.release()
        wakeLock = null
        airPlayVideoPlayer.stop()
        mediaSession?.isActive = false
        _audioOnly.value = false
        _videoPlaybackActive.value = false
        _mirroringActive.value = false
        _videoPlaybackInfo.value = VideoPlaybackInfo()
        _lastVideoPollAt = 0
        _videoPollSuppressed = false
        _coverArtBytes = null
        _trackInfo.value = TrackInfo()
        _positionMs.value = 0
        _durationMs.value = 0
        _serverState.value = ServerState.STOPPED
        _connectionCount.value = 0
        _receiverState.update { it.sessionEnded(ReceiverNotice.NONE).copy(
            running = false, starting = false, airplay = Advertisement.IDLE,
            raop = Advertisement.IDLE, discoveryErrorCode = null, portConflict = null,
            pendingSettings = pendingSettings.hasPending()) }
        dacpPlayer.refresh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
        log("Server stopped")
    }

    /** Transfers sole destruction ownership immediately; no blocking joins on the UI thread. */
    private fun enqueueNativeCleanup(retiredHandle: Long, stopFirst: Boolean) {
        val retiredSurface = desiredVideoSurface
        val retiredGeneration = callbackGeneration
        val cleanup = synchronized(nativeCleanupLock) {
            val previous = nativeCleanupTail
            nativeCleanupScope.async(start = CoroutineStart.LAZY) {
                previous?.await()
                audioRenderer.detachEngine()
                try {
                    if (retiredHandle != 0L) {
                        try {
                            if (stopFirst) NativeBridge.nativeStop(retiredHandle)
                        } finally {
                            NativeBridge.nativeDestroy(retiredHandle)
                        }
                    }
                } finally {
                    videoRenderer.release()
                    retiredSurface?.let(videoRenderer::clearSurface)
                }
                // Old callbacks have now joined. Clear any already-enqueued player work
                // before a new start is allowed to publish/attach another receiver.
                withContext(Dispatchers.Main) {
                    if (stopping && retiredGeneration == callbackGeneration) {
                        airPlayVideoPlayer.stop()
                        mirrorToken = -1L
                        _connectionCount.value = 0
                        _audioOnly.value = false
                        _mirroringActive.value = false
                        _videoPlaybackActive.value = false
                        _lastVideoPollAt = 0L
                        clearPin()
                        _receiverState.update { it.sessionEnded(it.notice) }
                    }
                }
            }.also { nativeCleanupTail = it }
        }
        cleanup.invokeOnCompletion { failure ->
            if (failure == null) synchronized(nativeCleanupLock) {
                // A completed lazy coroutine retains its captured service, surface and
                // predecessor. Keep only pending cleanup, never a process-wide history.
                if (nativeCleanupTail === cleanup) nativeCleanupTail = null
            }
        }
        cleanup.start()
    }

    /** Handler work queued by a retired native receiver cannot affect its replacement. */
    private fun postForReceiver(action: () -> Unit) {
        val generation = callbackGeneration
        _mainHandler.post { if (!stopping && generation == callbackGeneration) action() }
    }

    fun setVideoSurface(surface: Surface) {
        desiredVideoSurface = surface
        if (!stopping) videoRenderer.setSurface(surface)
    }

    fun clearVideoSurface(surface: Surface) {
        if (desiredVideoSurface === surface) desiredVideoSurface = null
        if (!stopping) videoRenderer.clearSurface(surface)
    }

    fun setVideoPlaybackSurface(surface: Surface) {
        airPlayVideoPlayer.setSurface(surface)
    }

    fun clearVideoPlaybackSurface(surface: Surface) {
        airPlayVideoPlayer.clearSurface(surface)
    }

    fun setVideoPlaying(playing: Boolean) {
        airPlayVideoPlayer.setPlaying(playing)
    }

    fun seekVideoTo(positionMs: Long) {
        airPlayVideoPlayer.scrub(positionMs / 1000f)
    }

    fun setVideoScrubbing(enabled: Boolean) {
        airPlayVideoPlayer.setScrubbing(enabled)
    }

    fun setVideoSpeed(speed: Float) {
        airPlayVideoPlayer.setSpeed(speed)
    }

    fun setVideoSkipSilence(enabled: Boolean) {
        airPlayVideoPlayer.setSkipSilence(enabled)
    }

    fun seekVideoBy(deltaMs: Long) {
        airPlayVideoPlayer.seekBy(deltaMs)
    }

    /**
     * Ending a stream returns the receiver to direct playback so the next connection is direct
     * again. Within a stream the choice stays one-way: a live mirror session is never switched
     * back, because the sender fixes the mode when it negotiates the session.
     */
    private fun resetPlaybackModeToDirect(reason: String) {
        val alreadyDirect = prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)
        if (!alreadyDirect) {
            prefs.edit().putBoolean(Prefs.ADVERTISE_VIDEO, true).apply()
            if (nativeHandle != 0L && !stopping) {
                NativeBridge.nativeSetHlsEnabled(nativeHandle, true)
                refreshDiscoveryRegistration()
            }
            log("Direct playback restored after $reason")
        }
        _receiverState.update { if (it.directPlayback) it else it.copy(directPlayback = true) }
    }

    fun setRemoteVideoPlaybackEnabled(enabled: Boolean): Boolean {
        if (_receiverState.value.starting) return false
        prefs.edit().putBoolean(Prefs.ADVERTISE_VIDEO, enabled).apply()
        _receiverState.update { it.copy(directPlayback = enabled) }
        if (nativeHandle != 0L && !stopping) {
            NativeBridge.nativeSetHlsEnabled(nativeHandle, enabled)
            if (!enabled) {
                val state = _receiverState.value
                if (_videoPlaybackActive.value || state.remotePreparing || !state.firstFrameSubmitted) {
                    _endVideoPlayback("Remote playback disabled by user", errorCode = 5003)
                } else {
                    // Changing a preference while already mirroring is not a
                    // failed video transition. Keep the confirmed screen visible.
                    _receiverState.update { it.copy(mediaErrorCode = null) }
                    log("Screen mirroring selected; existing screen preserved")
                }
            }
            else _receiverState.update { if (it.mediaErrorCode == 5003) it.copy(mediaErrorCode = null) else it }
            refreshDiscoveryRegistration()
        }
        return true
    }

    private val trustedDevices by lazy {
        io.github.jqssun.airplay.receiver.TrustedDevices(getSharedPreferences("trusted_airplay_devices", Context.MODE_PRIVATE))
    }
    override fun onRegisterClient(publicKey: String, name: String): Boolean = trustedDevices.register(publicKey, name)
    override fun onClientAllowed(publicKey: String): Boolean =
        io.github.jqssun.airplay.receiver.TrustedDevices.validKey(publicKey) && !trustedDevices.isBlocked(publicKey)
    override fun onCheckRegisteredClient(publicKey: String): Boolean = trustedDevices.contains(publicKey)

    fun stopVideoPlayback() = _endVideoPlayback("AirPlay Video stopped (local)")

    /** Ends only the currently presented stream. Discovery and the receiver listener stay alive. */
    @Volatile private var locallyEnded = false

    fun endCurrentStream() {
        if (stopping || nativeHandle == 0L) return
        locallyEnded = true
        cancelledMedia = true
        senderMedia = null
        _videoPollSuppressed = true
        NativeBridge.nativeEndCurrentStream(nativeHandle)
        _endVideoPlayback("Stream ended locally")
        mirrorToken = -1L
        videoRenderer.stopSession()
        audioRenderer.stop()
        _mirroringActive.value = false
        _audioOnly.value = false
        _receiverState.update { state ->
            state.sessionEnded(ReceiverNotice.NONE).copy(running = true, starting = false,
                airplay = state.airplay, raop = state.raop, connections = _connectionCount.value)
        }
        mediaSession?.isActive = false
        resetPlaybackModeToDirect("stream ended locally")
        log("Current stream ended; receiver remains available")
    }

    fun downloadVideo() {
        _videoLocation.value?.let { videoDownloader.start(it) }
    }

    private fun _endVideoPlayback(message: String, errorCode: Int? = null) {
        // A late sender /stop after an error or a mode change must not erase the
        // recovery notice or invalidate a screen frame that has since arrived.
        if (!_videoPlaybackActive.value && !_receiverState.value.remotePreparing && errorCode == null) return
        cancelledMedia = true
        lastRemoteProgressAt = 0L
        senderMedia = null
        // lingering polls after a stop must not bounce the UI back to a pending session
        _videoPollSuppressed = true
        _videoPlaybackActive.value = false
        _receiverState.update { it.copy(mediaPlaying = false, mediaPending = false, remotePreparing = false,
            playbackFailureId = if (errorCode != null && errorCode !in 5001..5003) android.os.SystemClock.elapsedRealtimeNanos() else it.playbackFailureId,
            mediaErrorCode = errorCode, mirrorRequested = it.screenSession, firstFrameSubmitted = false) }
        if (mirrorToken >= 0) videoRenderer.awaitFreshSubmission()
        airPlayVideoPlayer.stop()
        recordingSourceExecutor.execute {
            io.github.jqssun.airplay.recording.CleanRecording.select("mirror")
        }
        if (!_audioOnly.value) mediaSession?.isActive = false
        if (!stopping && _receiverState.value.screenSession && _connectionCount.value > 0) audioRenderer.start()
        log(message)
    }

    override fun onDestroy() {
        unregisterReceiver(discoveryWakeReceiver)
        networkMonitor?.close()
        networkMonitor = null
        videoRenderer.onFirstFrameSubmitted = null
        videoRenderer.onPresentationFailure = null
        stopServer()
        dacpPlayer.release()
        mediaReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        mediaReceiver = null
        volumeReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        volumeReceiver = null
        dacpController?.release()
        dacpController = null
        recordingSourceExecutor.shutdownNow()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // RaopCallbackHandler (called from native threads)

    private val lastTransportDiagnosticAt = AtomicLong()

    override fun onVideoData(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {
        if (stopping) return
        val arrivedAt = SystemClock.elapsedRealtime()
        val clockAgeMs = System.currentTimeMillis() - ntpTimeNs / 1_000_000L
        videoRenderer.feedFrame(data, ntpTimeNs, isH265)
        val previous = lastTransportDiagnosticAt.get()
        if (arrivedAt - previous >= 5_000 && lastTransportDiagnosticAt.compareAndSet(previous, arrivedAt)) {
            val audio = audioRenderer.audioDebug()
            log("Transport health: mode=${if (_videoPlaybackActive.value) "remote" else "mirror"} " +
                "videoFeedMs=${SystemClock.elapsedRealtime() - arrivedAt} " +
                "videoClockAgeMs=${clockAgeMs.takeIf { it in -10_000L..60_000L } ?: -1L} " +
                "fps=${videoRenderer.fps} lateOutputs=${videoRenderer.lateOutputFrames} " +
                "decodedFrames=${videoRenderer.decodedFrames} surfaceFrames=${videoRenderer.submittedFrames} " +
                "outputAgeMs=${videoRenderer.outputAgeMs} surfaceAgeMs=${videoRenderer.surfaceAgeMs} " +
                "decoderResidenceMs=${videoRenderer.decoderResidenceMs} coalescedOutputs=${videoRenderer.coalescedOutputs} " +
                "videoPtsZero=${ntpTimeNs == 0L} clockMappingValid=${ntpTimeNs > 0 && clockAgeMs in -10_000L..60_000L} " +
                "audioBacklogMs=${audio?.backlogMs} audioUnderruns=${audio?.underruns} " +
                "audioXruns=${audio?.xrun} audioDecodeErrors=${audio?.decodeErrors}")
        }
    }

    override fun onVideoSessionPoll() {
        if (locallyEnded || stopping || !prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)) return
        _lastVideoPollAt = SystemClock.elapsedRealtime()
        _receiverState.update { if (stopping) it else it.copy(mediaPending = videoSessionPending()) }
        _mainHandler.removeCallbacks(pendingVideoExpiry)
        _mainHandler.postDelayed(pendingVideoExpiry, VIDEO_POLL_PENDING_TIMEOUT_MS + 50)
    }

    private data class SenderMedia(val session: String, val item: String,
        val selection: io.github.jqssun.airplay.renderer.AudioSelection? = null)
    @Volatile private var senderMedia: SenderMedia? = null
    @Volatile private var cancelledMedia = false

    override fun onVideoPrepare(session: String, item: String) {
        if (locallyEnded || stopping || !prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)) return
        cancelledMedia = false
        senderMedia = SenderMedia(session, item)
        _videoPlaySeq.value++
        airPlayVideoPlayer.stop()
        _videoPlaybackActive.value = false
        _videoPlaybackInfo.value = VideoPlaybackInfo(mediaId = _videoPlaySeq.value.toLong(), buffering = true)
        _receiverState.update { it.copy(presentationHeld = true, mediaPending = true, remotePreparing = true,
            mediaPlaying = false, mediaErrorCode = null) }
        val generation = _videoPlaySeq.value
        val receiverGeneration = callbackGeneration
        _mainHandler.postDelayed({
            if (!stopping && !cancelledMedia && receiverGeneration == callbackGeneration && _videoPlaySeq.value == generation && !_videoPlaybackActive.value) {
                cancelledMedia = true
                senderMedia = null
                _receiverState.update { it.copy(mediaPending = false, remotePreparing = false, mediaErrorCode = 408) }
                log("Remote media preparation timed out: generation=$generation")
            }
        }, 30_000)
        log("Remote media preparing: generation=$generation")
    }

    override fun onVideoSelection(session: String, item: String, json: String) {
        if (stopping) return
        val identity = senderMedia ?: return
        if (identity.session != session || identity.item != item) {
            log("Ignored stale media selection")
            return
        }
        val selection = io.github.jqssun.airplay.renderer.AudioSelection.fromJson(json)
        if (selection == null) {
            log("Sender audio selection could not be resolved; no track changed")
            return
        }
        senderMedia = identity.copy(selection = selection)
        if (_videoPlaybackActive.value) airPlayVideoPlayer.selectAudio(selection)
    }

    override fun onVideoPlay(location: String, startPositionSeconds: Float) {
        if (locallyEnded || stopping || cancelledMedia || !prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)) return
        _videoLocation.value = location
        _videoPlaySeq.value++
        _videoPollSuppressed = false
        val startMs = io.github.jqssun.airplay.renderer.PlaybackStartPosition.milliseconds(startPositionSeconds)
        _videoPlaybackInfo.value = VideoPlaybackInfo(positionMs = startMs ?: 0L)
        _videoPlaybackAspect.value = 16f / 9f
        _videoPlaybackSize.value = null
        _videoTitle.value = ""
        recordingSourceExecutor.execute {
            io.github.jqssun.airplay.recording.CleanRecording.select("direct")
        }
        _videoPlaybackActive.value = true
        // Remote video owns audio output; don't play the still-connected screen
        // audio simultaneously. Native decoding survives this output-only pause.
        audioRenderer.pauseOutput()
        clearPin()
        _receiverState.update { if (stopping) it else it.copy(mediaPlaying = true, mediaPending = false, remotePreparing = false, presentationHeld = true, mediaErrorCode = null,
            notice = ReceiverNotice.NONE) }
        airPlayVideoPlayer.streamProfile = streamProfile()
        airPlayVideoPlayer.maxVideoBitrateBps = prefs.getInt(Prefs.REMOTE_VIDEO_MAX_BITRATE, Prefs.DEF_REMOTE_VIDEO_MAX_BITRATE)
        airPlayVideoPlayer.play(location, if (startMs == null) 0f else startPositionSeconds, _videoPlaySeq.value.toLong())
        senderMedia?.selection?.let { airPlayVideoPlayer.selectAudio(it) }
        // claim media-button routing for keys that arrive as media-session events
        mediaSession?.isActive = true
        requestIncomingPresentation()
        log("AirPlay Video play: media=${_videoPlaySeq.value} start=${startPositionSeconds}s")
    }

    override fun onVideoScrub(positionSeconds: Float) {
        if (stopping) return
        airPlayVideoPlayer.scrub(positionSeconds)
    }

    override fun onVideoRate(rate: Float) {
        if (stopping) return
        airPlayVideoPlayer.setRate(rate)
    }

    override fun onVideoStop() { if (!stopping) _endVideoPlayback("AirPlay Video stopped") }

    override fun onAudioFormat(ct: Int, spf: Int, usingScreen: Boolean) {
        if (locallyEnded || stopping) return
        clearPin()
        if (!_videoPlaybackActive.value) audioRenderer.start()
        audioRenderer.setFormat(ct, spf)
        if (stopping) return
        _receiverState.update { if (stopping) it else it.observeAudioFormat(usingScreen) }
        if (!usingScreen) _setPlaying(true)
        _setAudioOnly(!_receiverState.value.screenSession)
        if (!usingScreen) requestIncomingPresentation()
        log("Audio format: ct=$ct spf=$spf screen=$usingScreen")
    }

    override fun onVideoSize(srcW: Float, srcH: Float, w: Float, h: Float) {
        if (locallyEnded || stopping) return
        clearPin()
        if (w > 0f && h > 0f) {
            // Sender-controlled geometry: keep it inside the range the decoder and the
            // format hint can actually handle instead of trusting the wire value.
            val cw = w.toInt().coerceIn(MIN_VIDEO_DIM, MAX_VIDEO_DIM)
            val ch = h.toInt().coerceIn(MIN_VIDEO_DIM, MAX_VIDEO_DIM)
            if (mirrorToken < 0) onMirrorRunning(true)
            _videoAspect.value = cw.toFloat() / ch
            _videoResolution.value = "${cw}x${ch}"
            videoRenderer.setResolution(cw, ch)
            _mirroringActive.value = true
            _receiverState.update { if (stopping) it else it.copy(mirrorRequested = true, screenSession = true, presentationHeld = true, mediaErrorCode = null, mediaPending = false,
                audioConnected = false, notice = ReceiverNotice.NONE) }
        }
        log("Video size: ${srcW}x${srcH} -> ${w}x${h}")
    }

    override fun onVolumeChange(volume: Float) {
        if (stopping) return
        val frac = if (volume <= -144f) 0f else ((volume + 30f) / 30f).coerceIn(0f, 1f)
        _senderFrac = frac
        Log.d(TAG, "volume ${volume}dB, frac $frac")
        postForReceiver {
            if (_volSyncTarget >= 0f) {
                _volSyncStep()
                return@postForReceiver
            }
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val idx = (frac * max).roundToInt()
            _preZeroIdx = if (idx == 0) (if (_preZeroIdx < 0) cur else _preZeroIdx) else -1
            if (idx != cur) {
                _pendingVolEchoes++
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0)
            }
        }
    }

    override fun onClientVolume(): Float {
        if (stopping) return 0f
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val db = if (vol == 0) -144f else -30f + 30f * vol / max
        Log.d(TAG, "client volume query: $vol/$max -> ${db}dB")
        return db
    }

    private fun _volSyncStep() {
        _mainHandler.removeCallbacks(_volSyncTimeout)
        val target = _volSyncTarget
        if (target < 0f) return
        val frac = _senderFrac
        val want = when {
            frac < 0f -> _volSyncHint
            abs(target - frac) <= VOL_SYNC_EPS -> 0
            target > frac -> 1
            else -> -1
        }
        if (want == 0 || (_volSyncDir != 0 && want != _volSyncDir) || _volSyncSteps >= VOL_SYNC_MAX_STEPS) {
            _volSyncEnd()
            return
        }
        _volSyncDir = want
        _volSyncSteps++
        if (want > 0) dacpController?.volumeUp() else dacpController?.volumeDown()
        _mainHandler.postDelayed(_volSyncTimeout, VOL_SYNC_TIMEOUT_MS)
    }

    private fun _volSyncEnd() {
        _mainHandler.removeCallbacks(_volSyncTimeout)
        if (_volSyncTarget >= 0f) Log.d(TAG, "volume sync done: sender $_senderFrac, target $_volSyncTarget")
        _volSyncTarget = -1f
        _volSyncDir = 0
    }

    override fun onConnectionInit() {
        if (stopping) return
        // Native closes old RTP workers before accepting the next connection.
        if (_connectionCount.value == 0) locallyEnded = false
        _mainHandler.removeCallbacks(finishDisconnectedSession)
        _connectionCount.update { if (stopping) 0 else it + 1 }
        if (!stopping) _receiverState.update { if (stopping) it else it.copy(connections = _connectionCount.value,
            notice = ReceiverNotice.NONE) }
        log("Client connected (${_connectionCount.value})")
        // TCP discovery/probes are not a request to display. Wait for PIN or media setup.
    }

    @Volatile private var lastRemoteProgressAt = 0L
    private val finishDisconnectedSession = object : Runnable {
        override fun run() {
            if (stopping || _connectionCount.value > 0) return
            val now = SystemClock.elapsedRealtime()
            val recentPoll = now - _lastVideoPollAt < 5_000
            val progressing = lastRemoteProgressAt > 0 && now - lastRemoteProgressAt < 15_000
            if (mirrorToken >= 0 || (_videoPlaybackActive.value && (recentPoll || progressing))) {
                _mainHandler.postDelayed(this, 2_000)
                return
            }
            finishDisconnectedSessionNow()
        }
    }

    override fun onConnectionDestroy() {
        if (stopping) return
        _connectionCount.update { (it - 1).coerceAtLeast(0) }
        _receiverState.update { if (stopping) it else it.copy(connections = _connectionCount.value) }
        if (_connectionCount.value == 0) {
            _mainHandler.removeCallbacks(finishDisconnectedSession)
            _mainHandler.postDelayed(finishDisconnectedSession, 2_000)
        }
        log("Client disconnected (${_connectionCount.value}); transport grace pending")
    }

    private fun streamProfile() = io.github.jqssun.airplay.receiver.StreamProfile.from(
        prefs.getString(Prefs.STREAM_PROFILE, Prefs.DEF_STREAM_PROFILE))

    private fun effectiveMirrorFps(): Int {
        val requested = prefs.getInt(Prefs.MAX_FPS, Prefs.DEF_MAX_FPS)
        return streamProfile().frameLimit(requested)
    }

    private fun finishDisconnectedSessionNow() {
        if (stopping || _connectionCount.value > 0) return
        incomingPresentationRequested = false
        // The last control connection is gone: the local "stream ended" latch must not
        // survive it, or the next sender would be silently ignored until a /stop arrives.
        locallyEnded = false
        // iOS closes the PIN request's TCP connection while showing its code-entry
            // prompt. The challenge belongs to pairing, not to this control socket.
            // Keep its existing expiry; media setup, explicit stop/cancel or expiry clears it.
            mirrorToken = -1L
            _mirroringActive.value = false
            _receiverState.update { state ->
                if (stopping) state else state.sessionEnded(
                    if (state.pin != null) ReceiverNotice.NONE else ReceiverNotice.CONNECTION_LOST
                ).copy(pin = state.pin)
            }
            // clients may drop without POST /stop; must run before the poll-state reset
            _endVideoPlayback("AirPlay Video stopped (disconnect)")
            // The stream is over: the next connection starts from direct playback again.
            resetPlaybackModeToDirect("last client disconnected")
            // last client gone: release audio output devices to save power
            audioRenderer.stop()
            _audioOnly.value = false
            _lastVideoPollAt = 0
            _videoPollSuppressed = false
            _coverArtBytes = null
            _trackInfo.value = TrackInfo()
            _positionMs.value = 0
            _durationMs.value = 0
            dacpController?.reset()
            _senderFrac = -1f
            postForReceiver {
                _volSyncEnd()
                if (_preZeroIdx >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, _preZeroIdx, 0)
                    _preZeroIdx = -1
                }
            }
            mediaSession?.isActive = false
            _refreshDacpPlayer()
            _updateMediaNotification()
    }

    override fun onConnectionReset(reason: Int) {
        if (stopping) return
        // raop.h: RESET_TYPE_HLS_CONN_CLOSED. The sender abandoned a /play connection
        // without a stop; that is an informational event, not a transport reset, so the
        // confirmed screen state and any existing notice are left untouched.
        if (reason == RESET_TYPE_HLS_CONN_CLOSED) {
            log("Abandoned media play connection (reset $reason)")
            return
        }
        if (reason == 7) { // RESET_TYPE_SCREEN_ONLY_REQUEST; transport remains intact.
            if (!prefs.getBoolean(Prefs.ADVERTISE_VIDEO, Prefs.DEF_ADVERTISE_VIDEO)) {
                _receiverState.update { it.copy(presentationHeld = true, mediaErrorCode = 5003, firstFrameSubmitted = false) }
                if (mirrorToken >= 0) videoRenderer.awaitFreshSubmission()
                log("Remote play request declined: screen mirroring selected; transport preserved")
            }
            return
        }
        // mirror_video_running is authoritative for RTP lifetime; a control reset isn't.
        _receiverState.update {
            if (stopping) it else if (reason == 1 && mirrorToken < 0 && it.screenSession &&
                !it.mediaPlaying && !it.remotePreparing) {
                it.copy(notice = ReceiverNotice.CONNECTION_RESET, mediaErrorCode = 5002,
                    firstFrameSubmitted = false)
            } else it.copy(notice = ReceiverNotice.CONNECTION_RESET)
        }
        log("Connection reset: $reason")
    }

    override fun onDisplayPin(pin: String) {
        if (stopping) return
        // a new pin is the sync point with the client prompt: show every new value immediately
        if (_lastPin == pin) return
        _lastPin = pin
        _receiverState.update { if (stopping) it else it.copy(pin = pin, notice = ReceiverNotice.NONE) }
        pinCallback?.invoke(pin)
        postForReceiver {
            pinExpiry?.let { _mainHandler.removeCallbacks(it) }
            if (_lastPin != pin || stopping) return@postForReceiver
            pinExpiry = Runnable {
                if (_lastPin == pin && !stopping) cancelPairing(expired = true)
            }.also { _mainHandler.postDelayed(it, PAIRING_UI_TIMEOUT_MS) }
        }
        _updateMediaNotification()
        requestIncomingPresentation()
    }

    override fun onMetadata(data: ByteArray) {
        if (stopping) return
        val map = DmapParser.parse(data)
        val info = TrackInfo.fromDmap(map, _trackInfo.value.coverArt)
        _trackInfo.value = info
        if (info.durationMs > 0) _durationMs.value = info.durationMs
        _updateMediaMetadata()
        _refreshDacpPlayer()
        log("Track: ${info.artist} - ${info.title}")
    }

    override fun onCoverArt(data: ByteArray) {
        if (stopping) return
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return
        _coverArtBytes = data
        _trackInfo.value = _trackInfo.value.copy(coverArt = bmp)
        _updateMediaMetadata()
        _refreshDacpPlayer()
    }

    override fun onProgress(start: Long, curr: Long, end: Long) {
        if (stopping) return
        val rate = 44100.0
        val posMs = ((curr - start) / rate * 1000).toLong().coerceAtLeast(0)
        val durMs = ((end - start) / rate * 1000).toLong().coerceAtLeast(0)
        // pause/resume transitions emit degenerate progress; keep the last good value
        if (durMs <= 0) return
        _positionMs.value = posMs
        _durationMs.value = durMs
        _progressBaseMs = posMs
        _progressBaseTime = SystemClock.elapsedRealtime()
        _playing.value = true
        _updatePlaybackState()
        _refreshDacpPlayer()
    }

    override fun onAudioTeardown() {
        if (stopping) return
        _setPlaying(false)
    }

    override fun onDacpId(dacpId: String, activeRemote: String) {
        if (stopping) return
        dacpController?.update(dacpId, activeRemote)
        log("DACP: $dacpId")
    }

    override fun onMirrorRunning(running: Boolean) {
        if (locallyEnded || stopping) return
        if (running) {
            // Size/config can arrive before the running notification; don't restart that decoder.
            if (mirrorToken >= 0) return
            // iOS resumes screen mirroring on a new RTP channel without always
            // sending /stop for the paused remote video (e.g. Shorts -> Safari).
            // A duplicate running notification for the existing channel is ignored above.
            if (_videoPlaybackActive.value || _receiverState.value.remotePreparing)
                _endVideoPlayback("Remote video released for new mirror transport")
            mirrorToken = mirrorTokens.incrementAndGet()
            _receiverState.update { if (stopping) it else it.observeMirrorStart(mirrorToken) }
            videoRenderer.startSession(mirrorToken)
            requestIncomingPresentation()
        } else {
            mirrorToken = -1L
            videoRenderer.stopSession()
            _mirroringActive.value = false
            _receiverState.update { if (stopping) it else it.observeMirrorEnd() }
        }
        // A mirror socket closing is not evidence of a music session. Screen
        // audio can remain connected while its video channel is recovering.
        _setAudioOnly(!running && !_receiverState.value.screenSession && _receiverState.value.audioConnected)
    }

    private fun _setAudioOnly(audioOnly: Boolean) {
        if (stopping) return
        val prev = _audioOnly.value
        _audioOnly.value = audioOnly
        _receiverState.update { if (stopping) it else it.copy(audioConnected = audioOnly && !it.screenSession && _connectionCount.value > 0) }
        _refreshDacpPlayer()
        if (audioOnly && !prev) {
            mediaSession?.isActive = true
            log("Audio mode")
        } else if (!audioOnly && prev) {
            mediaSession?.isActive = false
            _coverArtBytes = null
            _trackInfo.value = TrackInfo()
            _positionMs.value = 0
            _durationMs.value = 0
            _updateMediaNotification()
            log("Mirror mode")
        }
    }

    private fun _updateMediaMetadata() {
        val info = _trackInfo.value
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, info.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, _durationMs.value)
        info.coverArt?.let { builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
        mediaSession?.setMetadata(builder.build())
        _updateMediaNotification()
    }

    fun togglePlayPause() {
        val nowPlaying = !_playing.value
        _setPlaying(nowPlaying)
        dacpController?.let { if (nowPlaying) it.play() else it.pause() }
    }

    private fun _setPlaying(playing: Boolean) {
        _playing.value = playing
        _refreshDacpPlayer()
        if (playing) {
            // resume extrapolation from current position
            _progressBaseMs = _positionMs.value
            _progressBaseTime = SystemClock.elapsedRealtime()
        } else {
            // freeze position
            _positionMs.value = currentPositionMs()
            _progressBaseTime = 0
        }
        _updatePlaybackState()
    }

    private fun _updatePlaybackState() {
        // the sender's silent raop audio session must not overwrite video session state
        if (_videoPlaybackActive.value) return
        val isPlaying = _playing.value
        val pbState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val speed = if (isPlaying) 1f else 0f
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(pbState, _positionMs.value, speed, SystemClock.elapsedRealtime())
            .build()
        mediaSession?.setPlaybackState(state)
        _updateMediaNotification()
    }

    // consumers extrapolate position from (position, speed, updateTime): push only discontinuities
    private fun _updateVideoPlaybackState(positionSeconds: Float, rate: Float) {
        val playing = rate > 0f
        val posMs = (positionSeconds * 1000).toLong()
        val now = SystemClock.elapsedRealtime()
        val expectedMs = _lastVideoStatePosMs +
            if (_lastVideoStateRate > 0f) ((now - _lastVideoStateAtMs) * _lastVideoStateRate).toLong() else 0L
        if (rate == _lastVideoStateRate && abs(posMs - expectedMs) < 1000) return
        _lastVideoStateRate = rate
        _lastVideoStatePosMs = posMs
        _lastVideoStateAtMs = now
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                posMs,
                rate,
                now
            )
            .build()
        mediaSession?.setPlaybackState(state)
    }

    private var _lastVideoStateRate = -1f
    private var _lastVideoStatePosMs = 0L
    private var _lastVideoStateAtMs = 0L

    private fun _refreshDacpPlayer() {
        postForReceiver { dacpPlayer.refresh() }
    }

    private fun clearPin() {
        _lastPin = null
        _receiverState.update { it.copy(pin = null) }
        pinExpiry?.let { _mainHandler.removeCallbacks(it) }
        pinExpiry = null
        pinCallback?.invoke(null)
        _updateMediaNotification()
    }

    fun collectDebugInfo() = DebugInfo(
        videoCodec = videoRenderer.codecName,
        videoRes = _videoResolution.value,
        videoFps = videoRenderer.fps,
        videoBitrate = videoRenderer.bitrateBps,
        videoFrames = videoRenderer.frameCount,
        droppedFrames = videoRenderer.droppedFrames,
        framePacingJitterUs = videoRenderer.framePacingJitterUs,
        audioCodec = audioRenderer.codecLabel,
        audioVolume = 100 * audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        audio = audioRenderer.audioDebug(),
        connections = _connectionCount.value,
    )

    // helpers

    private fun getHwAddr(): ByteArray {
        macFromString(prefs.getString("receiver_identity_mac", null))
            ?.takeIf { isUsableMac(it) }?.let { return it }
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                if (iface.name.startsWith("wlan") || iface.name.startsWith("eth")) {
                    val mac = iface.hardwareAddress
                    if (isUsableMac(mac)) {
                        check(prefs.edit().putString("receiver_identity_mac", macToString(mac!!)).commit())
                        return mac
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get hardware address", e)
        }

        // fall back to stable per-install random address
        val identity = persistedRandomMac() ?: error("Cannot create receiver identity")
        check(prefs.edit().putString("receiver_identity_mac", macToString(identity)).commit())
        return identity
    }

    private fun isUsableMac(mac: ByteArray?): Boolean =
        mac != null && mac.size == 6 &&
            mac.any { it != 0.toByte() } &&
            !(mac[0] == 0x02.toByte() && mac.drop(1).all { it == 0.toByte() })

    private fun persistedRandomMac(): ByteArray? {
        macFromString(prefs.getString(Prefs.FALLBACK_MAC_ADDRESS, null))
            ?.takeIf { isUsableMac(it) }?.let { return it }
        repeat(10) {
            val mac = randomAaiMac()
            if (isUsableMac(mac)) {
                prefs.edit().putString(Prefs.FALLBACK_MAC_ADDRESS, macToString(mac)).apply()
                return mac
            }
        }
        return null
    }

    // random locally-administered unicast MAC in AAI SLAP quadrant
    private fun randomAaiMac(): ByteArray {
        val mac = ByteArray(6).also { SecureRandom().nextBytes(it) }
        mac[0] = ((mac[0].toInt() and 0xF0) or 0x0A).toByte()
        return mac
    }

    private fun macToString(mac: ByteArray): String = mac.joinToString(":") { "%02x".format(it) }

    private fun macFromString(s: String?): ByteArray? {
        if (s == null) return null
        return try {
            s.split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return _buildMediaNotification()
    }

    private fun promoteToForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        foregroundStarted = true
    }

    private fun shouldLaunchOnConnect(): Boolean {
        return prefs.getBoolean(Prefs.LAUNCH_ON_CONNECT, Prefs.DEF_LAUNCH_ON_CONNECT)
    }

    private fun _buildMediaNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val info = _trackInfo.value
        val isAudio = _audioOnly.value && info.title.isNotEmpty()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Brand silhouette instead of a framework glyph: android.R.drawable contents are not
            // stable across OEM ROMs and are not our mark.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)

        if (isAudio) {
            builder.setContentTitle(info.title).setContentText(info.artist).setSubText(info.album)
            info.coverArt?.let { builder.setLargeIcon(it) }
            mediaSession?.sessionToken?.let { token ->
                builder.setStyle(
                    MediaNotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                // transport action buttons
                builder.addAction(android.R.drawable.ic_media_previous, io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(androidx.media3.ui.R.string.exo_controls_previous_description), _mediaAction(ACTION_PREV))
                builder.addAction(android.R.drawable.ic_media_pause, io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.cd_play_pause), _mediaAction(ACTION_PLAY_PAUSE))
                builder.addAction(android.R.drawable.ic_media_next, io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(androidx.media3.ui.R.string.exo_controls_next_description), _mediaAction(ACTION_NEXT))
            }
        } else {
            if (_lastPin != null) {
                // The PIN itself is never placed in a notification.
                builder.setContentTitle(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_pin_title))
                    .setContentText(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_pin_text))
            } else if (_receiverState.value.presentationHeld && !ReceiverPresentation.isVisible()) {
                builder.setContentTitle(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_incoming_title))
                    .setContentText(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_incoming_text))
            } else {
                builder.setContentTitle(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_title))
                    .setContentText(io.github.jqssun.airplay.receiver.AppLanguages.context(this).getString(R.string.notification_text))
            }
        }
        return builder.build()
    }

    private fun requestIncomingPresentation() {
        postForReceiver {
            if (incomingPresentationRequested || !shouldLaunchOnConnect()) return@postForReceiver
            incomingPresentationRequested = true
            _updateMediaNotification()
            if (ReceiverPresentation.isVisible()) return@postForReceiver
            if (!ReceiverPresentation.canOpenActivity(this)) {
                log("Incoming stream awaits user opening receiver: automatic display permission unavailable")
                return@postForReceiver
            }
            val launchIntent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try {
                startActivity(launchIntent)
            } catch (e: RuntimeException) {
                log("TV declined automatic display: ${e.javaClass.simpleName}; notification remains available")
            }
        }
    }

    private fun _mediaAction(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun _updateMediaNotification() {
        if (_serverState.value != ServerState.RUNNING) return
        val notification = _buildMediaNotification()
        // Media-session notifications are exempt; ordinary receiver notifications are not.
        val mediaSessionNotification = notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (Build.VERSION.SDK_INT >= 33 && !mediaSessionNotification &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun refreshPresentationLanguage() {
        // Presentation-only refresh: no listener, connection or codec restart.
        createNotificationChannel()
        _updateMediaNotification()
    }

    enum class ServerState {
        STOPPED,
        RUNNING,
        ERROR
    }

    companion object {
        // Process scope owns retired native pointers even if LifecycleService is destroyed.
        // All producers enqueue on Android's main thread; the lock also publishes the tail.
        private val nativeCleanupLock = Any()
        private val nativeCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var nativeCleanupTail: Deferred<Unit>? = null
        private const val TAG = "AirPlayService"
        private const val VOL_SYNC_EPS = 0.033f
        private const val VOL_SYNC_MAX_STEPS = 32
        private const val VOL_SYNC_TIMEOUT_MS = 800L
        private const val CHANNEL_ID = "airplay_service"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY_PAUSE = "io.github.jqssun.airplay.PLAY_PAUSE"
        const val ACTION_NEXT = "io.github.jqssun.airplay.NEXT"
        const val ACTION_PREV = "io.github.jqssun.airplay.PREV"
        const val ACTION_START_SERVER = "io.github.jqssun.airplay.START_SERVER"
        // shared with dpad/double-tap seeks
        const val VIDEO_SEEK_STEP_MS = 10_000L
        const val VIDEO_POLL_PENDING_TIMEOUT_MS = 3_000L

        // Product safety lease, not a change to the native pairing protocol or its handshake.
        private const val PAIRING_UI_TIMEOUT_MS = 120_000L
        private const val AUDIO_CONFIG_DEBOUNCE_MS = 500L
        // raop.h reset_type_t: a /play connection abandoned without a stop.
        private const val RESET_TYPE_HLS_CONN_CLOSED = 6
        // Advertised video geometry bounds; sender values are clamped to this range.
        private const val MIN_VIDEO_DIM = 16
        private const val MAX_VIDEO_DIM = 16384
    }
}

