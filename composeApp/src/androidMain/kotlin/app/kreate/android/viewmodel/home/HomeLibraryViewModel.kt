package app.kreate.android.viewmodel.home

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.mergeAndSortPlaylists
import app.kreate.android.utils.parsePlaylistSongCount
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.utils.completed
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CancellationException
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
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

enum class PlaylistSyncStatus {
    LOGGED_OUT,
    DISABLED,
    ENABLED,
    ERROR
}

internal fun resolvePlaylistSyncStatus(
    loginEnabled: Boolean,
    syncId: String,
    playlistSyncEnabled: Boolean
): PlaylistSyncStatus = when {
    !loginEnabled || syncId.isBlank() -> PlaylistSyncStatus.LOGGED_OUT
    !playlistSyncEnabled -> PlaylistSyncStatus.DISABLED
    else -> PlaylistSyncStatus.ENABLED
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLibraryViewModel : ViewModel(), KoinComponent {

    private val _syncedPlaylists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _localPlaylists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _playlists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _isRefreshing = MutableStateFlow(false)
    private val _syncStatus = MutableStateFlow(currentSyncStatus())
    private val refreshRequests = MutableStateFlow(0L)

    val playlists = _playlists.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()
    val syncStatus = _syncStatus.asStateFlow()

    init {
        val sortFlow = combine(
            snapshotFlow { Preferences.HOME_LIBRARY_SORT_BY.value },
            snapshotFlow { Preferences.HOME_LIBRARY_SORT_ORDER.value }
        ) { sortBy, sortOrder -> sortBy to sortOrder }
            .distinctUntilChanged()

        viewModelScope.launch( Dispatchers.IO ) {
            sortFlow
                .flatMapLatest { (sortBy, sortOrder) ->
                    Database.playlistTable.sortPreviews( sortBy, sortOrder )
                }
                .distinctUntilChanged()
                .collectLatest { localPlaylists ->
                    _localPlaylists.update { localPlaylists }
                }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            combine( _syncedPlaylists, _localPlaylists, sortFlow ) { online, local, sorting ->
                mergeAndSortPlaylists(
                    online = online,
                    local = local,
                    sortBy = sorting.first,
                    sortOrder = sorting.second
                )
            }
                .collectLatest { playlists ->
                    _playlists.update { playlists }
                }
        }

        val accountSyncFlow = snapshotFlow { currentSyncStatus() }
            .distinctUntilChanged()

        // Both account changes and explicit pull-to-refresh requests reach the same cancellable
        // pipeline. A login or sync-toggle change therefore cannot be lost behind an active fetch.
        viewModelScope.launch {
            combine( accountSyncFlow, refreshRequests ) { status, _ -> status }
                .collectLatest { status ->
                    _syncStatus.update { status }

                    if( status != PlaylistSyncStatus.ENABLED ) {
                        _syncedPlaylists.update { emptyList() }
                        _isRefreshing.update { false }
                        return@collectLatest
                    }

                    _isRefreshing.update { true }
                    try {
                        syncPlaylists()
                    } finally {
                        _isRefreshing.update { false }
                    }
                }
        }
    }

    private fun currentSyncStatus() = resolvePlaylistSyncStatus(
        loginEnabled = Preferences.YOUTUBE_LOGIN.value,
        syncId = Preferences.YOUTUBE_SYNC_ID.value,
        playlistSyncEnabled = Preferences.YOUTUBE_PLAYLISTS_SYNC.value
    )

    private suspend fun syncPlaylists() {
        val result = YouTube.library( "FEmusic_liked_playlists" ).completed()
        result.exceptionOrNull()?.let { error ->
            if( error is CancellationException ) throw error
        }

        result
               .onFailure { err ->
                   Logger.e( "", err, "HomePlaylist" )
                   _syncStatus.update { PlaylistSyncStatus.ERROR }
                   Toaster.e(
                       R.string.error_failed_to_sync_tab,
                       get<Context>().getString( R.string.playlists ).lowercase()
                   )
               }
               .onSuccess { result ->
                   result.items
                         .filterIsInstance<PlaylistItem>()
                         .filterNot { it.id == "SE" }
                         .map { item ->
                             PlaylistPreview(
                                 playlist = Playlist(
                                     name = item.title,
                                     browseId = item.id,
                                     isEditable = false,
                                     isYoutubePlaylist = true,
                                     id = -1L,
                                     isPinned = false,
                                     isMonthly = false
                                 ),
                                 songCount = parsePlaylistSongCount( item.songCountText ),
                                 thumbnailUrl = item.thumbnail
                             )
                         }
                         .also { playlists ->
                             _syncedPlaylists.update { playlists }
                             _syncStatus.update { PlaylistSyncStatus.ENABLED }
                         }
               }
    }

    fun onRefresh() {
        refreshRequests.update { it + 1L }
    }
}
