package io.github.jqssun.airplay

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import io.github.jqssun.airplay.receiver.PictureFit
import io.github.jqssun.airplay.ui.FittedReceiverSurface
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Pixel test through the actual production SurfaceView/TextureView and compositor.
 * Uses a drawn test pattern, not a decoder or an authenticated AirPlay sender. */
object RotationSurfaceProbe {
    fun run(test: Instrumentation): String {
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val angle = mutableIntStateOf(0)
        val output = AtomicReference<Surface?>()
        val folder = File(test.targetContext.filesDir, "rotation-pixels").apply { mkdirs() }
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val expected = mapOf(0 to listOf(0, 1, 2, 3), 90 to listOf(2, 0, 3, 1),
            180 to listOf(3, 2, 1, 0), 270 to listOf(1, 3, 0, 2))
        test.runOnMainSync {
            activity.setContent {
                FittedReceiverSurface(.5f, PictureFit.FIT, angle.intValue,
                    { output.set(it) }, { output.compareAndSet(it, null) })
            }
        }
        var checks = 0
        try {
            repeat(3) { cycle ->
                for (degrees in listOf(0, 90, 180, 270, 0)) {
                    test.runOnMainSync { angle.intValue = degrees }
                    test.waitForIdleSync()
                    val deadline = SystemClock.elapsedRealtime() + 5000
                    var matched = false
                    while (!matched && SystemClock.elapsedRealtime() < deadline) {
                        test.runOnMainSync {
                            val surface = output.get()
                            if (surface?.isValid == true) {
                                val canvas = surface.lockCanvas(null)
                                try {
                                    val paint = Paint()
                                    val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                                    for (i in 0..3) {
                                        paint.color = colors[i]
                                        val x = (i % 2) * w / 2; val y = (i / 2) * h / 2
                                        canvas.drawRect(x, y, x + w / 2, y + h / 2, paint)
                                    }
                                } finally { surface.unlockCanvasAndPost(canvas) }
                            }
                        }
                        SystemClock.sleep(120)
                        val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
                        try {
                            val aspect = if (degrees % 180 == 0) .5f else 2f
                            val w = minOf(bitmap.width.toFloat(), bitmap.height * aspect)
                            val h = w / aspect
                            matched = (0..3).all { i ->
                                val x = (bitmap.width / 2f + if (i % 2 == 0) -w / 4 else w / 4).toInt()
                                val y = (bitmap.height / 2f + if (i / 2 == 0) -h / 4 else h / 4).toInt()
                                val actual = bitmap.getPixel(x, y)
                                val target = colors[expected.getValue(degrees)[i]]
                                kotlin.math.abs(Color.red(actual) - Color.red(target)) < 40 &&
                                    kotlin.math.abs(Color.green(actual) - Color.green(target)) < 40 &&
                                    kotlin.math.abs(Color.blue(actual) - Color.blue(target)) < 40
                            }
                            if (matched && cycle == 0) File(folder, "angle-$degrees.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                        } finally { bitmap.recycle() }
                    }
                    check(matched) { "Displayed pixels did not rotate to $degrees degrees on cycle $cycle" }
                    checks++
                }
            }
        } finally { test.runOnMainSync { activity.finish() } }
        return "PASS rotation pixels: $checks angle checks (0/90/180/270/0 x3), actual production surfaces. Drawn pattern; no iPhone or decoder acceptance."
    }
}
