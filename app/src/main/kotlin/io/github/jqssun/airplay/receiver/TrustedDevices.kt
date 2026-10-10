package io.github.jqssun.airplay.receiver

import android.content.SharedPreferences

/** Public verification keys only. Registration is called after successful PIN pairing. */
class TrustedDevices(private val preferences: SharedPreferences) {
    fun contains(key: String): Boolean = synchronized(lock) {
        validKey(key) && !isBlocked(key) && key in preferences.getStringSet(KEYS, emptySet()).orEmpty()
    }

    fun register(key: String, name: String = ""): Boolean = synchronized(lock) {
        if (!validKey(key) || isBlocked(key)) return@synchronized false
        val keys = preferences.getStringSet(KEYS, emptySet()).orEmpty().filter(::validKey).toMutableSet()
        if (key !in keys && keys.size >= 64) return@synchronized false
        keys.add(key)
        val edit = preferences.edit().putStringSet(KEYS, keys)
        if (name.isNotBlank()) edit.putString("name_$key", name.filterNot { it.isISOControl() }.take(64))
        edit.commit()
    }

    data class Device(val key: String, val name: String, val id: String, val blocked: Boolean)
    fun isBlocked(key: String): Boolean = synchronized(lock) {
        key in preferences.getStringSet("blocked_keys", emptySet()).orEmpty()
    }
    fun devices(): List<Device> = synchronized(lock) {
        preferences.getStringSet(KEYS, emptySet()).orEmpty().filter(::validKey).sorted().map { key ->
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            Device(key, preferences.getString("name_$key", "").orEmpty(), digest.take(4).joinToString("") { "%02X".format(it) }, isBlocked(key))
        }
    }
    /**
     * Blocking is independent of the trusted list. With "Require PIN" off no key is ever
     * registered, so requiring membership here made every block attempt fail while the
     * receiver stayed open to every syntactically valid key.
     * apply() rather than commit(): this runs from a Compose click and must not block the UI
     * thread on disk I/O.
     */
    fun setBlocked(key: String, blocked: Boolean): Boolean = synchronized(lock) {
        if (!validKey(key)) return@synchronized false
        val keys = preferences.getStringSet("blocked_keys", emptySet()).orEmpty().toMutableSet()
        if (blocked) keys.add(key) else keys.remove(key)
        preferences.edit().putStringSet("blocked_keys", keys).apply()
        true
    }

    companion object {
        private val lock = Any()
        private const val KEYS = "verified_public_keys"
        private val canonicalKey = Regex("[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=")
        fun validKey(key: String): Boolean = canonicalKey.matches(key)
    }
}
