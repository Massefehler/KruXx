package app.kreate.android.downloads

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DownloadFile(val uri: Uri, val name: String, val mime: String, val size: Long, val path: String)
internal class DownloadFileChangedException : IOException("The selected file has changed")

/** The public Downloads destination is persisted like a picked folder, without a tree grant. */
internal object SharedDownloads {
    val destination: Uri = "kruxx-downloads://public".toUri()
    const val DIRECTORY = "KruXx-Downloads"
    val relativePath: String get() = "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY/"
    const val AUDIO = "Audio"
    const val VIDEO = "Video"
    private val generatedName = Regex("\\[[a-f0-9]{12}]")
    private val filesLock = Mutex()
    fun subdirectory(mime: String) = if (mime.startsWith("video/")) VIDEO else AUDIO
    fun relativePath(mime: String) = "$relativePath${subdirectory(mime)}/"
    private fun ownsPath(path: String?) = path in setOf(relativePath, "$relativePath$AUDIO/", "$relativePath$VIDEO/")

    @Suppress("DEPRECATION")
    fun legacyDirectory(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIRECTORY)
    fun legacyDirectory(mime: String): File = File(legacyDirectory(), subdirectory(mime))

    fun acceptsTree(uri: Uri): Boolean = runCatching {
        uri.authority == "com.android.externalstorage.documents" &&
            DocumentsContract.getTreeDocumentId(uri) in listOf(
                "primary:${relativePath.removeSuffix("/")}",
                "primary:$relativePath$AUDIO", "primary:$relativePath$VIDEO")
    }.getOrDefault(false)

    /** Delete an explicitly selected file, never its folder or any private download. */
    suspend fun remove(context: Context, file: DownloadFile) = filesLock.withLock {
        require(ownsPath(file.path) && file.name.isNotBlank() && '/' !in file.name &&
            !file.name.startsWith("kruxx-partial-"))
        when {
            file.uri.scheme == "file" -> {
                val selected = File(requireNotNull(file.uri.path))
                val directory = File(legacyDirectory(), file.path.removePrefix(relativePath)).canonicalFile
                require(selected.absoluteFile.parentFile?.canonicalFile == directory &&
                    selected.canonicalFile.parentFile == directory && selected.name == file.name)
                if (selected.exists()) {
                    if (!selected.isFile || selected.length() != file.size) throw DownloadFileChangedException()
                    check(selected.delete()) { "Cannot delete download" }
                }
            }
            file.uri.authority == MediaStore.AUTHORITY && Build.VERSION.SDK_INT >= 29 -> {
                // Fall back to an existing writable tree grant: a listed MediaStore copy can belong
                // to DocumentsUI, including copies made by our own folder picker.
                val granted = context.contentResolver.persistedUriPermissions.firstOrNull {
                    it.isWritePermission && it.isReadPermission && acceptsTree(it.uri) &&
                        "primary:${file.path.removeSuffix("/")}".let { parent ->
                            val tree = DocumentsContract.getTreeDocumentId(it.uri)
                            parent == tree || parent.startsWith("$tree/")
                        }
                }
                val document = granted?.let {
                    DocumentsContract.buildDocumentUriUsingTree(it.uri, "primary:${file.path}${file.name}")
                }
                try { removeMediaStore(context, file) }
                catch (e: SecurityException) {
                    if (document == null) throw e
                    removeDocument(context, file.copy(uri = document))
                }
            }
            DocumentsContract.isDocumentUri(context, file.uri) -> removeDocument(context, file)
            else -> error("Unsupported download URI")
        }
    }

    private fun removeDocument(context: Context, file: DownloadFile) {
        require(DocumentsContract.getDocumentId(file.uri) == "primary:${file.path}${file.name}")
        val resolver = context.contentResolver
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(file.uri, columns, null, null, null)
            ?: throw IOException("Cannot inspect download")
        cursor.use {
            if (!it.moveToFirst()) return
            if (it.getString(0) != file.name || it.getLong(1) != file.size ||
                it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) throw DownloadFileChangedException()
        }
        check(DocumentsContract.deleteDocument(resolver, file.uri)) { "Cannot delete document" }
    }

