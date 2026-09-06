@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.FileOutputStreamSeekableMuxerOutput
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.WebmMuxer
import app.kreate.di.CacheType
import app.kreate.di.forMediaTransport
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal object OfflineFiles : KoinComponent {
    /** Cache-only reads: an incomplete/removed span fails, and can never start a network request. */
    suspend fun snapshot(id: String, target: File, onProgress: suspend (Double) -> Unit = {}): String {
        val cache = get<Cache>(CacheType.DOWNLOAD)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(id))
        check(length > 0 && cache.isCached(id, 0, length)) { "Download is incomplete" }
        val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null)
            .setCacheWriteDataSinkFactory(null).createDataSource()
        try {
            source.open(DataSpec.Builder().setUri("https://offline.invalid/$id".toUri())
                .setKey(id).setLength(length).build())
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                var count = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = source.read(buffer, 0, buffer.size)
                    if (size == C.RESULT_END_OF_INPUT) break
                    output.write(buffer, 0, size)
                    count += size
                    onProgress(byteFraction(count, length))
                }
                check(count == length) { "Download was removed while copying" }
            }
        } finally { source.close() }
        return containerExtension(target)
    }

    fun containerExtension(file: File): String = file.inputStream().use { input ->
        val bytes = ByteArray(16)
        val size = input.read(bytes)
        require(size >= 12) { "Empty media file" }
        when {
            bytes[0] == 0x1a.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xdf.toByte() && bytes[3] == 0xa3.toByte() -> "webm"
            String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "m4a"
            String(bytes, 0, 4, Charsets.US_ASCII) == "OggS" -> "ogg"
            String(bytes, 0, 3, Charsets.US_ASCII) == "ID3" ||
                (bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 224 == 224) -> "mp3"
            else -> throw IOException("Unknown audio container")
        }
    }

    /** Direct CDN transfer is isolated from Media3 cache keys/itag metadata. */
    suspend fun video(track: DownloadTrack, audio: File, scratch: File, onProgress: FileProgress = { _, _ -> }): Pair<File, String> {
        onProgress(DownloadStep.VIDEO, 0.0)
        val stream = InnerTubeXPlayer.videoForDownload(track.id, track.explicit)
        val client = get<OkHttpClient>().forMediaTransport().newBuilder()
            .readTimeout(60, TimeUnit.SECONDS).build()
        val raw = File(scratch, "video-source")
        val length = stream.videoContentLengthBytes?.takeIf { it > 0 }
        val bounded = stream.requireBoundedRange || stream.useRangeChunks
        require(!bounded || length != null) { "Unknown video length" }
        var position = 0L
        raw.outputStream().buffered().use { output ->
            do {
                currentCoroutineContext().ensureActive()
                val end = if (bounded) minOf(length!! - 1, position + stream.rangeChunkSizeBytes.coerceIn(64 * 1024, 8 * 1024 * 1024) - 1) else null
                val request = Request.Builder().url(requireNotNull(stream.videoUrl)).apply {
                    stream.headers.forEach { (name, value) -> header(name, value) }
                    header("Accept-Encoding", "identity")
                    if (bounded) header("Range", "bytes=$position-$end")
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Video download HTTP ${response.code}")
                    if (bounded) {
                        val range = response.header("Content-Range")
                        // Never concatenate an ignored Range request with a partial response.
                        check(response.code == 206 && range?.startsWith("bytes $position-") == true) {
                            "Server did not honor the video byte range"
                        }
                    }
                    val body = requireNotNull(response.body)
                    val transferred = body.byteStream().use { input ->
                        copyStream(input, output) { bytes ->
                            onProgress(DownloadStep.VIDEO, byteFraction(position + bytes, length ?: body.contentLength()))
                        }
                    }
                    check(transferred > 0)
                    if (end != null) check(transferred == end - position + 1) { "Incomplete video range" }
                    position += transferred
                }
            } while (bounded && position < length!!)
        }
        if (length != null) check(position == length) { "Incomplete video download" }
        onProgress(DownloadStep.VIDEO, 1.0)
        return mux(raw, audio, scratch, track.durationMs * 1000, onProgress)
    }

    private suspend fun mux(video: File, audio: File, scratch: File, durationUs: Long, onProgress: FileProgress): Pair<File, String> {
        onProgress(DownloadStep.JOINING, 0.0)
        val extractors = listOf(MediaExtractor(), MediaExtractor())
        try {
            val formats = extractors.mapIndexed { index, extractor ->
                extractor.setDataSource((if (index == 0) video else audio).absolutePath)
                val prefix = if (index == 0) "video/" else "audio/"
                val track = (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
                }
                extractor.selectTrack(track)
                MediaFormatUtil.createFormatFromMediaFormat(extractor.getTrackFormat(track))
            }
            val webm = formats[0].sampleMimeType in listOf("video/x-vnd.on2.vp8", "video/x-vnd.on2.vp9") &&
                formats[1].sampleMimeType in listOf("audio/opus", "audio/vorbis")
            val extension = if (webm) "webm" else "mp4"
            val target = File(scratch, "rendered.$extension")
            val output = FileOutputStreamSeekableMuxerOutput(target.outputStream())
            val muxer: Muxer = if (webm) WebmMuxer.Builder(output).build() else Mp4Muxer.Builder(output).build()
            muxer.use {
                val tracks = formats.map(muxer::addTrack)
                val capacity = maxOf(8 * 1024 * 1024, formats.maxOf { it.maxInputSize.coerceAtLeast(0) })
                check(capacity <= 64 * 1024 * 1024) { "Video sample too large" }
                val buffer = ByteBuffer.allocateDirect(capacity)
                val samples = IntArray(2)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val next = extractors.indices.filter { extractors[it].sampleTime >= 0 }
                        .minByOrNull { extractors[it].sampleTime } ?: break
                    val extractor = extractors[next]
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    check(size >= 0 && size <= buffer.capacity())
                    buffer.position(0)
                    buffer.limit(size)
                    muxer.writeSampleData(tracks[next], buffer, BufferInfo(
                        extractor.sampleTime, size,
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) C.BUFFER_FLAG_KEY_FRAME else 0,
                    ))
                    samples[next]++
                    onProgress(DownloadStep.JOINING, byteFraction(extractor.sampleTime, durationUs))
                    extractor.advance()
                }
                check(samples.all { it > 0 }) { "Missing audio or video track" }
            }
            onProgress(DownloadStep.JOINING, 1.0)
            return target to extension
        } finally { extractors.forEach(MediaExtractor::release) }
    }

    fun fileName(track: DownloadTrack, extension: String): String = DownloadNames.fileName(track, extension)

    /** A provider may be a USB stick. Validate, stream, verify, then publish the final name. */
    suspend fun export(context: Context, folder: Uri, source: File, name: String, mime: String, marker: File, onProgress: FileProgress = { _, _ -> }): Boolean {
        if (folder == SharedDownloads.destination && Build.VERSION.SDK_INT >= 29)
            return SharedDownloads.export(context, source, name, mime, marker, onProgress)
        val directory = if (folder == SharedDownloads.destination) {
            val path = SharedDownloads.legacyDirectory(mime)
            check(path.isDirectory || path.mkdirs()) { "Cannot create downloads directory" }
            DocumentFile.fromFile(path)
        } else requireNotNull(DocumentFile.fromTreeUri(context, folder))
        check(directory.isDirectory && directory.canWrite()) { "Destination unavailable; choose the folder again" }
        SharedDownloads.renameGrantedFiles(directory)
        onProgress(DownloadStep.SAVING, 0.0)
        val expected = source.inputStream().use { digest(it) }
        var finalName = name
        var number = 2
        while (true) {
            val existing = directory.findFile(finalName) ?: break
            if (existing.isFile && existing.length() == source.length() &&
                context.contentResolver.openInputStream(existing.uri)?.use { digest(it).contentEquals(expected) } == true
            ) return false
            // Preserve different files, and recognize our identical numbered copies on retry.
            finalName = "${name.substringBeforeLast('.')} (${number++}).${name.substringAfterLast('.')}"
        }
        val temporaryName = "kruxx-partial-${UUID.randomUUID()}"
        val temporary = checkNotNull(directory.createFile(mime, temporaryName)) { "Cannot create destination file" }
        var committed = false
        try {
            marker.writeText(temporary.uri.toString() + "\n" + temporary.name.orEmpty())
            val copied = context.contentResolver.openOutputStream(temporary.uri, "wt")?.use { out ->
                source.inputStream().use { input -> copyStream(input, out) { onProgress(DownloadStep.SAVING, byteFraction(it, source.length())) } }
            }
            check(copied == source.length()) { "Incomplete copy" }
            onProgress(DownloadStep.VERIFYING, 0.0)
            check(context.contentResolver.openInputStream(temporary.uri)?.use { input ->
                digest(input) { onProgress(DownloadStep.VERIFYING, byteFraction(it, source.length())) }.contentEquals(expected)
            } == true) {
                "Destination verification failed"
            }
            check(temporary.renameTo(finalName)) { "Cannot finish destination file" }
            committed = true
            return true
        } finally {
            if (!committed) runCatching { temporary.delete() }
            marker.delete()
        }
    }

    fun cleanInterruptedExport(context: Context, marker: File) {
        if (!marker.isFile) return
        runCatching {
            val lines = marker.readLines()
            val uri = lines[0].toUri()
            if (uri.authority == "media" && Build.VERSION.SDK_INT >= 29) {
                SharedDownloads.cleanInterrupted(context, uri, lines.getOrNull(1))
                return@runCatching
            }
            val doc = if (uri.scheme == "file") {
                val file = File(requireNotNull(uri.path))
                check(file.canonicalFile.parentFile in listOf(SharedDownloads.legacyDirectory(),
                    SharedDownloads.legacyDirectory("audio/mpeg"), SharedDownloads.legacyDirectory("video/mp4"))
                    .map { it.canonicalFile })
                DocumentFile.fromFile(file)
            } else DocumentFile.fromSingleUri(context, uri)
            // A provider may retain the URI after rename: never remove a completed file.
            if (doc?.name == lines.getOrNull(1) && doc?.name?.startsWith("kruxx-partial-") == true) doc.delete()
        }
        marker.delete()
    }

    suspend fun copyStream(input: InputStream, output: OutputStream, onProgress: suspend (Long) -> Unit = {}): Long {
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) return total
            output.write(buffer, 0, size)
            total += size
            onProgress(total)
        }
    }

    internal suspend fun digest(input: InputStream, onProgress: suspend (Long) -> Unit = {}): ByteArray {
        var total = 0L
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) return hash.digest()
            hash.update(buffer, 0, size)
            total += size
            onProgress(total)
        }
    }
}
