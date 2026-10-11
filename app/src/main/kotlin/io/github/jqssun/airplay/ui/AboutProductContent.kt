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

/**
 * About section of Settings.
 *
 * Carries the notices the product is obliged to show: who develops the app, how to reach them, the
 * licence it is distributed under, where its source lives, the Apple trademark disclaimer, and the
 * two texts that must be readable from inside the application itself — the privacy policy (Google
 * Play requires in-app access, not only a store-listing link) and the open-source component
 * licences. Both are bundled assets, so they open without a network connection or a browser.
 */
@Composable
fun AboutProductContent() {
    val context = LocalContext.current
    val privacy = stringResource(R.string.p_privacy_policy)
    val licenses = stringResource(R.string.p_open_source_licenses)
    var open by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.p_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.p_about_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.p_contact_line), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.p_source_line), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.p_copyright_notice), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.p_trademark_notice), color = AirRelayColors.Muted,
            style = MaterialTheme.typography.bodySmall)

        AssetSection(
            title = privacy,
            focusId = "settings.privacy",
            asset = "PRIVACY_POLICY.txt",
            fallback = stringResource(R.string.p_privacy_unavailable),
            isOpen = open == "privacy",
            onToggle = { open = if (open == "privacy") null else "privacy" },
            context = context,
        )
        AssetSection(
            title = licenses,
            focusId = "settings.licenses",
            asset = "OPEN_SOURCE_NOTICES.txt",
            fallback = stringResource(R.string.p_licenses_unavailable),
            isOpen = open == "licenses",
            onToggle = { open = if (open == "licenses") null else "licenses" },
            context = context,
        )
    }
}

@Composable
private fun AssetSection(
    title: String,
    focusId: String,
    asset: String,
    fallback: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    context: android.content.Context,
) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isOpen) {
        if (isOpen && text == null) {
            text = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(asset).bufferedReader().use { it.readText() }
                }.getOrDefault(fallback)
            }
        }
    }
    TvAction(title, onToggle, selected = isOpen, focusId = focusId, compact = true)
    if (isOpen) {
        val scroll = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val paragraphs = remember(text) {
            text?.lineSequence()?.chunked(12)?.map { it.joinToString("\n") }?.toList()
        }
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxWidth().height(240.dp).tvFocusId("$focusId.content")
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
                }.focusable(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (paragraphs == null) {
                item { Text(stringResource(R.string.r2_working), style = MaterialTheme.typography.bodySmall) }
            } else {
                items(paragraphs) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodySmall, color = AirRelayColors.Muted)
                }
            }
        }
    }
}
