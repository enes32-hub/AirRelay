package io.github.jqssun.airplay.receiver

/** UI-only decisions. No Android, renderer, JNI, packet or timing dependencies. */
enum class ReceiverPage {
    HOME, HELP, SETTINGS, ADVANCED, LOGS, RECORDINGS;
    // LOGS is a first-class destination (its nav entry lives in MainScreen), so it is its own
    // tab and returns home with Back, exactly like the other top-level pages.
    val parent: ReceiverPage get() = when (this) {
        ADVANCED -> SETTINGS
        else -> HOME
    }
    val tab: ReceiverPage get() = when (this) {
        ADVANCED -> SETTINGS
        else -> this
    }
}

enum class HomePrimaryAction { START, VIEW, GUIDE }
fun ReceiverUiState.homePrimaryAction(): HomePrimaryAction = when {
    presentationHeld || mirrorRequested || mediaPlaying -> HomePrimaryAction.VIEW
    !running -> HomePrimaryAction.START
    else -> HomePrimaryAction.GUIDE
}

/** An opening press cannot also activate a control. A lost key-up cannot trap the next press. */
class RemoteWakeGuard {
    private var openingKey: Int? = null
    data class Decision(val consume: Boolean, val reveal: Boolean = false)
    fun reset() { openingKey = null }
    fun event(code: Int, down: Boolean, repeat: Int, wakeKey: Boolean, visible: Boolean): Decision {
        if (down && repeat == 0) openingKey = null // a fresh physical gesture, even after a lost release
        if (code == openingKey) {
            if (!down) openingKey = null
            return Decision(consume = true)
        }
        if (wakeKey && down && !visible) {
            openingKey = code
            return Decision(consume = true, reveal = true)
        }
        return Decision(consume = false)
    }
}

object OverlayPolicy {
    fun mayHide(visible: Boolean, waiting: Boolean, modal: Boolean,
        networkMissing: Boolean, pressed: Boolean): Boolean =
        visible && !waiting && !modal && !networkMissing && !pressed
}

/** A stopped start request must never resume after its settings I/O completes. */
class StartRequestGate {
    private var generation = 0L
    @Synchronized fun begin(): Long = ++generation
    @Synchronized fun cancel() { generation++ }
    @Synchronized fun isCurrent(token: Long): Boolean = token == generation
}

enum class UiFeedbackKind { SETTINGS_RESTARTING, SETTINGS_BLOCKED, SETTINGS_DISCARDED, DISCOVERY_REQUESTED }
data class UiFeedback(val sequence: Long, val kind: UiFeedbackKind)

/** Stable, unique fallback order; only composed and enabled controls are eligible. */
fun focusCandidateIds(remembered: String?, defaultId: String?, enabledIds: List<String>): List<String> =
    (listOfNotNull(remembered, defaultId) + enabledIds).distinct().filter { it in enabledIds }
