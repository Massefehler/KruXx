package it.fast4x.rimusic.ui.screens.searchresult

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultProviderTest {

    @Test
    fun mergesVideoFallbackAfterSongsAndRemovesDuplicateVideoIds() {
        val songs = Innertube.ItemsPage(
            items = listOf(song("song-1"), song("shared")),
            continuation = "songs-next"
        )
        val videos = Innertube.ItemsPage(
            items = listOf(video("shared"), video("video-only")),
            continuation = "videos-next"
        )

        val merged = mergeSongAndVideoPages(songs, videos)

        assertEquals(listOf("song-1", "shared", "video-only"), merged.items?.map { it.key })
        assertEquals("songs-next", merged.continuation)
    }

    @Test
    fun videoOnlyResultStillProducesPlayableSongItems() {
        val merged = mergeSongAndVideoPages(
            songsPage = null,
            videosPage = Innertube.ItemsPage(listOf(video("video-only", explicit = true)), "videos-next")
        )

        assertEquals(listOf("video-only"), merged.items?.map { it.key })
        assertEquals(true, merged.items?.single()?.explicit)
        assertEquals(null, merged.continuation)
    }

    private fun song(id: String) = Innertube.SongItem(
        info = Innertube.Info(id, NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = emptyList(),
        album = null,
        durationText = "3:00",
        thumbnail = null
    )

    private fun video(id: String, explicit: Boolean = false) = Innertube.VideoItem(
        info = Innertube.Info(id, NavigationEndpoint.Endpoint.Watch(videoId = id)),
        authors = emptyList(),
        viewsText = "1 view",
        durationText = "3:00",
        thumbnail = null,
        explicit = explicit
    )
}
