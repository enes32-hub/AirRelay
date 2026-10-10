package android.content

import java.io.File

/** Minimal host test double. Does NOT emulate Android, NSD, UI, Binder, codec or lifecycle. */
open class Context(val root: File) {
    val applicationContext: Context get() = this
    val filesDir = File(root, "files").apply { mkdirs() }
    val cacheDir = File(root, "cache").apply { mkdirs() }
    private val stores = java.util.concurrent.ConcurrentHashMap<String, FakePreferences>()
    val services = mutableMapOf<String, Any>()
    fun getSystemService(name: String): Any? = services[name]
    fun getSharedPreferences(name: String, mode: Int): FakePreferences = stores.computeIfAbsent(name) { FakePreferences() }
    companion object {
        const val MODE_PRIVATE = 0
        const val NSD_SERVICE = "servicediscovery"
        const val WIFI_SERVICE = "wifi"
    }
}
interface SharedPreferences {
    val all: Map<String, Any?>
    fun contains(key: String): Boolean
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}
class FakePreferences : SharedPreferences {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    @Volatile var failCommit = false
    @Volatile var beforeCommit: (() -> Unit)? = null
    override val all get() = values.toMap()
    override fun contains(key: String) = values.containsKey(key)
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun edit() = object : SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        var clearAll = false
        override fun putString(key: String, value: String) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun apply() { store() }
        override fun commit(): Boolean { beforeCommit?.invoke(); if (failCommit) return false; store(); return true }
        private fun store() {
            if (clearAll) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}
