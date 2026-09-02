package it.fast4x.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.Innertube.getBestQuality
import it.fast4x.innertube.models.GetSearchSuggestionsResponse
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.SearchSuggestionsResponse
import it.fast4x.innertube.models.bodies.SearchSuggestionsBody
import it.fast4x.innertube.models.oddElements
import it.fast4x.innertube.models.splitBySeparator
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.searchSuggestions(
    body: SearchSuggestionsBody,
    useLogin: Boolean = false
): Result<List<String>?>? {
    suspend fun request(loggedIn: Boolean): List<String>? {
        val response = client.post(searchSuggestions) {
            setLogin(body.context.client, loggedIn)
            setBody(body)
            mask("contents.searchSuggestionsSectionRenderer.contents.searchSuggestionRenderer.navigationEndpoint.searchEndpoint.query")
        }.body<SearchSuggestionsResponse>()

        return response
            .contents
            ?.firstOrNull()
            ?.searchSuggestionsSectionRenderer
            ?.contents
            ?.mapNotNull { content ->
                content
                    .searchSuggestionRenderer
                    ?.navigationEndpoint
                    ?.searchEndpoint
                    ?.query
            }
    }

    val shouldLogin = useLogin && !cookie.isNullOrBlank()
    val result = runCatchingNonCancellable { request(shouldLogin) }
    return if (
        shouldLogin && result != null &&
        (result.isFailure || result.getOrNull() == null)
    ) {
        runCatchingNonCancellable { request(false) }
    } else {
        result
    }
}

suspend fun Innertube.searchSuggestionsWithItems(
    body: SearchSuggestionsBody,
    useLogin: Boolean = false
): Result<Innertube.SearchSuggestions>? {
    suspend fun request(loggedIn: Boolean): Innertube.SearchSuggestions {
        val response = client.post(searchSuggestions) {
            setLogin(body.context.client, loggedIn)
            setBody(body)
        }.body<GetSearchSuggestionsResponse>()

        val queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
            content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text.orEmpty() }
        }.orEmpty()

        val recommendedItems =
            response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let(SearchSuggestionPage::fromMusicResponsiveListItemRenderer)
            }.orEmpty()

        return Innertube.SearchSuggestions(
            queries = queries,
            recommendedSong = recommendedItems.filterIsInstance<Innertube.SongItem>().firstOrNull(),
            recommendedPlaylist = recommendedItems.filterIsInstance<Innertube.PlaylistItem>().firstOrNull(),
            recommendedAlbum = recommendedItems.filterIsInstance<Innertube.AlbumItem>().firstOrNull(),
            recommendedArtist = recommendedItems.filterIsInstance<Innertube.ArtistItem>().firstOrNull(),
            recommendedVideo = recommendedItems.filterIsInstance<Innertube.VideoItem>().firstOrNull(),
        )
    }

    val shouldLogin = useLogin && !cookie.isNullOrBlank()
    val result = runCatchingNonCancellable { request(shouldLogin) }
    return if (shouldLogin && result?.isFailure == true) {
        runCatchingNonCancellable { request(false) }
    } else {
        result
    }
}

object SearchSuggestionPage {
    fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): Innertube.Item? {
        return when {
            /*
            renderer.isVideo -> {
                VideoItem(
                    id = renderer.playlistItemData?.videoId ?: return null,
                    title = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    thumbnails = renderer.thumbnail.musicThumbnailRenderer.thumbnail,
                    explicit = renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                    artists = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                        ?.getOrNull(1)?.oddElements()?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId
                            )
                        } ?: return null,
                    album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                        )
                    },
                    duration = null,
                    view = null
                )
            }
             */
            renderer.isSong -> {
                val title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                    ?: return null
                val watchEndpoint = renderer.navigationEndpoint?.watchEndpoint
                    ?: renderer.playlistItemData?.videoId?.let {
                        NavigationEndpoint.Endpoint.Watch(videoId = it)
                    }
                val explicitBadge = if (renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null) "e:" else ""
                Innertube.SongItem(
                    info = Innertube.Info(
                        name = "$explicitBadge$title",
                        endpoint = watchEndpoint
                    ),
                    authors = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                        ?.getOrNull(1)?.oddElements()?.map {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.browseEndpoint
                            )
                        } ?: return null,
                    album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                        ?.let {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.browseEndpoint
                            )
                        },
                    durationText = null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null,
                    explicit = renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null
                )
            }
            renderer.isArtist -> {
                val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint ?: return null
                Innertube.ArtistItem(
                    info = Innertube.Info(
                        name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                        endpoint = browseEndpoint
                    ),
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null,
                    subscribersCountText = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text
                )
            }
            renderer.isAlbum -> {
                val secondaryLine = renderer.flexColumns.getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator() ?: return null
                val browseEndpoint = renderer.navigationEndpoint?.browseEndpoint ?: return null
                Innertube.AlbumItem(
                    info = Innertube.Info(
                        name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: return null,
                        endpoint = browseEndpoint
                    ),
                    authors = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.splitBySeparator()
                        ?.getOrNull(1)?.oddElements()?.map {
                            Innertube.Info(
                                name = it.text,
                                endpoint = it.navigationEndpoint?.browseEndpoint
                            )
                        } ?: return null,
                    year = secondaryLine.lastOrNull()?.firstOrNull()?.text,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                        ?: return null
                )
            }
            else -> null
        }
    }
}
