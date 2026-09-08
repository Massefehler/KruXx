package app.kreate.android.downloads

import java.io.File

/** Tags written by KruXx's converter, separate from the native encoder and audio bytes. */
internal object Mp3Tags {
    fun encode(title: String, artist: String): ByteArray {
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

    /** Reuse an older private conversion with current names, without re-encoding its audio. */
    suspend fun copyWithUpdatedNames(source: File, target: File, names: TrackNames,
                                     onProgress: suspend (Double) -> Unit = {}): Boolean {
        require(source.canonicalFile != target.canonicalFile)
        val wanted = encode(names.title, names.artist)
        source.inputStream().buffered().use { input ->
            val header = ByteArray(10)
            check(input.read(header) == header.size && header.take(6) == listOf<Byte>(73, 68, 51, 3, 0, 0)) {
                "Unsupported private MP3 tags"
            }
            check(header.drop(6).all { it >= 0 })
            val size = header.drop(6).fold(0) { count, byte -> (count shl 7) or byte.toInt() }
            check(size in 1..64 * 1024 && source.length() > 10 + size)
            val body = ByteArray(size)
            java.io.DataInputStream(input).readFully(body)
            if ((header + body).contentEquals(wanted)) return false
            // Older KruXx conversions contain exactly TIT2 and TPE1. Do not discard tags
            // added by an external editor if a private file has unexpectedly been replaced.
            var offset = 0
            for (id in listOf("TIT2", "TPE1")) {
                check(offset + 10 <= body.size && String(body, offset, 4, Charsets.US_ASCII) == id)
                val length = java.nio.ByteBuffer.wrap(body, offset + 4, 4).int
                check(length > 0 && length <= body.size - offset - 10 && body[offset + 8] == 0.toByte() && body[offset + 9] == 0.toByte())
                offset += 10 + length
            }
            check(offset == body.size)
            target.outputStream().buffered().use { output ->
                output.write(wanted)
                OfflineFiles.copyStream(input, output) { bytes -> onProgress(byteFraction(bytes, source.length() - 10 - size)) }
            }
        }
        return true
    }
}
