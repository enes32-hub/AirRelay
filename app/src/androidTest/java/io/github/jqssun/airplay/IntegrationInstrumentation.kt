package io.github.jqssun.airplay

import io.github.jqssun.airplay.receiver.observeFirstFrame
import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import io.github.jqssun.airplay.ui.mainActivity
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityManager
import io.github.jqssun.airplay.receiver.PendingReceiverSettings
import io.github.jqssun.airplay.receiver.ReceiverNotice
import io.github.jqssun.airplay.receiver.ReceiverPhase
import io.github.jqssun.airplay.receiver.UserExperiencePrefs
import io.github.jqssun.airplay.receiver.LocalNetwork
import io.github.jqssun.airplay.receiver.Advertisement
import io.github.jqssun.airplay.discovery.NsdServiceManager
import io.github.jqssun.airplay.service.AirPlayService
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Actual Android components, synthetic callback input: never an AirPlay/media acceptance test. */
class IntegrationInstrumentation : Instrumentation() {
    private var recordingOnly = false
    private var playbackRevision: String? = null
    private var capsuleLocale: String? = null
    private var soakSeconds = 12
    private var mediaOnly = false
    private var tvHevcOnly = false
    // In-process soak: loops the TV HEVC probe and reports fd/thread/PSS growth (see
    // SyntheticVideoSoak). Cross-run measurement is impossible because every instrumentation
    // start force-stops the app process.
    private var soakHevcRounds = 0
    // Control run for the soak: the same Activity/SurfaceView churn with no decoding, used to
    // subtract harness cost from the soak result.
    private var churnRounds = 0
    private var presentationOnly = false
    private var quitOnly = false
    private val results = JSONArray()
    private var networkOnly = false
    private var themeOnly = false
    private var seekOnly = false
    private var httpOnly = false
    private var freezeOnly = false
    private var latencyOnly = false
    private var benchmarkFps = 60
    private var softwareCodec = false
    private var localVideoResult: JSONObject? = null
    private val invocationSamples = JSONArray()
    private val output = StringBuilder("LOCAL TEST: no authenticated iPhone. Individual results identify synthetic callbacks, real local decoding, generated audio and network probes.\n")
    private lateinit var receiver: AirPlayService
    private var bound = false
    private var syntheticSession = false
    private lateinit var activity: Activity
    private val ready = CountDownLatch(1)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            receiver = (binder as AirPlayService.LocalBinder).service
            ready.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    override fun onCreate(arguments: Bundle?) {
        recordingOnly = arguments?.getString("recordingOnly") == "true"
        soakSeconds = arguments?.getString("soakSeconds")?.toIntOrNull()?.coerceIn(12, 7200) ?: 12
        mediaOnly = arguments?.getString("mediaOnly") == "true"
        tvHevcOnly = arguments?.getString("tvHevcOnly") == "true"
        soakHevcRounds = arguments?.getString("soakHevcRounds")?.toIntOrNull()?.coerceIn(1, 100) ?: 0
        churnRounds = arguments?.getString("churnRounds")?.toIntOrNull()?.coerceIn(1, 100) ?: 0
        presentationOnly = arguments?.getString("presentationOnly") == "true"
        quitOnly = arguments?.getString("quitOnly") == "true"
        super.onCreate(arguments)
        networkOnly = arguments?.getString("networkOnly") == "true"
        themeOnly = arguments?.getString("themeOnly") == "true"
        seekOnly = arguments?.getString("seekOnly") == "true"
        httpOnly = arguments?.getString("httpOnly") == "true"
        freezeOnly = arguments?.getString("freezeOnly") == "true"
        latencyOnly = arguments?.getString("latencyOnly") == "true"
        benchmarkFps = arguments?.getString("benchmarkFps")?.toIntOrNull()?.takeIf { it == 30 || it == 60 } ?: 60
        softwareCodec = arguments?.getString("softwareCodec") == "true"
        playbackRevision = arguments?.getString("playbackRevision")
        capsuleLocale = arguments?.getString("capsuleLocale")?.takeIf { it == "tr" || it == "en" }
        start()
    }

