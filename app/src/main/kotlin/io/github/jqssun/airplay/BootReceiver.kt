package io.github.jqssun.airplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.receiver.UserExperiencePrefs
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val experience = context.getSharedPreferences(UserExperiencePrefs.NAME, Context.MODE_PRIVATE)
        if (!experience.getBoolean(UserExperiencePrefs.SETUP_COMPLETE, false) ||
            experience.getBoolean(UserExperiencePrefs.USER_STOPPED, false)) return
        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START)) return

        val serviceIntent = Intent(context, AirPlayService::class.java)
            .setAction(AirPlayService.ACTION_START_SERVER)
        // connectedDevice is permitted at boot; mediaPlayback is not (target 35+).
        // Some TV vendors still reject boot starts. Do not crash the broadcast or launch UI.
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: RuntimeException) {
            Log.w("BootReceiver", "TV declined automatic receiver startup: ${e.javaClass.simpleName}")
        }
    }
}
