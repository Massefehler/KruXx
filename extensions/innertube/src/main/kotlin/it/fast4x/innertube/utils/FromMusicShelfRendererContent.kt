package it.fast4x.innertube.utils

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.NavigationEndpoint

/**
 * Chooses the richest watch endpoint that belongs to this row.
 *
 * YTM sometimes repeats the same video id in several places while only one occurrence carries
 * musicVideoType. The id match prevents an unrelated secondary link from winning.
 */
private val MusicShelfRenderer.Content.bestWatchEndpoint: NavigationEndpoint.Endpoint.Watch?
    get() {
        val (mainRuns, otherRuns) = runs
        val renderer = musicResponsiveListItemRenderer
        val primaryCandidates = mainRuns.mapNotNull { it.navigationEndpoint?.watchEndpoint } +
                listOfNotNull(renderer?.navigationEndpoint?.watchEndpoint)
        val candidates = primaryCandidates +
                otherRuns.flatten().mapNotNull { it.navigationEndpoint?.watchEndpoint }
        val targetVideoId = renderer?.playlistItemData?.videoId
            ?: mainRuns.firstNotNullOfOrNull { it.navigationEndpoint?.watchEndpoint?.videoId }
            ?: renderer?.navigationEndpoint?.watchEndpoint?.videoId
            ?: candidates.firstNotNullOfOrNull { it.videoId }

        if (targetVideoId != null) {
            val matchingCandidates = candidates.filter { it.videoId == targetVideoId }

            return matchingCandidates.firstOrNull { it.type != null }
                ?: primaryCandidates
                    .firstOrNull { it.videoId == null && it.type != null }
                    ?.copy(videoId = targetVideoId)
                ?: matchingCandidates.firstOrNull()
                ?: NavigationEndpoint.Endpoint.Watch(videoId = targetVideoId)
        }

        return candidates.firstOrNull { it.videoId != null && it.type != null }
            ?: candidates.firstOrNull { it.videoId != null }
    }

fun Innertube.SongItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.SongItem? {
    val (mainRuns, otherRuns) = content.runs
    val renderer = content.musicResponsiveListItemRenderer
    val titleRun = mainRuns.firstOrNull()
    val watchEndpoint = content.bestWatchEndpoint

    // Possible configurations:
    // "song" • author(s) • album • duration
    // "song" • author(s) • duration
    // author(s) • album • duration
    // author(s) • duration

    val album: Innertube.Info<NavigationEndpoint.Endpoint.Browse>? = otherRuns
        .getOrNull(otherRuns.lastIndex - 1)
        ?.firstOrNull()
        ?.takeIf { run ->
            run
                .navigationEndpoint
                ?.browseEndpoint
                ?.type == "MUSIC_PAGE_TYPE_ALBUM"
        }
        ?.let(Innertube::Info)

    val isExplicit = content.musicResponsiveListItemRenderer
                                      ?.badges
                                      ?.any {
                                          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                      } ?: false

    return Innertube.SongItem(
        info = watchEndpoint?.let { Innertube.Info(titleRun?.text, it) },
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - if (album == null) 1 else 2)
            ?.map(Innertube::Info),
        album = album,
        durationText = otherRuns
            .lastOrNull()
            ?.firstOrNull()?.text,
        thumbnail = content
            .thumbnail,
        explicit = isExplicit
    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.VideoItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.VideoItem? {
    val (mainRuns, otherRuns) = content.runs
    val renderer = content.musicResponsiveListItemRenderer
    val titleRun = mainRuns.firstOrNull()
    val watchEndpoint = content.bestWatchEndpoint

    return runCatching {
        Innertube.VideoItem(
            info = watchEndpoint?.let { Innertube.Info(titleRun?.text, it) },
            authors = otherRuns
                .getOrNull(otherRuns.lastIndex - 2)
                ?.map(Innertube::Info),
            viewsText = otherRuns
                .getOrNull(otherRuns.lastIndex - 1)
                ?.firstOrNull()
                ?.text,
            durationText = otherRuns
                .getOrNull(otherRuns.lastIndex)
                ?.firstOrNull()
                ?.text,
            thumbnail = content
                .thumbnail,
            explicit = renderer?.badges?.any {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } == true
        ).takeIf { it.info?.endpoint?.videoId != null }
    }.getOrNull()

}

fun Innertube.AlbumItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.AlbumItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.AlbumItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.map(Innertube::Info),
        year = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.ArtistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.ArtistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        subscribersCountText = otherRuns
            .lastOrNull()
            ?.last()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.PlaylistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        channel = otherRuns
            .firstOrNull()
            ?.firstOrNull()
            ?.let(Innertube::Info),
        songCount = otherRuns
            .lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = content
            .thumbnail,
        isEditable = false
    ).takeIf { it.info?.endpoint?.browseId != null }
}
