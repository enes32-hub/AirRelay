package io.github.jqssun.airplay.service
import io.github.jqssun.airplay.receiver.ReceiverUiState
import kotlinx.coroutines.flow.MutableStateFlow
/** Command recorder only. It has no protocol or Android implementation. */
class AirPlayService {
    val receiverState = MutableStateFlow(ReceiverUiState())
    val commands = mutableListOf<String>()
    fun startServer(name: String) { commands += "start:$name" }
    fun stopServer() { commands += "stop" }
    fun cancelPairing() { commands += "cancel" }
    fun retryDiscovery() { commands += "rediscover" }
    fun dismissNotice() { commands += "dismiss-notice" }
    fun refreshPendingSettings() { commands += "draft-changed" }
}
