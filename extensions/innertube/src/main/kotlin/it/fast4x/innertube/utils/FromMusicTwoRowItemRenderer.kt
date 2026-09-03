package it.fast4x.innertube.utils

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.MusicTwoRowItemRenderer
import it.fast4x.innertube.models.NavigationEndpoint

fun Innertube.VideoItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.VideoItem? {
    val titleRun = renderer.title?.runs?.firstOrNull()
    val titleWatchEndpoint = titleRun?.navigationEndpoint?.watchEndpoint
    val rendererWatchEndpoint = renderer.navigationEndpoint?.watchEndpoint
    val endpointCandidates = listOfNotNull(rendererWatchEndpoint, titleWatchEndpoint)
    val targetVideoId = rendererWatchEndpoint?.videoId
        ?: titleWatchEndpoint?.videoId
    val watchEndpoint = if (targetVideoId != null) {
        val matchingEndpoints = endpointCandidates.filter { it.videoId == targetVideoId }

        matchingEndpoints.firstOrNull { it.type != null }
            ?: endpointCandidates
                .firstOrNull { it.videoId == null && it.type != null }
                ?.copy(videoId = targetVideoId)
            ?: matchingEndpoints.firstOrNull()
            ?: NavigationEndpoint.Endpoint.Watch(videoId = targetVideoId)
    } else {
        endpointCandidates.firstOrNull { it.videoId != null && it.type != null }
            ?: endpointCandidates.firstOrNull { it.videoId != null }
    }

    return Innertube.VideoItem(
        info = watchEndpoint?.let { Innertube.Info(titleRun?.text, it) },
        authors = null,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        durationText = null,
        viewsText = null

    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.AlbumItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.AlbumItem? {
    return Innertube.AlbumItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        authors = null,
        year = renderer
            .subtitle
            ?.runs
            ?.lastOrNull()
            ?.text,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.ArtistItem? {
    return Innertube.ArtistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        subscribersCountText = renderer
            .subtitle
            ?.runs
            ?.firstOrNull()
            ?.text,
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull()
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.PlaylistItem.Companion.from(renderer: MusicTwoRowItemRenderer): Innertube.PlaylistItem? {
    return Innertube.PlaylistItem(
        info = renderer
            .title
            ?.runs
            ?.firstOrNull()
            ?.let(Innertube::Info),
        channel = renderer
            .subtitle
            ?.runs
            ?.getOrNull(2)
            ?.let(Innertube::Info),
        songCount = renderer
            .subtitle
            ?.runs
            ?.getOrNull(4)
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = renderer
            .thumbnailRenderer
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        isEditable = false
    ).takeIf { it.info?.endpoint?.browseId != null }
}
