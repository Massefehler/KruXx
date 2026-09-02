package it.fast4x.rimusic.ui.screens.searchresult

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.clients.YouTubeLocale
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Runs
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.models.ThumbnailRenderer
import it.fast4x.innertube.requests.SearchSuggestionPage
import it.fast4x.innertube.utils.LocalePreferenceItem
import it.fast4x.innertube.utils.LocalePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInfrastructureTest {

    @Test
    fun localizedContextIsCalculatedFromCurrentPreferencesOnEveryRequest() {
        val previousPreference = LocalePreferences.preference
        val previousLocale = Innertube.locale

        try {
            LocalePreferences.preference = LocalePreferenceItem(hl = "de", gl = "DE")
            assertEquals("de", Context.DefaultWebWithLocale.client.hl)
            assertEquals("DE", Context.DefaultWebWithLocale.client.gl)

            LocalePreferences.preference = LocalePreferenceItem(hl = "fr", gl = "CA")
            assertEquals("fr", Context.DefaultWebWithLocale.client.hl)
            assertEquals("CA", Context.DefaultWebWithLocale.client.gl)
        } finally {
            LocalePreferences.preference = previousPreference
            Innertube.locale = previousLocale
        }
    }

    @Test
    fun suggestionSongUsesPlaylistVideoIdWhenNavigationEndpointIsMissing() {
        val renderer = MusicResponsiveListItemRenderer(
            fixedColumns = null,
            flexColumns = listOf(
                column(Runs.Run(text = "Track", navigationEndpoint = null)),
                column(
                    Runs.Run(text = "Song", navigationEndpoint = null),
                    Runs.Run(text = " • ", navigationEndpoint = null),
                    Runs.Run(
                        text = "Artist",
                        navigationEndpoint = NavigationEndpoint(
                            watchEndpoint = null,
                            watchPlaylistEndpoint = null,
                            browseEndpoint = NavigationEndpoint.Endpoint.Browse(browseId = "artist-id"),
                            searchEndpoint = null
                        )
                    )
                )
            ),
            thumbnail = ThumbnailRenderer(
                musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
                    thumbnail = ThumbnailRenderer.MusicThumbnailRenderer.Thumbnail(
                        thumbnails = listOf(Thumbnail("https://example.invalid/cover", 100, 100))
                    ),
                    thumbnailCrop = null,
                    thumbnailScale = null
                ),
                croppedSquareThumbnailRenderer = null
            ),
            navigationEndpoint = null,
            badges = null,
            playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
                playlistSetVideoId = null,
                videoId = "fallback-video"
            )
        )

        val item = SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)

        assertTrue(item is Innertube.SongItem)
        assertEquals("fallback-video", (item as Innertube.SongItem).info?.endpoint?.videoId)
        assertEquals("Track", item.title)
    }

    private fun column(vararg runs: Runs.Run) =
        MusicResponsiveListItemRenderer.FlexColumn(
            MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                Runs(runs.toList())
            )
        )
}
