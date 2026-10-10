package io.github.jqssun.airplay.receiver

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.concurrent.atomic.AtomicInteger

/** A foreground service alone never grants background-activity-start permission. */
object ReceiverPresentation {
    private val visibleActivities = AtomicInteger()
    fun activityStarted() { visibleActivities.incrementAndGet() }
    fun activityStopped() { visibleActivities.updateAndGet { (it - 1).coerceAtLeast(0) } }
    fun isVisible() = visibleActivities.get() > 0
    fun automaticDisplayAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)
    fun canOpenActivity(context: Context) = isVisible() || automaticDisplayAllowed(context)
}
