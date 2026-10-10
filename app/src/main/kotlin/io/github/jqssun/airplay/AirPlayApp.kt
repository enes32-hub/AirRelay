package io.github.jqssun.airplay

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AirPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        io.github.jqssun.airplay.receiver.ReceiverIdentity.ensureDefaultName(this)
    }
}
