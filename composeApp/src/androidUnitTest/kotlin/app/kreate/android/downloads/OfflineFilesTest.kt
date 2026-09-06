@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.kreate.di.CacheType
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [DocumentsQueryShadow::class])
class OfflineFilesTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var cache: SimpleCache
    private lateinit var database: StandaloneDatabaseProvider

    @Before fun setup() {
        // Robolectric's URI-grant shadow does not implement Android's prefix tree grants.
        // The provider below models a folder already selected in OpenDocumentTree.
        context = object : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun checkCallingOrSelfUriPermission(uri: Uri, modeFlags: Int) =
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        directory = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        database = StandaloneDatabaseProvider(context)
        cache = SimpleCache(File(directory, "cache"), NoOpCacheEvictor(), database)
        startKoin { modules(module { single<Cache>(CacheType.DOWNLOAD) { cache } }) }
        DownloadCenter.initialize(context)
    }

    @After fun cleanup() {
        cache.release()
        database.close()
        stopKoin()
        directory.deleteRecursively()
    }

    private fun span(id: String, position: Long, bytes: ByteArray) {
        val hole = cache.startReadWrite(id, position, bytes.size.toLong())
        try {
            val file = cache.startFile(id, position, bytes.size.toLong())
            file.writeBytes(bytes)
            cache.commitFile(file, bytes.size.toLong())
        } finally { cache.releaseHoleSpan(hole) }
    }

    @Test fun snapshotJoinsSpansInByteOrderAndIdentifiesWebm() = runBlocking {
        val input = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()) + ByteArray(500_000) { (it % 251).toByte() }
        span("song", 100_000, input.copyOfRange(100_000, input.size))
        span("song", 0, input.copyOfRange(0, 100_000))
        cache.applyContentMetadataMutations("song", ContentMetadataMutations().also {
            ContentMetadataMutations.setContentLength(it, input.size.toLong())
        })
        val output = File(directory, "snapshot")
        assertEquals("webm", OfflineFiles.snapshot("song", output))
        assertArrayEquals(input, output.readBytes())
        assertTrue(cache.isCached("song", 0, input.size.toLong()))
    }

    @Test fun incompleteDownloadCannotStartNetworkOrProduceAnExport() = runBlocking {
        span("song", 0, ByteArray(10))
        span("song", 20, ByteArray(10))
        cache.applyContentMetadataMutations("song", ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, 30) })
        val output = File(directory, "snapshot")
        assertTrue(runCatching { OfflineFiles.snapshot("song", output) }.isFailure)
        assertFalse(output.exists())
    }

    @Test fun unknownAudioIsNotMislabeledAsM4a() {
        val source = File(directory, "unknown").apply { writeBytes(ByteArray(32)) }
        assertTrue(runCatching { OfflineFiles.containerExtension(source) }.isFailure)
        source.writeBytes(byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray() + ByteArray(12))
        assertEquals("m4a", OfflineFiles.containerExtension(source))
    }

    @Test fun portableNamesPreserveUnicodeWithoutExposingIdsOrFormatLabels() {
        val title = "😀".repeat(100) + " /:*?<>|"
        val one = OfflineFiles.fileName(DownloadTrack("id-one", title, "Björk"), "mp3")
        val two = OfflineFiles.fileName(DownloadTrack("id-two", title, "Björk"), "mp3")
        assertEquals(one, two) // Destination collision handling distinguishes different contents.
        assertTrue(one.startsWith("Björk - 😀"))
        assertTrue(one.toByteArray().size < 255)
        assertFalse(one.contains('\uFFFD'))
        assertFalse(one.any { it in "/:*?\"<>|" })
        assertTrue(one.endsWith(".mp3"))
        assertFalse(one.contains("MP3 320"))
    }

    @Test fun persistedTrackRetainsDurationExplicitFlagAndArtistAlbumMappings() {
        val track = DownloadTrack("id", "Song", "Artist", "https://example.com/cover",
            true, 123_000, "Album", "album-id", listOf("Artist"), listOf("artist-id"))
        val restored = DownloadTrack.from(track.json()).mediaItem()
        assertEquals(track, DownloadTrack.from(restored))
        assertTrue(restored.mediaMetadata.extras!!.getBoolean(EXPLICIT_BUNDLE_TAG))
        assertEquals(123_000L, restored.mediaMetadata.durationMs)
        assertFalse(restored.mediaMetadata.extras!!.getBoolean("isVideo"))
    }

    @Test fun videoDownloadMenuIntentDoesNotTurnAnAudioQueueItemIntoVideoPlayback() {
        val item = DownloadTrack("id", "Title", "Artist").mediaItem().forVideoDownload()
        assertTrue(item.mediaMetadata.extras!!.getBoolean("downloadVideo"))
        assertFalse(item.mediaMetadata.extras!!.getBoolean("isVideo"))
    }

    private fun provider(): Pair<TestDocuments, Uri> {
        val provider = TestDocuments(File(directory, "documents").apply { mkdirs() })
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "kruxx.test.documents"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("kruxx.test.documents", provider)
        val uri = DocumentsContract.buildTreeDocumentUri("kruxx.test.documents", "root")
        context.grantUriPermission(context.packageName, uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)!!
        check(doc.isDirectory && doc.canWrite()) { "Invalid SAF test fixture" }
        return provider to uri
    }

    @Test fun exportVerifiesBytesSkipsIdenticalFilesAndKeepsTheSource() = runBlocking {
        val (provider, uri) = provider()
        val source = File(directory, "source").apply { writeBytes(ByteArray(300_000) { (it % 137).toByte() }) }
        val marker = File(directory, "marker")
        assertTrue(OfflineFiles.export(context, uri, source, "music.mp3", "audio/mpeg", marker))
        assertArrayEquals(source.readBytes(), provider.file("music.mp3").readBytes())
        assertFalse(OfflineFiles.export(context, uri, source, "music.mp3", "audio/mpeg", marker))
        assertEquals(listOf("music.mp3"), provider.names())
        assertTrue(source.exists())
        assertFalse(marker.exists())
    }

    @Test fun exportPreservesAnExistingFileWithDifferentContents() = runBlocking {
        val (provider, uri) = provider()
        provider.createDocument("root", "audio/mpeg", "music.mp3").also {
            provider.openDocument(it, "w", null).use { fd -> ParcelFileDescriptor.AutoCloseOutputStream(fd).write(byteArrayOf(1, 2, 3)) }
        }
        val source = File(directory, "source").apply { writeBytes(byteArrayOf(3, 2, 1)) }
        assertTrue(OfflineFiles.export(context, uri, source, "music.mp3", "audio/mpeg", File(directory, "marker")))
        assertArrayEquals(byteArrayOf(1, 2, 3), provider.file("music.mp3").readBytes())
        assertArrayEquals(byteArrayOf(3, 2, 1), provider.file("music (2).mp3").readBytes())
        assertFalse(OfflineFiles.export(context, uri, source, "music.mp3", "audio/mpeg", File(directory, "marker")))
        assertEquals(2, provider.names().size)
    }

    @Test fun failedRenameCleansOnlyTheTemporaryDestination() = runBlocking {
        val (provider, uri) = provider()
        provider.failRename = true
        val source = File(directory, "source").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(runCatching {
            OfflineFiles.export(context, uri, source, "music.mp3", "audio/mpeg", File(directory, "marker"))
        }.isFailure)
        assertTrue(provider.names().isEmpty())
        assertTrue(source.exists())
    }

    @Test fun interruptedExportCleanupNeverDeletesAnAlreadyRenamedFile() {
        val (provider, _) = provider()
        val id = provider.createDocument("root", "audio/mpeg", "kruxx-partial-owned")
        val marker = File(directory, "marker").apply { writeText(
            DocumentsContract.buildDocumentUri("kruxx.test.documents", id).toString() + "\nkruxx-partial-owned") }
        provider.renameDocument(id, "finished.mp3")
        OfflineFiles.cleanInterruptedExport(context, marker)
        assertEquals(listOf("finished.mp3"), provider.names())
    }
}

