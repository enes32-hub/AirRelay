package io.github.jqssun.airplay

import android.media.*
import java.io.File
import java.nio.ByteOrder

object RecordedAudioProbe {
    fun tone(): AudioTrack {
        val pcm = ShortArray(48000 * 2) { i -> (kotlin.math.sin((i / 2) * 2.0 * Math.PI * 440.0 / 48000) * 8000).toInt().toShort() }
        return AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build().apply {
                check(write(pcm, 0, pcm.size) == pcm.size)
                setLoopPoints(0, 48000, -1)
                play()
            }
    }

    fun verify(file: File): Int {
        val reader = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            reader.setDataSource(file.absolutePath)
            val track = (0 until reader.trackCount).first { reader.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            reader.selectTrack(track)
            val format = reader.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            decoder.configure(format, null, null, 0); decoder.start()
            val info = MediaCodec.BufferInfo()
            var eof = false
            var peak = 0
            repeat(400) {
                if (!eof) {
                    val input = decoder.dequeueInputBuffer(10000)
                    if (input >= 0) {
                        val buffer = decoder.getInputBuffer(input)!!
                        val size = reader.readSampleData(buffer, 0)
                        eof = size < 0
                        decoder.queueInputBuffer(input, 0, size.coerceAtLeast(0), reader.sampleTime.coerceAtLeast(0),
                            if (eof) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!eof) reader.advance()
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                if (index >= 0) {
                    val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    while (buffer.remaining() >= 2) peak = maxOf(peak, kotlin.math.abs(buffer.short.toInt()))
                    val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index, false)
                    if (ended) { check(peak > 500) { "Recorded audio is silent: peak=$peak" }; return peak }
                }
            }
            error("Audio decoding timed out")
        } finally { runCatching { codec?.stop() }; codec?.release(); reader.release() }
    }
}
