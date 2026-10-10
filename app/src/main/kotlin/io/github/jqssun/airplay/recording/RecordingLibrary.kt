package io.github.jqssun.airplay.recording

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class RecordingEntry(val file: File, val createdAt: Long, val durationMs: Long?, val height: Int?)

object RecordingLibrary {
    /** Call from an IO dispatcher: metadata may need disk access and codec inspection. */
    fun list(context: Context): List<RecordingEntry> = RecordingService.files(context).map { file ->
        val created = runCatching {
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).apply { isLenient = false }
                .parse(file.nameWithoutExtension.removePrefix("AirRelay-"))!!.time
        }.getOrDefault(file.lastModified())
        var duration: Long? = null
        var height: Int? = null
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(file.absolutePath)
            duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        } catch (_: Exception) {
            // A damaged file stays visible so its owner can remove it.
        } finally { reader.release() }
        RecordingEntry(file, created, duration, height)
    }

    fun delete(context: Context, name: String): Boolean = runCatching {
        val file = RecordingService.files(context).firstOrNull { it.name == name } ?: return false
        val folder = File(context.filesDir, "recordings").canonicalFile
        require(file.canonicalFile.parentFile == folder)
        file.delete()
    }.getOrDefault(false)
}
