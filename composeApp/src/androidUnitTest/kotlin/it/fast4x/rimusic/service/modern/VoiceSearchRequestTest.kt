package it.fast4x.rimusic.service.modern

import android.app.Application
import android.provider.MediaStore
import androidx.core.os.bundleOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoiceSearchRequestTest {

    @Test
    fun playMusicWithoutDetailsAsksForAnything() {
        assertEquals(VoiceSearchRequest.Anything, VoiceSearchRequest.from(null, null))
        assertEquals(VoiceSearchRequest.Anything, VoiceSearchRequest.from("  ", null))
        assertEquals(
            VoiceSearchRequest.Anything,
            VoiceSearchRequest.from("", bundleOf(MediaStore.EXTRA_MEDIA_FOCUS to "vnd.android.cursor.item/*")),
        )
    }

    @Test
    fun unstructuredQueriesAreSearchedAsSpoken() {
        assertEquals(VoiceSearchRequest.Text("rock classics"), VoiceSearchRequest.from(" rock classics ", null))
    }

    @Test
    fun focusedRequestsKeepTheNamedEntry() {
        assertEquals(
            VoiceSearchRequest.Playlist("Roadtrip", "roadtrip playlist"),
            VoiceSearchRequest.from(
                "roadtrip playlist",
                bundleOf(
                    MediaStore.EXTRA_MEDIA_FOCUS to MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE,
                    MediaStore.EXTRA_MEDIA_PLAYLIST to "Roadtrip",
                ),
            ),
        )
        assertEquals(
            VoiceSearchRequest.Artist("Queen", "queen"),
            VoiceSearchRequest.from(
                "queen",
                bundleOf(
                    MediaStore.EXTRA_MEDIA_FOCUS to MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE,
                    MediaStore.EXTRA_MEDIA_ARTIST to "Queen",
                ),
            ),
        )
        assertEquals(
            VoiceSearchRequest.Album("A Night at the Opera", "Queen", "a night at the opera"),
            VoiceSearchRequest.from(
                "a night at the opera",
                bundleOf(
                    MediaStore.EXTRA_MEDIA_FOCUS to MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE,
                    MediaStore.EXTRA_MEDIA_ALBUM to "A Night at the Opera",
                    MediaStore.EXTRA_MEDIA_ARTIST to "Queen",
                ),
            ),
        )
    }

    @Test
    fun songRequestsSearchForTitleAndArtist() {
        assertEquals(
            VoiceSearchRequest.Text("Bohemian Rhapsody Queen"),
            VoiceSearchRequest.from(
                "bohemian rhapsody by queen",
                bundleOf(
                    MediaStore.EXTRA_MEDIA_FOCUS to MediaStore.Audio.Media.ENTRY_CONTENT_TYPE,
                    MediaStore.EXTRA_MEDIA_TITLE to "Bohemian Rhapsody",
                    MediaStore.EXTRA_MEDIA_ARTIST to "Queen",
                ),
            ),
        )
    }

    @Test
    fun focusWithoutItsNameFallsBackToTheQuery() {
        assertEquals(
            VoiceSearchRequest.Text("roadtrip"),
            VoiceSearchRequest.from(
                "roadtrip",
                bundleOf(MediaStore.EXTRA_MEDIA_FOCUS to MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE),
            ),
        )
    }

    @Test
    fun spokenNamesMatchRegardlessOfCaseAndSpacing() {
        assertEquals("ac/dc best of", "  AC/DC   Best\tOf ".voiceKey())
        assertEquals("lieblingssongs", "Lieblingssongs".voiceKey())
    }
}
