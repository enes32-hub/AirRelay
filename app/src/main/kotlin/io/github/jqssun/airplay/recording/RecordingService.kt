package io.github.jqssun.airplay.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jqssun.airplay.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Explicit decoded-video and app-playback capture. No capture is restored after process death. */
class RecordingService : Service() {
    companion object {
        private const val TAG = "RecordingService"
        const val STOP = "recording.stop"
        private val mutable = MutableStateFlow(Status())
        val status = mutable.asStateFlow()
        fun files(context: android.content.Context): List<File> =
            File(context.filesDir, "recordings").listFiles()?.filter { it.extension == "mp4" }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    data class Status(val active: Boolean = false, val message: Int = 0)
    private val worker = HandlerThread("recording").apply { start() }
    private val handler = Handler(worker.looper)
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var pending: File? = null
    private var audio: RecordingAudio? = null
    private var audioFile: File? = null
    private var cleanTarget: CleanRecording.Target? = null
    private var started = false
    private var finishing = false
    private val notificationLock = Any()
    private var notificationVisible = false
    private val experience by lazy { getSharedPreferences(io.github.jqssun.airplay.receiver.UserExperiencePrefs.NAME, MODE_PRIVATE) }
    private val languageListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == io.github.jqssun.airplay.receiver.UserExperiencePrefs.LANGUAGE) synchronized(notificationLock) {
            if (notificationVisible && (Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED))
                getSystemService(NotificationManager::class.java).notify(207, recordingNotification())
        }
    }

    override fun onCreate() {
        super.onCreate()
        experience.registerOnSharedPreferenceChangeListener(languageListener)
    }

    private fun recordingNotification(): Notification {
        val localized = io.github.jqssun.airplay.receiver.AppLanguages.context(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("recording", localized.getString(R.string.rec_title), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "recording")
            .setSmallIcon(io.github.jqssun.airplay.R.drawable.ic_notification).setContentTitle(localized.getString(R.string.rec_active))
            .setOngoing(true).addAction(0, localized.getString(R.string.rec_stop), stop).build()
    }

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == STOP) {
            handler.post { finishCapture() }
            return START_NOT_STICKY
        }
        val notification = recordingNotification()
        try {
            synchronized(notificationLock) {
                if (Build.VERSION.SDK_INT >= 29) startForeground(207, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                else startForeground(207, notification)
                notificationVisible = true
            }
            handler.post { startCapture(intent) }
        } catch (_: Exception) {
            mutable.value = Status(message = R.string.rec_unavailable)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun startCapture(intent: Intent) {
        if (recorder != null || finishing) return
        try {
            val consent = intent.getParcelableExtra<Intent>("consent") ?: error("No consent")
            val folder = File(filesDir, "recordings").apply { mkdirs() }
            require(folder.usableSpace > 256L * 1024 * 1024)
            // Remove only unfinished recordings owned by this component.
            folder.listFiles()?.filter { it.extension == "partial" }?.forEach { it.delete() }
            val file = File(folder, "AirRelay-${SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())}.partial")
            pending = file
            val metrics = resources.displayMetrics
            val maxHeight = if (intent.getIntExtra("quality", 720) == 1080) 1080 else 720
            val scale = minOf(1.0, maxHeight.toDouble() / maxOf(1, metrics.heightPixels))
            val width = ((metrics.widthPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val height = ((metrics.heightPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val capture = MediaRecorder().also { recorder = it }
            capture.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            capture.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            capture.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            capture.setVideoSize(width, height)
            capture.setVideoFrameRate(30)
            capture.setVideoEncodingBitRate(if (maxHeight == 1080) 8_000_000 else 4_000_000)
            capture.setOutputFile(file.absolutePath)
            capture.setMaxFileSize(minOf(2L * 1024 * 1024 * 1024, (folder.usableSpace - 128L * 1024 * 1024) / 2))
            capture.setMaxDuration(60 * 60 * 1000)
            capture.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
                    handler.post { finishCapture() }
            }
            capture.setOnErrorListener { _, _, _ -> handler.post { finishCapture(R.string.rec_failed) } }
            capture.prepare()
            val session = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(Activity.RESULT_OK, consent) ?: error("No projection")
            projection = session
            session.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { finishCapture() }
            }, handler)
            capture.start()
            started = true
            val videoStartedNs = SystemClock.elapsedRealtimeNanos()
            cleanTarget = CleanRecording.Target(capture.surface, width, height, System.nanoTime()) {
                handler.post { finishCapture(R.string.rec_failed) }
            }.also { CleanRecording.attach(it) }
            if (Build.VERSION.SDK_INT >= 29) {
                val sound = File(folder, file.nameWithoutExtension + "-audio.partial")
                audioFile = sound
                // Retain ownership before start: initialization may fail after AudioRecord allocation.
                val captureAudio = RecordingAudio(session, sound)
                audio = captureAudio
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    throw SecurityException("Playback audio capture permission unavailable")
                captureAudio.start(videoStartedNs)
            }
            mutable.value = Status(active = true)
        } catch (e: Exception) {
            // Keep the real cause visible: a missing RECORD_AUDIO grant, an unsupported
            // capture size or a MediaRecorder failure are very different problems.
            Log.w(TAG, "Recording start failed: ${e.javaClass.simpleName}: ${e.message}")
            finishCapture(R.string.rec_unavailable)
        }
    }

    private fun finishCapture(error: Int = 0) {
        if (finishing) return
        finishing = true
        var saved = false
        runCatching { CleanRecording.detach() }
        try {
            if (started) { recorder?.stop(); saved = true }
        } catch (_: Exception) { saved = false }
        val sound = audio
        val audioSaved = if (Build.VERSION.SDK_INT >= 29 && sound != null) runCatching { sound.finish() }.isSuccess else false
        audio = null
        runCatching { recorder?.release() }; recorder = null
        runCatching { projection?.stop() }; projection = null
        val file = pending
        var soundAttached = false
        if (saved && file != null && file.length() > 0) {
            val destination = File(file.parentFile, file.nameWithoutExtension + ".mp4")
            if (Build.VERSION.SDK_INT >= 29 && audioSaved && audioFile != null && sound != null) {
                val merged = File(file.parentFile, file.nameWithoutExtension + "-merged.partial")
                soundAttached = runCatching {
                    val pictureDelayUs = cleanTarget?.let { target ->
                        (target.firstFrameNs.get() - target.startedNs).coerceAtLeast(0L) / 1000L
                    } ?: 0L
                    RecordingMux.combine(file, audioFile!!, merged, sound.offsetUs - pictureDelayUs)
                    check(merged.renameTo(destination))
                    file.delete()
                }.isSuccess
                if (!soundAttached) merged.delete()
            }
            saved = soundAttached || file.renameTo(destination)
        } else saved = false
        audioFile?.delete(); audioFile = null
        if (!saved) file?.delete()
        pending = null
        cleanTarget = null
        mutable.value = Status(message = if (error != 0) error else if (saved && Build.VERSION.SDK_INT >= 29 && !soundAttached) R.string.rec_audio_failed else if (saved) R.string.rec_saved else R.string.rec_failed)
        synchronized(notificationLock) {
            notificationVisible = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }
    override fun onDestroy() {
        experience.unregisterOnSharedPreferenceChangeListener(languageListener)
        handler.post { finishCapture(); worker.quitSafely() }
        super.onDestroy()
    }
}

