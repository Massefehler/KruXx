package it.fast4x.rimusic.service.modern

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.car.CarArtwork
import app.kreate.android.service.car.CarArtworkCache
import app.kreate.android.service.player.ExoPlayerListener
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.PersistentQueue
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.util.cleanPrefix
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.enums.AlbumsType
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.MediaSessionConstants.ID_CACHED
import it.fast4x.rimusic.service.modern.MediaSessionConstants.ID_DOWNLOADED
import it.fast4x.rimusic.service.modern.MediaSessionConstants.ID_FAVORITES
import it.fast4x.rimusic.service.modern.MediaSessionConstants.ID_ONDEVICE
import it.fast4x.rimusic.service.modern.MediaSessionConstants.ID_TOP
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

@UnstableApi
class MediaLibrarySessionCallback(
    val context: Context,
    val database: Database,
    val downloadHelper: MyDownloadHelper
) : MediaLibrarySession.Callback, KoinComponent {

    private val cache: Cache by inject(CacheType.CACHE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val searchCacheLock = Any()
    private val searchResults = object : LinkedHashMap<String, List<Song>>(
        MAX_CACHED_SEARCHES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<Song>>?,
        ): Boolean = size > MAX_CACHED_SEARCHES
    }

    @Volatile
    private var latestSearchKey: String? = null

    lateinit var listener: ExoPlayerListener

    fun toggleLike(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val statefulPlayer: StatefulPlayer by inject()
        Database.asyncTransaction {
            songTable.rotateLikeState(mediaItem.mediaId).also {
                listener.updateMediaControl(context, statefulPlayer)
            }
        }

        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
    }

    fun onSearch() {
        val intent = Intent(context.applicationContext, MainActivity::class.java)
            .setAction(MainActivity.action_search)
            .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    fun release() = scope.cancel()

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
            .add(MediaSessionConstants.CommandToggleDownload)
            .add(MediaSessionConstants.CommandToggleLike)
            .add(MediaSessionConstants.CommandToggleShuffle)
            .add(MediaSessionConstants.CommandToggleRepeatMode)
            .add(MediaSessionConstants.CommandStartRadio)

        if (session.isCarController(controller)) {
            // Search opens KruXx on the phone, so cars don't get it. Drop its button from the
            // shared platform controls as long as a car is connected. The update runs after
            // this controller has been registered.
            updateMediaControl(session)
        } else {
            sessionCommands.add(MediaSessionConstants.CommandSearch)
        }

        return MediaSession.ConnectionResult.accept(
            sessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (session.isCarController(controller)) updateMediaControl(session)
    }

    private fun updateMediaControl(session: MediaSession) {
        if (::listener.isInitialized) listener.updateMediaControl(context, session.player)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = scope.future(Dispatchers.IO) {
        val resultCount = searchSongs(query).size
        withContext(Dispatchers.Main.immediate) {
            session.notifySearchResultChanged(browser, query, resultCount, params)
        }
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        if (!isValidMediaLibraryPage(page, pageSize)) {
            return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        val searchParentId = MediaLibraryId.searchParent(query)
        val resultItems = searchSongs(query)
            .map { it.toMediaItem(searchParentId) }
            .mediaLibraryPage(page, pageSize)
        prefetchArtwork(resultItems)
        LibraryResult.ofItemList(resultItems, params)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val player = session.player as StatefulPlayer
        when (customCommand.customAction) {
            MediaSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike(player)
            MediaSessionConstants.ACTION_TOGGLE_DOWNLOAD -> player.downloadCurrentMediaItem()
            MediaSessionConstants.ACTION_TOGGLE_SHUFFLE -> player.toggleShuffleMode()
            MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> player.cycleRepeatMode()
            MediaSessionConstants.ACTION_START_RADIO -> player.startRadio()
            MediaSessionConstants.ACTION_SEARCH -> onSearch()
            else -> return Futures.immediateFuture(
                SessionResult(SessionError.ERROR_NOT_SUPPORTED)
            )
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    @OptIn(UnstableApi::class)
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            // Android Auto asks for a separate suggested root to fill its media recommendations.
            if (params?.isSuggested == true) suggestedRootMediaItem() else libraryRootMediaItem(),
            libraryRootParams(params)
        )
    )

    @OptIn(UnstableApi::class)
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        if (!isValidMediaLibraryPage(page, pageSize)) {
            return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
        val children = loadChildren(parentId)
            ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val pageItems = children.mediaLibraryPage(page, pageSize)
        prefetchArtwork(pageItems)
        LibraryResult.ofItemList(pageItems, params)
    }

    @OptIn(UnstableApi::class)
    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
        loadLibraryItem(mediaId)?.let {
            LibraryResult.ofItem(it, null)
        } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = scope.future(Dispatchers.IO) {
        val resolvedItems = mutableListOf<MediaItem>()
        for (mediaItem in mediaItems) {
            resolvedItems += resolveRequestedItems(mediaItem)
        }
        resolvedItems
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future(Dispatchers.IO) {
        val firstItem = mediaItems.firstOrNull()
            ?: return@future emptyMediaItemsWithStartPosition()

        if (firstItem.isPlayFromSearchRequest()) {
            return@future voiceSearchQueue(
                mediaSession,
                VoiceSearchRequest.from(
                    firstItem.requestMetadata.searchQuery,
                    firstItem.requestMetadata.extras,
                ),
            )
        }

        if (mediaItems.size == 1) {
            MediaLibraryId.selection(firstItem.mediaId)?.let { selection ->
                val songs = loadSongsForParent(selection.parentId).orEmpty()
                val selectedIndex = songs.indexOfFirst { it.id == selection.songId }
                if (selectedIndex < 0) {
                    val fallbackItem = resolveStoredOrRequestedItem(firstItem, selection)
                        ?: return@future emptyMediaItemsWithStartPosition()
                    return@future MediaSession.MediaItemsWithStartPosition(
                        listOf(fallbackItem),
                        0,
                        startPositionMs,
                    )
                }

                return@future MediaSession.MediaItemsWithStartPosition(
                    songs.map(Song::asMediaItem),
                    selectedIndex,
                    startPositionMs,
                )
            }

            val resolvedItems = resolveRequestedItems(firstItem)
            return@future MediaSession.MediaItemsWithStartPosition(
                resolvedItems,
                0,
                startPositionMs,
            )
        }

        val resolvedItems = mutableListOf<MediaItem>()
        for (mediaItem in mediaItems) {
            resolvedItems += resolveRequestedItems(mediaItem)
        }
        MediaSession.MediaItemsWithStartPosition(
            resolvedItems,
            startIndex.coerceIn(0, resolvedItems.lastIndex.coerceAtLeast(0)),
            startPositionMs,
        )
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future(Dispatchers.IO) {
        if (!Preferences.ENABLE_PERSISTENT_QUEUE.value) {
            return@future emptyMediaItemsWithStartPosition()
        }

        val queue = database.queueTable.blockingItems()
        if (queue.isEmpty()) return@future emptyMediaItemsWithStartPosition()

        val startIndex = queue.indexOfFirst { it.position != null }.coerceAtLeast(0)
        val startPositionMs = queue[startIndex].position ?: C.TIME_UNSET
        val mediaItems = queue.map {
            it.song.asMediaItem.buildUpon().setTag(PersistentQueue.Tag).build()
        }

        if (!isForPlayback) {
            return@future MediaSession.MediaItemsWithStartPosition(
                listOf(mediaItems[startIndex].withResumptionMetadata(startPositionMs)),
                0,
                C.TIME_UNSET,
            )
        }

        MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
    }

    /**
     * Gemini, Google Assistant and the car's voice button. Library matches come first, since
     * "my playlist" or a library artist is what the user means; anything else goes through the
     * same search as the car's search field.
     */
    private suspend fun voiceSearchQueue(
        session: MediaSession,
        request: VoiceSearchRequest,
    ): MediaSession.MediaItemsWithStartPosition {
        // "Play music on KruXx" continues the current queue, as Google's guidance asks.
        if (request == VoiceSearchRequest.Anything) currentQueue(session)?.let { return it }

        val songs = when (request) {
            VoiceSearchRequest.Anything ->
                topSongs().ifEmpty { database.songTable.allFavorites().first().reversed() }
            is VoiceSearchRequest.Playlist ->
                playlistSongsNamed(request.name).ifEmpty { searchSongs(request.query.ifEmpty { request.name }) }
            is VoiceSearchRequest.Artist ->
                artistSongsNamed(request.name).ifEmpty { searchSongs(request.query.ifEmpty { request.name }) }
            is VoiceSearchRequest.Album ->
                albumSongsNamed(request.title, request.artist).ifEmpty {
                    searchSongs(request.query.ifEmpty { request.title })
                }
            is VoiceSearchRequest.Text -> searchSongs(request.query)
        }
        if (songs.isEmpty()) {
            reportNothingToPlay(session)
            // A failed request leaves the current queue alone.
            throw NoSuchElementException("Nothing to play for $request")
        }

        return MediaSession.MediaItemsWithStartPosition(songs.map(Song::asMediaItem), 0, C.TIME_UNSET)
    }

    private suspend fun currentQueue(session: MediaSession) = withContext(Dispatchers.Main) {
        val player = session.player
        if (player.mediaItemCount == 0) return@withContext null
        MediaSession.MediaItemsWithStartPosition(
            List(player.mediaItemCount, player::getMediaItemAt),
            player.currentMediaItemIndex,
            player.currentPosition,
        )
    }

    private suspend fun playlistSongsNamed(name: String): List<Song> {
        val key = name.voiceKey()
        if (key == context.getString(R.string.favorites).voiceKey())
            return database.songTable.allFavorites().first().reversed()

        val playlists = database.playlistTable.sortPreviewsBySongCount().first()
        val playlist = playlists.firstOrNull { it.playlist.name.voiceKey() == key }
            ?: playlists.firstOrNull { key in it.playlist.name.voiceKey() }
            ?: return emptyList()
        return database.songPlaylistMapTable.allSongsOf(playlist.playlist.id).first()
    }

    private suspend fun artistSongsNamed(name: String): List<Song> {
        val key = name.voiceKey()
        val artist = database.artistTable.blockingAll()
            .filter { it.name?.voiceKey() == key }
            // Prefer the followed entry when several artists share the name.
            .maxByOrNull { it.bookmarkedAt ?: 0L }
            ?: return emptyList()
        return database.songArtistMapTable.allSongsBy(artist.id).first()
    }

    private suspend fun albumSongsNamed(title: String, artist: String?): List<Song> {
        val key = title.voiceKey()
        val artistKey = artist?.voiceKey()
        val album = database.albumTable.blockingAll()
            .filter { it.title?.voiceKey() == key }
            .let { albums ->
                albums.firstOrNull { artistKey != null && it.authorsText?.voiceKey()?.contains(artistKey) == true }
                    ?: albums.firstOrNull()
            }
            ?: return emptyList()
        return database.songAlbumMapTable.allSongsOf(album.id).first()
    }

    /**
     * Without results, Google's assistant guidance asks to stop playback and to report the
     * error in the playback state, where Android Auto shows it and the assistant reads it out.
     */
    private suspend fun reportNothingToPlay(session: MediaSession) = withContext(Dispatchers.Main) {
        session.player.pause()
        session.mediaNotificationControllerInfo?.let { notificationController ->
            session.sendError(
                notificationController,
                SessionError(SessionError.ERROR_UNKNOWN, context.getString(R.string.car_voice_nothing_found)),
            )
        }
    }

    private fun libraryRootParams(requested: MediaLibraryService.LibraryParams?) =
        MediaLibraryService.LibraryParams.Builder()
            .setRecent(requested?.isRecent == true)
            .setOffline(requested?.isOffline == true)
            .setSuggested(requested?.isSuggested == true)
            .setExtras(
                Bundle().apply {
                    // Albums and artists as cover grids, songs as lists
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM,
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                    )
                }
            )
            .build()

    private fun libraryRootMediaItem() = MediaItem.Builder()
        .setMediaId(PlayerServiceModern.ROOT)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun suggestedRootMediaItem() = MediaItem.Builder()
        .setMediaId(PlayerServiceModern.SUGGESTED)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun rootItems() = listOf(
        browsableMediaItem(
            PlayerServiceModern.SONG,
            context.getString(R.string.songs),
            null,
            drawableUri(R.drawable.musical_notes),
            MediaMetadata.MEDIA_TYPE_PLAYLIST,
        ),
        browsableMediaItem(
            PlayerServiceModern.ARTIST,
            context.getString(R.string.artists),
            null,
            drawableUri(R.drawable.people),
            MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
        ),
        browsableMediaItem(
            PlayerServiceModern.ALBUM,
            context.getString(R.string.albums),
            null,
            drawableUri(R.drawable.album),
            MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
        ),
        browsableMediaItem(
            PlayerServiceModern.PLAYLIST,
            context.getString(R.string.playlists),
            null,
            drawableUri(R.drawable.library),
            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
            // Playlists only have small icons, which category lists show with proper margins.
            Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
                )
            },
        ),
    )

    private suspend fun loadChildren(parentId: String): List<MediaItem>? = when (parentId) {
        PlayerServiceModern.ROOT -> rootItems()
        // Artists and albums as in the phone's library, with its filter and sort order
        PlayerServiceModern.ARTIST -> libraryArtists().map { artist ->
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.ARTIST, artist.id),
                artist.name ?: "",
                null,
                CarArtwork.uri(context, artist.thumbnailUrl),
                MediaMetadata.MEDIA_TYPE_ARTIST,
            )
        }
        PlayerServiceModern.ALBUM -> libraryAlbums().map { album ->
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.ALBUM, album.id),
                album.title ?: "",
                album.authorsText,
                CarArtwork.uri(context, album.cleanThumbnailUrl()),
                MediaMetadata.MEDIA_TYPE_ALBUM,
            )
        }
        PlayerServiceModern.PLAYLIST -> playlistItems()
        else -> loadSongsForParent(parentId)?.map { it.toMediaItem(parentId) }
    }

    private suspend fun libraryArtists() = when (Preferences.HOME_ARTIST_TYPE.value) {
        ArtistsType.Favorites -> database.artistTable.sortFollowing(
            Preferences.HOME_ARTISTS_SORT_BY.value,
            Preferences.HOME_ARTISTS_SORT_ORDER.value,
        )
        ArtistsType.Library -> database.artistTable.sortInLibrary(
            Preferences.HOME_ARTISTS_SORT_BY.value,
            Preferences.HOME_ARTISTS_SORT_ORDER.value,
        )
    }.first()

    private suspend fun libraryAlbums() = when (Preferences.HOME_ALBUM_TYPE.value) {
        AlbumsType.Favorites -> database.albumTable.sortBookmarked(
            Preferences.HOME_ALBUMS_SORT_BY.value,
            Preferences.HOME_ALBUM_SORT_ORDER.value,
        )
        AlbumsType.Library -> database.albumTable.sortInLibrary(
            Preferences.HOME_ALBUMS_SORT_BY.value,
            Preferences.HOME_ALBUM_SORT_ORDER.value,
        )
    }.first()

    private suspend fun playlistItems(): List<MediaItem> {
        val likedSongCount = database.songTable.allFavorites().first().size
        val cachedSongCount = getCountCachedSongs().first()
        val downloadedSongCount = downloadedSongs().size
        val onDeviceSongCount = database.songTable.allOnDevice().first().size
        val playlists = database.playlistTable.sortPreviewsBySongCount().first()

        return listOf(
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.PLAYLIST, ID_FAVORITES),
                context.getString(R.string.favorites),
                likedSongCount.toString(),
                drawableUri(R.drawable.heart),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ),
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.PLAYLIST, ID_CACHED),
                context.getString(R.string.cached),
                cachedSongCount.toString(),
                drawableUri(R.drawable.download),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ),
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.PLAYLIST, ID_DOWNLOADED),
                context.getString(R.string.downloaded),
                downloadedSongCount.toString(),
                drawableUri(R.drawable.downloaded),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ),
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.PLAYLIST, ID_TOP),
                context.getString(R.string.playlist_top),
                Preferences.MAX_NUMBER_OF_TOP_PLAYED.value.name,
                drawableUri(R.drawable.trending),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ),
            browsableMediaItem(
                MediaLibraryId.container(PlayerServiceModern.PLAYLIST, ID_ONDEVICE),
                context.getString(R.string.on_device),
                onDeviceSongCount.toString(),
                drawableUri(R.drawable.devices),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            ),
        ) + playlists.map { playlist ->
            browsableMediaItem(
                MediaLibraryId.container(
                    PlayerServiceModern.PLAYLIST,
                    playlist.playlist.id.toString(),
                ),
                playlist.playlist.name,
                playlist.songCount.toString(),
                drawableUri(R.drawable.playlist),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            )
        }
    }

    private suspend fun loadSongsForParent(parentId: String): List<Song>? {
        if (parentId == PlayerServiceModern.SONG) return topSongs()
        if (parentId == PlayerServiceModern.SUGGESTED) return topSongs().take(SUGGESTED_SONGS)

        MediaLibraryId.containerValue(parentId, PlayerServiceModern.ARTIST)?.let { artistId ->
            return database.songArtistMapTable.allSongsBy(artistId).first()
        }
        MediaLibraryId.containerValue(parentId, PlayerServiceModern.ALBUM)?.let { albumId ->
            return database.songAlbumMapTable.allSongsOf(albumId).first()
        }
        MediaLibraryId.containerValue(parentId, PlayerServiceModern.PLAYLIST)?.let { playlistId ->
            return when (playlistId) {
                ID_FAVORITES -> database.songTable.allFavorites().first().reversed()
                ID_CACHED -> database.formatTable.allWithSongs().first()
                    .filter { formatWithSong ->
                        val contentLength = formatWithSong.format.contentLength
                        contentLength != null && cache.isCached(
                            formatWithSong.song.id,
                            0L,
                            contentLength,
                        )
                    }
                    .map(FormatWithSong::song)
                    .reversed()
                ID_TOP -> database.eventTable.findSongsMostPlayedBetween(
                    from = 0,
                    limit = Preferences.MAX_NUMBER_OF_TOP_PLAYED.value.toInt(),
                ).first()
                ID_ONDEVICE -> database.songTable.allOnDevice().first()
                ID_DOWNLOADED -> downloadedSongs()
                else -> playlistId.toLongOrNull()?.let {
                    database.songPlaylistMapTable.allSongsOf(it).first()
                } ?: return null
            }
        }
        MediaLibraryId.searchQuery(parentId)?.let { query ->
            return searchSongs(query)
        }
        if (parentId == PlayerServiceModern.SEARCHED) return latestSearchSongs().orEmpty()

        return null
    }

    private suspend fun topSongs(): List<Song> = database.eventTable
        .findSongsMostPlayedBetween(StatisticsType.OneMonth.timeStampInMillis())
        .first()
        .ifEmpty {
            database.eventTable.findSongsMostPlayedBetween(0L).first()
        }

    private suspend fun searchSongs(query: String): List<Song> {
        val searchKey = query.trim().lowercase(Locale.ROOT)
        cachedSearchSongs(searchKey)?.let { return it }

        val songs = if (searchKey.isEmpty()) {
            topSongs()
        } else {
            coroutineScope {
                val localSongs = async(Dispatchers.IO) {
                    try {
                        database.songTable.findAllTitleArtistContains(query.trim()).first()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val onlineSongs = async(Dispatchers.IO) {
                    try {
                        Innertube.searchPage(
                            body = SearchBody(
                                query = query.trim(),
                                params = Innertube.SearchFilter.Song.value,
                            ),
                            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from,
                        )?.map { page ->
                            page?.items?.map { it.asSong }
                        }?.getOrNull().orEmpty()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                (localSongs.await() + onlineSongs.await()).distinctBy(Song::id)
            }
        }

        synchronized(searchCacheLock) {
            searchResults[searchKey] = songs
            latestSearchKey = searchKey
        }
        return songs
    }

    private fun cachedSearchSongs(searchKey: String): List<Song>? = synchronized(searchCacheLock) {
        searchResults[searchKey]?.also { latestSearchKey = searchKey }
    }

    private fun latestSearchSongs(): List<Song>? = synchronized(searchCacheLock) {
        latestSearchKey?.let(searchResults::get)
    }

    private suspend fun loadLibraryItem(mediaId: String): MediaItem? {
        if (mediaId == PlayerServiceModern.ROOT) return libraryRootMediaItem()
        if (mediaId == PlayerServiceModern.SUGGESTED) return suggestedRootMediaItem()
        if (mediaId in setOf(
                PlayerServiceModern.SONG,
                PlayerServiceModern.ARTIST,
                PlayerServiceModern.ALBUM,
                PlayerServiceModern.PLAYLIST,
            )
        ) {
            return rootItems().firstOrNull { it.mediaId == mediaId }
        }

        MediaLibraryId.selection(mediaId)?.let { selection ->
            val selectedSong = loadSongsForParent(selection.parentId)
                ?.firstOrNull { it.id == selection.songId }
                ?: database.songTable.findById(selection.songId).first()
            return selectedSong
                ?.toMediaItem(selection.parentId)
        }

        MediaLibraryId.parentCategory(mediaId)?.let { categoryId ->
            return loadChildren(categoryId)?.firstOrNull { it.mediaId == mediaId }
        }

        return database.songTable.findById(mediaId).first()?.asMediaItem
    }

    private suspend fun resolveRequestedItems(mediaItem: MediaItem): List<MediaItem> {
        mediaItem.requestMetadata.searchQuery?.let { query ->
            return searchSongs(query).map(Song::asMediaItem)
        }

        MediaLibraryId.selection(mediaItem.mediaId)?.let { selection ->
            return resolveSelectedItem(mediaItem, selection)?.let(::listOf).orEmpty()
        }

        if (mediaItem.localConfiguration != null) return listOf(mediaItem)

        loadSongsForParent(mediaItem.mediaId)?.let { songs ->
            return songs.map(Song::asMediaItem)
        }

        return database.songTable.findById(mediaItem.mediaId).first()
            ?.let { listOf(it.asMediaItem) }
            .orEmpty()
    }

    private suspend fun resolveSelectedItem(
        requestedItem: MediaItem,
        selection: MediaLibrarySelection,
    ): MediaItem? {
        val selectedSong = loadSongsForParent(selection.parentId)
            ?.firstOrNull { it.id == selection.songId }
        if (selectedSong != null) return selectedSong.asMediaItem

        return resolveStoredOrRequestedItem(requestedItem, selection)
    }

    private suspend fun resolveStoredOrRequestedItem(
        requestedItem: MediaItem,
        selection: MediaLibrarySelection,
    ): MediaItem? {
        database.songTable.findById(selection.songId).first()?.let {
            return it.asMediaItem
        }

        return requestedItem
            .takeIf { it.localConfiguration != null }
            ?.buildUpon()
            ?.setMediaId(selection.songId)
            ?.build()
    }

    private suspend fun downloadedSongs(): List<Song> {
        val downloads = downloadHelper.instance.downloads.value
        return database.songTable.all(excludeHidden = true).first()
            .filter { downloads[it.id]?.state == Download.STATE_COMPLETED }
            .sortedByDescending { downloads[it.id]?.updateTimeMs ?: 0L }
    }

    private fun emptyMediaItemsWithStartPosition() = MediaSession.MediaItemsWithStartPosition(
        emptyList(),
        0,
        C.TIME_UNSET,
    )

    private fun MediaItem.withResumptionMetadata(positionMs: Long): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY)
        val durationMs = mediaMetadata.durationMs ?: C.TIME_UNSET
        val completionStatus = when {
            positionMs <= 0L -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
            durationMs > 0L && positionMs >= durationMs ->
                MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
            else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
        }
        extras.putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, completionStatus)
        if (positionMs >= 0L && durationMs > 0L) {
            extras.putDouble(
                MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0),
            )
        }

        return buildUpon()
            .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    private fun drawableUri(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()

    private fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC,
        extras: Bundle? = null,
    ) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanPrefix(title))
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(iconUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build()
            )
            .build()

    /** A song as browse or search result, with artwork and badges the car can show. */
    private fun Song.toMediaItem(path: String) =
        with(this.asMediaItem) {
            val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
                if (isExplicit)
                    putLong(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT)
                carDownloadStatus()?.let { putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, it) }
            }
            buildUpon()
                .setMediaId(MediaLibraryId.playable(path, id))
                .setMediaMetadata(
                    mediaMetadata.buildUpon()
                        .setArtworkUri(CarArtwork.uri(context, cleanThumbnailUrl()))
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

    @OptIn(UnstableApi::class)
    private fun Song.carDownloadStatus(): Long? {
        if (isLocal) return MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
        return when (downloadHelper.instance.downloads.value[id]?.state) {
            Download.STATE_COMPLETED -> MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_RESTARTING -> MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADING
            else -> null
        }
    }

    private fun prefetchArtwork(items: List<MediaItem>) =
        CarArtworkCache.prefetch(context, items.mapNotNull { it.mediaMetadata.artworkUri })

    /** Play-from-search requests carry a query, or nothing at all for "Play music on KruXx". */
    private fun MediaItem.isPlayFromSearchRequest(): Boolean =
        requestMetadata.searchQuery != null ||
            (mediaId.isEmpty() && localConfiguration == null && requestMetadata.mediaUri == null)

    private fun getCountCachedSongs() =
        database.formatTable
                .allWithSongs()
                .map { list ->
                    list.filter {
                            val contentLength = it.format.contentLength
                            contentLength != null && cache.isCached( it.song.id, 0L, contentLength )
                        }
                        .size
                }

    private companion object {
        const val MAX_CACHED_SEARCHES = 8

        /** Google asks for about ten recommendations. */
        const val SUGGESTED_SONGS = 10
    }
}

/** Android Auto on the phone or the media center of Android Automotive OS. */
@OptIn(UnstableApi::class)
fun MediaSession.isCarController(controller: MediaSession.ControllerInfo): Boolean =
    isAutomotiveController(controller) || isAutoCompanionController(controller)



object MediaSessionConstants {
    const val ID_FAVORITES = "FAVORITES"
    const val ID_CACHED = "CACHED"
    const val ID_DOWNLOADED = "DOWNLOADED"
    const val ID_TOP = "TOP"
    const val ID_ONDEVICE = "ONDEVICE"
    const val ACTION_TOGGLE_DOWNLOAD = "TOGGLE_DOWNLOAD"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_START_RADIO = "START_RADIO"
    const val ACTION_SEARCH = "ACTION_SEARCH"
    val CommandToggleDownload = SessionCommand(ACTION_TOGGLE_DOWNLOAD, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandStartRadio = SessionCommand(ACTION_START_RADIO, Bundle.EMPTY)
    val CommandSearch = SessionCommand(ACTION_SEARCH, Bundle.EMPTY)
}
