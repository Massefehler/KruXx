package app.kreate.android.viewmodel.home

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.constant.ArtistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Artist
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.pages.BrowseResult
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.FilterBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


internal fun mergeAndSortArtists(
    online: List<Artist>,
    local: List<Artist>,
    filterBy: FilterBy,
    sortBy: ArtistSortBy,
    sortOrder: SortOrder
): List<Artist> {
    val filtered = when( filterBy ) {
        FilterBy.All            -> online + local
        FilterBy.YoutubeLibrary -> (online + local).filter { it.isYoutubeArtist }
        FilterBy.Local          -> local.filterNot { it.isYoutubeArtist }
    }

    // The same artist can be present in both the current YouTube response and
    // the local database. Keep a single row while retaining useful local
    // metadata such as bookmark and insertion timestamps.
    val unique = linkedMapOf<String, Artist>()
    filtered.forEach { artist ->
        val existing = unique[artist.id]
        unique[artist.id] = existing?.copy(
            name = existing.name ?: artist.name,
            thumbnailUrl = existing.thumbnailUrl ?: artist.thumbnailUrl,
            timestamp = artist.timestamp ?: existing.timestamp,
            bookmarkedAt = artist.bookmarkedAt ?: existing.bookmarkedAt,
            isYoutubeArtist = existing.isYoutubeArtist || artist.isYoutubeArtist
        ) ?: artist
    }

    val merged = unique.values.toList()
    return when( sortBy ) {
        ArtistSortBy.TITLE      -> sortOrder.applyTo( merged.sortedBy( Artist::cleanName ) )
        ArtistSortBy.RANDOM     -> merged.shuffled()
        // YouTube does not provide a reliable added-at timestamp here. Preserve
        // its server order and the database's already sorted local order.
        ArtistSortBy.DATE_ADDED -> merged
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
class HomeArtistsViewModel : ViewModel(), KoinComponent {

    private val _syncedArtists = MutableStateFlow(emptyList<Artist>())
    private val _localArtists = MutableStateFlow(emptyList<Artist>())
    private val _artists = MutableStateFlow(emptyList<Artist>())
    private val _isRefreshing = MutableStateFlow(false)

    val artists = _artists.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch( Dispatchers.IO ) {
            val typeFlow = snapshotFlow { Preferences.HOME_ARTIST_TYPE.value }
            val sortByFlow = snapshotFlow { Preferences.HOME_ARTISTS_SORT_BY.value }
            val sortOrderFlow = snapshotFlow { Preferences.HOME_ARTISTS_SORT_ORDER.value }

            combine( typeFlow, sortByFlow, sortOrderFlow, ::Triple )
                .flatMapLatest { (type, sortBy, sortOrder) ->
                    when( type ) {
                        ArtistsType.Favorites -> Database.artistTable.sortFollowing( sortBy, sortOrder )
                        ArtistsType.Library -> Database.artistTable.sortInLibrary( sortBy, sortOrder )
                    }
                }
                .distinctUntilChanged()
                .collectLatest { localAlbums ->
                    _localArtists.update { localAlbums }
                }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            val filterByFlow = snapshotFlow { Preferences.HOME_ARTIST_AND_ALBUM_FILTER.value }
            val sortByFlow = snapshotFlow { Preferences.HOME_ARTISTS_SORT_BY.value }
            val sortOrderFlow = snapshotFlow { Preferences.HOME_ARTISTS_SORT_ORDER.value }
            val sortingFlow = combine( sortByFlow, sortOrderFlow, ::Pair )

            combine( _syncedArtists, _localArtists, filterByFlow, sortingFlow ) {
                    online, local, filterBy, (sortBy, sortOrder) ->
                mergeAndSortArtists( online, local, filterBy, sortBy, sortOrder )
            }.collectLatest { artists -> _artists.update { artists } }
        }

        // Trigger sync on first run
        onRefresh()
    }

    private suspend fun syncArtists() {
        val isEnabled = withContext( Dispatchers.Main ) {
            InnertubeUtils.isLoggedIn && Preferences.YOUTUBE_ARTISTS_SYNC.value
        }
        if( !isEnabled ) return

        YouTube.browse( "FEmusic_library_landing", null )
               .onFailure { err ->
                   Logger.e( "", err, "HomeArtists" )
                   Toaster.e(
                       R.string.error_failed_to_sync_tab,
                       get<Context>().getString( R.string.artists ).lowercase()
                   )
               }
               .onSuccess { result ->
                   result.items
                         .flatMap( BrowseResult.Item::items )
                         .mapNotNull { it as? ArtistItem }
                         .map { item ->
                             Artist(
                                 id = item.id,
                                 name = item.title,
                                 thumbnailUrl = item.thumbnail,
                                 isYoutubeArtist = true
                             )
                         }
                         .also { artists ->
                             _syncedArtists.update { artists }
                         }
               }
    }

    fun onRefresh() {
        _isRefreshing.update { true }
        // Clear fetched artists to prevent stale items
        _syncedArtists.update { emptyList() }
        viewModelScope.launch {
            syncArtists()
            _isRefreshing.update { false }
        }
    }
}
