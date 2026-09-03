package me.knighthat.impl

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import app.kreate.android.Preferences
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.isDownloadPending
import app.kreate.android.service.isDownloadRemovable
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.di.DownloadFormatMismatchException
import app.kreate.di.InnertubeDataSourceType
import app.kreate.di.resetLegacyPartialDownload
import co.touchlab.kermit.Logger
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.download
import it.fast4x.rimusic.utils.downloadSyncedLyrics
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.removeDownload
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


@OptIn(UnstableApi::class)
class DownloadHelperImpl(
    private val context: Context,
): DownloadHelper, KoinComponent {

    companion object {

        // Five streams improve bulk throughput when the CDN limits each connection. Going much
        // higher tends to trade speed for throttling, retries, radio use and battery drain.
        private const val NUM_PARALLEL_DOWNLOADS = 5
        private const val NUM_RETRIES = 5
        private const val EXECUTOR_NAME = "DownloadHelper-Executor-Scope"
        // Lyrics are useful offline, but must not compete with a whole batch of audio transfers.
        private const val MAX_PARALLEL_AUXILIARY_REQUESTS = 1
    }

    private val executor = Executors.newCachedThreadPool()
    private val coroutineScope = CoroutineScope(
        executor.asCoroutineDispatcher() +
                SupervisorJob() +
                CoroutineName(EXECUTOR_NAME)
    )
    private val commandMutex = Mutex()
    private val auxiliaryRequestSlots = Semaphore(MAX_PARALLEL_AUXILIARY_REQUESTS)
    private val pendingCommandIds = ConcurrentHashMap.newKeySet<String>()
    private val logger = Logger.withTag("DownloadHelperImpl")
    private val downloadCache: Cache by lazy { get(CacheType.DOWNLOAD) }

    private fun Throwable.hasDownloadFormatMismatch(): Boolean {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = this
        while( current != null && visited.add(current) ) {
            if( current is DownloadFormatMismatchException ) return true
            current = current.cause
        }
        return false
    }

    override val downloads: MutableStateFlow<Map<String, Download>>
    override val downloadManager by lazy {
        val listener = object: DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                pendingCommandIds.remove(download.request.id)

                // The task has fully stopped when this callback arrives, so incompatible spans
                // can now be removed safely. A manual retry then gets a new CacheWriter starting
                // at byte zero instead of repeatedly appending to the old format.
                if( download.state == Download.STATE_FAILED &&
                    finalException?.hasDownloadFormatMismatch() == true
                ) {
                    runCatching { downloadCache.removeResource( download.request.id ) }
                        .onSuccess {
                            logger.w {
                                "Cleared incompatible partial download ${download.request.id}; " +
                                        "the next retry starts cleanly"
                            }
                        }
                        .onFailure {
                            logger.e( "Failed to clear incompatible partial ${download.request.id}", it )
                        }
                }

                syncDownloads(download)
            }

            override fun onDownloadRemoved(
                downloadManager: DownloadManager,
                download: Download
            ) {
                pendingCommandIds.remove(download.request.id)
                downloads.update { it - download.request.id }
            }
        }

        val databaseProvider = StandaloneDatabaseProvider(context)
        val downloadIndex = DefaultDownloadIndex(databaseProvider)

        // DownloadManager starts paused, but initializes its index on another thread immediately.
        // Migrate legacy partials before constructing it so CacheWriter never observes old bytes.
        val migrationCursor = downloadIndex.getDownloads()
        try {
            while( migrationCursor.moveToNext() ) {
                val download = migrationCursor.download
                if( download.state != Download.STATE_COMPLETED &&
                    download.state != Download.STATE_REMOVING
                ) {
                    resetLegacyPartialDownload( downloadCache, download.request.id )
                }
            }
        } finally {
            migrationCursor.close()
        }

        val downloaderFactory = DefaultDownloaderFactory(
            CacheDataSource.Factory()
                .setCache( downloadCache )
                .setUpstreamDataSourceFactory(
                    get<ResolvingDataSource.Factory>( InnertubeDataSourceType.DOWNLOAD )
                ),
            executor,
        )
        val manager = DownloadManager( context, downloadIndex, downloaderFactory )

        manager.maxParallelDownloads = NUM_PARALLEL_DOWNLOADS
        manager.minRetryCount = NUM_RETRIES
        manager.requirements = Requirements(Requirements.NETWORK)
        manager.addListener( listener )

        manager
    }

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper

    init {
        val results = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        try {
            while ( cursor.moveToNext() ) {
                results[cursor.download.request.id] = cursor.download
            }
        } finally {
            cursor.close()
        }
        downloads = MutableStateFlow(results)
    }

    @Synchronized
    private fun syncDownloads( download: Download ) =
        downloads.update { map ->
            map.toMutableMap().apply {
                set(download.request.id, download)
            }
        }

    override fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    override fun getDownloadNotificationHelper(): DownloadNotificationHelper {
        if (!::downloadNotificationHelper.isInitialized) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context, DownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper
    }

    private fun makeDownloadRequest(mediaItem: MediaItem) =
        DownloadRequest
            .Builder(
                /* id      = */ mediaItem.mediaId,
                /* uri     = */ mediaItem.mediaId.toUri()
            )
            .setCustomCacheKey(mediaItem.mediaId)
            .setData("${mediaItem.mediaMetadata.artist.toString()} - ${mediaItem.mediaMetadata.title.toString()}".encodeToByteArray()) // Title in notification
            .build()

    private fun canAdd(mediaItem: MediaItem): Boolean {
        if( mediaItem.isLocal ) return false

        val currentState = downloads.value[mediaItem.mediaId]?.state
        if( currentState == Download.STATE_COMPLETED ||
            currentState == Download.STATE_REMOVING ||
            currentState?.isDownloadPending() == true
        ) return false

        // Covers the short interval between sending the service command and receiving the first
        // DownloadManager callback. DownloadService itself remains the source of truth.
        return pendingCommandIds.add(mediaItem.mediaId)
    }

    private fun fetchAuxiliaryAssets(mediaItem: MediaItem) = coroutineScope.launch {
        auxiliaryRequestSlots.withPermit {
            downloadSyncedLyrics( mediaItem.asSong )

            val imageUrl = mediaItem.mediaMetadata.artworkUri.thumbnail(1200)
            ImageFactory.requestBuilder( imageUrl.toString() ) {
                bitmapConfig( Bitmap.Config.ARGB_8888 )
                allowHardware( false )
            }
        }
    }

    private fun enqueue(mediaItems: List<MediaItem>) {
        val accepted = mediaItems
            .distinctBy(MediaItem::mediaId)
            .filter(::canAdd)
        if( accepted.isEmpty() ) return

        accepted.forEach { mediaItem ->
            // Also covers orphaned partial cache data whose old index row no longer exists.
            resetLegacyPartialDownload( downloadCache, mediaItem.mediaId )

            Database.asyncTransaction {
                insertIgnore( mediaItem )
            }
        }

        coroutineScope.launch {
            commandMutex.withLock {
                accepted.forEach { mediaItem ->
                    val result = context.download<MyDownloadService>(makeDownloadRequest(mediaItem))

                    result.exceptionOrNull()?.let {
                        pendingCommandIds.remove(mediaItem.mediaId)
                        if (it is CancellationException) throw it
                        Logger.e( it, "DownloadHelperImpl" ) {
                            "addDownload failed for ${mediaItem.mediaId}"
                        }
                    } ?: fetchAuxiliaryAssets(mediaItem)
                }
            }
        }
    }

    override fun addDownload( mediaItem: MediaItem ) {
        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            return
        }

        enqueue(listOf(mediaItem))
    }

    override fun addDownloads(mediaItems: List<MediaItem>) {
        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            return
        }

        enqueue(mediaItems)
    }

    override fun removeDownload( mediaItem: MediaItem ) {
        if (mediaItem.isLocal) return

        //sendRemoveDownload(context,MyDownloadService::class.java,mediaItem.mediaId,false)
        coroutineScope.launch {
            context.removeDownload<MyDownloadService>(mediaItem.mediaId).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                Logger.e( it, "DownloadHelperImpl" ) { "removeDownload failed!"}
            }
        }
    }

    override fun autoDownload( mediaItem: MediaItem ) {
        if ( Preferences.AUTO_DOWNLOAD.value ) {
            if (downloads.value[mediaItem.mediaId]?.state != Download.STATE_COMPLETED)
                addDownload(mediaItem)
        }
    }

    override fun autoDownloadWhenLiked( mediaItem: MediaItem ) {
        if ( Preferences.AUTO_DOWNLOAD_ON_LIKE.value ) {
            Database.asyncQuery {
                runBlocking {
                    if( songTable.isLiked( mediaItem.mediaId ).first() )
                        autoDownload(mediaItem)
                    else
                        removeDownload(mediaItem)
                }
            }
        }
    }

    override fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean? ) {
        // Only continues when this setting is enabled
        val isSettingEnabled by Preferences.AUTO_DOWNLOAD_ON_LIKE
        if( !isSettingEnabled || !isNetworkConnected( context ) )
            return

        // [likeState] is a tri-state value,
        // only `true` represents like, so
        // `true` must be value set to download
        if( likeState == true )
            autoDownload( mediaItem)
        else
            removeDownload( mediaItem)
    }

    override fun handleDownload( song: Song, removeIfDownloaded: Boolean ) {
        if( song.isLocal ) return

        val state = downloads.value[song.id]?.state

        if( removeIfDownloaded && state?.isDownloadRemovable() == true )
            removeDownload( song.asMediaItem )
        else if( state != Download.STATE_COMPLETED && state?.isDownloadPending() != true )
            addDownload( song.asMediaItem )
    }
}
