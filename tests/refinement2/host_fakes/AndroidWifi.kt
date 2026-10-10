package android.net.wifi
class WifiManager {
    val locks = mutableListOf<MulticastLock>()
    fun createMulticastLock(name: String): MulticastLock = MulticastLock().also { locks += it }
    class MulticastLock {
        var isHeld = false; private set
        fun setReferenceCounted(value: Boolean) {}
        fun acquire() { isHeld = true }
        fun release() { isHeld = false }
    }
}