/** Robolectric routes legacy resolver queries directly; Android forwards them to the Bundle API. */
@org.robolectric.annotation.Implements(DocumentsProvider::class)
class DocumentsQueryShadow : org.robolectric.shadows.ShadowContentProvider() {
    @org.robolectric.annotation.RealObject
    private lateinit var provider: DocumentsProvider

    @org.robolectric.annotation.Implementation
    protected fun query(uri: Uri, projection: Array<String>?, selection: String?,
                        selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
        provider.query(uri, projection, android.os.Bundle(), null)
}

/** Minimal SAF provider with stable URIs, like providers that retain the ID on rename. */
private class TestDocuments(private val directory: File) : DocumentsProvider() {
    private val entries = linkedMapOf<String, Pair<String, String>>()
    var failRename = false
    fun names() = entries.values.map { it.first }
    fun file(name: String) = File(directory, entries.entries.first { it.value.first == name }.key)
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(projection ?: emptyArray())
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        cursor(projection).apply { add(this, documentId) }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        cursor(projection).apply { entries.keys.forEach { add(this, it) } }
    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE,
    ))
    private fun add(cursor: MatrixCursor, id: String) {
        val root = id == "root"
        val entry = entries[id]
        val row = cursor.newRow()
        cursor.columnNames.forEach { column -> row.add(when (column) {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> if (root) "Root" else entry?.first
            DocumentsContract.Document.COLUMN_MIME_TYPE -> if (root) DocumentsContract.Document.MIME_TYPE_DIR else entry?.second
            DocumentsContract.Document.COLUMN_FLAGS -> if (root) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                else DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            DocumentsContract.Document.COLUMN_SIZE -> File(directory, id).length()
            else -> null
        }) }
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val id = UUID.randomUUID().toString()
        entries[id] = displayName to mimeType
        File(directory, id).createNewFile()
        return id
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
    override fun deleteDocument(documentId: String) {
        entries.remove(documentId)
        File(directory, documentId).delete()
    }
    override fun renameDocument(documentId: String, displayName: String): String? {
        if (failRename) throw java.io.FileNotFoundException("Simulated provider failure")
        entries[documentId] = displayName to entries.getValue(documentId).second
        return documentId
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = parentDocumentId == "root" && entries.containsKey(documentId)
}
