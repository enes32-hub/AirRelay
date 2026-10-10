import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.service.AirPlayService

fun main() {
    val service = AirPlayService()
    val controller = ReceiverController().apply { attach(service) }
    service.receiverState.value = ReceiverUiState(running = true, pendingSettings = true, connections = 1)
    check(!controller.applySettings("TV"))
    check(service.commands.isEmpty()) // unconfirmed active stream is untouched
    check(controller.applySettings("TV", true))
    check(service.commands == listOf("stop", "start:TV"))
    service.commands.clear()
    service.receiverState.value = service.receiverState.value.copy(starting = true)
    check(!controller.applySettings("TV", true))
    check(service.commands.isEmpty())
    service.receiverState.value = ReceiverUiState(pendingSettings = false)
    check(!controller.applySettings("TV", true))
    service.receiverState.value = ReceiverUiState(pendingSettings = true)
    check(controller.applySettings("TV"))
    check(service.commands == listOf("stop", "start:TV"))
    println("PASS: active-stream confirmation, pending/start guards and stop-before-start ordering")
}
