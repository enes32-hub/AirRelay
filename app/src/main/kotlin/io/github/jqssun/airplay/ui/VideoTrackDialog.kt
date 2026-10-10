package io.github.jqssun.airplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.renderer.VideoTrackOption

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun DirectVideoSubtitles(cues: List<Cue>) {
    val (size, _) = subtitleSize()
    AndroidView(factory = { context -> SubtitleView(context).apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setStyle(androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE,
            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null))
        setBottomPaddingFraction(0.18f)
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    } }, modifier = Modifier.fillMaxSize(), update = { view ->
        view.setFractionalTextSize(.045f * size / 100f)
        view.setCues(cues.map(::positionedSubtitle))
    })
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun positionedSubtitle(cue: Cue): Cue = cue.buildUpon().clearWindowColor().apply {
            // Negative line numbers count from the bottom; padding alone does
            // not move these explicitly positioned WebVTT subtitles.
            if (cue.line == Cue.DIMEN_UNSET ||
                (cue.lineType == Cue.LINE_TYPE_NUMBER && cue.line < 0f) ||
                (cue.lineType == Cue.LINE_TYPE_FRACTION && cue.line > .82f))
                setLine(.82f, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END)
        }.build()

@Composable
private fun subtitleSize(): Pair<Int, (Int) -> Unit> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("subtitle_options", android.content.Context.MODE_PRIVATE) }
    var value by remember { mutableIntStateOf(prefs.getInt("size", 100).coerceIn(70, 160)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "size") value = prefs.getInt("size", 100).coerceIn(70, 160)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value to { prefs.edit().putInt("size", it.coerceIn(70, 160)).apply() }
}

@Composable
internal fun VideoTrackDialog(type: Int, tracks: List<VideoTrackOption>, onChoose: (String) -> Unit, onDismiss: () -> Unit) = DialogInputScope {
    val options = tracks.filter { it.type == type }
    val (size, setSize) = subtitleSize()
    val initialFocus = remember(type) { FocusRequester() }
    // Keyed on the option list itself: re-keying on emptiness alone left no node holding the
    // initial focus when the list changed content without changing whether it is empty.
    val initialKey = remember(type, options) { (options.firstOrNull { it.selected } ?: options.firstOrNull())?.key }
    LaunchedEffect(type, options) { initialFocus.requestFocusUntilLanded() }
    ProductAlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(when(type) {
            C.TRACK_TYPE_VIDEO -> R.string.track_quality
            C.TRACK_TYPE_TEXT -> R.string.track_subtitles
            else -> R.string.track_audio
        })) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isEmpty()) Text(stringResource(R.string.track_unavailable))
                if (type == C.TRACK_TYPE_TEXT) Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TvAction("−", { setSize(size - 10) }, enabled = size > 70, compact = true)
                    Text(stringResource(R.string.subtitle_size_value, size))
                    TvAction("+", { setSize(size + 10) }, enabled = size < 160, compact = true)
                }
                options.forEach { option ->
                    TvAction(when(option.key) {
                        "auto" -> stringResource(R.string.track_auto)
                        "off" -> stringResource(R.string.track_off)
                        else -> option.label
                    }, { onChoose(option.key) }, Modifier.fillMaxWidth().then(
                        if (option.key == initialKey) Modifier.focusRequester(initialFocus) else Modifier), selected = option.selected)
                }
            }
        }, confirmButton = {}, dismissButton = { TvAction(stringResource(R.string.p_cancel), onDismiss,
            if (options.isEmpty()) Modifier.focusRequester(initialFocus) else Modifier) })
}
