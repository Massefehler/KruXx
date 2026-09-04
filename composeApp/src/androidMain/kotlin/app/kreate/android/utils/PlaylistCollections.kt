package app.kreate.android.utils

import app.kreate.constant.PlaylistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.PlaylistPreview


internal fun normalizePlaylistBrowseId( browseId: String? ): String? =
    browseId
        ?.trim()
        ?.takeIf( String::isNotEmpty )
        ?.removePrefix( "VL" )
        ?.takeIf( String::isNotEmpty )

private fun PlaylistPreview.collectionKey(): String =
    normalizePlaylistBrowseId( playlist.browseId )
        ?.let { "youtube:$it" }
        ?: "local:${playlist.id}:${playlist.cleanName()}"

/**
 * Merge live YouTube Music results with database playlists without showing synced entries twice.
 * A database entry wins because it keeps its local id and therefore remains fully editable.
 */
internal fun mergeAndSortPlaylists(
    online: List<PlaylistPreview>,
    local: List<PlaylistPreview>,
    sortBy: PlaylistSortBy,
    sortOrder: SortOrder
): List<PlaylistPreview> {
    val merged = linkedMapOf<String, PlaylistPreview>()

    online.forEach { preview ->
        merged[preview.collectionKey()] = preview
    }
    local.forEach { preview ->
        val key = preview.collectionKey()
        val onlinePreview = merged[key]
        val playlist = if( onlinePreview?.playlist?.isYoutubePlaylist == true &&
            !preview.playlist.isYoutubePlaylist
        )
            preview.playlist.copy( isYoutubePlaylist = true )
        else
            preview.playlist

        if( onlinePreview != null ) merged.remove( key )
        merged[key] = preview.copy(
            playlist = playlist,
            thumbnailUrl = preview.thumbnailUrl ?: onlinePreview?.thumbnailUrl
        )
    }

    val values = merged.values.toList()
    return when( sortBy ) {
        PlaylistSortBy.TITLE ->
            sortOrder.applyTo( values.sortedBy { it.playlist.cleanName() } )

        PlaylistSortBy.SONG_COUNT ->
            sortOrder.applyTo( values.sortedBy( PlaylistPreview::songCount ) )

        PlaylistSortBy.RANDOM -> values.shuffled()

        // Online results do not expose either timestamp or listening time. Preserve their server
        // order and the database order already produced by PlaylistTable.sortPreviews().
        PlaylistSortBy.DATE_ADDED,
        PlaylistSortBy.TOTAL_PLAY_TIME -> values
    }
}

/** Return local playlists that are not already represented by a live YTM carousel item. */
internal fun localPlaylistsMissingFrom(
    remoteBrowseIds: Iterable<String>,
    local: List<PlaylistPreview>
): List<PlaylistPreview> {
    val remoteIds = remoteBrowseIds.mapNotNullTo( hashSetOf(), ::normalizePlaylistBrowseId )

    return local
        .distinctBy( PlaylistPreview::collectionKey )
        .filter { preview ->
            normalizePlaylistBrowseId( preview.playlist.browseId )?.let { it !in remoteIds } ?: true
        }
}

internal fun parsePlaylistSongCount( text: String? ): Int =
    text
        ?.filter( Char::isDigit )
        ?.takeIf( String::isNotEmpty )
        ?.toIntOrNull()
        ?: -1
