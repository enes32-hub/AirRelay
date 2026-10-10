package io.github.jqssun.airplay.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import io.github.jqssun.airplay.receiver.focusCandidateIds

val LocalTvInputEnabled = staticCompositionLocalOf { true }
val LocalBlockingOverlay = staticCompositionLocalOf { false }
val LocalControlPressed = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalControlActivity = staticCompositionLocalOf<() -> Unit> { {} }
private val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

/** Only currently composed targets are registered; no retained Android Views or lazy item handles. */
private class TvFocusMemory {
    class Target(val requester: FocusRequester, var enabled: Boolean, var focused: Boolean = false)
    val targets = linkedMapOf<String, Target>()
    var remembered: String? = null
    var defaultId: String? = null
    var enabled = true
    var rememberFocus: (String) -> Unit = {}
    var requestRestore: () -> Unit = {}
    suspend fun restore() {
        if (!enabled) return
        // Wait for placement, not a wall-clock delay. Cancelled when the page leaves composition.
        withFrameNanos { }
        repeat(10) {
            if (!enabled) return
            val candidates = focusCandidateIds(remembered, defaultId,
                targets.filterValues { target -> target.enabled }.keys.toList())
            // Give the remembered node two placement frames, then try usable fallbacks.
            // A composed-but-unfocusable node must not monopolize all recovery attempts.
            val attempts = if (it < 2) candidates.take(1) else candidates
            for (id in attempts) {
                val candidate = targets[id] ?: continue
                runCatching { candidate.requester.requestFocus() }
                if (candidate.focused) return
            }
            withFrameNanos { }
        }
    }
}

/** Save this region with SaveableStateProvider to retain both focus identity and lazy scroll position. */
@Composable
fun TvFocusRegion(enabled: Boolean, defaultId: String? = null, restoreToken: Int = 0, preferDefault: Boolean = false, content: @Composable () -> Unit) {
    var last by rememberSaveable { mutableStateOf<String?>(null) }
    val memory = remember { TvFocusMemory() }
    val scope = rememberCoroutineScope()
    SideEffect {
        memory.enabled = enabled
        memory.defaultId = defaultId
        memory.remembered = last
        memory.rememberFocus = { last = it }
        memory.requestRestore = { scope.launch { memory.restore() } }
    }
    LaunchedEffect(enabled, restoreToken) { if (enabled) {
        if (preferDefault) { last = defaultId; memory.remembered = defaultId }
        memory.restore()
    } }
    CompositionLocalProvider(LocalTvFocusMemory provides memory, LocalTvInputEnabled provides enabled) {
        Box(Modifier.focusGroup()) { content() }
    }
}

@Composable
fun Modifier.tvFocusId(id: String?, enabled: Boolean = true): Modifier {
    if (id == null) return this
    val memory = LocalTvFocusMemory.current ?: return this
    val target = remember(memory, id) { TvFocusMemory.Target(FocusRequester(), enabled) }
    SideEffect { target.enabled = enabled }
    DisposableEffect(memory, id) {
        // Two settings can share a translated title, and switching UI language can make
        // previously distinct ids collide. A duplicate must never crash composition: the later
        // node simply stays out of the focus memory, and the collision is logged so the
        // identity can be made stable (a Prefs key instead of localized text).
        val duplicate = id in memory.targets
        if (duplicate) {
            android.util.Log.w("TvFocusMemory", "Duplicate TV focus identity ignored: $id")
        } else {
            memory.targets[id] = target
        }
        onDispose {
            if (!duplicate) {
                memory.targets.remove(id)
                // A conditional action (e.g. Apply) vanished. Do not strand focus on a removed node.
                if (target.focused) memory.requestRestore()
            }
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled && target.focused) memory.restore()
    }
    return this.focusRequester(target.requester).onFocusChanged {
        target.focused = it.isFocused
        if (it.isFocused) {
            memory.remembered = id
            memory.rememberFocus(id)
        }
    }
}

/** Dialog controls must remain usable while their underlying page is input-blocked. */
@Composable
fun DialogInputScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvInputEnabled provides true, LocalTvFocusMemory provides null,
        LocalControlActivity provides {}, LocalControlPressed provides {}) { content() }
}
