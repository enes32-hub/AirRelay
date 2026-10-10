import android.content.Context
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.diagnostics.*
import io.github.jqssun.airplay.service.AirPlayService
import kotlinx.coroutines.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

private var passed = 0
private fun checkCase(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS $name") }
    catch (t: Throwable) { throw AssertionError("FAIL $name", t) }
}
fun main() = runBlocking {
    val ready = ReceiverUiState(running = true, network = LocalNetwork.WIFI,
        airplay = Advertisement.REGISTERED, raop = Advertisement.REGISTERED)
    checkCase("idle is not ready") { check(ReceiverUiState().phase == ReceiverPhase.STOPPED) }
    checkCase("start is explicit") { check(ready.copy(starting = true).phase == ReceiverPhase.STARTING) }
    checkCase("NSD registration is required") { check(ready.copy(airplay = Advertisement.REGISTERING).phase == ReceiverPhase.DISCOVERY_WAIT) }
    checkCase("NSD failure is visible") { check(ready.copy(airplay = Advertisement.FAILED).phase == ReceiverPhase.DISCOVERY_ERROR) }
    checkCase("registered AirPlay is ready") { check(ready.phase == ReceiverPhase.READY) }
    checkCase("Ethernet works without internet validation") { check(ready.copy(network = LocalNetwork.ETHERNET).phase == ReceiverPhase.READY) }
    checkCase("network loss wins over ready") { check(ready.copy(network = LocalNetwork.UNAVAILABLE).phase == ReceiverPhase.NETWORK_WAIT) }
    checkCase("permission denial is visible") { check(ready.copy(network = LocalNetwork.PERMISSION_DENIED).phase == ReceiverPhase.NETWORK_WAIT) }
    checkCase("unknown network does not claim missing LAN") { check(ready.copy(network = LocalNetwork.UNKNOWN).phase == ReceiverPhase.READY) }
    checkCase("TCP connect is not mirroring") { check(ready.copy(connections = 1).phase == ReceiverPhase.CONNECTING) }
    checkCase("PIN is pairing not live") { check(ready.copy(pin = "1234", mirrorRequested = true, firstFrameSubmitted = true).phase == ReceiverPhase.PAIRING) }
    checkCase("video size alone is waiting") { check(ready.copy(mirrorRequested = true).phase == ReceiverPhase.WAITING_VIDEO) }
    checkCase("submitted frame marks mirroring") { check(ready.copy(mirrorRequested = true, firstFrameSubmitted = true).phase == ReceiverPhase.MIRRORING) }
    checkCase("old first-frame fact alone is not live") { check(ready.copy(firstFrameSubmitted = true).phase == ReceiverPhase.READY) }
    checkCase("audio has a separate phase") { check(ready.copy(audioConnected = true).phase == ReceiverPhase.AUDIO_CONNECTED) }
    checkCase("play path has a separate phase") { check(ready.copy(mediaPlaying = true).phase == ReceiverPhase.MEDIA_PLAYING) }
    checkCase("pending media blocks applying settings") { check(!ready.copy(mediaPending = true, pendingSettings = true).canApplySettings) }
    checkCase("start failure never claims readiness") { check(ready.copy(notice = ReceiverNotice.START_FAILED).phase == ReceiverPhase.ERROR) }
    listOf(ready.copy(connections = 1), ready.copy(pin = "1234"), ready.copy(mirrorRequested = true),
        ready.copy(audioConnected = true), ready.copy(mediaPlaying = true), ready.copy(starting = true))
        .forEachIndexed { i, s -> checkCase("busy condition $i blocks restart") { check(!s.copy(pendingSettings = true).canApplySettings) } }
    checkCase("idle can explicitly apply drafts") { check(ready.copy(pendingSettings = true).canApplySettings) }
    checkCase("session cleanup clears all private session facts") {
        val ended = ready.copy(connections = 3, pin = "1234", mirrorRequested = true, firstFrameSubmitted = true,
            audioConnected = true, mediaPlaying = true, mediaPending = true, pendingSettings = true).sessionEnded()
        check(!ended.sessionBusy && !ended.firstFrameSubmitted && ended.pin == null)
        check(ended.running && ended.airplay == Advertisement.REGISTERED && ended.pendingSettings)
    }
    checkCase("cancel notice survives stopped state") { check(ready.sessionEnded(ReceiverNotice.PAIRING_CANCELLED).copy(running = false).phase == ReceiverPhase.STOPPED) }
    checkCase("valid Unicode name") { check(ReceiverInput.validName("Salon TV · İstanbul")) }
    checkCase("blank and control names rejected") { listOf("", " ", " A", "A ", "A\nB", "A\u0000B").forEach { check(!ReceiverInput.validName(it)) } }
    checkCase("name limit is UTF-8 bytes not character count") { check(ReceiverInput.validName("ş".repeat(24))); check(!ReceiverInput.validName("ş".repeat(25))) }
    checkCase("port bounds") { check(ReceiverInput.validPort(7000)); check(!ReceiverInput.validPort(0)); check(!ReceiverInput.validPort(65536)) }
    checkCase("FPS validation") { check(ReceiverInput.validFps(60)); check(!ReceiverInput.validFps(-1)); check(!ReceiverInput.validFps(999999)) }
    checkCase("resolution validation") { listOf("auto", "portrait", "landscape", "1920x1080").forEach { check(ReceiverInput.validResolution(it)) }; listOf("0x0", "99999x99999", "hello", "1x1x1").forEach { check(!ReceiverInput.validResolution(it)) } }
    val gate = FirstFrameGate()
    checkCase("frame gate closed before session") { check(!gate.acceptSubmission(-1)); check(!gate.acceptSubmission(1)) }
    checkCase("frame gate accepts only the first matching token") { gate.begin(1); check(!gate.acceptSubmission(0)); check(gate.acceptSubmission(1)); check(!gate.acceptSubmission(1)) }
    checkCase("old session cannot mark new session live") { gate.begin(2); check(!gate.acceptSubmission(1)); check(gate.acceptSubmission(2)) }
    checkCase("invalidated gate cannot report late frame") { gate.invalidate(); check(!gate.acceptSubmission(2)) }
    checkCase("concurrent frame observation fires once") {
        gate.begin(4); val count = AtomicInteger(); val threads = List(20) { Thread { if (gate.acceptSubmission(4)) count.incrementAndGet() } }
        threads.forEach { it.start() }; threads.forEach { it.join() }; check(count.get() == 1)
    }
    listOf("PIN: 1234", "pin = 0123", "device_pin=9876", "Authorization: Basic xyz", "session_key = abc", "active-remote: 42", "aes_key=somekey", "token=hello")
        .forEachIndexed { i, text -> checkCase("secret pattern $i redacted") { check(LogRedactor.redact(text) == "[sensitive diagnostic omitted]") } }
    checkCase("normal diagnostic not lost due to pin substring") { check(LogRedactor.redact("Frame pacing: 60 fps") == "Frame pacing: 60 fps") }
    checkCase("timestamps preserved") { check(LogRedactor.redact("12:34:56.789 Server ready") == "12:34:56.789 Server ready") }
    checkCase("URLs and addresses removed") {
        val s = LogRedactor.redact("media https://example.invalid/personal/video from 192.168.1.24 aa:bb:cc:dd:ee:ff")
        check(!s.contains("example") && !s.contains("192.168") && !s.contains("aa:bb"))
    }
    listOf("fe80::a123:4567%wlan0", "2001:db8:1:2:3:4:5:6", "::1").forEach { ip ->
        checkCase("IPv6 $ip removed") { check(!LogRedactor.redact("remote [$ip]").contains(ip)) }
    }
    checkCase("media metadata omitted") { check(LogRedactor.redact("Track: artist - title") == "[media metadata omitted]") }
    checkCase("line size and controls bounded") { val s = LogRedactor.redact("\u0000\r\n" + "x".repeat(10000)); check(s.length <= 4096 && '\n' !in s && '\u0000' !in s) }
    checkCase("redaction is idempotent") { val s = LogRedactor.redact("PIN 1234"); check(LogRedactor.redact(s) == s) }
    checkCase("buffer keeps only its configured tail") { val b = BoundedLogBuffer(3); repeat(10) { b.append("$it") }; check(b.snapshot() == listOf("7", "8", "9")); b.clear(); check(b.snapshot().isEmpty()) }
    checkCase("buffer rejects invalid size") { check(runCatching { BoundedLogBuffer(0) }.isFailure) }
    val root = Files.createTempDirectory("airrelay-host-checks").toFile()
    val ctx = Context(root)
    val active = ctx.getSharedPreferences(Prefs.NAME, 0)
    active.edit().putString(Prefs.SERVER_NAME, "Existing TV").putBoolean(Prefs.REQUIRE_PIN, true).apply()
    val pending = PendingReceiverSettings(ctx)
    checkCase("editing a draft never changes active preferences") {
        pending.put(Prefs.SERVER_NAME, "Next TV"); pending.put(Prefs.REQUIRE_PIN, false)
        check(active.getString(Prefs.SERVER_NAME, "") == "Existing TV" && active.getBoolean(Prefs.REQUIRE_PIN, false))
        check(pending.string(Prefs.SERVER_NAME, "") == "Next TV" && pending.hasPending())
    }
    checkCase("drafts survive a new settings facade") { check(PendingReceiverSettings(ctx).string(Prefs.SERVER_NAME, "") == "Next TV") }
    checkCase("reverting a draft removes it") { pending.put(Prefs.REQUIRE_PIN, true); check(!ctx.getSharedPreferences("airrelay_next_receiver_start", 0).contains(Prefs.REQUIRE_PIN)) }
    checkCase("failed settings persistence does not permit start") { active.failCommit = true; check(!pending.applyAtReceiverStart()); check(pending.hasPending()); active.failCommit = false }
    checkCase("explicit start applies draft and clears it") { check(pending.applyAtReceiverStart()); check(active.getString(Prefs.SERVER_NAME, "") == "Next TV" && !pending.hasPending()) }
    checkCase("empty settings apply is idempotent") { check(pending.applyAtReceiverStart()) }
    checkCase("discard does not change active settings") { pending.put(Prefs.SERVER_NAME, "Discard me"); pending.discard(); check(pending.string(Prefs.SERVER_NAME, "") == "Next TV") }
    checkCase("reverting to an unwritten default also removes the draft") {
        check(pending.boolean(Prefs.ALLOW_NEW_CONN, Prefs.DEF_ALLOW_NEW_CONN))
        pending.put(Prefs.ALLOW_NEW_CONN, false); check(pending.hasPending())
        pending.put(Prefs.ALLOW_NEW_CONN, true); check(!pending.hasPending())
    }
    val svc = AirPlayService(); val controller = ReceiverController()
    checkCase("unbound controller cannot restart") { check(!controller.applySettings("TV")) }
    controller.attach(svc)
    checkCase("settings change issues no stop or start") { controller.settingsChanged(); check(svc.commands == listOf("draft-changed")) }
    checkCase("busy controller rejects explicit apply") { svc.receiverState.value = ready.copy(pin = "1234", pendingSettings = true); check(!controller.applySettings("TV")); check(svc.commands.size == 1) }
    checkCase("idle apply emits one stop then one start") { svc.receiverState.value = ready.copy(pendingSettings = true); check(controller.applySettings("TV")); check(svc.commands.takeLast(2) == listOf("stop", "start:TV")) }
    checkCase("discovery retry is not native receiver restart") { controller.retryDiscovery(); check(svc.commands.last() == "rediscover") }
    controller.detach()
    checkCase("detached controller sends no commands") { val n = svc.commands.size; controller.start("TV"); check(svc.commands.size == n) }
    // Real production log sink with real coroutines and host filesystem; Context is a test double.
    ctx.filesDir.resolve("airplay_logs.txt").writeText("PIN: 1234")
    val store = DiagnosticStore.get(ctx)
    delay(650)
    checkCase("legacy raw log removed") { check(!ctx.filesDir.resolve("airplay_logs.txt").exists()) }
    store.offer("PIN: 5555"); store.offer("Receiver started on port 7000")
    delay(650)
    val exported = store.export()
    checkCase("export contains filtered log only") { val text = exported.readText(); check(!text.contains("5555") && text.contains("port 7000")); check(store.lines.value.size <= 600) }
    repeat(4) { repeat(256) { store.offer("ş".repeat(2000)) }; delay(600) }
    checkCase("log files remain bounded") { ctx.filesDir.listFiles()!!.filter { it.name.startsWith("airrelay_diagnostics") }.forEach { check(it.length() <= 262144L) } }
    checkCase("log rotation preserves complete UTF-8") {
        ctx.filesDir.listFiles()!!.filter { it.name.startsWith("airrelay_diagnostics") }.forEach { file ->
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(file.readBytes()))
        }
    }
    store.clear()
    checkCase("clear removes export and bounded history") { check(store.lines.value.isEmpty() && !exported.exists()) }
    root.deleteRecursively()
    println("HOST CHECKS: $passed passed. No Android build, emulator, native receiver, media or device test was executed.")
}
