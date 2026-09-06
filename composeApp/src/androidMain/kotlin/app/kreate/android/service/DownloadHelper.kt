package app.kreate.android.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import app.kreate.database.models.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@UnstableApi
interface DownloadHelper {

    companion object {

        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }

    val downloadManager: DownloadManager
    val downloads: MutableStateFlow<Map<String, Download>>

    fun getDownload( songId: String ): Flow<Download?>

    fun getDownloadNotificationHelper(): DownloadNotificationHelper

    fun addDownload( mediaItem: MediaItem )

    /**
     * Adds a complete list through one ordered service-command queue.
     *
     * This avoids starting one unrelated coroutine per item in bulk-download actions and lets
     * Media3 apply [DownloadManager.maxParallelDownloads] to the accepted queue.
     */
    fun addDownloads( mediaItems: List<MediaItem> )

    /** Enqueue after a storage decision, or from an automatic background task. Never opens UI. */
    fun addDownloadsInternal(mediaItems: List<MediaItem>)

    fun removeDownload( mediaItem: MediaItem )

    fun autoDownload( mediaItem: MediaItem )

    fun autoDownloadWhenLiked( mediaItem: MediaItem )

    fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean? )

    fun handleDownload( song: Song, removeIfDownloaded: Boolean = false )
}
