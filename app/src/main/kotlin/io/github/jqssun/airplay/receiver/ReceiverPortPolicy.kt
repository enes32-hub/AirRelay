package io.github.jqssun.airplay.receiver

/** 0 delegates port allocation to native sockets; null means do not start. */
object ReceiverPortPolicy {
    fun isEmulator(hardware: String, fingerprint: String, model: String): Boolean =
        hardware.lowercase() in setOf("ranchu", "goldfish", "vbox86") ||
            fingerprint.startsWith("generic") || fingerprint.contains("sdk_gphone") ||
            model.startsWith("sdk_") || model.contains("Android SDK built for")

    fun nativePort(requestedPort: Int, emulator: Boolean, conflict: Boolean): Int? {
        require(requestedPort in 1..65535)
        return when {
            !conflict -> requestedPort
            emulator -> null // A LAN bridge cannot forward new ephemeral transport ports.
            else -> 0
        }
    }
}
