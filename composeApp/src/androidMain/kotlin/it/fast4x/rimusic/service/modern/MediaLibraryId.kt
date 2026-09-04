package it.fast4x.rimusic.service.modern

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

internal data class MediaLibrarySelection(
    val parentId: String,
    val songId: String,
)

/** Builds and parses the opaque IDs exposed through the media library. */
@OptIn(UnstableApi::class)
internal object MediaLibraryId {

    private const val SEARCH_QUERY_PREFIX = "query:"

    private val playableRootIds = setOf(
        PlayerServiceModern.SONG,
        PlayerServiceModern.ARTIST,
        PlayerServiceModern.ALBUM,
        PlayerServiceModern.PLAYLIST,
        PlayerServiceModern.SEARCHED,
    )

    fun container(categoryId: String, containerId: String): String =
        "$categoryId/${Uri.encode(containerId)}"

    fun playable(parentId: String, songId: String): String =
        "$parentId/${Uri.encode(songId)}"

    fun searchParent(query: String): String =
        container(PlayerServiceModern.SEARCHED, SEARCH_QUERY_PREFIX + query.trim())

    fun searchQuery(parentId: String): String? =
        containerValue(parentId, PlayerServiceModern.SEARCHED)
            ?.takeIf { it.startsWith(SEARCH_QUERY_PREFIX) }
            ?.removePrefix(SEARCH_QUERY_PREFIX)

    fun containerValue(mediaId: String, categoryId: String): String? {
        val prefix = "$categoryId/"
        if (!mediaId.startsWith(prefix)) return null

        val encodedValue = mediaId.removePrefix(prefix)
        return encodedValue
            .takeIf { it.isNotEmpty() && '/' !in it }
            ?.let(Uri::decode)
    }

    fun parentCategory(mediaId: String): String? =
        listOf(
            PlayerServiceModern.ARTIST,
            PlayerServiceModern.ALBUM,
            PlayerServiceModern.PLAYLIST,
        ).firstOrNull { containerValue(mediaId, it) != null }

    fun selection(mediaId: String): MediaLibrarySelection? {
        val rootId = mediaId.substringBefore('/')
        if (rootId !in playableRootIds) return null

        val remainder = mediaId.substringAfter('/', missingDelimiterValue = "")
        if (remainder.isEmpty()) return null

        if (rootId == PlayerServiceModern.SONG) {
            if ('/' in remainder) return null
            return MediaLibrarySelection(rootId, Uri.decode(remainder))
        }

        val separatorIndex = remainder.indexOf('/')
        if (separatorIndex < 0) {
            // Compatibility with search IDs emitted by older KruXx versions.
            return if (rootId == PlayerServiceModern.SEARCHED) {
                MediaLibrarySelection(rootId, Uri.decode(remainder))
            } else {
                null
            }
        }

        val encodedContainer = remainder.substring(0, separatorIndex)
        val encodedSongId = remainder.substring(separatorIndex + 1)
        if (encodedContainer.isEmpty() || encodedSongId.isEmpty() || '/' in encodedSongId) return null

        return MediaLibrarySelection(
            parentId = "$rootId/$encodedContainer",
            songId = Uri.decode(encodedSongId),
        )
    }
}

internal fun <T> List<T>.mediaLibraryPage(page: Int, pageSize: Int): List<T> {
    if (!isValidMediaLibraryPage(page, pageSize) || isEmpty()) return emptyList()

    val fromIndex = page.toLong() * pageSize.toLong()
    if (fromIndex >= size) return emptyList()

    val toIndex = minOf(fromIndex + pageSize, size.toLong())
    return subList(fromIndex.toInt(), toIndex.toInt())
}

internal fun isValidMediaLibraryPage(page: Int, pageSize: Int): Boolean =
    page >= 0 && pageSize > 0
