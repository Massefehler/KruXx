package app.kreate.android.viewmodel

import android.content.Context
import androidx.annotation.AnyThread
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.LoadMoreContentType
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toSong
import app.kreate.database.models.Song
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem as MetrolistSong
import com.metrolist.innertube.pages.PlaylistPage as MetrolistPlaylistPage
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster


data class YouTubePlaylistHeader(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val subtitleText: String?,
    val description: String?,
) {
    fun shareUrl(host: String): String =
        "$host/playlist?list=${id.removePrefix("VL")}"
}

internal fun normalizeYouTubePlaylistBrowseId(browseId: String): String =
    if( browseId.startsWith("VL") ) browseId else "VL$browseId"

private fun InnertubePlaylist.toHeader(): YouTubePlaylistHeader = YouTubePlaylistHeader(
    id = id,
    name = name,
    thumbnailUrl = thumbnails.firstOrNull()?.url,
    subtitleText = subtitleText,
    description = description,
)

private fun MetrolistPlaylistPage.toHeader(): YouTubePlaylistHeader = YouTubePlaylistHeader(
    id = normalizeYouTubePlaylistBrowseId(playlist.id),
    name = playlist.title,
    thumbnailUrl = playlist.thumbnail?.takeIf(String::isNotBlank),
    subtitleText = listOfNotNull(playlist.author?.name, playlist.songCountText)
        .filter(String::isNotBlank)
        .joinToString(" • ")
        .takeIf(String::isNotBlank),
    description = playlist.description?.takeIf(String::isNotBlank),
)

internal fun Int.toPlaylistDurationText(): String {
    val safeSeconds = coerceAtLeast(0)
    val seconds = (safeSeconds % 60).toString().padStart(2, '0')
    val totalMinutes = safeSeconds / 60
    return if( totalMinutes >= 60 ) {
        val minutes = (totalMinutes % 60).toString().padStart(2, '0')
        "${totalMinutes / 60}:$minutes:$seconds"
    } else {
        "$totalMinutes:$seconds"
    }
}

internal fun MetrolistSong.toDatabaseSong(): Song = Song(
    id = id,
    title = title,
    artistsText = artists.joinToString(", ") { it.name }.takeIf(String::isNotBlank),
    durationText = duration?.toPlaylistDurationText(),
    thumbnailUrl = thumbnail.takeIf(String::isNotBlank),
    isExplicit = explicit,
)


class YouTubePlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val _playlistPage = MutableStateFlow<YouTubePlaylistHeader?>(null)
    private val _initialSongs = MutableStateFlow(emptyList<Song>())
    private val _continued = MutableStateFlow(emptyList<Song>())
    private val _continuation = MutableStateFlow<String?>(null)
    private val loadMoreMutex = Mutex()
    private var anonymousVisitorData: String? = null

    // browseId must not be empty or null in any case
    val browseId: String = savedStateHandle["browseId"]!!

    /**
     * YouTube Music addresses a playlist by its playlist id prefixed with `VL`; the bare id is
     * rejected (HTTP 400, or 401 once the request also carries an account context).
     *
     * Metrolist's library parsers strip that prefix (`LibraryPage`/`RelatedPage` both call
     * `removePrefix("VL")`), so entries coming from the synced account library arrive here
     * without it, while entries coming from the YTM home sections keep it. Normalize both
     * spellings instead of relying on the caller.
     */
    private val playlistBrowseId: String = normalizeYouTubePlaylistBrowseId(browseId)
    val params: String? = savedStateHandle["params"]
    val useLogin: Boolean = savedStateHandle["useLogin"]!!
    val listState = LazyListState()
    val search = Search(listState)
    val playlistPage = _playlistPage.asStateFlow()
    val continuation = _continuation.asStateFlow()
    val songs: StateFlow<List<Song>>
    val hasMore: StateFlow<Boolean>

    init {
        //<editor-fold desc="Fetch more at the end of list">
        viewModelScope.launch { // Interact with UI component [listState], keep it on Main thread
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .filter { info ->
                    info.fastAny { it.contentType === LoadMoreContentType }
                }
                .collectLatest { onGetMore() }
        }
        //</editor-fold>
        //<editor-fold desc="Combine initial song list and its continuation + filter search">
        this.songs = combine( _initialSongs, _continued ) { initial, continued ->
                initial + continued
            }
            .combine( snapshotFlow { search.input } ) { songs, input ->
                songs.fastFilter {
                         !Preferences.PARENTAL_CONTROL.value
                                 || !it.isExplicit
                     }
                     .fastFilter {
                         val query = input.text
                         it.title.contains( query, true )
                                 || it.artistsText?.contains( query, true ) == true
                     }
            }
            .distinctUntilChanged()
            .flowOn( Dispatchers.Default )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )
        //</editor-fold>
        this.hasMore = _continuation.map { it != null }
                                    .stateIn(
                                        scope = viewModelScope,
                                        started = SharingStarted.Lazily,
                                        initialValue = false
                                    )
    }

    @AnyThread
    fun onFetch() = viewModelScope.launch( Dispatchers.IO ) {
        if( !isNetworkConnected(appContext) ) {
            Toaster.noInternet()
            return@launch
        }

        val result = if( useLogin ) {
            YouTube.playlist(playlistBrowseId.removePrefix("VL")).map { page ->
                LoadedPlaylist(
                    header = page.toHeader(),
                    songs = page.songs.fastMap(MetrolistSong::toDatabaseSong),
                    continuation = page.songsContinuation ?: page.continuation,
                    visitorData = null,
                )
            }
        } else {
            Innertube.browsePlaylist(playlistBrowseId, CURRENT_LOCALE, false).map { page ->
                LoadedPlaylist(
                    header = page.toHeader(),
                    songs = page.songs.fastMap { it.toSong },
                    continuation = page.songContinuation,
                    visitorData = page.visitorData,
                )
            }
        }

        result.onSuccess { loaded ->
            anonymousVisitorData = loaded.visitorData
            _playlistPage.value = loaded.header
            _initialSongs.value = loaded.songs
            _continued.value = emptyList()
            _continuation.value = loaded.continuation
        }.onFailure { err ->
            Logger.e( "Failed to load YouTube playlist (useLogin=$useLogin)", err, "YouTubePlaylist" )
            Toaster.e( R.string.error_failed_to_load_playlist )
        }
    }

    @AnyThread
    fun onGetMore() = viewModelScope.launch( Dispatchers.IO ) {
        loadMoreMutex.withLock {
            if( !isNetworkConnected(appContext) ) {
                Toaster.noInternet()
                return@withLock
            }

            val continuation = _continuation.value
            if( continuation == null || (!useLogin && anonymousVisitorData == null) ) {
                Toaster.w( R.string.warning_end_of_list )
                return@withLock
            }

            val result = if( useLogin ) {
                YouTube.playlistContinuation(continuation).map { page ->
                    LoadedContinuation(
                        songs = page.songs.fastMap(MetrolistSong::toDatabaseSong),
                        continuation = page.continuation,
                    )
                }
            } else {
                Innertube.playlistContinued(
                    anonymousVisitorData,
                    continuation,
                    CURRENT_LOCALE,
                    params,
                    false,
                ).map { page ->
                    LoadedContinuation(
                        songs = page.songs.fastMap { it.toSong },
                        continuation = page.continuation,
                    )
                }
            }

            result.onSuccess { continued ->
                _continued.update { it + continued.songs }
                _continuation.value = continued.continuation
            }.onFailure { err ->
                Logger.e( "Failed to load more YouTube playlist songs (useLogin=$useLogin)", err, "YouTubePlaylist" )
                Toaster.e( R.string.error_failed_to_get_playlists_next_songs )
            }
        }
    }

    private data class LoadedPlaylist(
        val header: YouTubePlaylistHeader,
        val songs: List<Song>,
        val continuation: String?,
        val visitorData: String?,
    )

    private data class LoadedContinuation(
        val songs: List<Song>,
        val continuation: String?,
    )
}
