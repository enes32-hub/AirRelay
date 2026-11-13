package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.jqssun.airplay.BuildConfig
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.ui.theme.AirRelayColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
fun AboutProductContent(onModalChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val privacy = stringResource(R.string.p_privacy_policy)
    val licenses = stringResource(R.string.p_open_source_licenses)
    val privacyUnavailable = stringResource(R.string.p_privacy_unavailable)
    val licensesUnavailable = stringResource(R.string.p_licenses_unavailable)
    var openDoc by rememberSaveable { mutableStateOf<String?>(null) }
    val blocked = LocalBlockingOverlay.current
    SideEffect { onModalChange(openDoc != null && !blocked) }
    DisposableEffect(Unit) { onDispose { onModalChange(false) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(AirRelayColors.Panel, RoundedCornerShape(18.dp))
                .focusRing(RoundedCornerShape(18.dp))
                .focusable()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.p_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                color = AirRelayColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.p_about_body), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.p_contact_line),
                color = AirRelayColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.p_source_line),
                color = AirRelayColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.p_copyright_notice),
                color = AirRelayColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.p_trademark_notice),
                color = AirRelayColors.Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvAction(
                privacy,
                { openDoc = "privacy" },
                Modifier.weight(1f),
                selected = openDoc == "privacy",
                focusId = "settings.privacy",
                compact = true,
            )
            TvAction(
                licenses,
                { openDoc = "licenses" },
                Modifier.weight(1f),
                selected = openDoc == "licenses",
                focusId = "settings.licenses",
                compact = true,
            )
        }
    }

    if (openDoc != null && !blocked) {
        val isPrivacy = openDoc == "privacy"
        LegalDocumentDialog(
            title = if (isPrivacy) privacy else licenses,
            focusId = if (isPrivacy) "settings.privacy" else "settings.licenses",
            asset = if (isPrivacy) "PRIVACY_POLICY.txt" else "OPEN_SOURCE_NOTICES.txt",
            fallback = if (isPrivacy) privacyUnavailable else licensesUnavailable,
            context = context,
            onDismiss = { openDoc = null },
        )
    }
}

private fun splitDocumentBlocks(text: String): List<String> {
    val out = ArrayList<String>()
    for (block in text.split(Regex("\\r?\\n\\s*\\r?\\n"))) {
        val trimmed = block.trimEnd()
        if (trimmed.isEmpty()) continue
        val lines = trimmed.lines()
        if (lines.size <= 28) {
            out.add(trimmed)
        } else {
            for (chunk in lines.chunked(28)) {
                out.add(chunk.joinToString("\n"))
            }
        }
    }
    return out
}

@Composable
private fun LegalDocumentDialog(
    title: String,
    focusId: String,
    asset: String,
    fallback: String,
    context: android.content.Context,
    onDismiss: () -> Unit,
) = DialogInputScope {
    var text by remember(asset) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }.getOrDefault(fallback)
        }
    }
    val blocks = remember(text) { text?.let(::splitDocumentBlocks) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val stepPx = with(density) { 160.dp.toPx() }
    val pagePx = with(density) { 360.dp.toPx() }
    LaunchedEffect(asset) { contentFocus.requestFocusUntilLanded() }

    ProductDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 860.dp).fillMaxWidth(0.92f).fillMaxHeight(0.86f),
            shape = RoundedCornerShape(24.dp),
            color = AirRelayColors.Panel,
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                    )
                    TvAction(
                        stringResource(R.string.access_close),
                        onDismiss,
                        Modifier.focusRequester(closeFocus),
                        compact = true,
                        focusId = "$focusId.close",
                    )
                }

                LazyColumn(
                    state = scroll,
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(AirRelayColors.Background, RoundedCornerShape(14.dp))
                        .border(1.dp, AirRelayColors.Border.copy(alpha = .7f), RoundedCornerShape(14.dp))
                        .clipToBounds()
                        .focusRing(RoundedCornerShape(14.dp))
                        .focusRequester(contentFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (scroll.canScrollForward) scope.launch { scroll.scrollBy(stepPx) }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (scroll.canScrollBackward) {
                                        scope.launch { scroll.scrollBy(-stepPx) }
                                        true
                                    } else {
                                        runCatching { closeFocus.requestFocus() }
                                        true
                                    }
                                }
                                android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                                    if (scroll.canScrollForward) scope.launch { scroll.scrollBy(pagePx) }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                                    if (scroll.canScrollBackward) scope.launch { scroll.scrollBy(-pagePx) }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                ) {
                    if (blocks == null) {
                        item {
                            Text(
                                stringResource(R.string.r2_working),
                                style = MaterialTheme.typography.bodySmall,
                                color = AirRelayColors.Muted,
                            )
                        }
                    } else {
                        items(blocks) { block ->
                            Text(
                                block,
                                style = MaterialTheme.typography.bodySmall,
                                color = AirRelayColors.Text,
                            )
                        }
                    }
                }
            }
        }
    }
}
