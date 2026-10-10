package io.github.jqssun.airplay.receiver

import io.github.jqssun.airplay.service.AirPlayService

/** The product UI sends commands here, never directly to JNI or to a media callback. */
class ReceiverController {
    private var service: AirPlayService? = null
    fun attach(value: AirPlayService) { service = value }
    fun detach() { service = null }
    fun start(name: String) { service?.startServer(name) }
    fun stop() { service?.stopServer() }
    fun cancelPairing() { service?.cancelPairing() }
    fun retryDiscovery(): Boolean {
        val receiver = service ?: return false
        if (!receiver.receiverState.value.canRetryDiscovery) return false
        receiver.retryDiscovery()
        return true
    }
    fun dismissNotice() { service?.dismissNotice() }
    fun settingsChanged() { service?.refreshPendingSettings() }
    fun applySettings(name: String, endSessionConfirmed: Boolean = false): Boolean {
        val receiver = service ?: return false
        val state = receiver.receiverState.value
        if (!state.pendingSettings || state.starting || (state.sessionBusy && !endSessionConfirmed)) return false
        receiver.stopServer()
        receiver.startServer(name)
        return true
    }
}