    @RequiresApi(29)
    private fun removeMediaStore(context: Context, file: DownloadFile) {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        require(file.uri == ContentUris.withAppendedId(collection, ContentUris.parseId(file.uri)))
        val resolver = context.contentResolver
        val columns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING)
        val cursor = resolver.query(file.uri, columns, null, null, null)
            ?: throw IOException("Cannot inspect download")
        cursor.use {
            if (!it.moveToFirst()) return
            if (it.getString(0) != file.name || it.getString(1) != file.path ||
                it.getLong(2) != file.size || it.getInt(3) != 0) throw DownloadFileChangedException()
        }
        // Keep the same snapshot constraints in the mutation, including the exact row URI.
        check(resolver.delete(file.uri,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.SIZE} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(file.name, file.path, file.size.toString())) == 1) { "Cannot delete download" }
    }

    /** Read the current files, including old copies in the root, never the private asset registry. */
    suspend fun list(context: Context): List<DownloadFile> = filesLock.withLock {
        if (Build.VERSION.SDK_INT >= 29) {
            migrate(context)
            (listMediaStore(context) + listGrantedFolders(context)).distinctBy { it.path to it.name }
        } else {
            val root = legacyDirectory()
            listOf(root, File(root, AUDIO), File(root, VIDEO)).flatMap { directory ->
                renameGrantedFiles(DocumentFile.fromFile(directory))
                directory.listFiles()?.filter { it.isFile && !it.name.startsWith("kruxx-partial-") }?.map { file ->
                    DownloadFile(Uri.fromFile(file), file.name,
                        fileMime(context, Uri.fromFile(file), file.name, null,
                            relativePath + if (directory == root) "" else "${directory.name}/"), file.length(),
                        relativePath + if (directory == root) "" else "${directory.name}/")
                }.orEmpty()
            }
        }.sortedBy { it.name.lowercase() }
    }

    /** SAF-created copies belong to the document provider, so MediaStore may hide them from us. */
    private fun listGrantedFolders(context: Context): List<DownloadFile> = buildList {
        val rootId = "primary:${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY"
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.forEach { permission ->
            val id = runCatching { DocumentsContract.getTreeDocumentId(permission.uri) }.getOrNull()
            if (id !in listOf(rootId, "$rootId/$AUDIO", "$rootId/$VIDEO")) return@forEach
            val tree = DocumentFile.fromTreeUri(context, permission.uri) ?: return@forEach
            val directories = if (id == rootId) listOf(tree) + listOfNotNull(tree.findFile(AUDIO), tree.findFile(VIDEO))
                else listOf(tree)
            directories.filter { it.isDirectory }.forEach { directory ->
                val path = relativePath + if (directory == tree && id == rootId) "" else "${directory.name}/"
                renameGrantedFiles(directory)
                directory.listFiles().filter { it.isFile }.forEach fileLoop@ { file ->
                    val name = file.name ?: return@fileLoop
                    if (!name.startsWith("kruxx-partial-"))
                        add(DownloadFile(file.uri, name, fileMime(context, file.uri, name, file.type, path), file.length(), path))
                }
            }
        }
    }

    internal fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "webm" -> if (name.contains(" - Video")) "video/webm" else "audio/webm"
        else -> "application/octet-stream"
    }

    // Android's scanner can replace audio/webm with video/webm based on the extension alone.
    // Our destination and generated video suffix retain the actual export format.
    private fun listedMime(name: String, reported: String?, path: String): String {
        if (name.endsWith(".webm", ignoreCase = true)) {
            if (path == relativePath("audio/webm")) return "audio/webm"
            if (path == relativePath("video/webm")) return "video/webm"
            if (generatedName.containsMatchIn(name)) return mimeFor(name)
        }
        return reported ?: mimeFor(name)
    }

    /** Old root copies have no Audio/Video path; after renaming, inspect ambiguous WebM tracks. */
    private fun fileMime(context: Context, uri: Uri, name: String, reported: String?, path: String): String {
        val fallback = listedMime(name, reported, path)
        if (path != relativePath || !name.endsWith(".webm", ignoreCase = true) ||
            generatedName.containsMatchIn(name)) return fallback
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val tracks = (0 until extractor.trackCount).mapNotNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            }
            when {
                tracks.any { it.startsWith("video/") } -> "video/webm"
                tracks.any { it.startsWith("audio/") } -> "audio/webm"
                else -> fallback
            }
        } catch (_: Exception) { fallback }
        finally { extractor.release() }
    }

    @RequiresApi(29)
    private fun listMediaStore(context: Context): List<DownloadFile> = buildList {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        for (path in listOf(relativePath, "$relativePath$AUDIO/", "$relativePath$VIDEO/")) {
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf(path), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (!name.startsWith("kruxx-partial-")) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                        add(DownloadFile(uri, name, fileMime(context, uri, name, cursor.getString(2), cursor.getString(4)),
                            cursor.getLong(3), cursor.getString(4)))
                    }
                }
            }
        }
    }

    /** Rename only known old exporter names in a directory already writable through SAF/legacy. */
    internal fun renameGrantedFiles(directory: DocumentFile) {
        if (!directory.isDirectory || !directory.canWrite()) return
        val tracks = DownloadCenter.saved.value.map { it.track }.distinctBy { it.id }
        if (tracks.isEmpty()) return
        val files = directory.listFiles()
        val occupied = files.mapNotNull { it.name }.toMutableSet()
        files.filter { it.isFile }.forEach { file ->
            val oldName = file.name ?: return@forEach
            val wanted = DownloadNames.renamedLegacyFile(oldName, tracks) ?: return@forEach
            val next = DownloadNames.availableName(wanted, occupied)
            if (runCatching { file.renameTo(next) }.getOrDefault(false)) {
                occupied -= oldName
                occupied += file.name ?: next
            }
        }
    }

    /** Move generated root files and rename known copies; never modify another app's MediaStore rows. */
    @RequiresApi(29)
    private fun migrate(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        data class Candidate(val uri: Uri, val name: String, val mime: String, val path: String, val owned: Boolean)
        val files = mutableListOf<Candidate>()
        for (path in listOf(relativePath, "$relativePath$AUDIO/", "$relativePath$VIDEO/")) {
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf(path), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    files += Candidate(ContentUris.withAppendedId(collection, cursor.getLong(0)), name,
                        listedMime(name, cursor.getString(2), path), path, cursor.getString(3) == context.packageName)
                }
            }
        }
        val tracks = DownloadCenter.saved.value.map { it.track }.distinctBy { it.id }
        val occupied = files.groupBy { it.path }.mapValues { (_, rows) -> rows.map { it.name }.toMutableSet() }.toMutableMap()
        files.filter { it.owned && generatedName.containsMatchIn(it.name) }.forEach { file ->
            val targetPath = if (file.path == relativePath &&
                (file.mime.startsWith("audio/") || file.mime.startsWith("video/"))) relativePath(file.mime) else file.path
            val wanted = DownloadNames.renamedLegacyFile(file.name, tracks)
            if (targetPath == file.path && wanted == null) return@forEach
            val names = occupied.getOrPut(targetPath) { mutableSetOf() }
            val next = DownloadNames.availableName(wanted ?: file.name, if (targetPath == file.path) names - file.name else names)
            // Failed renames stay visible at their actual path and can be retried on the next refresh.
            if (runCatching { resolver.update(file.uri, ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetPath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, next)
            }, null, null) == 1 }.getOrDefault(false)) {
                occupied[file.path]?.remove(file.name)
                names += next
            }
        }
    }

    @RequiresApi(29)
    suspend fun export(context: Context, source: File, name: String, mime: String, marker: File, onProgress: FileProgress = { _, _ -> }): Boolean = filesLock.withLock {
        onProgress(DownloadStep.SAVING, 0.0)
        migrate(context)
        val relativePath = relativePath(mime)
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val expected = source.inputStream().use { OfflineFiles.digest(it) }
        val names = mutableSetOf<String>()
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.')
        val numbered = Regex("${Regex.escape(stem)} \\([0-9]+\\)\\.${Regex.escape(extension)}")
        resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE), "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
            arrayOf(relativePath), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val existingName = cursor.getString(1)
                names += existingName
                if ((existingName == name || numbered.matches(existingName)) && cursor.getLong(2) == source.length()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    if (resolver.openInputStream(uri)?.use { OfflineFiles.digest(it).contentEquals(expected) } == true)
                        return@withLock false
                }
            }
        }
        var finalName = name
        var number = 2
        while (finalName in names) finalName = "$stem (${number++}).$extension"
        val temporaryName = "kruxx-partial-${UUID.randomUUID()}.$extension"
        val uri = checkNotNull(resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, temporaryName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })) { "Cannot create download file" }
        var committed = false
        try {
            marker.writeText("$uri\n$temporaryName")
            val copied = resolver.openOutputStream(uri, "wt")?.use { out ->
                source.inputStream().use { input -> OfflineFiles.copyStream(input, out) { onProgress(DownloadStep.SAVING, byteFraction(it, source.length())) } }
            }
            check(copied == source.length()) { "Incomplete copy" }
            onProgress(DownloadStep.VERIFYING, 0.0)
            check(resolver.openInputStream(uri)?.use { input ->
                OfflineFiles.digest(input) { onProgress(DownloadStep.VERIFYING, byteFraction(it, source.length())) }.contentEquals(expected)
            } == true) {
                "Destination verification failed"
            }
            // MediaStore resolves collisions with files outside our readable collection itself.
            check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null) == 1) { "Cannot publish download file" }
            committed = true
            return@withLock true
        } finally {
            if (!committed) runCatching { resolver.delete(uri, null, null) }
            marker.delete()
        }
    }

    @RequiresApi(29)
    fun cleanInterrupted(context: Context, uri: Uri, name: String?) {
        if (name?.startsWith("kruxx-partial-") != true) return
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && cursor.getString(0) == name && cursor.getInt(1) == 1 &&
                ownsPath(cursor.getString(2))) resolver.delete(uri, null, null)
        }
    }
}
