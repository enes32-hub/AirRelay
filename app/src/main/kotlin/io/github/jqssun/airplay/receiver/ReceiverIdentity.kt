package io.github.jqssun.airplay.receiver

import android.content.Context
import android.os.Build
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.R
import java.util.UUID

object ReceiverIdentity {
    private const val SUFFIX = "receiver_name_suffix"
    private const val GENERATED = "receiver_generated_name"

    /** Never changes the MAC identity, pairing keys or a user-selected name. */
    fun ensureDefaultName(context: Context) {
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(Prefs.SERVER_NAME, null)
        if (!ReceiverName.needsDefault(current, prefs.getString(GENERATED, null))) return
        val suffix = prefs.getString(SUFFIX, null) ?: UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val generated = ReceiverName.defaultName(context.getString(R.string.app_name), Build.MODEL, suffix)
        prefs.edit().putString(SUFFIX, suffix).putString(GENERATED, generated)
            .putString(Prefs.SERVER_NAME, generated).apply()
    }
}
