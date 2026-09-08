package app.kreate.android.downloads

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer

class Mp3TagsTest {
    @get:Rule val directory = TemporaryFolder()

    private fun textFrames(tag: ByteArray): Map<String, String> {
        var offset = 10
        return buildMap {
            while (offset < tag.size) {
                val id = String(tag, offset, 4, Charsets.US_ASCII)
                val length = ByteBuffer.wrap(tag, offset + 4, 4).int
                put(id, String(tag, offset + 11, length - 1, Charsets.UTF_16))
                offset += length + 10
            }
        }
    }

    @Test fun tagsContainSeparateUnicodeArtistAndTrackNames() {
        val names = DownloadTrack("id", "Künstler – Titel (Live)", "Uploader").names
        assertEquals(mapOf("TIT2" to "Titel (Live)", "TPE1" to "Künstler"),
            textFrames(Mp3Tags.encode(names.title, names.artist)))
    }

    @Test fun reusedConversionGetsCorrectTagsWithExactlyTheSameAudioBytes() = runBlocking {
        val source = directory.newFile("old.mp3")
        val target = directory.root.resolve("updated.mp3")
        val audio = ByteArray(400_000) { (it % 251).toByte() }
        val old = Mp3Tags.encode("Kilophil - Protoporn", "dejanprogtrens")
        source.writeBytes(old + audio)
        val names = DownloadTrack("id", "Kilophil - Protoporn", "dejanprogtrens").names
        assertTrue(Mp3Tags.copyWithUpdatedNames(source, target, names))
        val tag = Mp3Tags.encode(names.title, names.artist)
        assertEquals(mapOf("TIT2" to "Protoporn", "TPE1" to "Kilophil"), textFrames(tag))
        assertArrayEquals(tag + audio, target.readBytes())
        assertArrayEquals(old + audio, source.readBytes())
        val duplicate = directory.root.resolve("duplicate.mp3")
        assertFalse(Mp3Tags.copyWithUpdatedNames(target, duplicate, names))
        assertFalse(duplicate.exists())
    }

    @Test fun truncatedOrForeignPrivateFileCannotBeSilentlyRewritten() = runBlocking {
        val source = directory.newFile("truncated.mp3")
        val target = directory.root.resolve("updated.mp3")
        source.writeBytes(Mp3Tags.encode("Title", "Artist").take(15).toByteArray())
        assertTrue(runCatching { Mp3Tags.copyWithUpdatedNames(source, target, TrackNames("New", "Title")) }.isFailure)
        assertFalse(target.exists())
    }
}
