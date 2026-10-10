package io.github.jqssun.airplay.receiver

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import android.os.Handler
import android.os.Looper

/** Observes local Wi-Fi/Ethernet, including LANs with no internet. Never binds or changes routing. */
class LocalNetworkMonitor(context: Context, private val changed: (LocalNetwork) -> Unit) : AutoCloseable {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val networks = mutableMapOf<Network, LocalNetwork>()
    private val links = mutableMapOf<Network, String>()
    @Volatile private var closed = false
    private var registered = false
    private var last: LocalNetwork? = null
    private var lastTopology = ""
    private fun type(caps: NetworkCapabilities?) = when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> LocalNetwork.ETHERNET
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> LocalNetwork.WIFI
        else -> null
    }
    private fun publish() {
        val value = when {
            networks.containsValue(LocalNetwork.ETHERNET) -> LocalNetwork.ETHERNET
            networks.containsValue(LocalNetwork.WIFI) -> LocalNetwork.WIFI
            else -> LocalNetwork.UNAVAILABLE
        }
        val topology = networks.entries.map { (network, kind) ->
            "${network.networkHandle}:$kind:${links[network].orEmpty()}"
        }.sorted().joinToString("|")
        if (value != last || topology != lastTopology) {
            last = value; lastTopology = topology; changed(value)
        }
    }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            main.post {
                if (!closed) {
                    val kind = type(caps)
                    if (kind == null) networks.remove(network) else networks[network] = kind
                    publish()
                }
            }
        }
        override fun onLost(network: Network) {
            main.post { if (!closed) { networks.remove(network); links.remove(network); publish() } }
        }
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
            main.post {
                if (!closed) {
                    links[network] = properties.linkAddresses.map { it.toString() }.sorted().joinToString(",")
                    publish()
                }
            }
        }
    }
    fun start() {
        if (registered || closed) return
        try {
            manager.allNetworks.forEach { network ->
                type(manager.getNetworkCapabilities(network))?.let { networks[network] = it }
            }
            val request = NetworkRequest.Builder().apply {
                if (android.os.Build.VERSION.SDK_INT >= 30) clearCapabilities()
                else {
                    // Before API 30, remove the three default capabilities explicitly.
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                }
            }
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build()
            manager.registerNetworkCallback(request, callback)
            registered = true
            publish()
        } catch (_: SecurityException) {
            changed(LocalNetwork.PERMISSION_DENIED)
        } catch (_: RuntimeException) {
            changed(LocalNetwork.UNKNOWN) // observation failure is not proof of network loss
        }
    }
    override fun close() {
        closed = true
        if (registered) runCatching { manager.unregisterNetworkCallback(callback) }
        registered = false
        main.removeCallbacksAndMessages(null)
    }
}
