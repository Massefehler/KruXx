package app.kreate.android.viewmodel

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubePlaylistMappingTest {

    @Test
    fun browseIdIsNormalizedExactlyOnce() {
        assertEquals("VLPL123", normalizeYouTubePlaylistBrowseId("PL123"))
        assertEquals("VLPL123", normalizeYouTubePlaylistBrowseId("VLPL123"))
    }

    @Test
    fun metrolistSongMapsToExistingPlaylistSongModel() {
        val source = SongItem(
            id = "video-id",
            title = "Song title",
            artists = listOf(Artist("First", "artist-1"), Artist("Second", null)),
            duration = 3_725,
            thumbnail = "https://example.invalid/cover.jpg",
            explicit = true,
        )

        val song = source.toDatabaseSong()

        assertEquals("video-id", song.id)
        assertEquals("Song title", song.title)
        assertEquals("First, Second", song.artistsText)
        assertEquals("1:02:05", song.durationText)
        assertEquals("https://example.invalid/cover.jpg", song.thumbnailUrl)
        assertEquals(true, song.isExplicit)
        assertFalse(song.isLocal)
    }

    @Test
    fun durationBelowOneHourKeepsMinuteFormat() {
        assertEquals("0:07", 7.toPlaylistDurationText())
        assertEquals("12:34", 754.toPlaylistDurationText())
        assertEquals("0:00", (-1).toPlaylistDurationText())
    }

    @Test
    fun headerShareUrlNeverLeaksBrowsePrefix() {
        val header = YouTubePlaylistHeader(
            id = "VLPL123",
            name = "Playlist",
            thumbnailUrl = null,
            subtitleText = null,
            description = null,
        )

        assertEquals(
            "https://music.youtube.com/playlist?list=PL123",
            header.shareUrl("https://music.youtube.com"),
        )
    }

    @Test
    fun blankOptionalSongMetadataMapsToNull() {
        val source = SongItem(
            id = "video-id",
            title = "Song title",
            artists = emptyList(),
            thumbnail = "",
        )

        val song = source.toDatabaseSong()

        assertNull(song.artistsText)
        assertNull(song.durationText)
        assertNull(song.thumbnailUrl)
    }
}
