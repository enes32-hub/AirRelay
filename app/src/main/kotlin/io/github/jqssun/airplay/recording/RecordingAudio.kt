package io.github.jqssun.airplay.recording

import android.media.*
import android.media.projection.MediaProjection
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ByteBuffer

/** Captures only this application's playback, never a microphone or another app. */
@RequiresApi(29)
class RecordingAudio(private val projection: MediaProjection, private val output: File) {
    @Volatile private var running = true
    @Volatile private var failure: Throwable? = null
    private var thread: Thread? = null
    private var input: AudioRecord? = null
    var offsetUs: Long = 0; private set

    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun start(videoStartedNs: Long) {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUid(Process.myUid())
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        val format = AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val min = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        require(min > 0)
        val record = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(min * 2, 16384))
            .setAudioPlaybackCaptureConfig(config).build()
        input = record
        check(record.state == AudioRecord.STATE_INITIALIZED)
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        offsetUs = (SystemClock.elapsedRealtimeNanos() - videoStartedNs) / 1000L
        thread = Thread({ encode(record) }, "recording-aac").apply { start() }
    }

    private fun encode(record: AudioRecord) {
        var encoder: MediaCodec? = null
        var mux: MediaMuxer? = null
        var muxStarted = false
        try {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also { encoder = it }
            codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { mux = it }
            val info = MediaCodec.BufferInfo()
            var track = -1
            var frames = 0L
            var eosQueued = false
            var eosDeadline = Long.MAX_VALUE
            var done = false
            var lastPts = -1L
            while (!done) {
                if (!running && eosDeadline == Long.MAX_VALUE) eosDeadline = SystemClock.elapsedRealtime() + 3000
                if (!eosQueued) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(index)).apply { clear() }
                        val count = if (running) record.read(buffer, minOf(buffer.capacity(), 4096), AudioRecord.READ_NON_BLOCKING) else 0
                        check(count >= 0) { "Playback audio capture failed: $count" }
                        val pts = frames * 1_000_000L / 48000
                        if (!running) {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosQueued = true
                            eosDeadline = SystemClock.elapsedRealtime() + 3000
                        } else {
                            codec.queueInputBuffer(index, 0, count, pts, 0)
                            frames += count / 4
                            if (count == 0) Thread.sleep(5)
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    check(!muxStarted)
                    track = writer.addTrack(codec.outputFormat)
                    writer.start(); muxStarted = true
                } else if (index >= 0) {
                    val buffer = checkNotNull(codec.getOutputBuffer(index))
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(muxStarted)
                        info.presentationTimeUs = info.presentationTimeUs.coerceAtLeast(lastPts + 1)
                        lastPts = info.presentationTimeUs
                        writer.writeSampleData(track, buffer, info)
                    }
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
                check(SystemClock.elapsedRealtime() <= eosDeadline) { "Audio encoder drain timed out" }
            }
        } catch (error: Throwable) { failure = error }
        finally {
            runCatching { record.stop() }; record.release(); input = null
            runCatching { encoder?.stop() }; encoder?.release()
            if (muxStarted) runCatching { mux?.stop() }.onFailure { failure = it }
            mux?.release()
        }
    }

    fun finish() {
        running = false
        val worker = thread
        if (worker == null) { runCatching { input?.stop() }; input?.release(); input = null; return }
        worker.join(5000)
        check(!worker.isAlive) { "Audio capture did not stop" }
        failure?.let { throw IllegalStateException("Audio capture failed", it) }
        check(output.length() > 0)
    }
}

object RecordingMux {
    /** Streaming remux; memory usage does not grow with recording duration. */
    @RequiresApi(28)
    fun combine(video: File, audio: File, output: File, audioOffsetUs: Long) {
        require(video.parentFile!!.usableSpace > video.length() + audio.length() + 32L * 1024 * 1024)
        val sources = listOf(MediaExtractor(), MediaExtractor())
        var writer: MediaMuxer? = null
        try {
            sources[0].setDataSource(video.absolutePath)
            sources[1].setDataSource(audio.absolutePath)
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { writer = it }
            val tracks = sources.mapIndexed { i, reader ->
                val track = (0 until reader.trackCount).first { reader.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(if (i == 0) "video/" else "audio/") }
                reader.selectTrack(track)
                mux.addTrack(reader.getTrackFormat(track))
            }
            val origins = sources.map { it.sampleTime.coerceAtLeast(0) }
            mux.start()
            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (sources.any { it.sampleTime >= 0 }) {
                val i = sources.indices.filter { sources[it].sampleTime >= 0 }.minBy {
                    sources[it].sampleTime - origins[it] + if (it == 1) audioOffsetUs else 0
                }
                val reader = sources[i]
                check(reader.sampleSize <= buffer.capacity())
                buffer.clear()
                val size = reader.readSampleData(buffer, 0)
                check(size >= 0)
                info.set(0, size, reader.sampleTime - origins[i] + if (i == 1) audioOffsetUs else 0,
                    if (reader.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                // Audio may begin before the first picture. Trim that prefix instead of
                // shifting the whole soundtrack or writing illegal negative timestamps.
                if (info.presentationTimeUs >= 0) mux.writeSampleData(tracks[i], buffer, info)
                reader.advance()
            }
            mux.stop()
        } catch (error: Throwable) { output.delete(); throw error }
        finally { sources.forEach { it.release() }; writer?.release() }
    }
}