    override fun onStart() {
        if (recordingOnly) {
            try {
                val recordings = io.github.jqssun.airplay.recording.RecordingService.files(targetContext)
                val source = recordings.firstOrNull { it.length() > 1_000_000 } ?: error("Run real consent capture first")
                val metadata = android.media.MediaMetadataRetriever()
                try {
                    metadata.setDataSource(source.absolutePath)
                    check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) == "1920")
                    check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) == "1080")
                    check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 30_000)
                } finally { metadata.release() }
                val original = java.security.MessageDigest.getInstance("SHA-256").digest(source.readBytes())
                val uri = io.github.jqssun.airplay.recording.RecordingExport.toMovies(targetContext, source)
                try {
                    val copy = targetContext.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    check(original.contentEquals(java.security.MessageDigest.getInstance("SHA-256").digest(copy)))
                    check(source.exists())
                } finally { targetContext.contentResolver.delete(uri, null, null) }
                check(runCatching { io.github.jqssun.airplay.recording.RecordingExport.toMovies(targetContext,
                    File(targetContext.filesDir, "not-a-recording.mp4")) }.isFailure)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS RECORDING: real capture metadata, export SHA256, original retained, invalid source rejected. No AirPlay/audio acceptance.\n") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (playbackRevision != null) {
            try {
                runPlaybackRevision(playbackRevision!!)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS PLAYBACK_REVISION " + playbackRevision + "\n" + output) })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (presentationOnly) {
            try {
                runPresentationProbe()
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", output.toString()) })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (churnRounds > 0) {
            try {
                val report = SyntheticVideoSoak.runActivityChurn(this, churnRounds)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "CHURN: $report") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (soakHevcRounds > 0) {
            try {
                val report = SyntheticVideoSoak.run(this, soakHevcRounds)
                val ok = report.optBoolean("passed", false)
                finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                    Bundle().apply { putString("stream", "SOAK_HEVC: $report") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (tvHevcOnly) {
            try {
                val result = SyntheticVideoProbe.runTvHevc(this)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS TV_HEVC: $result") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (quitOnly) {
            try {
                runQuitProbe()
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS QUIT: localized activity lookup; real confirmation closes activity and service; user stop retained") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (mediaOnly) {
            try {
                val report = TransitionPlaybackProbe.run(this, soakSeconds)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS REMOTE_MEDIA: $report") })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (httpOnly) {
            try {
                val report = HttpFailureProbe.run(this)
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report) })
            } catch (failure: Throwable) {
                finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAILED: ${failure.stackTraceToString()}") })
            }
            return
        }
        if (latencyOnly) {
            var passed = false
            val report = try {
                val result = SyntheticVideoProbe.runThroughput(this, benchmarkFps, softwareCodec)
                passed = true
                "THROUGHPUT: $result"
            } catch (failure: Throwable) { "FAILED: ${failure.stackTraceToString()}" }
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("stream", report) })
            return
        }
        if (freezeOnly) {
            val report = StringBuilder()
            var passed = false
            try {
                val pipelineRecovery = SyntheticVideoProbe.runPipelineFailureRecovery(this)
                report.append("PASS PIPELINE_FAILURE_RECOVERY ").append(pipelineRecovery).append('\n')
                val recovery = SyntheticVideoProbe.runObservationRecovery(this)
                report.append("PASS OBSERVATION_RECOVERY ").append(recovery).append('\n')
                val resolution = SyntheticVideoProbe.runResolutionChanges(this)
                report.append("PASS RESOLUTION_RETURN ").append(resolution).append('\n')
                val reconnect = SyntheticVideoProbe.run(this)
                report.append("PASS RECONNECT ").append(reconnect).append('\n')
                passed = true
            } catch (failure: Throwable) { report.append("FAILED: ").append(failure.stackTraceToString()) }
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("stream", report.toString()) })
            return
        }
        val experience = targetContext.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
        val changedKeys = listOf(UserExperiencePrefs.SETUP_COMPLETE, UserExperiencePrefs.USER_STOPPED,
            UserExperiencePrefs.LANGUAGE, UserExperiencePrefs.NOTIFICATION_PROMPTED,
            UserExperiencePrefs.THEME, UserExperiencePrefs.SHOW_STREAM_METRICS)
        val original = changedKeys.associateWith { experience.all[it] }
        val receiverPrefs = targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val originalUiPrefs = mapOf(Prefs.AUTO_FULLSCREEN to receiverPrefs.all[Prefs.AUTO_FULLSCREEN])
        var prepared = false
        var passed = true
        try {
            check(targetContext.getSharedPreferences("airrelay_next_receiver_start", Context.MODE_PRIVATE).all.isEmpty()) {
                "Unapplied receiver settings exist; refusing to consume the user's draft."
            }
            prepared = true
            check(experience.edit().putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true)
                .putBoolean(UserExperiencePrefs.USER_STOPPED, true)
                .putBoolean(UserExperiencePrefs.NOTIFICATION_PROMPTED, true)
                .putString(UserExperiencePrefs.LANGUAGE, "en").commit())
            check(receiverPrefs.edit().putBoolean(Prefs.AUTO_FULLSCREEN, true).commit())
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            runOnMainSync {
                bound = targetContext.bindService(Intent(targetContext, AirPlayService::class.java),
                    connection, Context.BIND_AUTO_CREATE)
            }
            check(bound && ready.await(10, TimeUnit.SECONDS)) { "Service bind timed out" }
            check(receiver.connectionCount.value == 0) { "Real receiver connection exists; refusing synthetic session" }

            scenario("real Android activity and receiver startup") {
                main { receiverCommand("stop") { receiver.stopServer() } }
                main {
                    receiverCommand("start") { receiver.startServer(savedName()) }
                    check(receiver.receiverState.value.starting) { "STARTING was not published synchronously" }
                }
                awaitState { receiver.receiverState.value.running }
                awaitUi { targetWindowVisible() }
                screenshot("01-real-ready")
            }
            scenario("cancelled startup cannot revive the receiver") {
                main {
                    receiverCommand("stop-before-cancel-test") { receiver.stopServer() }
                    receiverCommand("start-to-cancel") { receiver.startServer(savedName()) }
                    receiverCommand("stop-cancels-pending-start") { receiver.stopServer() }
                }
                SystemClock.sleep(800)
                check(!receiver.receiverState.value.running && !receiver.receiverState.value.starting)
                startReceiver()
            }
            scenario("three AirRelay themes and popup selection") {
                val handleBefore = nativeHandle()
                clickText(label(R.string.p_settings))
                clickText(label(R.string.p_section_appearance))
                val themes = listOf(Triple("classic", R.string.theme_classic, 0xFF121416.toInt()),
                    Triple("light", R.string.theme_light, 0xFFF7F7F5.toInt()),
                    Triple("dark", R.string.theme_dark, 0xFF090A0B.toInt()))
                for ((key, labelId, color) in themes) {
                    val current = io.github.jqssun.airplay.receiver.AppearancePolicy.resolve(experience.getString(UserExperiencePrefs.THEME, "classic") ?: "classic")
                    val row = label(R.string.appearance_theme) + ": " + label(themes.first { it.first == current }.second)
                    waitForIdleSync()
                    revealText(row)
                    check(!hasText(label(R.string.theme_light))) { "Theme choices leaked into the settings page" }
                    clickText(row)
                    awaitUi { hasText(label(R.string.theme_classic)) && hasText(label(R.string.theme_light)) && hasText(label(R.string.theme_dark)) }
                    check(!hasText("Amber"))
                    screenshot("airrelay-theme-dialog-$key")
                    revealText(label(labelId))
                    clickText(label(labelId))
                    awaitState { experience.getString(UserExperiencePrefs.THEME, null) == key }
                    awaitUi { !hasText(label(R.string.p_cancel)) }
                    awaitUi {
                        uiAutomation.takeScreenshot()?.let { image ->
                            val match = image.getPixel(10, 10) == color
                            image.recycle(); match
                        } == true
                    }
                    screenshot("theme-$key")
                    awaitUi { focusedText(label(R.string.appearance_theme) + ": " + label(labelId)) }
                    check(!hasText("720p") && !hasText("1080p")) { "Recording quality leaked into appearance" }
                    check(nativeHandle() == handleBefore) { "Theme change restarted the receiver" }
                }
                clickText(label(R.string.settings_playback))
                revealText(label(R.string.stream_metrics))
                val metricsBefore = experience.getBoolean(UserExperiencePrefs.SHOW_STREAM_METRICS, false)
                clickText(label(R.string.stream_metrics))
                awaitState { experience.getBoolean(UserExperiencePrefs.SHOW_STREAM_METRICS, false) != metricsBefore }
                screenshot("appearance-metrics-toggled")
                clickText(label(R.string.p_help))
                check(!hasText(label(R.string.p_support)))
                clickText(label(R.string.p_home))
            }
            scenario("PIN appears immediately and cancelled PIN never appears later") {
                var receivedAt = 0L
                runOnMainSync {
                    receiver.onConnectionInit(); syntheticSession = true
                    receivedAt = SystemClock.elapsedRealtime()
                    receiver.onDisplayPin("0000")
                }
                await(1200) { hasText(label(R.string.p_pair_title)) }
                val elapsed = SystemClock.elapsedRealtime() - receivedAt
                check(elapsed < 1400) { "PIN display still has an artificial delay" }
                output.append("PIN DISPLAY elapsedMs=").append(elapsed).append("; synthetic PIN, no screenshot retained\n")
                main { receiver.cancelPairing() }
                awaitState { !receiver.receiverState.value.running }
                syntheticSession = false
                startReceiver()
                runOnMainSync {
                    receiver.onConnectionInit(); syntheticSession = true
                    receiver.onDisplayPin("1111")
                    receiver.cancelPairing()
                }
                SystemClock.sleep(1800)
                check(!hasText(label(R.string.p_pair_title))) { "Cancelled PIN flashed later" }
                awaitState { !receiver.receiverState.value.running }
                syntheticSession = false
                startReceiver()
            }
            scenario("rapid replacement awaits teardown and rejects retired callbacks") {
                repeat(3) {
                    main {
                        receiverCommand("rapid-stop") { receiver.stopServer() }
                        receiver.onConnectionInit()
                        receiver.onMirrorRunning(true)
                        receiver.onDisplayPin("0000")
                        check(receiver.connectionCount.value == 0 && receiver.receiverState.value.pin == null)
                        receiverCommand("rapid-start") { receiver.startServer(savedName()) }
                    }
                    awaitState { receiver.receiverState.value.running }
                    check(nativeHandle() != 0L && receiver.connectionCount.value == 0)
                }
                val tail = AirPlayService::class.java.getDeclaredField("nativeCleanupTail").apply { isAccessible = true }
                awaitState { tail.get(null) == null }
            }
            scenario("network-loss callback displays waiting and restores without restarting") {
                val before = receiver.receiverState.value.network
                val handle = nativeHandle()
                val monitor = receiver.javaClass.getDeclaredField("networkMonitor").apply { isAccessible = true }.get(receiver)
                @Suppress("UNCHECKED_CAST")
                val changed = monitor.javaClass.getDeclaredField("changed").apply { isAccessible = true }.get(monitor) as (LocalNetwork) -> Unit
                try {
                    main { changed(LocalNetwork.UNAVAILABLE) }
                    awaitState { receiver.receiverState.value.phase == ReceiverPhase.NETWORK_WAIT }
                    accessibilitySnapshot("network-wait-observation")
                    output.append("NETWORK expected UI: ").append(label(R.string.p_network_note)).append('\n')
                    awaitUi { hasText(label(R.string.p_network_note)) }
                    screenshot("04-simulated-network-loss")
                    check(nativeHandle() == handle)
                } finally { main { changed(before) } }
            }
            scenario("simulated Wi-Fi identity and IP changes refresh discovery") {
                val handle = nativeHandle()
                val originalNetwork = receiver.receiverState.value.network
                val realMonitor = receiver.javaClass.getDeclaredField("networkMonitor").apply { isAccessible = true }.get(receiver)
                @Suppress("UNCHECKED_CAST")
                val changed = realMonitor.javaClass.getDeclaredField("changed").apply { isAccessible = true }.get(realMonitor) as (LocalNetwork) -> Unit
                val generationField = receiver.javaClass.getDeclaredField("discoveryGeneration").apply { isAccessible = true }
                fun generation() = generationField.getLong(receiver)
                val events = java.util.concurrent.CopyOnWriteArrayList<LocalNetwork>()
                // Never start/register this monitor: only inject synthetic callback objects.
                val simulated = io.github.jqssun.airplay.receiver.LocalNetworkMonitor(targetContext) {
                    events.add(it); changed(it)
                }
                val callback = simulated.javaClass.getDeclaredField("callback").apply { isAccessible = true }.get(simulated) as android.net.ConnectivityManager.NetworkCallback
                val wifiA = android.net.Network.fromNetworkHandle((91001L shl 32) or 0xcafed00dL)
                val wifiB = android.net.Network.fromNetworkHandle((91002L shl 32) or 0xcafed00dL)
                @Suppress("UNCHECKED_CAST")
                val networks = simulated.javaClass.getDeclaredField("networks").apply { isAccessible = true }.get(simulated) as MutableMap<android.net.Network, LocalNetwork>
                @Suppress("UNCHECKED_CAST")
                val links = simulated.javaClass.getDeclaredField("links").apply { isAccessible = true }.get(simulated) as MutableMap<android.net.Network, String>
                val publish = simulated.javaClass.getDeclaredMethod("publish").apply { isAccessible = true }
                // Inject topology into our own monitor, not hidden Android network APIs.
                fun wifi(network: android.net.Network, address: String) = main {
                    networks[network] = LocalNetwork.WIFI
                    links[network] = address
                    publish.invoke(simulated)
                }
                fun registeredAfter(previous: Long) {
                    awaitState { generation() > previous && receiver.receiverState.value.airplay == Advertisement.REGISTERED }
                    check(nativeHandle() == handle) { "Network update restarted native receiver" }
                }
                try {
                    var before = generation()
                    wifi(wifiA, "192.0.2.10/24")
                    registeredAfter(before)
                    check(events.last() == LocalNetwork.WIFI)
                    before = generation()
                    val beforeEvents = events.size
                    wifi(wifiA, "192.0.2.11/24")
                    registeredAfter(before)
                    check(events.size > beforeEvents) { "Same Wi-Fi type IP change was ignored" }
                    callback.onLost(wifiA)
                    awaitState { receiver.receiverState.value.network == LocalNetwork.UNAVAILABLE &&
                        receiver.receiverState.value.airplay == Advertisement.IDLE && receiver.receiverState.value.raop == Advertisement.IDLE }
                    before = generation()
                    wifi(wifiB, "198.51.100.20/24")
                    registeredAfter(before)
                    check(events.contains(LocalNetwork.UNAVAILABLE))
                    val eventCount = events.size
                    simulated.close()
                    callback.onLost(wifiB)
                    runOnMainSync { }
                    check(events.size == eventCount) { "Closed monitor emitted a stale callback" }
                } finally {
                    simulated.close()
                    main { changed(originalNetwork) }
                    awaitState { receiver.receiverState.value.airplay == Advertisement.REGISTERED }
                }
            }
            scenario("discovery failure callback displays an error and recovers") {
                awaitState { receiver.receiverState.value.airplay == Advertisement.REGISTERED }
                val actualName = receiver.receiverState.value.advertisedName
                val manager = receiver.javaClass.getDeclaredField("nsdManager").apply { isAccessible = true }.get(receiver)
                @Suppress("UNCHECKED_CAST")
                val result = manager.javaClass.getDeclaredField("onResult").apply { isAccessible = true }.get(manager) as (NsdServiceManager.Result) -> Unit
                try {
                    main { result(NsdServiceManager.Result(NsdServiceManager.Kind.AIRPLAY, Advertisement.FAILED, errorCode = NsdServiceManager.ERROR_PLATFORM)) }
                    awaitState { receiver.receiverState.value.phase == ReceiverPhase.DISCOVERY_ERROR }
                    screenshot("05-simulated-discovery-failure")
                } finally {
                    main { result(NsdServiceManager.Result(NsdServiceManager.Kind.AIRPLAY, Advertisement.REGISTERED, actualName)) }
                }
                awaitState { receiver.receiverState.value.phase == ReceiverPhase.READY }
            }
            scenario("PIN survives request disconnect and reset until media setup") {
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onDisplayPin("0000") }
                awaitUi { hasText(label(R.string.p_pair_title)) }
                main { receiver.onConnectionDestroy(); receiver.onConnectionReset(0) }
                SystemClock.sleep(1600)
                check(receiver.receiverState.value.pin == "0000" && receiver.connectionCount.value == 0)
                check(receiver.receiverState.value.phase == ReceiverPhase.PAIRING)
                check(hasText(label(R.string.p_pair_title)))
                main { receiver.onConnectionInit(); receiver.onDisplayPin("1111") }
                check(receiver.receiverState.value.pin == "1111")
                main { receiver.onAudioFormat(8, 480, true) }
                awaitUi { !hasText(label(R.string.p_pair_title)) }
                check(receiver.receiverState.value.pin == null)
                main { receiver.onConnectionDestroy(); syntheticSession = false }
            }
            scenario("disconnected PIN retains expiry and explicit cancellation") {
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onDisplayPin("0000") }
                main { receiver.onConnectionDestroy() }
                val expiryField = receiver.javaClass.getDeclaredField("pinExpiry").apply { isAccessible = true }
                val expiry = checkNotNull(expiryField.get(receiver) as? Runnable)
                main { expiry.run() }
                check(!receiver.receiverState.value.running && receiver.receiverState.value.pin == null)
                check(receiver.receiverState.value.notice == ReceiverNotice.PAIRING_EXPIRED)
                syntheticSession = false
                startReceiver()
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onDisplayPin("0000") }
                main { receiver.onConnectionDestroy() }
                awaitUi { hasText(label(R.string.p_pair_title)) }
                clickText(label(R.string.p_pair_cancel))
                awaitState { !receiver.receiverState.value.running }
                check(receiver.receiverState.value.pin == null)
                syntheticSession = false
                startReceiver()
            }
            scenario("mirror request waits for actual video submission") {
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true) }
                awaitState { receiver.receiverState.value.phase == ReceiverPhase.WAITING_VIDEO }
                check(!receiver.receiverState.value.firstFrameSubmitted)
                awaitUi { targetWindowVisible() && !hasText(label(R.string.p_state_video_wait)) }
                screenshot("02-simulated-waiting-video")
            }
            scenario("screen audio and video loss remain WAITING_VIDEO") {
                main { receiver.onAudioFormat(8, 480, true); receiver.onMirrorRunning(false) }
                check(receiver.receiverState.value.phase == ReceiverPhase.WAITING_VIDEO)
                check(receiver.receiverState.value.screenSession && !receiver.receiverState.value.audioConnected)
                check(!receiver.audioOnly.value)
                awaitUi { !hasText(label(R.string.p_state_video_wait)) && !hasText(label(R.string.p_stop)) }
            }
            scenario("settings draft and discard preserve the active synthetic screen session") {
                val settings = PendingReceiverSettings(targetContext)
                val originalName = savedName()
                val draftName = if (originalName == "AirRelay simulated draft") "AirRelay alternate draft" else "AirRelay simulated draft"
                val stateBefore = receiver.receiverState.value
                val handle = receiver.javaClass.getDeclaredField("nativeHandle").apply { isAccessible = true }
                val nativeBefore = handle.getLong(receiver)
                check(nativeBefore != 0L)
                try {
                    main {
                        check(settings.put(Prefs.SERVER_NAME, draftName))
                        receiver.refreshPendingSettings()
                    }
                    check(receiver.receiverState.value.pendingSettings)
                    check(!receiver.receiverState.value.canApplySettings)
                    // Allow the active preference collector/debounce window to run.
                    SystemClock.sleep(800)
                    val during = receiver.receiverState.value
                    check(during.running && !during.starting && during.connections == stateBefore.connections)
                    check(during.presentationSession == stateBefore.presentationSession && during.screenSession)
                    check(handle.getLong(receiver) == nativeBefore && savedName() == originalName)
                } finally {
                    main { check(settings.discard()); receiver.refreshPendingSettings() }
                }
                check(!receiver.receiverState.value.pendingSettings && savedName() == originalName)
                check(receiver.receiverState.value.running && handle.getLong(receiver) == nativeBefore)
            }
            scenario("stale first-frame callback cannot mark a new session live") {
                main { receiver.onMirrorRunning(true) }
                val oldToken = receiver.receiverState.value.presentationSession
                main { receiver.onMirrorRunning(false); receiver.onMirrorRunning(true) }
                val newToken = receiver.receiverState.value.presentationSession
                check(oldToken != newToken)
                // Test-only reflection invokes the service observer, never the renderer/decoder.
                val getter = receiver.videoRenderer.javaClass.getMethod("getOnFirstFrameSubmitted")
                @Suppress("UNCHECKED_CAST")
                val observer = getter.invoke(receiver.videoRenderer) as ((Long) -> Unit)
                main { observer(oldToken) }
                check(!receiver.receiverState.value.firstFrameSubmitted)
                main { observer(newToken) }
                check(receiver.receiverState.value.firstFrameSubmitted)
                awaitUi { hasText(label(R.string.metrics_mirror)) }
                screenshot("metrics-synthetic-mirror")
                // This is synthetic observer acceptance, not evidence of a rendered frame.
                main { receiver.onMirrorRunning(false) }
                check(!receiver.receiverState.value.firstFrameSubmitted)
            }
            scenario("real overlay auto-hide and held remote wake consume the whole wake key") {
                main { receiver.onMirrorRunning(true) }
                val token = receiver.receiverState.value.presentationSession
                val getter = receiver.videoRenderer.javaClass.getMethod("getOnFirstFrameSubmitted")
                @Suppress("UNCHECKED_CAST")
                val observer = getter.invoke(receiver.videoRenderer) as ((Long) -> Unit)
                main { observer(token) }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitUi { hasText(label(R.string.playback_choice_title)) }
                val accessibility = targetContext.getSystemService(AccessibilityManager::class.java)
                val configuredHideMs = accessibility.getRecommendedTimeoutMillis(5000,
                    AccessibilityManager.FLAG_CONTENT_ICONS or AccessibilityManager.FLAG_CONTENT_TEXT or
                        AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
                check(configuredHideMs <= 15_000) { "Configured accessibility timeout exceeds the bounded UI probe" }
                await(configuredHideMs + 2500) { !hasText(label(R.string.playback_choice_title)) }
                val downAt = SystemClock.uptimeMillis()
                sendKeySync(KeyEvent(downAt, downAt, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                SystemClock.sleep(600)
                sendKeySync(KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER, 1, 0, -1, 0, KeyEvent.FLAG_LONG_PRESS))
                SystemClock.sleep(100)
                sendKeySync(KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER, 0))
                awaitUi { hasText(label(R.string.playback_choice_title)) }
                check(receiver.receiverState.value.mirrorRequested && receiver.receiverState.value.running)
                check(!hasText(label(R.string.p_stop_title)))
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                awaitUi { !hasText(label(R.string.playback_choice_title)) }
                main { receiver.onMirrorRunning(false) }
            }
            scenario("last disconnect clears screen intent; fresh music is audio-only") {
                main { receiver.onConnectionDestroy(); syntheticSession = false }
                awaitState { !receiver.receiverState.value.screenSession && !receiver.receiverState.value.mirrorRequested }
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onAudioFormat(4, 1024, false) }
                check(receiver.receiverState.value.phase == ReceiverPhase.AUDIO_CONNECTED)
                check(receiver.audioOnly.value && receiver.receiverState.value.audioConnected)
                main { receiver.onConnectionDestroy(); syntheticSession = false }
            }
            awaitState { !receiver.receiverState.value.presentationHeld }
            scenario("real stop dialog cancellation keeps session and receiver") {
                main { receiver.onConnectionInit(); syntheticSession = true }
                clickText(label(R.string.p_stop))
                awaitUi { hasText(label(R.string.p_stop_title)) }
                clickText(label(R.string.p_cancel))
                awaitUi { !hasText(label(R.string.p_stop_title)) }
                check(receiver.receiverState.value.running && receiver.connectionCount.value == 1)
            }
            scenario("PIN takes priority over stop dialog and pairing cancellation stops") {
                clickText(label(R.string.p_stop))
                awaitUi { hasText(label(R.string.p_stop_title)) }
                main { receiver.onDisplayPin("0000") }
                awaitUi { hasText(label(R.string.p_pair_title)) && !hasText(label(R.string.p_stop_title)) }
                check(receiver.receiverState.value.phase == ReceiverPhase.PAIRING)
                // No screenshot/accessibility dump is retained while PIN is visible.
                clickText(label(R.string.p_pair_cancel))
                awaitState { !receiver.receiverState.value.running }
                syntheticSession = false
                check(receiver.receiverState.value.pin == null && receiver.connectionCount.value == 0)
                check(receiver.receiverState.value.notice == ReceiverNotice.PAIRING_CANCELLED)
                startReceiver()
                awaitUi { targetWindowVisible() && !hasText(label(R.string.p_pair_title)) }
                screenshot("03-real-restarted-ready")
            }
            scenario("cancelled media preparation cannot restart from a late callback") {
                main { receiver.onVideoPrepare("local-test", "cancelled") }
                check(receiver.receiverState.value.remotePreparing)
                main { receiver.onVideoStop(); receiver.onVideoPlay("http://127.0.0.1:9/never-fetch", 0f) }
                check(!receiver.receiverState.value.remotePreparing && !receiver.videoPlaybackActive.value)
                main { receiver.stopServer() }
                startReceiver()
            }
            scenario("remote videos retain broadcast UI and seek draft cancels") {
                val fixtures = TransitionPlaybackProbe.FixtureServer(this)
                try {
                    main { receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true) }
                    repeat(3) { item ->
                        main {
                            receiver.onVideoPrepare("local-test", "item-$item")
                            receiver.onVideoPlay(fixtures.url("clip.mp4?item=$item"), 0f)
                        }
                        awaitState { receiver.videoPlaybackInfo.value.firstFrame }
                        check(receiver.receiverState.value.presentationHeld)
                        check(!hasText(label(R.string.p_stop))) { "Receiver home appeared during media" }
                        main { receiver.onVideoRate(0f) }
                        awaitState { !receiver.videoPlaybackInfo.value.playing }
                        if (!hasText(label(R.string.playback_choice_title))) sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        awaitUi { hasText(label(R.string.transition_seek_hint)) }
                        screenshot("06-remote-video-controls")
                        main { receiver.seekVideoTo(2_000) }
                        awaitState { receiver.videoPlaybackInfo.value.positionMs in 1_800..2_300 }
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                        val beforeSeek = receiver.videoPlaybackInfo.value.positionMs
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                        SystemClock.sleep(250)
                        screenshot("07-seek-preview")
                        accessibilitySnapshot("07-seek-preview")
                        awaitUi { (progressFraction(uiAutomation.rootInActiveWindow) ?: -1f) >= .99f }
                        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                        SystemClock.sleep(300)
                        check(kotlin.math.abs(receiver.videoPlaybackInfo.value.positionMs - beforeSeek) < 500)
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        awaitState { receiver.videoPlaybackInfo.value.positionMs < 500 }
                        main {
                            if (item == 2) {
                                // Sender opens a new mirroring transport but omits /stop
                                // for its paused Shorts player. The video view must release.
                                receiver.onMirrorRunning(false)
                                receiver.onMirrorRunning(true)
                            } else receiver.onVideoStop()
                        }
                        awaitState { !receiver.videoPlaybackActive.value }
                        check(receiver.receiverState.value.presentationHeld && receiver.receiverState.value.screenSession)
                        awaitUi { !hasText(label(R.string.p_stop)) && !hasText(label(R.string.p_state_video_wait)) }
                        // Actual phone return is covered by separate iPhone acceptance; here inject
                        // only the already tested renderer observer to check UI routing.
                        val token = receiver.receiverState.value.presentationSession
                        main { receiver.videoRenderer.onFirstFrameSubmitted?.invoke(token) }
                        awaitState { receiver.receiverState.value.firstFrameSubmitted }
                    }
                } finally {
                    fixtures.close()
                    main { receiver.onVideoStop(); receiver.onMirrorRunning(false); receiver.onConnectionDestroy(); syntheticSession = false }
                    awaitState { !receiver.receiverState.value.presentationHeld }
                }
            }
            if (!seekOnly && !networkOnly && !themeOnly) {
            localVideoResult = SyntheticVideoProbe.run(this)
            val resolutionResult = SyntheticVideoProbe.runResolutionChanges(this)
            output.append("PASS [RESOLUTION_TRANSITIONS]: ").append(resolutionResult).append('\n')
            val remoteMediaResult = TransitionPlaybackProbe.run(this, soakSeconds)
            output.append("PASS [REMOTE_MEDIA]: ").append(remoteMediaResult).append('\n')
            output.append("PASS [LOCAL_ENCODED_AVC]: ").append(localVideoResult).append('\n')
            }
        } catch (failure: Throwable) {
            passed = false
            if (::receiver.isInitialized && receiver.receiverState.value.pin == null) runCatching { screenshot("08-failed-ui") }
            output.append(failure.stackTraceToString().take(1800)).append('\n')
            output.append("FAILED: ").append(failure.javaClass.simpleName).append(": ")
                .append(failure.message?.take(220) ?: "no message").append('\n')
        } finally {
            if (prepared) {
                try {
                    if (::receiver.isInitialized && (receiver.connectionCount.value == 0 || syntheticSession)) {
                        main { receiverCommand("cleanup-stop") { receiver.stopServer() }; syntheticSession = false }
                    }
                    restore(experience, original)
                    restore(receiverPrefs, originalUiPrefs)
                    if (::receiver.isInitialized && !receiver.receiverState.value.running) startReceiver()
                    if (bound) runOnMainSync { targetContext.unbindService(connection) }
                } catch (failure: Throwable) {
                    passed = false
                    output.append("CLEANUP FAILED: ").append(failure.javaClass.simpleName).append('\n')
                }
            }
        }
        val summary = JSONObject().put("mode", "ANDROID_INTEGRATION").put("passed", passed)
            .put("scenarioInput", "SIMULATED_CALLBACKS").put("localVideoProbe", localVideoResult ?: JSONObject.NULL)
            .put("realMediaValidated", false).put("scenarios", results)
            .put("mainThreadReceiverInvocations", invocationSummary())
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", output.toString())
            putString("airrelay_results", summary.toString())
        })
    }

    private fun runPlaybackRevision(stage: String) {
        if (stage == "languages") { runLanguageRevision(); return }
        if (stage == "subtitle-position") {
            val cases = listOf(
                androidx.media3.common.text.Cue.Builder().setText("Default").build(),
                androidx.media3.common.text.Cue.Builder().setText("Bottom numbered").setLine(-1f, androidx.media3.common.text.Cue.LINE_TYPE_NUMBER).build(),
                androidx.media3.common.text.Cue.Builder().setText("Bottom fractional").setLine(.95f, androidx.media3.common.text.Cue.LINE_TYPE_FRACTION).build())
            cases.forEach { cue ->
                val actual = io.github.jqssun.airplay.ui.positionedSubtitle(cue)
                check(actual.line == .82f && actual.lineAnchor == androidx.media3.common.text.Cue.ANCHOR_TYPE_END)
                check(actual.lineType == androidx.media3.common.text.Cue.LINE_TYPE_FRACTION && !actual.windowColorSet)
            }
            val upper = androidx.media3.common.text.Cue.Builder().setText("Top caption").setLine(.2f, androidx.media3.common.text.Cue.LINE_TYPE_FRACTION).build()
            check(io.github.jqssun.airplay.ui.positionedSubtitle(upper).line == .2f)
            output.append("PASS default, negative numbered and bottom fractional cues raised to 82%; upper placement preserved; window fill cleared. Cue policy test, not real sender subtitle acceptance.\n")
            return
        }
        if (stage == "clean-recording") { output.append(CleanRecordingProbe.run(this)); return }
        if (stage == "adaptive-quality") { output.append(AdaptiveQualityProbe.run(this)); return }
        if (stage == "recording-library") {
            val fixture = File(File(targetContext.filesDir, "recordings").apply { mkdirs() }, "AirRelay-library-fixture-${System.currentTimeMillis()}.mp4")
            context.assets.open("media/clip.mp4").use { input -> fixture.outputStream().use { input.copyTo(it) } }
            val original = io.github.jqssun.airplay.recording.RecordingLibrary.list(targetContext).first { it.file == fixture }
            val originalHash = java.security.MessageDigest.getInstance("SHA-256").digest(original.file.readBytes())
            check(original.createdAt > 0 && original.height != null)
            val copy = File(original.file.parentFile, "AirRelay-test-library-${System.currentTimeMillis()}.mp4")
            original.file.copyTo(copy)
            var local: Activity? = null
            try {
                val prefs = targetContext.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true).commit()
                val libraryUiContext = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply {
                    val language = prefs.getString(UserExperiencePrefs.LANGUAGE, "system")
                    if (language == "tr" || language == "en") setLocale(Locale.forLanguageTag(language))
                })
                activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                awaitUi { hasText(libraryUiContext.getString(R.string.rec_title)) }
                val recordingsLabel = libraryUiContext.getString(R.string.rec_title)
                clickText(recordingsLabel)
                awaitUi { hasText(libraryUiContext.getString(R.string.rec_watch)) }
                local = startActivitySync(Intent(targetContext, io.github.jqssun.airplay.recording.RecordingPlayerActivity::class.java)
                    .putExtra("recording", copy.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val field = local.javaClass.getDeclaredField("player").apply { isAccessible = true }
                var prepared = false
                awaitState { main { prepared = (field.get(local) as? androidx.media3.exoplayer.ExoPlayer)?.playbackState == androidx.media3.common.Player.STATE_READY }; prepared }
                main {
                    val player = field.get(local) as androidx.media3.exoplayer.ExoPlayer
                    check(player.duration >= 2000)
                    player.pause()
                    val screen = local.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(local) as androidx.media3.ui.PlayerView
                    screen.hideController()
                    player.seekTo(1000)
                    check(!player.playWhenReady)
                }
                SystemClock.sleep(500)
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                SystemClock.sleep(400)
                main {
                    val screen = local.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(local) as androidx.media3.ui.PlayerView
                    check(screen.isControllerFullyVisible) { "OK failed to reveal local controls" }
                    check(!(field.get(local) as androidx.media3.exoplayer.ExoPlayer).playWhenReady) { "Reveal press unexpectedly resumed playback" }
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                main {
                    val screen = local.javaClass.getDeclaredField("view").apply { isAccessible = true }.get(local) as androidx.media3.ui.PlayerView
                    check(screen.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress).hasFocus())
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitState { var advanced = false; main { advanced = (field.get(local) as androidx.media3.exoplayer.ExoPlayer).currentPosition > 1500 }; advanced }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitState { var rewound = false; main { rewound = (field.get(local) as androidx.media3.exoplayer.ExoPlayer).currentPosition < 500 }; rewound }
                main { local.finish() }
                awaitState { var released = false; main { released = field.get(local) == null }; released }
                check(!io.github.jqssun.airplay.recording.RecordingLibrary.delete(targetContext, "../outside.mp4"))
                check(io.github.jqssun.airplay.recording.RecordingLibrary.delete(targetContext, copy.name))
                check(!copy.exists() && original.file.exists())
                check(originalHash.contentEquals(java.security.MessageDigest.getInstance("SHA-256").digest(original.file.readBytes())))
                output.append("PASS independent Recordings tab, real metadata, local ExoPlayer prepare/pause/seek/release, owned-file deletion and traversal rejection. Original recording unchanged.\n")
            } finally { local?.let { main { it.finish() } }; copy.delete(); fixture.delete() }
            return
        }
        if (stage == "direct-rotation") {
            output.setLength(0)
            output.append(DirectRotationProbe.run(this)).append('\n')
            return
        }
        if (stage == "track-options") {
            output.setLength(0)
            output.append(TrackOptionsProbe.run(this)).append('\n')
            return
        }
        if (stage == "rotation-pixels") {
            output.append(RotationSurfaceProbe.run(this)).append('\n')
            return
        }
        val store = targetContext.getSharedPreferences("rc22_test_trust", Context.MODE_PRIVATE)
        val trust = io.github.jqssun.airplay.receiver.TrustedDevices(store)
        val key = "A".repeat(43) + "="
        if (stage == "read") {
            check(trust.contains(key)) { "Trust key lost after process restart" }
            check(!trust.contains("B".repeat(43) + "="))
            check(store.edit().clear().commit())
            return
        }
        check(store.edit().clear().commit())
        check(!trust.contains(key)); check(!trust.register("malformed"))
        check(trust.register(key)); check(trust.register(key))
        check(io.github.jqssun.airplay.receiver.TrustedDevices(store).contains(key))
        if (stage == "write") return
        val experience = targetContext.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
        experience.edit().putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true)
            .putBoolean(UserExperiencePrefs.USER_STOPPED, false).commit()
        val originalCapsuleLanguage = experience.getString(UserExperiencePrefs.LANGUAGE, null)
        if (stage == "capsule-ui" && capsuleLocale != null)
            experience.edit().putString(UserExperiencePrefs.LANGUAGE, capsuleLocale).commit()
        if (stage == "auth-fixture") targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Prefs.REQUIRE_PIN, true).putBoolean(Prefs.ALLOW_NEW_CONN, true).commit()
        if (stage.startsWith("access")) targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(Prefs.ALLOW_NEW_CONN, stage == "access-takeover").commit()
        activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        main { bound = targetContext.bindService(Intent(targetContext, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE) }
        try {
            check(bound && ready.await(10, TimeUnit.SECONDS))
            main { receiver.startServer(savedName()) }
            awaitState { receiver.receiverState.value.running && !receiver.receiverState.value.starting }
            check(receiver.connectionCount.value == 0) { "Real sender connected; synthetic test cancelled" }
            val handleField = AirPlayService::class.java.getDeclaredField("nativeHandle").apply { isAccessible = true }
            val handle = handleField.getLong(receiver)
            check(handle != 0L)
            val port = AirPlayService::class.java.getDeclaredField("registeredPort").apply { isAccessible = true }.getInt(receiver)
            if (stage == "capsule-ui") {
                val uiContext = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(capsuleLocale ?: "en"))
                })
                fun visible(id: Int) = hasText(uiContext.getString(id)) || hasText(targetContext.getString(id)) || hasText(label(id))
                fun uiLabel(id: Int) = if (hasText(uiContext.getString(id))) uiContext.getString(id)
                    else if (hasText(targetContext.getString(id))) targetContext.getString(id) else label(id)
                fun focused(text: String): Boolean {
                    var node = findText(uiAutomation.rootInActiveWindow, text)
                    repeat(5) { node?.refresh(); if (node?.isFocused == true) return true; node = node?.parent }
                    return false
                }
                fun reveal() {
                    if (!visible(R.string.playback_choice_title) && !visible(R.string.p_play) && !visible(R.string.p_pause))
                        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                    awaitUi { visible(R.string.playback_choice_title) || visible(R.string.p_play) || visible(R.string.p_pause) }
                }
                fun noRecording() {
                    check(visible(R.string.rec_start)) { "Recording action missing" }
                    check(!visible(R.string.rec_title)) { "Recording library leaked into capsule" }
                }
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true) }
                SyntheticVideoProbe.startRecordingFeed(receiver.videoRenderer).use {
                    awaitState { receiver.receiverState.value.firstFrameSubmitted }
                    reveal(); noRecording()
                    awaitUi { focused(uiContext.getString(R.string.picture_rotate, 0)) }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                    awaitUi { focused(uiLabel(R.string.rec_start)) }
                    clickText(uiLabel(R.string.rec_start))
                    awaitUi { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
                    check(!hasText("720p") && !hasText("1080p")) { "Record opened quality chooser" }
                    check(!visible(R.string.rec_title) && !visible(R.string.rec_empty) && !visible(R.string.rec_watch))
                    screenshot("capsule-record-system-consent")
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { !hasText("720p") && focused(uiLabel(R.string.rec_start)) }
                    screenshot("capsule-mirror-real-decoder")
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                    awaitUi { !visible(R.string.playback_choice_title) }
                    reveal()
                    clickText(uiLabel(R.string.playback_choice_title))
                    awaitUi { visible(R.string.playback_mirror) }
                    screenshot("capsule-playback-options-mint")
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { visible(R.string.playback_choice_title) && !visible(R.string.playback_mirror) }
                    awaitUi { focused(uiLabel(R.string.playback_choice_title)) }
                    output.append("PASS real mirror decoder, record immediately after Rotate opens system consent without quality/library dialog; cancelling returns to controls; Up hides, OK reveals, playback dialog closes.\n")
                }
                val fixtures = TransitionPlaybackProbe.FixtureServer(this)
                try {
                    main { receiver.onVideoPrepare("local-test", "capsule"); receiver.onVideoPlay(fixtures.url("clip.mp4"), 0f) }
                    awaitState { receiver.videoPlaybackInfo.value.firstFrame }
                    main { receiver.onVideoRate(0f) }
                    awaitState { !receiver.videoPlaybackInfo.value.playing }
                    reveal(); noRecording()
                    awaitUi { focused(uiLabel(R.string.p_play)) }
                    check(hasText("−10 s") && hasText("+10 s"))
                    screenshot("capsule-direct-real-video")
                    for (id in listOf(R.string.track_subtitles, R.string.track_audio, R.string.track_quality,
                        R.string.p_settings, R.string.p_stop_confirm)) {
                        repeat(if (id == R.string.track_subtitles) 2 else 1) {
                            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(250)
                        }
                        screenshot("capsule-navigation")
                        accessibilitySnapshot("capsule-navigation")
                        try { awaitUi { focused(uiContext.getString(id)) } }
                        catch (failure: Throwable) {
                            screenshot("capsule-focus-failure")
                            accessibilitySnapshot("capsule-focus-failure")
                            error("Cannot reach ${uiContext.getString(id)}: $failure")
                        }
                    }
                    screenshot("capsule-right-actions")
                    repeat(2) { sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(150) }
                    awaitUi { focused(uiContext.getString(R.string.track_quality)) }
                    clickText(uiLabel(R.string.track_quality))
                    awaitUi { visible(R.string.p_cancel) }
                    screenshot("capsule-quality-options-mint")
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { !visible(R.string.p_cancel) && visible(R.string.p_play) }
                    awaitUi { focused(uiLabel(R.string.track_quality)) }
                    check(!visible(R.string.playback_actual_direct)) { "Stream type label still visible" }
                    clickText(uiLabel(R.string.track_subtitles))
                    awaitUi { hasText("+") && hasText("−") }
                    val subtitlePrefs = targetContext.getSharedPreferences("subtitle_options", Context.MODE_PRIVATE)
                    val originalSize = subtitlePrefs.getInt("size", 100)
                    try {
                        subtitlePrefs.edit().putInt("size", 100).commit()
                        awaitUi { hasText(uiContext.getString(R.string.subtitle_size_value, 100)) }
                        clickText("+")
                        awaitUi { hasText(uiContext.getString(R.string.subtitle_size_value, 110)) }
                        clickText("−")
                        awaitUi { hasText(uiContext.getString(R.string.subtitle_size_value, 100)) }
                        screenshot("classic-v2-subtitle-size")
                    } finally { subtitlePrefs.edit().putInt("size", originalSize).commit() }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { !visible(R.string.p_cancel) }
                    clickText(uiLabel(R.string.rec_start))
                    awaitUi { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
                    check(!hasText("720p") && !hasText("1080p"))
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { visible(R.string.p_play) }
                    clickText(uiLabel(R.string.track_quality))
                    awaitUi { visible(R.string.p_cancel) }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    awaitUi { focused(uiLabel(R.string.track_quality)) }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                    awaitUi { visible(R.string.transition_seek_hint) }
                    val before = receiver.videoPlaybackInfo.value.positionMs
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                    awaitUi { (progressFraction(uiAutomation.rootInActiveWindow) ?: -1f) >= .99f }
                    screenshot("capsule-seek-draft")
                    sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    waitForIdleSync()
                    screenshot("capsule-seek-cancel")
                    accessibilitySnapshot("capsule-seek-cancel")
                    check(kotlin.math.abs(receiver.videoPlaybackInfo.value.positionMs - before) < 500)
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                    awaitUi { !visible(R.string.p_play) }
                    reveal()
                    output.append("PASS real local Media3 video, track actions, bounded seek buttons, seek draft cancel and hide/reveal.\n")
                    @Suppress("UNCHECKED_CAST")
                    val info = AirPlayService::class.java.getDeclaredField("_videoPlaybackInfo").apply { isAccessible = true }
                        .get(receiver) as kotlinx.coroutines.flow.MutableStateFlow<io.github.jqssun.airplay.service.VideoPlaybackInfo>
                    val mediaPlayer = receiver.airPlayVideoPlayer
                    val observer = mediaPlayer.onPlaybackInfo
                    try {
                        main { mediaPlayer.onPlaybackInfo = null; info.value = info.value.copy(live = true, seekable = false) }
                        awaitUi { !hasText("−10 s") && !hasText("+10 s") }
                        screenshot("capsule-live-ui-state")
                    } finally { main { mediaPlayer.onPlaybackInfo = observer } }
                    output.append("PASS unseekable live UI state hides seek controls (injected capability, not live AirPlay).\n")
                } finally { fixtures.close() }
                return
            }
            if (stage == "control-panel" || stage.startsWith("record-controls") || stage == "direct-controls") {
                fun visible(id: Int) = hasText(targetContext.getString(id)) || hasText(label(id))
                fun visibleLabel(id: Int) = if (hasText(targetContext.getString(id))) targetContext.getString(id) else label(id)
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true) }
                val session = receiver.receiverState.value.presentationSession
                val getter = receiver.videoRenderer.javaClass.getMethod("getOnFirstFrameSubmitted")
                @Suppress("UNCHECKED_CAST")
                val observer = getter.invoke(receiver.videoRenderer) as ((Long) -> Unit)
                main { observer(session) }
                if (stage == "direct-controls") {
                    @Suppress("UNCHECKED_CAST")
                    val active = AirPlayService::class.java.getDeclaredField("_videoPlaybackActive").apply { isAccessible = true }.get(receiver) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
                    main { active.value = true }
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitUi { visible(R.string.playback_choice_title) && visible(R.string.p_settings) }
                fun focusedAround(node: AccessibilityNodeInfo?): Boolean {
                    var current = node
                    repeat(4) {
                        if (current?.isFocused == true) return true
                        current = current?.parent
                    }
                    return false
                }
                awaitUi {
                    focusedAround(
                        findText(
                            uiAutomation.rootInActiveWindow,
                            targetContext.getString(R.string.picture_rotate, 0),
                        ),
                    )
                }
                check(!visible(R.string.p_hide_controls)) { "Hide controls remained in the panel" }
                check(!visible(R.string.p_return_home)) { "Return home remained in the panel" }
                for (id in listOf(R.string.playback_choice_title, R.string.scale_best_fit,
                    R.string.p_stop_confirm, R.string.p_settings)) check(visible(id))
                check(hasText(targetContext.getString(R.string.picture_rotate, 0)))
                if (stage == "direct-controls") {
                    fun buttonBounds(id: Int): android.graphics.Rect {
                        var node = findText(uiAutomation.rootInActiveWindow, visibleLabel(id))
                        repeat(6) {
                            if (node?.isClickable == true) return android.graphics.Rect().also { node!!.getBoundsInScreen(it) }
                            node = node?.parent
                        }
                        error("Missing button bounds")
                    }
                    val left = buttonBounds(R.string.playback_choice_title)
                    val right = buttonBounds(R.string.p_settings)
                    val screen = android.graphics.Rect().also { uiAutomation.rootInActiveWindow.getBoundsInScreen(it) }
                    check(kotlin.math.abs((left.left + right.right) / 2 - screen.centerX()) <= 8) { "Main control row is not centered: $left $right screen=$screen" }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                    awaitUi { !visible(R.string.playback_choice_title) }
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                    awaitUi { visible(R.string.playback_choice_title) }
                    screenshot("r31-direct-centered-controls")
                    output.append("PASS direct-mode main row centered by measured UI bounds; Rotate has initial focus. Synthetic direct-mode state.\n")
                    return
                }
                if (stage.startsWith("record-controls")) {
                    val height = if (stage.endsWith("720")) 720 else 1080
                    val previousRecords = io.github.jqssun.airplay.recording.RecordingService.files(targetContext).map { it.name }.toSet()
                    var producedRecord: java.io.File? = null
                    val recordingFeed = SyntheticVideoProbe.startRecordingFeed(receiver.videoRenderer)
                    val directAudio = stage.contains("-direct-")
                    val fixtureServer = if (directAudio) TransitionPlaybackProbe.FixtureServer(this) else null
                    val directSource = if (directAudio) io.github.jqssun.airplay.renderer.AirPlayVideoPlayer(targetContext) else null
                    val directSnapshot = java.util.concurrent.atomic.AtomicReference<io.github.jqssun.airplay.renderer.PlaybackSnapshot>()
                    val tone = if (stage.endsWith("audio") && !directAudio) RecordedAudioProbe.tone() else null
                    try {
                    if (directSource != null) {
                        directSource.onPlaybackInfo = { directSnapshot.set(it) }
                        io.github.jqssun.airplay.recording.CleanRecording.select("direct")
                        directSource.play(fixtureServer!!.url("live.m3u8"), 0f, 3501)
                        awaitState { directSnapshot.get()?.let { it.firstFrame && it.rate > 0 } == true }
                    }
                    clickText(visibleLabel(R.string.rec_start))
                    awaitUi { hasText("720p") && hasText("1080p") }
                    clickText("${height}p")
                    check(targetContext.getSharedPreferences("recording_options", Context.MODE_PRIVATE).getInt("quality", 0) == height)
                    screenshot("r31-recording-options")
                    clickText(visibleLabel(R.string.rec_start))
                    val consentLabels = listOf("Start now", "START NOW", "Şimdi başlat", "ŞİMDİ BAŞLAT", "Start recording", "Kaydı başlat", "Start sharing", "START SHARING", "Share screen", "SHARE SCREEN", "Başlat", "START")
                    awaitUi { (uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.systemui" && consentLabels.any { hasText(it) }) || visible(R.string.rec_unavailable) }
                    check(!visible(R.string.rec_unavailable)) { "TV capture consent is unavailable" }
                    val consentLabel = consentLabels.first { hasText(it) }
                    clickText(consentLabel)
                    awaitState { io.github.jqssun.airplay.recording.RecordingService.status.value.active }
                    SystemClock.sleep(4000)
                    sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                    clickText(visibleLabel(R.string.rec_stop))
                    awaitState { !io.github.jqssun.airplay.recording.RecordingService.status.value.active }
                    check(io.github.jqssun.airplay.recording.RecordingService.status.value.message == R.string.rec_saved)
                    val source = io.github.jqssun.airplay.recording.RecordingService.files(targetContext).single { it.name !in previousRecords }
                    producedRecord = source
                    val metadata = android.media.MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(source.absolutePath)
                        check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt() == minOf(height, targetContext.resources.displayMetrics.heightPixels))
                        check(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 2000)
                    } finally { metadata.release() }
                    val uri = io.github.jqssun.airplay.recording.RecordingExport.toMovies(targetContext, source)
                    try {
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        check(digest.digest(source.readBytes()).contentEquals(digest.digest(targetContext.contentResolver.openInputStream(uri)!!.use { it.readBytes() })))
                    } finally { targetContext.contentResolver.delete(uri, null, null) }
                    if (tone != null || directAudio) {
                        val peak = RecordedAudioProbe.verify(source)
                        output.append("PASS recorded AAC decoded with nonzero ${if (directAudio) "Media3 HLS audio" else "test-tone"} signal peak=$peak.\n")
                    }
                    check(receiver.receiverState.value.running && receiver.receiverState.value.presentationHeld)
                    output.append("PASS record panel, persisted ${height}p choice, real system consent/capture, stop from controls, MP4 metadata and export hash. Decoded synthetic AVC; real iPhone acceptance pending.\n")
                    } finally {
                        directSource?.stop(); fixtureServer?.close()
                        io.github.jqssun.airplay.recording.CleanRecording.select("mirror")
                        recordingFeed.close(); tone?.let { it.stop(); it.release() }; producedRecord?.delete()
                    }
                    return
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                awaitUi { !visible(R.string.playback_choice_title) }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                awaitUi { visible(R.string.playback_choice_title) }
                screenshot("r30-3-modern-controls")
                clickText(visibleLabel(R.string.p_settings))
                awaitUi { visible(R.string.p_section_connection) }
                screenshot("r30-3-settings-from-stream")
                check(receiver.receiverState.value.presentationHeld && receiver.receiverState.value.running)
                output.append("PASS compact stream panel: mode/fit/rotate/end/settings; initial focus landed on Rotate; removed hide/home; Settings opened while receiver and presentation remained active. Synthetic session, not iPhone acceptance.\n")
                return
            }
            if (stage == "zero-pts-return") {
                val end = AirPlayService::class.java.getDeclaredMethod("_endVideoPlayback", String::class.java, Integer::class.java).apply { isAccessible = true }
                main { receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true) }
                SyntheticVideoProbe.startRecordingFeed(receiver.videoRenderer, zeroPts = true).use {
                    awaitState { receiver.receiverState.value.firstFrameSubmitted }
                    main {
                        check(receiver.setRemoteVideoPlaybackEnabled(false))
                        check(receiver.receiverState.value.firstFrameSubmitted)
                        check(receiver.receiverState.value.mediaErrorCode == null)
                    }
                    repeat(3) {
                        main { end.invoke(receiver, "Test remote return with zero sender PTS", 5003) }
                        awaitState { receiver.receiverState.value.firstFrameSubmitted && receiver.receiverState.value.mediaErrorCode == null }
                    }
                    main { receiver.onConnectionReset(7) }
                    awaitState { receiver.receiverState.value.firstFrameSubmitted && receiver.receiverState.value.mediaErrorCode == null }
                }
                output.append("PASS real MediaCodec/EGL zero-PTS screen: preference preserves visible frame; three returns and a declined remote request recover with newly submitted frames. YouTube/iPhone not exercised.\n")
                return
            }
            if (stage == "return-error") {
                val end = AirPlayService::class.java.getDeclaredMethod("_endVideoPlayback", String::class.java, Integer::class.java).apply { isAccessible = true }
                main {
                    receiver.onConnectionInit(); syntheticSession = true
                    receiver.onMirrorRunning(true)
                    end.invoke(receiver, "Synthetic denied source", 403)
                    check(receiver.receiverState.value.mediaErrorCode == 403)
                    receiver.onVideoStop()
                    check(receiver.receiverState.value.mediaErrorCode == 403) { "Late stop erased source error" }
                    check(receiver.setRemoteVideoPlaybackEnabled(false))
                    check(receiver.receiverState.value.mediaErrorCode == 5003)
                    receiver.onVideoStop()
                    check(receiver.receiverState.value.mediaErrorCode == 5003) { "Late stop erased mirror return guidance" }
                    val state = receiver.receiverState.value
                    check(state.observeFirstFrame(state.presentationSession).mediaErrorCode == null)
                }
                output.append("PASS source error and mirror-return notice survive late sender stop; valid screen-frame observation clears notice. Synthetic callbacks, not iPhone media acceptance.\n")
                return
            }
            if (stage == "transport-end") {
                fun probe(socket: java.net.Socket) {
                    socket.soTimeout = 6000
                    socket.getOutputStream().write("OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    check(reader.readLine()?.contains(" 200 ") == true)
                    while (!reader.readLine().isNullOrEmpty()) { }
                }
                repeat(3) {
                    java.net.Socket("127.0.0.1", port).use { socket ->
                        probe(socket)
                        awaitState { receiver.connectionCount.value > 0 }
                        main {
                            receiver.onMirrorRunning(true)
                            receiver.endCurrentStream()
                            receiver.onMirrorRunning(true)
                            receiver.onVideoSize(498f, 1080f, 498f, 1080f)
                            receiver.onVideoPrepare("old", "old")
                            check(!receiver.receiverState.value.presentationHeld) { "Late callback revived ended stream" }
                        }
                        check(socket.getInputStream().read() == -1) { "Sender socket still open" }
                    }
                    awaitState { receiver.connectionCount.value == 0 }
                    check(receiver.receiverState.value.running)
                    check(handleField.getLong(receiver) == handle)
                    java.net.Socket("127.0.0.1", port).use { fresh -> probe(fresh) }
                    awaitState { receiver.connectionCount.value == 0 }
                }
                output.append("PASS 3 real TCP disconnect/reconnect cycles; listener retained; stale mirror/media callbacks ignored. iPhone status requires real sender test.\n")
                return
            }
            if (stage == "end-stream") {
                main {
                    receiver.onConnectionInit(); syntheticSession = true
                    receiver.onMirrorRunning(true)
                    check(receiver.receiverState.value.running)
                    receiver.endCurrentStream()
                    check(receiver.receiverState.value.running) { "Ending a stream stopped the receiver" }
                    check(receiver.receiverState.value.airplay != io.github.jqssun.airplay.receiver.Advertisement.IDLE) {
                        "Ending a stream removed AirPlay discovery"
                    }
                    check(!receiver.receiverState.value.presentationHeld)
                }
                output.append("PASS ending current stream releases presentation but keeps receiver and AirPlay discovery running. Synthetic connection, not iPhone disconnect.\n")
                return
            }
            if (stage == "playback-help" || stage == "playback-help-debug") {
                experience.edit().remove(UserExperiencePrefs.PLAYBACK_HELP_SEEN).commit()
                fun visibleLabel(id: Int) = if (hasText(targetContext.getString(id))) targetContext.getString(id) else label(id)
                fun visible(id: Int) = hasText(targetContext.getString(id)) || hasText(label(id))
                val end = AirPlayService::class.java.getDeclaredMethod("_endVideoPlayback", String::class.java, Integer::class.java).apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val flow = AirPlayService::class.java.getDeclaredField("_receiverState").apply { isAccessible = true }.get(receiver) as kotlinx.coroutines.flow.MutableStateFlow<io.github.jqssun.airplay.receiver.ReceiverUiState>
                main {
                    receiver.onConnectionInit(); syntheticSession = true; receiver.onMirrorRunning(true)
                    end.invoke(receiver, "Synthetic denied source", 403)
                    // A new frame can remove error text before Compose sees it; guidance must survive.
                    flow.value = flow.value.copy(mediaErrorCode = null, firstFrameSubmitted = true)
                }
                awaitUi { visible(R.string.playback_help_confirm) }
                screenshot("r28-first-playback-help")
                fun selected(): Boolean {
                    if (android.os.Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
                    fun selectedChild(node: AccessibilityNodeInfo?, depth: Int): Boolean {
                        if (node == null || depth > 3) return false
                        if (node.isSelected || node.stateDescription?.toString()?.lowercase() in setOf("selected", "seçili")) return true
                        return (0 until node.childCount).any { selectedChild(node.getChild(it), depth + 1) }
                    }
                    // Compose exposes the clickable semantics as a sibling of its label.
                    val labelNode = findText(uiAutomation.rootInActiveWindow, visibleLabel(R.string.playback_choice_title))
                    return selectedChild(labelNode?.parent, 0)
                }
                fun countPulses(): Int {
                    var count = 0; var previous = false
                    val until = SystemClock.elapsedRealtime() + 6800
                    while (SystemClock.elapsedRealtime() < until) {
                        val current = selected()
                        if (current && !previous) count++
                        previous = current; SystemClock.sleep(80)
                    }
                    return count
                }
                clickText(visibleLabel(R.string.playback_help_confirm))
                val firstPulses = countPulses()
                screenshot("r28-help-after-confirm")
                accessibilitySnapshot("r28-help-after-confirm")
                if (stage == "playback-help-debug") {
                    output.append("DEBUG pulses=$firstPulses held=${receiver.receiverState.value.presentationHeld} frame=${receiver.receiverState.value.firstFrameSubmitted}\n")
                    return
                }
                check(firstPulses == 5) { "First failure produced $firstPulses pulses instead of five" }
                check(experience.getBoolean(UserExperiencePrefs.PLAYBACK_HELP_SEEN, false))
                main { end.invoke(receiver, "Second denied source", 403) }
                val secondPulses = countPulses()
                check(secondPulses == 5) { "Second failure produced $secondPulses pulses instead of five" }
                check(!visible(R.string.playback_help_confirm)) { "One-time help repeated" }
                clickText(visibleLabel(R.string.playback_choice_title))
                awaitUi { visible(R.string.playback_mirror) }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                check(countPulses() == 0) { "Returning from choices replayed pulse" }
                screenshot("r28-playback-error-controls")
                output.append("PASS one-time help, frame-cleared error guidance, five pulses per failure and no replay after dialog close. Synthetic errors, not iPhone video.\n")
                return
            }
            if (stage == "auth-fixture") {
                val access = targetContext.getSharedPreferences("trusted_airplay_devices", Context.MODE_PRIVATE)
                val before = access.getStringSet("verified_public_keys", emptySet())!!.toSet()
                val injectedKey = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg="
                val done = File(targetContext.filesDir, "auth-fixture-done")
                val portFile = File(targetContext.filesDir, "auth-fixture-port")
                val pinPortFile = File(targetContext.filesDir, "auth-fixture-pin-port")
                // Test APK only: relay the ephemeral challenge in memory over loopback.
                // No PIN is written to files or logs; production has no such endpoint.
                val pinSocket = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
                pinSocket.soTimeout = 100
                done.delete()
                try {
                    check(io.github.jqssun.airplay.receiver.TrustedDevices(access).register(injectedKey))
                    portFile.writeText(port.toString())
                    pinPortFile.writeText(pinSocket.localPort.toString())
                    val deadline = SystemClock.elapsedRealtime() + 240_000
                    while (!done.exists() && SystemClock.elapsedRealtime() < deadline) {
                        try { pinSocket.accept().use { it.getOutputStream().write((receiver.receiverState.value.pin ?: "WAIT").toByteArray()) } }
                        catch (_: java.net.SocketTimeoutException) { }
                    }
                    check(done.exists()) { "External protocol probe did not finish" }
                    output.append("Fixture completed; see external protocol results. No PIN or private keys logged.\n")
                } finally {
                    access.edit().putStringSet("verified_public_keys", before).commit()
                    pinSocket.close(); pinPortFile.delete(); portFile.delete(); done.delete()
                }
                return
            }
            if (stage == "background") {
                @Suppress("UNCHECKED_CAST")
                val active = AirPlayService::class.java.getDeclaredField("_videoPlaybackActive").apply { isAccessible = true }.get(receiver) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
                val progress = AirPlayService::class.java.getDeclaredField("lastRemoteProgressAt").apply { isAccessible = true }
                main {
                    receiver.onConnectionInit(); syntheticSession = true
                    active.value = true
                    progress.setLong(receiver, SystemClock.elapsedRealtime())
                    receiver.onConnectionDestroy(); syntheticSession = false
                }
                SystemClock.sleep(3100)
                check(active.value) { "Continuing playback stopped just because control socket closed" }
                main { progress.setLong(receiver, SystemClock.elapsedRealtime() - 20_000) }
                awaitState { !active.value }
                output.append("PASS no-control-socket playback retained while progressing; stale progress cleaned up. Synthetic policy input, not an iPhone lock test.\n")
                return
            }
            if (stage.startsWith("access")) {
                fun exchange(socket: java.net.Socket, request: ByteArray): String {
                    socket.soTimeout = 4000
                    socket.getOutputStream().write(request)
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val status = reader.readLine() ?: error("Missing status")
                    while (true) { val line = reader.readLine() ?: error("Incomplete headers"); if (line.isEmpty()) break }
                    return status
                }
                fun options() = "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n".toByteArray()
                java.net.Socket("127.0.0.1", port).use { first ->
                    check(exchange(first, options()).contains(" 200 "))
                    java.net.Socket("127.0.0.1", port).use { second ->
                        val status = exchange(second, options())
                        check(status.contains(if (stage == "access-takeover") " 200 " else " 409 ")) { status }
                        if (stage != "access-takeover") check(exchange(first, options()).contains(" 200 "))
                    }
                }
                awaitState { receiver.connectionCount.value == 0 }
                val accessPrefs = targetContext.getSharedPreferences("trusted_airplay_devices", Context.MODE_PRIVATE)
                val beforeKeys = accessPrefs.getStringSet("verified_public_keys", emptySet())!!.toSet()
                val beforeBlocked = accessPrefs.getStringSet("blocked_keys", emptySet())!!.toSet()
                val access = io.github.jqssun.airplay.receiver.TrustedDevices(accessPrefs)
                val testKey = "A".repeat(43) + "="
                try {
                    check(access.register(testKey))
                    check(access.setBlocked(testKey, true))
                    check(!access.register(testKey))
                    check(!receiver.onClientAllowed(testKey))
                    java.net.Socket("127.0.0.1", port).use { client ->
                        val body = ByteArray(68).apply { this[0] = 1 }
                        val header = "POST /pair-verify RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 68\r\n\r\n".toByteArray()
                        check(exchange(client, header + body).contains(" 403 "))
                    }
                    check(access.setBlocked(testKey, false))
                    check(receiver.onClientAllowed(testKey))
                    output.append("PASS two control clients ($stage); blocked key rejected by actual pair-verify; unblock restored admission. Not two authenticated iPhones.\n")
                } finally {
                    accessPrefs.edit().putStringSet("verified_public_keys", beforeKeys).putStringSet("blocked_keys", beforeBlocked).commit()
                }
                return
            }
            fun httpStatus(path: String, extraHeader: String = ""): String {
                return java.net.Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write(("POST $path HTTP/1.1\r\nHost: localhost\r\n" + extraHeader + "Content-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val status = reader.readLine() ?: error("Receiver closed without a response")
                    var completed = false
                    repeat(100) { if (!completed) { val line = reader.readLine() ?: error("Incomplete response"); if (line.isEmpty()) completed = true } }
                    check(completed) { "Response headers not completed" }
                    status
                }
            }
            repeat(25) {
                main { check(receiver.setRemoteVideoPlaybackEnabled(false)) }
                for (path in listOf("/play", "/action")) check(httpStatus(path).contains(" 409 "))
                awaitState { receiver.receiverState.value.mediaErrorCode == 5003 }
                check(httpStatus("/play", "X-Test: " + "a".repeat(2048) + "\r\n").contains(" 431 "))
                check(handleField.getLong(receiver) == handle && receiver.receiverState.value.running)
                val txt = io.github.jqssun.airplay.bridge.NativeBridge.nativeGetAirplayTxtRecords(handle)!!
                val bits = txt.getValue("features").substringBefore(',').removePrefix("0x").toLong(16)
                check(bits and 0x11L == 0L && bits and 0x80L != 0L)
                main { receiver.onVideoPlay("https://invalid.example/blocked.m3u8", 0f) }
                check(!receiver.videoPlaybackActive.value)
                main { check(receiver.setRemoteVideoPlaybackEnabled(true)) }
                check(handleField.getLong(receiver) == handle && receiver.receiverState.value.running)
            }
            output.append("PASS complete native HTTP 409 and 431 replies across 25 mode toggles; video flags disabled, mirroring retained.\n")
            fun visible(id: Int) = hasText(targetContext.getString(id)) || hasText(label(id))
            fun visibleLabel(id: Int) = if (hasText(targetContext.getString(id))) targetContext.getString(id) else label(id)
            main { receiver.onConnectionInit(); syntheticSession = true; receiver.onVideoPrepare("probe", "probe") }
            awaitUi { targetWindowVisible() }
            waitForIdleSync()
            SystemClock.sleep(300)
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            awaitUi { visible(R.string.playback_choice_title) }
            screenshot("rc23-controls")
            @Suppress("UNCHECKED_CAST")
            val uiState = AirPlayService::class.java.getDeclaredField("_receiverState").apply { isAccessible = true }.get(receiver) as kotlinx.coroutines.flow.MutableStateFlow<io.github.jqssun.airplay.receiver.ReceiverUiState>
            main { uiState.value = uiState.value.copy(mediaErrorCode = 403) }
            awaitUi { visible(R.string.transition_access_denied) }
            waitForIdleSync()
            SystemClock.sleep(300)
            screenshot("rc23-error-focus")
            // No navigation/clickText: OK must open the choice already focused by the error.
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            awaitUi { visible(R.string.playback_mirror) }
            screenshot("rc25-playback-choice")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            SystemClock.sleep(2600)
            repeat(3) {
                clickText(visibleLabel(R.string.playback_choice_title))
                awaitUi { visible(R.string.playback_mirror) }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                repeat(12) {
                    val node = findText(uiAutomation.rootInActiveWindow, visibleLabel(R.string.playback_choice_title))
                    check(node?.isSelected != true && node?.parent?.isSelected != true) { "Hint replayed after dialog close" }
                    SystemClock.sleep(100)
                }
            }
            clickText(visibleLabel(R.string.playback_choice_title))
            awaitUi { visible(R.string.playback_mirror) }
            clickText(visibleLabel(R.string.playback_mirror))
            awaitUi { visible(R.string.playback_return_to_screen) }
            screenshot("rc23-return-guidance")
            check(handleField.getLong(receiver) == handle && receiver.receiverState.value.running)
            val waiting = uiState.value.copy(presentationSession = 123L, mirrorRequested = true)
            check(waiting.observeFirstFrame(122L).mediaErrorCode == 5003)
            check(waiting.observeFirstFrame(123L).mediaErrorCode == null)
            check(waiting.sessionEnded().mediaErrorCode == null)
            clickText(visibleLabel(R.string.p_settings))
            awaitUi { visible(R.string.p_section_connection) }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitUi { visible(R.string.p_show_mirror) }
            waitForIdleSync(); SystemClock.sleep(300)
            screenshot("rc25-home-return-focus")
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            awaitUi { visible(R.string.playback_choice_title) }
            output.append("PASS hint does not replay across 3 dialogs; Back from Settings focuses Show mirroring (OK resumes overlay).\n")

            output.append("PASS native handle preserved across toggles; video flags disabled, mirroring flag retained; UI choice exercised.\n")
        } finally {
            if (::receiver.isInitialized) main { receiver.stopServer() }
            if (bound) main { targetContext.unbindService(connection); bound = false }
            main { activity.finish() }
            if (stage == "capsule-ui" && capsuleLocale != null) {
                if (originalCapsuleLanguage == null) experience.edit().remove(UserExperiencePrefs.LANGUAGE).commit()
                else experience.edit().putString(UserExperiencePrefs.LANGUAGE, originalCapsuleLanguage).commit()
            }
        }
    }

    private fun runPresentationProbe() {
        val prefs = targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        check(prefs.getBoolean(Prefs.RUN_IN_BACKGROUND, Prefs.DEF_RUN_IN_BACKGROUND)) { "Background operation disabled" }
        check(io.github.jqssun.airplay.receiver.ReceiverPresentation.automaticDisplayAllowed(targetContext)) { "Automatic display permission missing" }
        activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        runOnMainSync {
            bound = targetContext.bindService(Intent(targetContext, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        try {
            check(bound && ready.await(10, TimeUnit.SECONDS))
            awaitState { receiver.receiverState.value.running }
            check(receiver.connectionCount.value == 0) { "Real sender connected; test cannot inject callbacks" }
            // This probe runs on the user's current locale; the general harness forces English.
            fun visibleLabel(id: Int) = hasText(targetContext.getString(id)) || hasText(label(id))
            awaitUi { targetWindowVisible() && visibleLabel(R.string.p_stop) }
            repeat(2) { round ->
                if (round == 1) {
                    main { check(activity.moveTaskToBack(true)) }
                    awaitState { !io.github.jqssun.airplay.receiver.ReceiverPresentation.isVisible() }
                    check(receiver.receiverState.value.running) { "Background receiver stopped" }
                }
                var receivedAt = 0L
                runOnMainSync {
                    receiver.onConnectionInit(); syntheticSession = true
                    receivedAt = SystemClock.elapsedRealtime()
                    receiver.onDisplayPin("0000")
                    check(receiver.receiverState.value.pin == "0000") { "PIN was not published synchronously" }
                }
                await(if (round == 0) 1200 else 5000) { visibleLabel(R.string.p_pair_title) }
                val elapsed = SystemClock.elapsedRealtime() - receivedAt
                check(targetWindowVisible())
                output.append("PASS ").append(if (round == 0) "IMMEDIATE_PIN" else "BACKGROUND_PIN_FOREGROUND")
                    .append(" elapsedMs=").append(elapsed).append("; synthetic callback, no iPhone pairing test\n")
                main { receiver.cancelPairing(); syntheticSession = false }
                awaitState { !receiver.receiverState.value.running }
                if (round == 0) { startReceiver(); awaitUi { targetWindowVisible() } }
            }
        } finally {
            if (::receiver.isInitialized) main { receiver.stopServer() }
            if (bound) runOnMainSync { targetContext.unbindService(connection); bound = false }
            runOnMainSync { activity.finish() }
        }
    }

    private fun runQuitProbe() {
        val experience = targetContext.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
        val keys = listOf(UserExperiencePrefs.SETUP_COMPLETE, UserExperiencePrefs.LANGUAGE, UserExperiencePrefs.USER_STOPPED)
        val saved = keys.associateWith { experience.all[it] }
        val prefs = targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val savedAuto = mapOf(Prefs.AUTO_START to prefs.all[Prefs.AUTO_START])
        var launched: MainActivity? = null
        try {
            check(experience.edit().putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true)
                .putString(UserExperiencePrefs.LANGUAGE, "en").putBoolean(UserExperiencePrefs.USER_STOPPED, false).commit())
            check(prefs.edit().putBoolean(Prefs.AUTO_START, true).commit())
            launched = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            check(ContextWrapper(ContextWrapper(launched)).mainActivity() === launched)
            awaitUi { hasText(label(R.string.p_quit)) }
            val manager = targetContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            fun receiverRunning() = manager.getRunningServices(50).any {
                it.service.className == AirPlayService::class.java.name
            }
            awaitState { receiverRunning() }
            clickText(label(R.string.p_quit))
            awaitUi { hasText(label(R.string.p_quit_title)) }
            clickText(label(R.string.p_quit))
            awaitState { !receiverRunning() }
            awaitUi { !targetWindowVisible() }
            check(experience.getBoolean(UserExperiencePrefs.USER_STOPPED, false))
        } finally {
            launched?.let { activity -> main { if (!activity.isFinishing) activity.finishAndRemoveTask() } }
            restore(experience, saved)
            restore(prefs, savedAuto)
        }
    }

    private fun scenario(name: String, action: () -> Unit) {
        if (themeOnly && name !in listOf("real Android activity and receiver startup", "three AirRelay themes and popup selection")) return
        if (networkOnly && name !in listOf("real Android activity and receiver startup",
            "network-loss callback displays waiting and restores without restarting",
            "simulated Wi-Fi identity and IP changes refresh discovery",
            "discovery failure callback displays an error and recovers")) return

        if (seekOnly && name !in listOf("real Android activity and receiver startup",
            "cancelled media preparation cannot restart from a late callback",
            "remote videos retain broadcast UI and seek draft cancels")) return
        val begin = SystemClock.elapsedRealtime()
        try {
            action()
            results.put(JSONObject().put("name", name).put("result", "PASS")
                .put("elapsedMs", SystemClock.elapsedRealtime() - begin))
            output.append("PASS [SIMULATED_CALLBACKS]: ").append(name).append('\n')
        } catch (failure: Throwable) {
            results.put(JSONObject().put("name", name).put("result", "FAIL")
                .put("error", failure.javaClass.simpleName))
            throw failure
        }
    }

    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
        waitForIdleSync()
    }
    private fun savedName() = targetContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        .getString(Prefs.SERVER_NAME, Prefs.DEF_SERVER_NAME) ?: Prefs.DEF_SERVER_NAME
    private fun nativeHandle() = receiver.javaClass.getDeclaredField("nativeHandle").apply { isAccessible = true }.getLong(receiver)
    private fun startReceiver() {
        main { receiverCommand("start") { receiver.startServer(savedName()) } }
        awaitState { receiver.receiverState.value.running }
    }
    /** Measures only this real main-thread call, excluding dispatch/wait time and async work. */
    private fun receiverCommand(name: String, action: () -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val beginNs = SystemClock.elapsedRealtimeNanos()
        try { action() } finally {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - beginNs) / 1_000_000.0
            invocationSamples.put(JSONObject().put("command", name)
                .put("operation", if (name.startsWith("start") || name == "rapid-start") "start" else "stop").put("elapsedMs", elapsedMs))
        }
    }
    private fun invocationSummary(): JSONObject {
        var maxMs = 0.0
        val byOperation = JSONObject()
        for (operation in listOf("start", "stop")) {
            var count = 0
            var operationMaxMs = 0.0
            for (i in 0 until invocationSamples.length()) {
                val sample = invocationSamples.getJSONObject(i)
                if (sample.getString("operation") == operation) {
                    count++
                    operationMaxMs = maxOf(operationMaxMs, sample.getDouble("elapsedMs"))
                }
            }
            byOperation.put(operation, JSONObject().put("sampleCount", count).put("maxMs", operationMaxMs))
        }
        for (i in 0 until invocationSamples.length()) {
            maxMs = maxOf(maxMs, invocationSamples.getJSONObject(i).getDouble("elapsedMs"))
        }
        output.append("MEASURED MAIN-THREAD CALLS: count=").append(invocationSamples.length())
            .append(" maxMs=").append(maxMs).append("; not a frame-jank or full native startup measurement.\n")
        return JSONObject().put("sampleCount", invocationSamples.length()).put("maxMs", maxMs)
            .put("kind", "MAIN_THREAD_INVOCATION_ONLY_NOT_FRAME_JANK")
            .put("excludes", "dispatch wait, asynchronous startup, frames and end-to-end media latency")
            .put("coverage", "direct harness start/stop calls; UI-triggered stop is a separate state assertion")
            .put("byOperation", byOperation)
            .put("samples", invocationSamples)
    }
    private fun awaitState(predicate: () -> Boolean) = await(10_000, predicate)
    private fun awaitUi(predicate: () -> Boolean) = await(5_000, predicate)
    private fun await(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(80)
        }
        check(predicate()) { "Bounded state/UI wait timed out" }
    }
    private fun label(id: Int): String {
        val config = Configuration(targetContext.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        return targetContext.createConfigurationContext(config).getString(id)
    }
    private fun targetWindowVisible(): Boolean = uiAutomation.rootInActiveWindow?.let {
        it.packageName?.toString() == targetContext.packageName && it.childCount > 0
    } == true
    private fun progressFraction(root: AccessibilityNodeInfo?): Float? {
        if (root == null || !root.refresh()) return null
        root.rangeInfo?.let { range ->
            if (range.max > range.min) return (range.current - range.min) / (range.max - range.min)
        }
        for (index in 0 until root.childCount) progressFraction(root.getChild(index))?.let { return it }
        return null
    }
    private fun runLanguageRevision() {
        val policy = io.github.jqssun.airplay.receiver.AppLanguages
        check(policy.choices.size == 16 && policy.choices.map { it.first }.distinct().size == 16)
        check(policy.resolve("system", listOf(Locale.FRENCH)).language == "fr")
        check(policy.resolve("system", listOf(Locale.forLanguageTag("sv"))).language == "en")
        check(policy.resolve("system", listOf(Locale.forLanguageTag("sv"), Locale.GERMAN)).language == "de")
        check(policy.resolve("system", listOf(Locale.forLanguageTag("pt-PT"))).toLanguageTag() == "pt-BR")
        check(policy.resolve("system", listOf(Locale.forLanguageTag("id-ID"))).language in listOf("id", "in"))
        check(policy.resolve("tr", listOf(Locale.JAPANESE)).language == "tr")
        check(!policy.isSupportedSelection("invalid"))
        val prefs = targetContext.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
        val keys = listOf(UserExperiencePrefs.LANGUAGE, UserExperiencePrefs.SETUP_COMPLETE,
            UserExperiencePrefs.NOTIFICATION_PROMPTED, UserExperiencePrefs.LARGE_TEXT)
        val saved = keys.associateWith { prefs.all[it] }
        try {
            prefs.edit().putString(UserExperiencePrefs.LANGUAGE, "en")
                .putBoolean(UserExperiencePrefs.SETUP_COMPLETE, true)
                .putBoolean(UserExperiencePrefs.NOTIFICATION_PROMPTED, true)
                .putBoolean(UserExperiencePrefs.LARGE_TEXT, true).commit()
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            main { bound = targetContext.bindService(Intent(targetContext, AirPlayService::class.java), connection, Context.BIND_AUTO_CREATE) }
            check(bound && ready.await(10, TimeUnit.SECONDS))
            check(receiver.connectionCount.value == 0) { "Real connection exists; test refuses to disturb it" }
            awaitState { !receiver.receiverState.value.starting }
            if (!receiver.receiverState.value.running) startReceiver()
            val handle = nativeHandle()
            check(handle != 0L) { "Language test requires a running native receiver" }
            fun textFor(code: String, id: Int): String = targetContext.createConfigurationContext(
                policy.configuration(targetContext.resources.configuration, code)).getString(id)
            clickText(textFor("en", R.string.p_settings))
            clickText(textFor("en", R.string.p_section_appearance))
            var current = "en"
            fun row(): String = textFor(current, R.string.p_language) + ": " +
                (policy.choices.firstOrNull { it.first == current }?.second ?: textFor(current, R.string.p_language_system))
            fun chooseWithRemote(code: String) {
                val choices = listOf("system" to textFor(current, R.string.p_language_system)) + policy.choices
                var index = choices.indexOfFirst { it.first == current }
                val target = choices.indexOfFirst { it.first == code }
                awaitUi { focusedText(choices[index].second) }
                while (index != target) {
                    val direction = if (target > index) 1 else -1
                    sendKeyDownUpSync(if (direction > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                    index += direction
                    awaitUi { focusedText(choices[index].second) }
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            }
            for ((code, _) in policy.choices) {
                output.append("Selecting $code from $current\n")
                revealText(row()); clickText(row())
                try { chooseWithRemote(code) } catch (failure: Throwable) {
                    screenshot("language-failed-$code")
                    accessibilitySnapshot("language-failed-$code")
                    throw failure
                }
                current = code
                awaitUi { focusedText(row()) }
                check(prefs.getString(UserExperiencePrefs.LANGUAGE, null) == code)
                check(nativeHandle() == handle) { "Language change restarted receiver" }
                val config = policy.configuration(targetContext.resources.configuration, code)
                check((config.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) == (code == "ar"))
                screenshot("language-$code-large-text")
            }
            clickText(row()); chooseWithRemote("system"); current = "system"
            awaitUi { focusedText(row()) }
            clickText(row())
            awaitUi { focusedText(textFor(current, R.string.p_language_system)) }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitUi { focusedText(row()) }
            check(nativeHandle() == handle)
            output.append("PASS all 16 languages selected through real UI; persistence, focus restoration, dialog Back, RTL config, system fallback, large text and unchanged native handle. Screenshots saved. Not human translation review or AirPlay acceptance.\n")
        } finally {
            restore(prefs, saved)
            if (bound) main { targetContext.unbindService(connection); bound = false }
            if (::activity.isInitialized) main { activity.finish() }
        }
    }

    private fun findText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null || !root.refresh()) return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (i in 0 until root.childCount) findText(root.getChild(i), text)?.let { return it }
        return null
    }
    private fun hasText(text: String) = findText(uiAutomation.rootInActiveWindow, text) != null
    private fun clickText(text: String) {
        awaitUi { hasText(text) }
        var node = findText(uiAutomation.rootInActiveWindow, text)
        repeat(6) {
            if (node?.isClickable == true && node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                waitForIdleSync()
                return
            }
            node = node?.parent
        }
        error("Visible test action has no clickable accessibility ancestor")
    }
    private fun focusedText(text: String): Boolean {
        var node = findText(uiAutomation.rootInActiveWindow, text)
        while (node != null) {
            node.refresh()
            if (node.isFocused) return true
            node = node.parent
        }
        return false
    }
    private fun revealText(text: String) {
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable) return node
            for (i in node.childCount - 1 downTo 0) scrollable(node.getChild(i))?.let { return it }
            return null
        }
        repeat(8) {
            if (findText(uiAutomation.rootInActiveWindow, text)?.isVisibleToUser == true) return
            scrollable(uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            waitForIdleSync()
        }
        repeat(8) {
            if (findText(uiAutomation.rootInActiveWindow, text)?.isVisibleToUser == true) return
            check(scrollable(uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true)
            waitForIdleSync()
        }
        check(hasText(text)) { "Setting not found after bounded scrolling" }
    }
    private fun accessibilitySnapshot(name: String) {
        check(receiver.receiverState.value.pin == null)
        val nodes = JSONArray()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40 || nodes.length() > 1000 || !node.refresh()) return
            val value = JSONObject().put("depth", depth).put("text", node.text?.toString())
                .put("description", node.contentDescription?.toString()).put("focused", node.isFocused)
                .put("class", node.className?.toString())
            node.rangeInfo?.let { value.put("range", JSONObject().put("min", it.min).put("max", it.max).put("value", it.current)) }
            nodes.put(value)
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(uiAutomation.rootInActiveWindow, 0)
        File(targetContext.filesDir, "integration-simulated-callbacks/$name.json").writeText(nodes.toString(2))
    }
    private fun screenshot(name: String) {
        check(receiver.receiverState.value.pin == null)
        val bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "Screenshot unavailable" }
        val directory = File(targetContext.filesDir, "integration-simulated-callbacks").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun restore(prefs: SharedPreferences, values: Map<String, Any?>) {
        val edit = prefs.edit()
        values.forEach { (key, value) -> when (value) {
            null -> edit.remove(key)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            else -> error("Unexpected experience setting type")
        } }
        check(edit.commit()) { "Experience setting restore failed" }
    }
}
