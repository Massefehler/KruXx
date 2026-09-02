package it.fast4x.rimusic.ui.screens.searchresult

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fetches YTM's song and video result shelves together.
 *
 * YouTube Music frequently classifies an otherwise playable track as a music video or UGC item.
 * Keeping the song shelf first preserves YTM's ranking while the first video page makes those
 * tracks discoverable without forcing the user to know which backend category was chosen.
 */
internal suspend fun searchSongsWithVideoFallback(
    query: String,
    context: Context,
    useLogin: Boolean
): Result<Innertube.ItemsPage<Innertube.SongItem>?>? = coroutineScope {
    val songsRequest = async {
        Innertube.searchPage(
            body = SearchBody(
                context = context,
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from,
            useLogin = useLogin
        )
    }
    val videosRequest = async {
        Innertube.searchPage(
            body = SearchBody(
                context = context,
                query = query,
                params = Innertube.SearchFilter.Video.value
            ),
            fromMusicShelfRendererContent = Innertube.VideoItem.Companion::from,
            useLogin = useLogin
        )
    }

    val songsResult = songsRequest.await()
    val videosResult = videosRequest.await()
    val songsPage = songsResult?.getOrNull()
    val videosPage = videosResult?.getOrNull()

    if (songsPage != null || videosPage != null) {
        Result.success(mergeSongAndVideoPages(songsPage, videosPage))
    } else {
        val failure = songsResult?.exceptionOrNull() ?: videosResult?.exceptionOrNull()
        when {
            failure != null -> Result.failure(failure)
            songsResult == null && videosResult == null -> null
            else -> Result.success(null)
        }
    }
}

internal fun mergeSongAndVideoPages(
    songsPage: Innertube.ItemsPage<Innertube.SongItem>?,
    videosPage: Innertube.ItemsPage<Innertube.VideoItem>?
): Innertube.ItemsPage<Innertube.SongItem> {
    val videoFallback = videosPage
        ?.items
        .orEmpty()
        .map { video ->
            Innertube.SongItem(
                info = video.info,
                authors = video.authors,
                album = null,
                durationText = video.durationText,
                thumbnail = video.thumbnail,
                explicit = video.explicit
            )
        }

    return Innertube.ItemsPage(
        items = (songsPage?.items.orEmpty() + videoFallback).distinctBy(Innertube.Item::key),
        // Continue the primary song shelf. The first video page is deliberately a fallback;
        // the dedicated Videos tab retains full video pagination.
        continuation = songsPage?.continuation
    )
}
