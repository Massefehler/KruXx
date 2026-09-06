package app.kreate.android.downloads

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Looper
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.provider.MediaStore
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29], shadows = [DocumentsQueryShadow::class])
class SharedDownloadsTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var provider: DownloadsProvider
    private lateinit var source: File
    private lateinit var marker: File

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        root = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        provider = DownloadsProvider(File(root, "media").apply { mkdirs() })
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        source = File(root, "source").apply { writeBytes(ByteArray(300_000) { (it % 137).toByte() }) }
        marker = File(root, "marker")
    }

    @After fun cleanup() { DownloadCenter.saved.value = emptyList(); root.deleteRecursively() }

    @Test fun publicDownloadsAreVerifiedPublishedAndSkippedOnRetry() = runBlocking {
        val progress = mutableListOf<Pair<DownloadStep, Double>>()
        assertTrue(OfflineFiles.export(context, SharedDownloads.destination, source, "music.mp3", "audio/mpeg", marker) { step, fraction ->
            progress += step to fraction
            if (step == DownloadStep.VERIFYING) {
                // Even the last verified byte is still unpublished until rename/publish succeeds.
                assertEquals(1, provider.rows.values.single().getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            }
        })
        assertTrue(progress.any { it.first == DownloadStep.SAVING && it.second > 0.0 && it.second < 1.0 })
        assertEquals(DownloadStep.VERIFYING to 1.0, progress.last())
        val row = provider.rows.values.single()
        assertEquals("Download/KruXx-Downloads/Audio/", row.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("music.mp3", row.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals(0, row.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertArrayEquals(source.readBytes(), provider.files().single().readBytes())
        assertFalse(OfflineFiles.export(context, SharedDownloads.destination, source, "music.mp3", "audio/mpeg", marker))
        assertEquals(1, provider.rows.size)
        assertTrue(source.exists())
        assertFalse(marker.exists())
    }

    @Test fun differentExistingFileIsPreservedAndNumberedCopyIsRecognized() = runBlocking {
        val other = File(root, "other").apply { writeText("unrelated content") }
        SharedDownloads.export(context, other, "music.mp3", "audio/mpeg", marker)
        assertTrue(SharedDownloads.export(context, source, "music.mp3", "audio/mpeg", marker))
        assertFalse(SharedDownloads.export(context, source, "music.mp3", "audio/mpeg", marker))
        assertEquals(setOf("music.mp3", "music (2).mp3"), provider.rows.values.map { it.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) }.toSet())
        assertEquals("unrelated content", provider.files().first().readText())
    }

    @Test fun failedPublicationRemovesOnlyOurPendingFile() = runBlocking {
        SharedDownloads.export(context, source, "existing.mp3", "audio/mpeg", marker)
        provider.failPublish = true
        assertTrue(runCatching { SharedDownloads.export(context, source, "next.mp3", "audio/mpeg", marker) }.isFailure)
        assertEquals("existing.mp3", provider.rows.values.single().getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertFalse(marker.exists())
        assertTrue(source.exists())
    }

    @Test fun crashCleanupPreservesPublishedFilesAndRemovesPendingCopies() {
        val temporary = "kruxx-partial-owned.mp3"
        fun create() = provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, temporary)
            put(MediaStore.MediaColumns.RELATIVE_PATH, SharedDownloads.relativePath("audio/mpeg"))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })
        val pending = create()
        marker.writeText("$pending\n$temporary")
        OfflineFiles.cleanInterruptedExport(context, marker)
        assertTrue(provider.rows.isEmpty())
        val published = create()
        marker.writeText("$published\n$temporary")
        provider.update(published, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "finished.mp3")
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
        OfflineFiles.cleanInterruptedExport(context, marker)
        assertEquals(1, provider.rows.size)
    }

    @Test @Config(sdk = [28]) fun legacyAndroidCreatesTheSameFolderAndPreservesCopies() = runBlocking {
        val name = "kruxx-test-${UUID.randomUUID()}.mp3"
        val output = File(SharedDownloads.legacyDirectory("audio/mpeg"), name)
        try {
            assertTrue(OfflineFiles.export(context, SharedDownloads.destination, source, name, "audio/mpeg", marker))
            assertArrayEquals(source.readBytes(), output.readBytes())
            assertFalse(OfflineFiles.export(context, SharedDownloads.destination, source, name, "audio/mpeg", marker))
            assertTrue(source.exists())
        } finally { output.delete() }
    }

    @Test fun audioAndVideoAreSeparatedAndTheListReflectsExternalRemoval() = runBlocking {
        SharedDownloads.export(context, source, "audio.webm", "audio/webm", marker)
        SharedDownloads.export(context, source, "video.webm", "video/webm", marker)
        SharedDownloads.export(context, source, "original.m4a", "audio/mp4", marker)
        val files = SharedDownloads.list(context)
        assertEquals(3, files.size)
        assertEquals("Download/KruXx-Downloads/Video/", files.single { it.name == "video.webm" }.path)
        assertEquals("Download/KruXx-Downloads/Audio/", files.single { it.name == "audio.webm" }.path)
        assertEquals("Download/KruXx-Downloads/Audio/", files.single { it.name == "original.m4a" }.path)
        provider.delete(files.first().uri, null, null)
        assertEquals(2, SharedDownloads.list(context).size)
    }

    @Test fun oldGeneratedFilesMoveOnlyWhenOwnedAndFinished() = runBlocking {
        fun old(name: String, owner: String = context.packageName, pending: Int = 0): Uri =
            provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, SharedDownloads.relativePath)
                put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, owner)
                put(MediaStore.MediaColumns.IS_PENDING, pending)
            })
        val mine = old("Title [abcdef012345] - MP3 320.mp3")
        val foreign = old("Friend [012345abcdef] - MP3 320.mp3", "another.app")
        val unrelated = old("notes.mp3")
        val unfinished = old("Pending [123456abcdef] - MP3 320.mp3", pending = 1)
        val before = provider.files().map { it.readBytes().toList() }
        val visible = SharedDownloads.list(context)
        assertEquals(3, visible.size)
        fun path(uri: Uri) = provider.rows.getValue(ContentUris.parseId(uri)).getAsString(MediaStore.MediaColumns.RELATIVE_PATH)
        assertEquals(SharedDownloads.relativePath("audio/mpeg"), path(mine))
        listOf(foreign, unrelated, unfinished).forEach { assertEquals(SharedDownloads.relativePath, path(it)) }
        assertEquals(before, provider.files().map { it.readBytes().toList() })
        assertEquals(visible, SharedDownloads.list(context))
    }

    @Test fun cleanupDoesNotDeletePendingFileOutsideDownloadFolders() {
        val uri = provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "kruxx-partial-other.mp3")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Other/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })
        marker.writeText("$uri\nkruxx-partial-other.mp3")
        OfflineFiles.cleanInterruptedExport(context, marker)
        assertEquals(1, provider.rows.size)
    }

    @Test fun existingKnownDownloadsAreRenamedWithoutChangingBytesOrOverwritingAnotherFile() = runBlocking {
        val track = DownloadTrack("pITRfcVDyGQ", "Noma - Sleepwalker", "dejanprogtrens", videoSource = true)
        DownloadCenter.saved.value = listOf(SavedMedia(track, DownloadKind.MP3, "mp3", source.length()))
        val oldName = "dejanprogtrens - Noma - Sleepwalker [c587dba6992e] - MP3 320.mp3"
        fun create(name: String, owner: String = context.packageName): Uri =
            provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, SharedDownloads.relativePath("audio/mpeg"))
                put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, owner)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            })
        create("Noma - Sleepwalker.mp3")
        val mine = create(oldName)
        val foreign = create(oldName, "another.app")
        context.contentResolver.openOutputStream(mine, "wt")!!.use { it.write(source.readBytes()) }
        val files = SharedDownloads.list(context)
        assertEquals("Noma - Sleepwalker (2).mp3", files.single { ContentUris.parseId(it.uri) == ContentUris.parseId(mine) }.name)
        assertEquals(oldName, files.single { ContentUris.parseId(it.uri) == ContentUris.parseId(foreign) }.name)
        assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(mine)!!.use { it.readBytes() })
        assertEquals(files, SharedDownloads.list(context)) // Idempotent; no additional copies.
        assertFalse(SharedDownloads.export(context, source, "Noma - Sleepwalker.mp3", "audio/mpeg", marker))
        assertEquals(3, provider.rows.size)
    }

    @Test fun scannerWebmMimeDoesNotMisclassifyAudioOrMoveOldAudioIntoVideo() = runBlocking {
        SharedDownloads.export(context, source, "track.webm", "audio/webm", marker)
        SharedDownloads.export(context, source, "film.webm", "video/webm", marker)
        // Some Android providers infer video/webm for both after publication.
        provider.rows.values.forEach { it.put(MediaStore.MediaColumns.MIME_TYPE, "video/webm") }
        provider.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Old audio [abcdef012345].webm")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/webm")
            put(MediaStore.MediaColumns.RELATIVE_PATH, SharedDownloads.relativePath)
            put(MediaStore.MediaColumns.OWNER_PACKAGE_NAME, context.packageName)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        })
        val files = SharedDownloads.list(context)
        assertEquals("audio/webm", files.single { it.name == "track.webm" }.mime)
        assertEquals("video/webm", files.single { it.name == "film.webm" }.mime)
        val migrated = files.single { it.name.startsWith("Old audio") }
        assertEquals("audio/webm", migrated.mime)
        assertEquals(SharedDownloads.relativePath("audio/webm"), migrated.path)
    }

    @Test fun removingAPublicFilePreservesOtherFormatsAndTheSource() = runBlocking {
        SharedDownloads.export(context, source, "music.mp3", "audio/mpeg", marker)
        SharedDownloads.export(context, source, "music.webm", "audio/webm", marker)
        SharedDownloads.export(context, source, "music.mp4", "video/mp4", marker)
        val selected = SharedDownloads.list(context).single { it.name == "music.mp3" }
        SharedDownloads.remove(context, selected)
        assertEquals(setOf("music.webm", "music.mp4"), SharedDownloads.list(context).map { it.name }.toSet())
        assertFalse(File(root, "media/${ContentUris.parseId(selected.uri)}").exists())
        assertTrue(source.exists())
        SharedDownloads.remove(context, selected) // Already removed outside the list is harmless.
        assertEquals(2, provider.rows.size)
    }

    @Test fun changedMovedOrPendingFilesCannotBeDeletedUsingAnOldSelection() = runBlocking {
        SharedDownloads.export(context, source, "music.mp3", "audio/mpeg", marker)
        val selected = SharedDownloads.list(context).single()
        val row = provider.rows.values.single()
        val original = ContentValues(row)
        listOf(ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "renamed.mp3") },
            ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Other/") },
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }).forEach { change ->
            row.clear(); row.putAll(original); row.putAll(change)
            assertTrue(runCatching { SharedDownloads.remove(context, selected) }.exceptionOrNull() is DownloadFileChangedException)
            assertEquals(1, provider.rows.size)
        }
        row.clear(); row.putAll(original)
        provider.files().single().appendText("changed")
        assertTrue(runCatching { SharedDownloads.remove(context, selected) }.exceptionOrNull() is DownloadFileChangedException)
        assertEquals(1, provider.rows.size)
    }

    @Test fun batchRemovalReportsPartialFailureAndRetriesOnlyTheFailedSelection() = runBlocking {
        listOf("one.mp3", "two.mp3", "keep.mp3").forEach {
            SharedDownloads.export(context, source, it, "audio/mpeg", marker)
        }
        val selected = SharedDownloads.list(context).filter { it.name != "keep.mp3" }
        provider.deniedId = ContentUris.parseId(selected.last().uri)
        val model = DownloadRemovalViewModel()
        val store = ViewModelStore().apply { put("removal", model) }
        suspend fun finish() = withTimeout(5_000) {
            while (model.state.value?.finished != true) { shadowOf(Looper.getMainLooper()).idle(); delay(10) }
        }
        try {
            model.request(selected.map { DownloadRemovalTarget.Public(it) })
            model.dismiss() // Opening and cancelling a confirmation never touches files.
            assertEquals(3, provider.rows.size)
            model.request(selected.map { DownloadRemovalTarget.Public(it) })
            model.confirm(context)
            finish()
            assertEquals(1, model.state.value!!.removed)
            assertEquals(RemovalProblem.ACCESS, model.state.value!!.failures.single().problem)
            assertEquals(setOf("two.mp3", "keep.mp3"), SharedDownloads.list(context).map { it.name }.toSet())
            provider.deniedId = null
            model.retry(context)
            finish()
            assertTrue(model.state.value!!.failures.isEmpty())
            assertEquals("keep.mp3", SharedDownloads.list(context).single().name)
            assertTrue(source.exists())
        } finally { store.clear() }
    }

    @Test fun providerRefusingDeletionKeepsTheFileAndDoesNotReportSuccess() = runBlocking {
        SharedDownloads.export(context, source, "music.mp3", "audio/mpeg", marker)
        val selected = SharedDownloads.list(context).single()
        provider.failDelete = true
        assertTrue(runCatching { SharedDownloads.remove(context, selected) }.isFailure)
        assertEquals(selected, SharedDownloads.list(context).single())
        assertArrayEquals(source.readBytes(), provider.files().single().readBytes())
    }

    @Test @Config(sdk = [28]) fun legacyRemovalDeletesOnlyTheExactSelectedFileAndNeverAFolderOrOutsideFile() = runBlocking {
        val name = "kruxx-test-${UUID.randomUUID()}.mp3"
        val output = File(SharedDownloads.legacyDirectory("audio/mpeg"), name)
        try {
            OfflineFiles.export(context, SharedDownloads.destination, source, name, "audio/mpeg", marker)
            val selected = SharedDownloads.list(context).single { it.name == name }
            assertTrue(runCatching { SharedDownloads.remove(context, selected.copy(uri = Uri.fromFile(source), name = source.name)) }.isFailure)
            val outsideLink = File(root, name)
            java.nio.file.Files.createSymbolicLink(outsideLink.toPath(), output.toPath())
            assertTrue(runCatching { SharedDownloads.remove(context, selected.copy(uri = Uri.fromFile(outsideLink))) }.isFailure)
            assertTrue(java.nio.file.Files.isSymbolicLink(outsideLink.toPath()))
            outsideLink.delete()
            val folder = DownloadFile(Uri.fromFile(output.parentFile), "Audio", "audio/mpeg", 0, SharedDownloads.relativePath)
            assertTrue(runCatching { SharedDownloads.remove(context, folder) }.isFailure)
            assertTrue(output.exists())
            SharedDownloads.remove(context, selected)
            assertFalse(output.exists())
            assertTrue(output.parentFile!!.isDirectory)
            assertTrue(source.exists())
        } finally { output.delete() }
    }

    @Test fun safRemovalUsesTheDocumentProviderAndKeepsSiblingFilesAndFolders() = runBlocking {
        val documents = RemovalDocuments()
        val authority = "com.android.externalstorage.documents"
        val info = ProviderInfo().apply {
            this.authority = authority; exported = true; grantUriPermissions = true
            packageName = context.packageName; name = RemovalDocuments::class.java.name
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        }
        documents.attachInfo(context, info)
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(DocumentsContract.PROVIDER_INTERFACE),
            ResolveInfo().apply { providerInfo = info })
        ShadowContentResolver.registerProviderInternal(authority, documents)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "primary:Download/KruXx-Downloads")
        val path = SharedDownloads.relativePath("audio/mpeg")
        fun file(name: String) = DownloadFile(DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$path$name"),
            name, "audio/mpeg", 10, path)
        val selected = file("remove.mp3")
        val sibling = file("keep.mp3")
        documents.entries += DocumentsContract.getDocumentId(selected.uri) to "audio/mpeg"
        documents.entries += DocumentsContract.getDocumentId(sibling.uri) to "audio/mpeg"
        documents.entries += "primary:Download/KruXx-Downloads/Audio" to DocumentsContract.Document.MIME_TYPE_DIR
        SharedDownloads.remove(context, selected)
        assertFalse(documents.entries.containsKey(DocumentsContract.getDocumentId(selected.uri)))
        assertTrue(documents.entries.containsKey(DocumentsContract.getDocumentId(sibling.uri)))
        assertEquals(2, documents.entries.size)
        val folder = DownloadFile(DocumentsContract.buildDocumentUriUsingTree(tree,
            "primary:Download/KruXx-Downloads/Audio"), "Audio", "audio/mpeg", 10, SharedDownloads.relativePath)
        assertTrue(runCatching { SharedDownloads.remove(context, folder) }.exceptionOrNull() is DownloadFileChangedException)
        assertEquals(2, documents.entries.size)
    }
}

