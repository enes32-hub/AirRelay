package io.github.jqssun.airplay.receiver

import android.content.Context
import android.content.SharedPreferences
import io.github.jqssun.airplay.Prefs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Drafts cannot mutate the active receiver. UI writes fail fast while durable startup I/O owns the lock. */
class PendingReceiverSettings(context: Context) {
    private val active = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val draft = context.getSharedPreferences("airrelay_next_receiver_start", Context.MODE_PRIVATE)
    private val defaults = ConcurrentHashMap<String, Any>()
    // Non-blocking: the lock is held across two synchronous commits by applyAtReceiverStart()
    // on a background dispatcher, and this is called from a Compose click. Blocking here would
    // freeze the UI for the duration of that durable write.
    fun remove(key: String): Boolean {
        if (!lock.tryLock()) return false
        try { draft.edit().remove(key).apply(); return true }
        finally { lock.unlock() }
    }
    fun hasPending(): Boolean = draft.all.isNotEmpty()
    fun string(key: String, default: String): String {
        defaults[key] = default
        return (if (draft.contains(key)) draft else active).getString(key, default) ?: default
    }
    fun boolean(key: String, default: Boolean): Boolean {
        defaults[key] = default
        return (if (draft.contains(key)) draft else active).getBoolean(key, default)
    }
    fun int(key: String, default: Int): Int {
        defaults[key] = default
        return (if (draft.contains(key)) draft else active).getInt(key, default)
    }
    /** False means not accepted: caller must not optimistically display this value as saved. */
    fun put(key: String, value: Any): Boolean = putAll(mapOf(key to value))
    fun putAll(values: Map<String, Any>): Boolean {
        if (!lock.tryLock()) return false
        try {
            val edit = draft.edit()
            values.forEach { (key, value) ->
                val effectiveActive = if (active.contains(key)) active.all[key] else defaults[key]
                if (effectiveActive == value) edit.remove(key) else edit.putTyped(key, value)
            }
            edit.apply()
            return true
        } finally { lock.unlock() }
    }
    fun discard(): Boolean {
        if (!lock.tryLock()) return false
        try { draft.edit().clear().apply(); return true }
        finally { lock.unlock() }
    }
    /** Called on Dispatchers.IO. Idempotent across active commit / draft cleanup interruption.
     *  Returns the result of the *settings* commit; the draft clear is best-effort, because a
     *  failed clear must not abort a start whose settings were committed successfully. */
    fun applyAtReceiverStart(): Boolean = lock.withLock {
        if (!hasPending()) return@withLock true
        val edit = active.edit()
        draft.all.forEach { (key, value) -> if (value != null) edit.putTyped(key, value) }
        if (!edit.commit()) return@withLock false
        draft.edit().clear().apply()
        true
    }
    private fun SharedPreferences.Editor.putTyped(key: String, value: Any) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is String -> putString(key, value)
            else -> error("Unsupported receiver setting type")
        }
    }
    companion object { private val lock = ReentrantLock() }
}
