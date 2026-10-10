package io.github.jqssun.airplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.ui.theme.*

/** Design-time sample facts only. This provider is never connected to the runtime service. */
@Preview(name = "TV • Turkish • ready (sample)", locale = "tr", widthDp = 960, heightDp = 540)
@Preview(name = "TV • English • ready (sample)", locale = "en", widthDp = 960, heightDp = 540)
@Composable
private fun ReadyHomePreview() {
    AirPlayTheme {
        Column(Modifier.fillMaxSize().background(AirRelayColors.Background).padding(32.dp)) {
            ReceiverBrand()
            Spacer(Modifier.height(18.dp))
            ReceiverHome(ReceiverUiState(running = true, advertisedName = "AirRelay-Salon",
                network = LocalNetwork.WIFI, airplay = Advertisement.REGISTERED, raop = Advertisement.REGISTERED),
                savedName = "AirRelay-Salon", bound = true,
                onStart = {}, onStop = {}, onShowMirror = {}, onHelp = {}, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(name = "TV • stopped", locale = "tr", widthDp = 960, heightDp = 540)
@Composable
private fun StoppedHomePreview() = ReviewHomePreview(ReceiverUiState())

@Preview(name = "TV • no local network", locale = "tr", widthDp = 960, heightDp = 540)
@Composable
private fun NoNetworkHomePreview() = ReviewHomePreview(ReceiverUiState(running = true, network = LocalNetwork.UNAVAILABLE))

@Preview(name = "TV • long name • large font", locale = "tr", widthDp = 960, heightDp = 540, fontScale = 1.5f)
@Composable
private fun LargeTextHomePreview() = ReviewHomePreview(ReceiverUiState(running = true, airplay = Advertisement.REGISTERED,
    network = LocalNetwork.ETHERNET, advertisedName = "AirRelay-Toplanti-Odasi"))

@Composable
private fun ReviewHomePreview(state: ReceiverUiState) {
    AirPlayTheme {
        Column(Modifier.fillMaxSize().background(AirRelayColors.Background).padding(28.dp)) {
            ReceiverBrand()
            Spacer(Modifier.height(16.dp))
            ReceiverHome(state, "AirRelay-Salon", true, {}, {}, {}, {}, Modifier.weight(1f))
        }
    }
}

