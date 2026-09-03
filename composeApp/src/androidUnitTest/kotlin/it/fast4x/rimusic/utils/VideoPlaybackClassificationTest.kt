package it.fast4x.rimusic.utils

import android.app.Application
import androidx.media3.common.util.UnstableApi
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.MusicTwoRowItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Runs
import it.fast4x.innertube.utils.from
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VideoPlaybackClassificationTest {

    @Test
    fun classifiesKnownYtmVideoTypes() {
        assertEquals(
            Innertube.VideoItem.VideoType.OFFICIAL_MUSIC_VIDEO,
            video("MUSIC_VIDEO_TYPE_OMV").videoType
        )
        assertEquals(
            Innertube.VideoItem.VideoType.USER_GENERATED_VIDEO,
            video("MUSIC_VIDEO_TYPE_UGC").videoType
        )
        assertEquals(
            Innertube.VideoItem.VideoType.ART_TRACK,
            video("MUSIC_VIDEO_TYPE_ATV").videoType
        )
        assertEquals(Innertube.VideoItem.VideoType.UNKNOWN, video(null).videoType)
        assertEquals(Innertube.VideoItem.VideoType.UNKNOWN, video("MUSIC_VIDEO_TYPE_OTHER").videoType)
    }

    @Test
    fun onlyDirectlySelectedVisualVideosRequestVideoPlayback() {
        val officialVideo = video("MUSIC_VIDEO_TYPE_OMV")
        val userVideo = video("MUSIC_VIDEO_TYPE_UGC")
        val artTrack = video("MUSIC_VIDEO_TYPE_ATV")
        val unknown = video(null)

        assertTrue(officialVideo.supportsVideoPlayback)
        assertTrue(userVideo.supportsVideoPlayback)
        assertFalse(artTrack.supportsVideoPlayback)
        assertFalse(unknown.supportsVideoPlayback)

        // The regular media item is used for queues and is deliberately audio-only.
        assertFalse(officialVideo.asMediaItem.isVideo)
        assertTrue(officialVideo.asMediaItem.supportsVideoPlayback)

        assertTrue(officialVideo.asVideoMediaItem.isVideo)
        assertTrue(userVideo.asVideoMediaItem.isVideo)
        assertFalse(artTrack.asVideoMediaItem.isVideo)
        assertFalse(unknown.asVideoMediaItem.isVideo)
        assertTrue(artTrack.asMediaItem.isArtTrack)
    }

    @Test
    fun combinesVideoTypeWithPlaylistItemVideoIdFromTheSameRow() {
        val parsed = Innertube.VideoItem.from(
            searchRow(
                playlistVideoId = "target-video",
                titleEndpoint = watchEndpoint(videoId = null, type = "MUSIC_VIDEO_TYPE_OMV")
            )
        )

        assertEquals("target-video", parsed?.key)
        assertEquals(Innertube.VideoItem.VideoType.OFFICIAL_MUSIC_VIDEO, parsed?.videoType)
    }

    @Test
    fun doesNotUseVideoTypeFromAnUnrelatedSecondaryEndpoint() {
        val parsed = Innertube.VideoItem.from(
            searchRow(
                playlistVideoId = "target-video",
                titleEndpoint = watchEndpoint(
                    videoId = "target-video",
                    type = null
                ),
                secondaryEndpoint = watchEndpoint(
                    videoId = "different-video",
                    type = "MUSIC_VIDEO_TYPE_OMV"
                )
            )
        )

        assertEquals("target-video", parsed?.key)
        assertEquals(Innertube.VideoItem.VideoType.UNKNOWN, parsed?.videoType)
    }

    @Test
    fun keepsRicherRendererVideoTypeWhenTwoRowTitleEndpointIsIncomplete() {
        val parsed = Innertube.VideoItem.from(
            MusicTwoRowItemRenderer(
                navigationEndpoint = navigationEndpoint(
                    watchEndpoint("renderer-video", "MUSIC_VIDEO_TYPE_UGC")
                ),
                thumbnailRenderer = null,
                title = Runs(
                    listOf(
                        Runs.Run(
                            text = "Test video",
                            navigationEndpoint = navigationEndpoint(
                                watchEndpoint("renderer-video", null)
                            )
                        )
                    )
                ),
                subtitle = null,
                thumbnailOverlay = null,
                aspectRatio = "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9",
                subtitleBadges = null,
                menu = null
            )
        )

        assertEquals("renderer-video", parsed?.key)
        assertEquals(Innertube.VideoItem.VideoType.USER_GENERATED_VIDEO, parsed?.videoType)
    }

    @Test
    fun doesNotUseTwoRowTitleTypeFromADifferentVideo() {
        val parsed = Innertube.VideoItem.from(
            MusicTwoRowItemRenderer(
                navigationEndpoint = navigationEndpoint(
                    watchEndpoint("renderer-video", null)
                ),
                thumbnailRenderer = null,
                title = Runs(
                    listOf(
                        Runs.Run(
                            text = "Test video",
                            navigationEndpoint = navigationEndpoint(
                                watchEndpoint("different-video", "MUSIC_VIDEO_TYPE_OMV")
                            )
                        )
                    )
                ),
                subtitle = null,
                thumbnailOverlay = null,
                aspectRatio = "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9",
                subtitleBadges = null,
                menu = null
            )
        )

        assertEquals("renderer-video", parsed?.key)
        assertEquals(Innertube.VideoItem.VideoType.UNKNOWN, parsed?.videoType)
    }

    private fun video(type: String?): Innertube.VideoItem {
        return Innertube.VideoItem(
            info = Innertube.Info(
                name = "Test video",
                endpoint = watchEndpoint(videoId = "video-${type ?: "unknown"}", type = type)
            ),
            authors = emptyList(),
            viewsText = "1 view",
            durationText = "3:00",
            thumbnail = null
        )
    }

    private fun searchRow(
        playlistVideoId: String,
        titleEndpoint: NavigationEndpoint.Endpoint.Watch,
        secondaryEndpoint: NavigationEndpoint.Endpoint.Watch? = null
    ): MusicShelfRenderer.Content = MusicShelfRenderer.Content(
        musicResponsiveListItemRenderer = MusicResponsiveListItemRenderer(
            fixedColumns = null,
            flexColumns = listOfNotNull(
                MusicResponsiveListItemRenderer.FlexColumn(
                    MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                        Runs(
                            listOf(
                                Runs.Run(
                                    text = "Test video",
                                    navigationEndpoint = navigationEndpoint(titleEndpoint)
                                )
                            )
                        )
                    )
                ),
                secondaryEndpoint?.let { endpoint ->
                    MusicResponsiveListItemRenderer.FlexColumn(
                        MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(
                            Runs(
                                listOf(
                                    Runs.Run(
                                        text = "Secondary",
                                        navigationEndpoint = navigationEndpoint(endpoint)
                                    )
                                )
                            )
                        )
                    )
                }
            ),
            thumbnail = null,
            navigationEndpoint = null,
            badges = null,
            playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
                playlistSetVideoId = null,
                videoId = playlistVideoId
            )
        ),
        musicMultiRowListItemRenderer = null,
        continuationItemRenderer = null
    )

    private fun navigationEndpoint(
        watchEndpoint: NavigationEndpoint.Endpoint.Watch
    ): NavigationEndpoint = NavigationEndpoint(
        watchEndpoint = watchEndpoint,
        watchPlaylistEndpoint = null,
        browseEndpoint = null,
        searchEndpoint = null
    )

    private fun watchEndpoint(
        videoId: String?,
        type: String?
    ): NavigationEndpoint.Endpoint.Watch {
        val watchConfig = type?.let {
            NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs(
                NavigationEndpoint.Endpoint.Watch.WatchEndpointMusicSupportedConfigs
                    .WatchEndpointMusicConfig(it)
            )
        }

        return NavigationEndpoint.Endpoint.Watch(
            videoId = videoId,
            watchEndpointMusicSupportedConfigs = watchConfig
        )
    }
}
