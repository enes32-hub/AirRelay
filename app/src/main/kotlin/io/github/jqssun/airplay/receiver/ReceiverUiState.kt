package io.github.jqssun.airplay.receiver

/** Product-facing facts, never transport parameters. TCP connection != authenticated stream. */
enum class ReceiverPhase { STOPPED, STARTING, NETWORK_WAIT, DISCOVERY_WAIT, DISCOVERY_ERROR,
    READY, CONNECTING, PAIRING, WAITING_VIDEO, MIRRORING, AUDIO_CONNECTED, MEDIA_PLAYING, ERROR }
enum class LocalNetwork { UNKNOWN, WIFI, ETHERNET, UNAVAILABLE, PERMISSION_DENIED }
enum class Advertisement { IDLE, REGISTERING, REGISTERED, FAILED, NOT_REQUESTED,
    /** A device on the network asked for the record and we answered: publishing is proven. */
    CONFIRMED }
enum class ReceiverNotice { NONE, CONNECTION_LOST, CONNECTION_RESET, PAIRING_CANCELLED,
    PAIRING_EXPIRED, START_FAILED, SETTINGS_DEFERRED, NETWORK_CHANGED, DISCOVERY_FAILED }

data class ReceiverUiState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val advertisedName: String = "",
    val network: LocalNetwork = LocalNetwork.UNKNOWN,
    val airplay: Advertisement = Advertisement.IDLE,
    val raop: Advertisement = Advertisement.IDLE,
    val discoveryErrorCode: Int? = null,
    val portConflict: String? = null,
    val connections: Int = 0,
    val pin: String? = null,
    val mirrorRequested: Boolean = false,
    // Presentation belongs to the broadcast, not the lifetime of a media item/socket.
    val presentationHeld: Boolean = false,
    val mediaErrorCode: Int? = null,
    // Retained when the next screen frame clears the transient error text.
    val playbackFailureId: Long = 0L,
    // Latches the sender's screen intent until the last control connection ends.
    val screenSession: Boolean = false,
    val presentationSession: Long = -1L,
    // A successful EGL buffer submission, NOT proof of panel scan-out or end-to-end latency.
    val firstFrameSubmitted: Boolean = false,
    val audioConnected: Boolean = false,
    val mediaPlaying: Boolean = false,
    val mediaPending: Boolean = false,
    val remotePreparing: Boolean = false,
    val pendingSettings: Boolean = false,
    val notice: ReceiverNotice = ReceiverNotice.NONE,
) {
    val sessionBusy: Boolean get() = presentationHeld || connections > 0 || pin != null || mirrorRequested ||
        audioConnected || mediaPlaying || mediaPending
    val canRetryDiscovery: Boolean get() = running && !starting &&
        airplay != Advertisement.REGISTERING && raop != Advertisement.REGISTERING
    val canApplySettings: Boolean get() = pendingSettings && !sessionBusy && !starting
    val phase: ReceiverPhase get() = when {
        notice == ReceiverNotice.START_FAILED -> ReceiverPhase.ERROR
        starting -> ReceiverPhase.STARTING
        !running -> ReceiverPhase.STOPPED
        network == LocalNetwork.UNAVAILABLE || network == LocalNetwork.PERMISSION_DENIED -> ReceiverPhase.NETWORK_WAIT
        pin != null -> ReceiverPhase.PAIRING
        mediaErrorCode != null -> ReceiverPhase.ERROR
        mediaPlaying -> ReceiverPhase.MEDIA_PLAYING
        mirrorRequested && firstFrameSubmitted -> ReceiverPhase.MIRRORING
        mirrorRequested -> ReceiverPhase.WAITING_VIDEO
        audioConnected -> ReceiverPhase.AUDIO_CONNECTED
        presentationHeld -> ReceiverPhase.WAITING_VIDEO
        connections > 0 || mediaPending -> ReceiverPhase.CONNECTING
        airplay == Advertisement.FAILED -> ReceiverPhase.DISCOVERY_ERROR
        airplay != Advertisement.REGISTERED && airplay != Advertisement.CONFIRMED ->
            ReceiverPhase.DISCOVERY_WAIT
        else -> ReceiverPhase.READY
    }

    fun sessionEnded(notice: ReceiverNotice = ReceiverNotice.CONNECTION_LOST) = copy(
        connections = 0, pin = null, mirrorRequested = false, screenSession = false,
        presentationHeld = false, mediaErrorCode = null,
        presentationSession = -1L, firstFrameSubmitted = false,
        audioConnected = false, mediaPlaying = false, mediaPending = false, remotePreparing = false, notice = notice,
    )
}

