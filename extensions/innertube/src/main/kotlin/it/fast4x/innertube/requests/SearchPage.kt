package it.fast4x.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ContinuationResponse
import it.fast4x.innertube.models.MusicResponsiveListItemRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.SearchResponse
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.utils.runCatchingNonCancellable

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: SearchBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?,
    useLogin: Boolean = false
): Result<Innertube.ItemsPage<T>?>? {
    suspend fun request(loggedIn: Boolean): Innertube.ItemsPage<T>? {
        val response = client.post(search) {
            setLogin(body.context.client, loggedIn)
            setBody(body)
            mask("contents.tabbedSearchResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents.musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)")
        }.body<SearchResponse>()

        val shelves = response
            .contents
            ?.tabbedSearchResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?.mapNotNull { it.musicShelfRenderer?.toItemsPage(fromMusicShelfRendererContent) }
            .orEmpty()

        // Depending on locale/account experiments, YTM can put an empty explanatory shelf before
        // the real result shelf. Prefer the first populated shelf while preserving an empty shelf's
        // continuation as a last-resort response.
        return shelves.firstOrNull { !it.items.isNullOrEmpty() } ?: shelves.firstOrNull()
    }

    val shouldLogin = useLogin && !cookie.isNullOrBlank()
    val result = runCatchingNonCancellable { request(shouldLogin) }
    return if (
        shouldLogin && result != null &&
        (result.isFailure || result.getOrNull() == null)
    ) {
        // An expired account cookie can produce either an error or a successful response without
        // a result shelf. Neither case must turn the whole search into an empty screen.
        runCatchingNonCancellable { request(false) }
    } else {
        result
    }
}

suspend fun <T : Innertube.Item> Innertube.searchPage(
    body: ContinuationBody,
    fromMusicShelfRendererContent: (MusicShelfRenderer.Content) -> T?,
    useLogin: Boolean = false
): Result<Innertube.ItemsPage<T>?>? {
    suspend fun request(loggedIn: Boolean): Innertube.ItemsPage<T>? {
        val response = client.post(search) {
            setLogin(body.context.client, loggedIn)
            setBody(body)
            mask("continuationContents.musicShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)")
        }.body<ContinuationResponse>()

        return response
            .continuationContents
            ?.musicShelfContinuation
            ?.toItemsPage(fromMusicShelfRendererContent)
    }

    val shouldLogin = useLogin && !cookie.isNullOrBlank()
    val result = runCatchingNonCancellable { request(shouldLogin) }
    return if (shouldLogin && result?.isFailure == true) {
        runCatchingNonCancellable { request(false) }
    } else {
        result
    }
}

private fun <T : Innertube.Item> MusicShelfRenderer?.toItemsPage(mapper: (MusicShelfRenderer.Content) -> T?) =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(mapper),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )

private fun <T : Innertube.Item> MusicResponsiveListItemRenderer?.toItemsPage(mapper: (MusicResponsiveListItemRenderer.FlexColumn) -> T?) =
    Innertube.ItemsPage(
        items = this
            ?.flexColumns
            ?.mapNotNull(mapper),
        continuation = null
    )
