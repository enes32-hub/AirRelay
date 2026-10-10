package io.github.jqssun.airplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.BuildConfig
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun AboutProductContent() {
    val context = LocalContext.current
    val missing = stringResource(R.string.p_licenses_unavailable)
    var notices by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(expanded) {
        if (expanded && notices == null) notices = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("OPEN_SOURCE_NOTICES.txt").bufferedReader().use { it.readText() } }.getOrDefault(missing)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.p_version, BuildConfig.VERSION_NAME), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.p_about_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.p_trademark_notice), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)
        TvAction(stringResource(R.string.p_open_source_licenses), { expanded = !expanded },
            selected = expanded, focusId = "settings.licenses", compact = true)
        if (expanded) {
            val paragraphs = remember(notices) { notices?.lineSequence()?.chunked(12)?.map { it.joinToString("\n") }?.toList() }
            LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth().height(220.dp).tvFocusId("settings.licenses.content")
                .dpadFocus().onPreviewKeyEvent { event ->
                    val direction = when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (scroll.canScrollForward) 1 else 0
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> if (scroll.canScrollBackward) -1 else 0
                        else -> 0
                    }
                    if (direction == 0) false else {
                        if (event.type == KeyEventType.KeyDown) scope.launch { scroll.scrollBy(direction * 100f) }
                        true
                    }
                }.focusable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paragraphs == null) item { Text(stringResource(R.string.r2_working), style = MaterialTheme.typography.bodySmall) }
                else items(paragraphs) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                }
            }
        }
    }
}
