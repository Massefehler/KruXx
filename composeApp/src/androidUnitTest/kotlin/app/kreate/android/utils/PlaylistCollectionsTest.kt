package app.kreate.android.utils

import app.kreate.constant.PlaylistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistCollectionsTest {

    @Test
    fun browseIdsUseTheSameIdentityWithOrWithoutVlPrefix() {
        assertEquals( "PL123", normalizePlaylistBrowseId("VLPL123") )
        assertEquals( "PL123", normalizePlaylistBrowseId(" PL123 ") )
        assertNull( normalizePlaylistBrowseId("  ") )
    }

    @Test
    fun localPlaylistReplacesMatchingOnlineEntryAndKeepsRemoteArtwork() {
        val online = preview(
            name = "Remote title",
            browseId = "VLPL123",
            id = -1,
            songCount = 99,
            thumbnailUrl = "remote-cover"
        )
        val local = preview(
            name = "Local title",
            browseId = "PL123",
            id = 42,
            songCount = 7,
            isYoutubePlaylist = false
        )

        val result = mergeAndSortPlaylists(
            online = listOf(online),
            local = listOf(local),
            sortBy = PlaylistSortBy.DATE_ADDED,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( 1, result.size )
        assertEquals( 42L, result.single().playlist.id )
        assertEquals( "Local title", result.single().playlist.name )
        assertEquals( true, result.single().playlist.isYoutubePlaylist )
        assertEquals( 7, result.single().songCount )
        assertEquals( "remote-cover", result.single().thumbnailUrl )
    }

    @Test
    fun titleSortIsAppliedToTheCombinedCollection() {
        val result = mergeAndSortPlaylists(
            online = listOf( preview("Zeta", "PL1", -1) ),
            local = listOf( preview("Alpha", null, 1), preview("Beta", null, 2) ),
            sortBy = PlaylistSortBy.TITLE,
            sortOrder = SortOrder.DESCENDING
        )

        assertEquals( listOf("Zeta", "Beta", "Alpha"), result.map { it.playlist.name } )
    }

    @Test
    fun homeCarouselOnlyAppendsLocalEntriesMissingFromRemoteItems() {
        val local = listOf(
            preview("Already online", "PL123", 1),
            preview("Local only", null, 2)
        )

        val result = localPlaylistsMissingFrom(
            remoteBrowseIds = listOf("VLPL123"),
            local = local
        )

        assertEquals( listOf("Local only"), result.map { it.playlist.name } )
    }

    @Test
    fun localizedSongCountTextIsParsed() {
        assertEquals( 52, parsePlaylistSongCount("52 songs") )
        assertEquals( 1234, parsePlaylistSongCount("1,234 songs") )
        assertEquals( 1234, parsePlaylistSongCount("1.234 Titel") )
        assertEquals( 0, parsePlaylistSongCount("0 Titel") )
        assertEquals( -1, parsePlaylistSongCount(null) )
        assertEquals( -1, parsePlaylistSongCount("No count available") )
    }

    @Test
    fun duplicateOnlineBrowseIdsCollapseAcrossVlVariants() {
        val result = mergeAndSortPlaylists(
            online = listOf(
                preview("First response", "VLPL123", -1),
                preview("Continuation response", "PL123", -2)
            ),
            local = emptyList(),
            sortBy = PlaylistSortBy.DATE_ADDED,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( 1, result.size )
        assertEquals( "Continuation response", result.single().playlist.name )
    }

    @Test
    fun localArtworkWinsOverRemoteFallback() {
        val result = mergeAndSortPlaylists(
            online = listOf(preview("Remote", "VLPL123", -1, thumbnailUrl = "remote-cover")),
            local = listOf(preview("Local", "PL123", 42, thumbnailUrl = "local-cover")),
            sortBy = PlaylistSortBy.DATE_ADDED,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( "local-cover", result.single().thumbnailUrl )
    }

    @Test
    fun localEntriesSurviveAnEmptyRemoteResult() {
        val local = listOf(preview("On device", null, 7))

        val result = mergeAndSortPlaylists(
            online = emptyList(),
            local = local,
            sortBy = PlaylistSortBy.DATE_ADDED,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( local, result )
    }

    @Test
    fun youtubeOnlyEntriesRemainVisibleWithoutASyncedDatabaseRow() {
        val result = mergeAndSortPlaylists(
            online = listOf(preview("Cloud only", "VLPL-CLOUD", -1, songCount = 12)),
            local = emptyList(),
            sortBy = PlaylistSortBy.DATE_ADDED,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( 1, result.size )
        assertEquals( "PL-CLOUD", normalizePlaylistBrowseId(result.single().playlist.browseId) )
        assertNotNull( result.single().playlist.browseId )
    }

    @Test
    fun songCountSortUsesTheMergedCollection() {
        val result = mergeAndSortPlaylists(
            online = listOf(preview("Cloud", "PL1", -1, songCount = 100)),
            local = listOf(
                preview("Small", null, 1, songCount = 2),
                preview("Medium", null, 2, songCount = 20)
            ),
            sortBy = PlaylistSortBy.SONG_COUNT,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( listOf(2, 20, 100), result.map(PlaylistPreview::songCount) )
    }

    private fun preview(
        name: String,
        browseId: String?,
        id: Long,
        songCount: Int = 0,
        thumbnailUrl: String? = null,
        isYoutubePlaylist: Boolean = browseId != null
    ) = PlaylistPreview(
        playlist = Playlist(
            name = name,
            browseId = browseId,
            isYoutubePlaylist = isYoutubePlaylist,
            id = id
        ),
        songCount = songCount,
        thumbnailUrl = thumbnailUrl
    )
}