/** Shared by UI input validation and host-side regression tests. */
object ReceiverInput {
    const val NAME_MAX_UTF8_BYTES = 48 // leaves headroom for RAOP's prefix in a DNS label
    fun validName(value: String): Boolean = value.trim().isNotEmpty() &&
        value == value.trim() && value.none { it.isISOControl() } &&
        value.toByteArray(Charsets.UTF_8).size <= NAME_MAX_UTF8_BYTES
    /** The fixed transport endpoints are not user-settable: a control port equal to one of
     *  them would make the native receiver bind the same port twice. */
    fun validPort(port: Int): Boolean = port in 1..65535 && port !in ReceiverPortGuard.reservedPorts
    fun validFps(fps: Int): Boolean = fps in 1..120
    fun validResolution(value: String): Boolean {
        if (value in setOf("auto", "portrait", "landscape")) return true
        val parts = value.split("x")
        return parts.size == 2 && parts.all { it.toIntOrNull()?.let { n -> n in 16..16384 } == true }
    }
}

/** Observational only: never drops, buffers, schedules or modifies a frame. */
class FirstFrameGate {
    private var session = -1L
    private var generation = 0L
    private var notified = false
    data class Session(val token: Long, val generation: Long)
    data class DecoderFrame(val session: Session?, val presentationTimeUs: Long, val surfaceTimestampNs: Long, val sequence: Long = 0)
    private var outputSequence = 0L
    private var minimumOutputSequence = 0L
    private val outputs = java.util.ArrayDeque<DecoderFrame>()
    private var lastTextureTimestampNs: Long? = null
    private var lastTextureFrame: DecoderFrame? = null
    private var trackingUncertain = false

    @Synchronized fun begin(token: Long) { session = token; generation++; notified = false }
    @Synchronized fun token(): Long = session
    @Synchronized fun decoderSession(): Session? = if (session >= 0) Session(session, generation) else null
    @Synchronized fun invalidate() { session = -1L; generation++; notified = false }

    /** Observe a fresh frame on the existing decoder after remote playback. */
    @Synchronized fun awaitFreshSubmission() {
        minimumOutputSequence = outputSequence + 1
        notified = false
    }

    /** Capture provenance before releasing this output buffer; the GL thread may wake immediately. */
    @Synchronized fun recordDecoderOutput(origin: Session?, ptsUs: Long, surfaceTimestampNs: Long) {
        if (trackingUncertain) return
        val output = DecoderFrame(origin, ptsUs, surfaceTimestampNs, outputSequence + 1)
        // Consecutive identical timestamps from one decoder carry identical provenance.
        val last = outputs.peekLast()
        if (last != null && last.session == origin && last.presentationTimeUs == ptsUs && last.surfaceTimestampNs == surfaceTimestampNs) return
        outputSequence++
        if (outputs.size == MAX_PENDING_OUTPUTS) {
            // Never evict an unseen old timestamp and then attribute its texture to a new
            // session with the same timestamp. Rendering continues; observation fails closed
            // until the input SurfaceTexture is recreated and no old buffers can survive.
            trackingUncertain = true
            outputs.clear()
            lastTextureFrame = null
            return
        }
        outputs.addLast(output)
    }

    /** SurfaceTexture latches in producer order and may skip several queued decoder outputs. */
    @Synchronized fun frameForTexture(timestampNs: Long): DecoderFrame? {
        if (trackingUncertain) return null
        // A delayed frame-available callback or display rebind may redraw the cached texture.
        // A newly registered output with the same timestamp cannot relabel that texture.
        if (lastTextureTimestampNs == timestampNs) return lastTextureFrame
        lastTextureTimestampNs = timestampNs
        val first = outputs.firstOrNull { it.surfaceTimestampNs == timestampNs }
        lastTextureFrame = null
        if (first == null) return null
        val ambiguous = outputs.any { it.surfaceTimestampNs == timestampNs && it.session != first.session }
        // Retain all matching entries until a different timestamp advances the texture.
        // This also rejects duplicate PTS spanning two decoder/session generations.
        while (outputs.peekFirst() !== first) outputs.removeFirst()
        if (!ambiguous) lastTextureFrame = first
        return lastTextureFrame
    }

    @Synchronized fun acceptSubmission(frame: DecoderFrame?): Boolean {
        val origin = frame?.session ?: return false
        if (trackingUncertain || origin.generation != generation || frame.sequence < minimumOutputSequence) return false
        return acceptSubmission(origin.token)
    }

    /** Called only when a new SurfaceTexture has replaced the old producer buffer queue. */
    @Synchronized fun resetSurfaceObservation() {
        outputs.clear()
        lastTextureTimestampNs = null
        lastTextureFrame = null
        trackingUncertain = false
    }

    // Token-only gate remains useful for state/callback checks; rendering uses DecoderFrame.
    @Synchronized fun acceptSubmission(frameToken: Long): Boolean {
        if (frameToken < 0 || frameToken != session || notified) return false
        notified = true
        return true
    }

    companion object { private const val MAX_PENDING_OUTPUTS = 256 }
}
