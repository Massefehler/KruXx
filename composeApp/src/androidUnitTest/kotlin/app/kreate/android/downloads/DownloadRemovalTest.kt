package app.kreate.android.downloads

import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class DownloadRemovalTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kruxx_download_options", Context.MODE_PRIVATE).edit().clear().commit()
        DownloadCenter.initialize(context)
    }

    @After fun cleanup() { DownloadCenter.mediaDirectory.deleteRecursively(); DownloadCenter.saved.value = emptyList() }

    private fun asset(id: String, kind: DownloadKind): SavedMedia {
        val asset = SavedMedia(DownloadTrack(id, "Title", "Artist"), kind, if (kind == DownloadKind.MP3) "mp3" else "mp4", 3)
        DownloadCenter.file(asset).writeBytes(byteArrayOf(1, 2, 3))
        DownloadCenter.register(asset)
        return asset
    }

    @Test fun removingOneFormatPersistsTheRemainingAssetsAndInvalidatesOldWork() {
        val mp3 = asset("one", DownloadKind.MP3)
        val video = asset("one", DownloadKind.VIDEO)
        val other = asset("two", DownloadKind.MP3)
        val generation = DownloadCenter.generation("one")
        DownloadCenter.removeAsset(mp3)
        assertFalse(DownloadCenter.file(mp3).exists())
        assertTrue(DownloadCenter.file(video).exists())
        assertTrue(DownloadCenter.file(other).exists())
        assertEquals(setOf(video, other), DownloadCenter.saved.value.toSet())
        DownloadCenter.initialize(context)
        assertEquals(setOf(video, other), DownloadCenter.saved.value.toSet())
        assertEquals(generation + 1, DownloadCenter.generation("one"))
        assertEquals(0, DownloadCenter.generation("two"))
    }

    @Test fun trackRemovalDeletesAllItsConversionsAndPersistsUnrelatedDownloads() = runBlocking {
        val mp3 = asset("one", DownloadKind.MP3)
        val video = asset("one", DownloadKind.VIDEO)
        val other = asset("two", DownloadKind.MP3)
        val generation = DownloadCenter.generation("one")
        val removal = DownloadCenter.removeFiles("one")
        assertTrue(DownloadCenter.generation("one") > generation)
        removal.join()
        assertFalse(DownloadCenter.file(mp3).exists())
        assertFalse(DownloadCenter.file(video).exists())
        assertTrue(DownloadCenter.file(other).exists())
        DownloadCenter.initialize(context)
        assertEquals(listOf(other), DownloadCenter.saved.value)
        assertTrue(DownloadCenter.generation("one") > generation)
        assertEquals(0, DownloadCenter.generation("two"))
    }

    @Test fun removingAnOriginalWithNoConversionsAlsoInvalidatesPendingConversionWork() = runBlocking {
        val generation = DownloadCenter.generation("one")
        DownloadCenter.removeFiles("one").join()
        DownloadCenter.initialize(context)
        assertTrue(DownloadCenter.generation("one") > generation)
        assertTrue(DownloadCenter.saved.value.isEmpty())
    }

    @Test fun changedAssetCannotBeRemovedOrForgottenUsingAnOldSelection() {
        val selected = asset("one", DownloadKind.MP3)
        DownloadCenter.file(selected).appendBytes(byteArrayOf(4))
        assertThrows(DownloadFileChangedException::class.java) { DownloadCenter.removeAsset(selected) }
        assertEquals(listOf(selected), DownloadCenter.saved.value)
        assertTrue(DownloadCenter.file(selected).exists())
        assertEquals(0, DownloadCenter.generation("one"))
        val newer = selected.copy(size = 4)
        DownloadCenter.register(newer)
        assertThrows(DownloadFileChangedException::class.java) { DownloadCenter.removeAsset(selected) }
        assertEquals(listOf(newer), DownloadCenter.saved.value)
    }
}
