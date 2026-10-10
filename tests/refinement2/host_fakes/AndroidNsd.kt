package android.net.nsd

/** Callback recorder, NOT real Android NSD or network discovery. */
class NsdServiceInfo {
    var serviceName = ""
    var serviceType = ""
    var port = 0
    val attributes = linkedMapOf<String, String>()
    fun setAttribute(key: String, value: String) { attributes[key] = value }
}
class NsdManager {
    interface RegistrationListener {
        fun onServiceRegistered(info: NsdServiceInfo)
        fun onRegistrationFailed(info: NsdServiceInfo, code: Int)
        fun onServiceUnregistered(info: NsdServiceInfo)
        fun onUnregistrationFailed(info: NsdServiceInfo, code: Int)
    }
    data class Request(val info: NsdServiceInfo, val protocol: Int, val listener: RegistrationListener)
    val requests = mutableListOf<Request>()
    val unregistered = mutableListOf<RegistrationListener>()
    var registrationException: RuntimeException? = null
    fun registerService(info: NsdServiceInfo, protocol: Int, listener: RegistrationListener) {
        registrationException?.let { throw it }
        requests += Request(info, protocol, listener)
    }
    fun unregisterService(listener: RegistrationListener) { unregistered += listener }
    companion object { const val PROTOCOL_DNS_SD = 1 }
}
