package app.kreate.android.downloads

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.Keep
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteOrder

/** Android decodes the best downloaded source; LAME encodes PCM as MPEG-1 MP3, CBR 320. */
@Keep
object Mp3Encoder {
    init { System.loadLibrary("kruxx_mp3") }
    private external fun create(rate: Int, channels: Int): Long
    private external fun encode(handle: Long, pcm: ShortArray, samples: Int, output: ByteArray): Int
    private external fun flush(handle: Long, output: ByteArray): Int
    private external fun close(handle: Long)

    suspend fun convert(source: File, destination: File, track: DownloadTrack, onProgress: suspend (Double) -> Unit = {}) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var handle = 0L
        var started = false
        try {
            extractor.setDataSource(source.absolutePath)
            val index = (0 until extractor.trackCount).first { extractor.getTrackFormat(it)
                .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else track.durationMs * 1000
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(channels in 1..2) { "Unsupported channel count: $channels" }
            if (Build.VERSION.SDK_INT >= 24)
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            started = true
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var sampleCount = 0L
            destination.outputStream().buffered().use { output ->
                output.write(id3Tag(track.names.title, track.names.artist))
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = requireNotNull(decoder.getInputBuffer(inputIndex))
                            input.clear()
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(handle == 0L) { "Audio format changed during encoding" }
                            val decoded = decoder.outputFormat
                            channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            pcmEncoding = if (Build.VERSION.SDK_INT >= 24 && decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT ||
                                pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT)
                            handle = create(decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels)
                            check(handle != 0L) { "MP3 encoder initialization failed" }
                        }
                        else -> if (outputIndex >= 0) {
                            try {
                                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                    check(handle != 0L)
                                    val buffer = requireNotNull(decoder.getOutputBuffer(outputIndex))
                                        .order(ByteOrder.nativeOrder())
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val pcm = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        val floats = buffer.asFloatBuffer()
                                        ShortArray(floats.remaining()) {
                                            (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                        }
                                    } else {
                                        val shorts = buffer.asShortBuffer()
                                        ShortArray(shorts.remaining()).also(shorts::get)
                                    }
                                    check(pcm.size % channels == 0)
                                    val samples = pcm.size / channels
                                    val encoded = ByteArray((samples * 1.25).toInt() + 7200)
                                    val count = encode(handle, pcm, samples, encoded)
                                    check(count >= 0) { "MP3 encoding failed ($count)" }
                                    output.write(encoded, 0, count)
                                    sampleCount += samples
                                    onProgress(byteFraction(info.presentationTimeUs, durationUs))
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            } finally {
                                decoder.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
                check(sampleCount > 0 && handle != 0L) { "Empty audio stream" }
                val encoded = ByteArray(7200)
                val count = flush(handle, encoded)
                check(count >= 0)
                output.write(encoded, 0, count)
            }
            onProgress(1.0)
        } finally {
            if (handle != 0L) close(handle)
            if (started) runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }

    /** Small UTF-16 ID3v2.3 tags; accented artist/title names survive on USB players. */
    internal fun id3Tag(title: String, artist: String): ByteArray {
        fun frame(id: String, value: String): ByteArray {
            val text = byteArrayOf(1) + value.toByteArray(Charsets.UTF_16)
            val size = text.size
            return id.toByteArray(Charsets.US_ASCII) +
                byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte(), 0, 0) + text
        }
        val body = frame("TIT2", title.take(4096)) + frame("TPE1", artist.take(4096))
        val size = body.size
        return byteArrayOf(73, 68, 51, 3, 0, 0, ((size ushr 21) and 127).toByte(),
            ((size ushr 14) and 127).toByte(), ((size ushr 7) and 127).toByte(), (size and 127).toByte()) + body
    }
}