/** Models pending MediaStore rows, stable item URIs and a failed publication. */
private class DownloadsProvider(private val directory: File) : ContentProvider() {
    val rows = linkedMapOf<Long, ContentValues>()
    private var nextId = 1L
    var failPublish = false
    var failDelete = false
    var deniedId: Long? = null
    fun files() = rows.keys.map { File(directory, it.toString()) }
    override fun onCreate() = true
    override fun getType(uri: Uri) = "audio/mpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextId++
        rows[id] = ContentValues(values!!)
        File(directory, id.toString()).createNewFile()
        return ContentUris.withAppendedId(uri, id)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: emptyArray()
        return MatrixCursor(columns).apply {
            rows.filter { (id, row) ->
                (uri.lastPathSegment?.toLongOrNull()?.let { it == id } ?: true) &&
                    (selection == null || (row.getAsString(MediaStore.MediaColumns.RELATIVE_PATH) == selectionArgs?.firstOrNull() &&
                        row.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0))
            }.forEach { (id, values) ->
                addRow(columns.map { column -> when (column) {
                    MediaStore.MediaColumns._ID -> id
                    MediaStore.MediaColumns.SIZE -> File(directory, id.toString()).length()
                    else -> values[column]
                } })
            }
        }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(directory, ContentUris.parseId(uri).toString()), ParcelFileDescriptor.parseMode(mode))
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (failPublish) return 0
        rows.getValue(ContentUris.parseId(uri)).putAll(values!!)
        return 1
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = ContentUris.parseId(uri)
        if (id == deniedId) throw SecurityException("No write access")
        if (failDelete || id !in rows) return 0
        if (selection != null && (rows[id]?.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) != selectionArgs?.get(0) ||
                rows[id]?.getAsString(MediaStore.MediaColumns.RELATIVE_PATH) != selectionArgs?.get(1) ||
                File(directory, id.toString()).length().toString() != selectionArgs?.get(2) ||
                rows[id]?.getAsInteger(MediaStore.MediaColumns.IS_PENDING) != 0)) return 0
        rows.remove(id)
        File(directory, id.toString()).delete()
        return 1
    }
}

private class RemovalDocuments : DocumentsProvider() {
    val entries = linkedMapOf<String, String>()
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: emptyArray())
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
        MatrixCursor(projection ?: emptyArray())
    override fun queryDocument(documentId: String, projection: Array<out String>?) = MatrixCursor(projection!!).apply {
        entries[documentId]?.let { mime -> addRow(projection.map { when (it) {
            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> documentId.substringAfterLast('/')
            DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
            DocumentsContract.Document.COLUMN_SIZE -> 10L
            else -> null
        } }) }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        throw UnsupportedOperationException()
    override fun deleteDocument(documentId: String) { check(entries.remove(documentId) != null) }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
}
