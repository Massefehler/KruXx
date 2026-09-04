package it.fast4x.rimusic.service.modern

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaLibraryIdTest {

    @Test
    fun containerAndPlayableIdsRoundTripReservedCharacters() {
        val parentId = MediaLibraryId.container(PlayerServiceModern.ARTIST, "artist/100%")
        val mediaId = MediaLibraryId.playable(parentId, "song/42%")

        assertEquals(
            "artist/100%2F",
            MediaLibraryId.container(PlayerServiceModern.ARTIST, "100/"),
        )
        assertEquals(
            "artist/100%",
            MediaLibraryId.containerValue(parentId, PlayerServiceModern.ARTIST),
        )
        assertEquals(
            MediaLibrarySelection(parentId, "song/42%"),
            MediaLibraryId.selection(mediaId),
        )
    }

    @Test
    fun searchIdsKeepTheQueryThatProducedTheQueue() {
        val parentId = MediaLibraryId.searchParent("  AC/DC 100% 🔥  ")
        val mediaId = MediaLibraryId.playable(parentId, "video-id")

        assertEquals("AC/DC 100% 🔥", MediaLibraryId.searchQuery(parentId))
        assertEquals(
            MediaLibrarySelection(parentId, "video-id"),
            MediaLibraryId.selection(mediaId),
        )
    }

    @Test
    fun emptySearchStillProducesAValidQueueParent() {
        val parentId = MediaLibraryId.searchParent("   ")
        val mediaId = MediaLibraryId.playable(parentId, "video-id")

        assertEquals("", MediaLibraryId.searchQuery(parentId))
        assertEquals(
            MediaLibrarySelection(parentId, "video-id"),
            MediaLibraryId.selection(mediaId),
        )
    }

    @Test
    fun oldSearchAndSongIdsRemainReadable() {
        assertEquals(
            MediaLibrarySelection(PlayerServiceModern.SEARCHED, "old-video-id"),
            MediaLibraryId.selection("${PlayerServiceModern.SEARCHED}/old-video-id"),
        )
        assertEquals(
            MediaLibrarySelection(PlayerServiceModern.SONG, "song-id"),
            MediaLibraryId.selection("${PlayerServiceModern.SONG}/song-id"),
        )
    }

    @Test
    fun malformedOrBrowsableIdsAreNotParsedAsSongs() {
        assertNull(MediaLibraryId.selection(PlayerServiceModern.ROOT))
        assertNull(MediaLibraryId.selection("${PlayerServiceModern.ARTIST}/artist-id"))
        assertNull(MediaLibraryId.selection("unknown/container/song"))
        assertNull(MediaLibraryId.selection("${PlayerServiceModern.ALBUM}//song"))
    }

    @Test
    fun mediaLibraryPagesAreBoundedAndOverflowSafe() {
        val items = (0 until 10).toList()

        assertEquals(false, isValidMediaLibraryPage(page = -1, pageSize = 3))
        assertEquals(false, isValidMediaLibraryPage(page = 0, pageSize = 0))
        assertEquals(true, isValidMediaLibraryPage(page = 0, pageSize = Int.MAX_VALUE))
        assertEquals(listOf(3, 4, 5), items.mediaLibraryPage(page = 1, pageSize = 3))
        assertEquals(listOf(9), items.mediaLibraryPage(page = 3, pageSize = 3))
        assertEquals(emptyList<Int>(), items.mediaLibraryPage(page = 4, pageSize = 3))
        assertEquals(emptyList<Int>(), items.mediaLibraryPage(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(items, items.mediaLibraryPage(page = 0, pageSize = Int.MAX_VALUE))
    }
}
