package app.kreate.android.viewmodel.home

import app.kreate.constant.ArtistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Artist
import it.fast4x.rimusic.enums.FilterBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeArtistsOrderingTest {

    @Test
    fun titleSortOrdersMergedArtistsAndRemovesDuplicates() {
        val artists = mergeAndSortArtists(
            online = listOf(
                Artist( id = "z", name = "Zeta", isYoutubeArtist = true ),
                Artist( id = "a", name = "Alpha", isYoutubeArtist = true )
            ),
            local = listOf(
                Artist( id = "b", name = "Beta", timestamp = 10L ),
                Artist( id = "a", name = "Alpha", timestamp = 20L, bookmarkedAt = 30L )
            ),
            filterBy = FilterBy.All,
            sortBy = ArtistSortBy.TITLE,
            sortOrder = SortOrder.ASCENDING
        )

        assertEquals( listOf( "Alpha", "Beta", "Zeta" ), artists.map( Artist::name ) )
        assertEquals( 20L, artists.first().timestamp )
        assertEquals( 30L, artists.first().bookmarkedAt )
        assertTrue( artists.first().isYoutubeArtist )
    }

    @Test
    fun descendingTitleSortIsAppliedAfterMerging() {
        val artists = mergeAndSortArtists(
            online = listOf( Artist( id = "a", name = "Alpha", isYoutubeArtist = true ) ),
            local = listOf( Artist( id = "b", name = "Beta" ) ),
            filterBy = FilterBy.All,
            sortBy = ArtistSortBy.TITLE,
            sortOrder = SortOrder.DESCENDING
        )

        assertEquals( listOf( "Beta", "Alpha" ), artists.map( Artist::name ) )
    }
}
