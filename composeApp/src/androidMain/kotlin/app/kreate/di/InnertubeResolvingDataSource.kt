@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpUtil
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.player.isRejectedStreamHttpStatus
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.Format
import co.touchlab.kermit.Logger
import com.metrolist.music.utils.InnerTubeXPlayer
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.innertube.Innertube
import me.knighthat.utils.Toaster
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


/** Cache metadata key: itag of the bytes stored under a song's cache key. */
private const val METADATA_KEY_ITAG = "kruxx_itag"

/** Marks partial downloads created by the dedicated highest-quality resolver. */
private const val METADATA_KEY_DOWNLOAD_POLICY = "kruxx_download_policy"
private const val DOWNLOAD_POLICY_HIGH_QUALITY = 1L

/** A private key used only to bypass incompatible entries in the read-only player cache. */
private const val DOWNLOAD_NETWORK_CACHE_KEY_PREFIX = "kruxx_download_network"

/**
 * Thrown by the resolver when the player cache holds bytes of a different itag than the
 * stream that was just resolved. The cached spans have already been dropped; the current
 * load must be abandoned because ExoPlayer may have consumed stale bytes and CacheDataSource
 * sized this request from the stale content length. [app.kreate.android.service.player.ExoPlayerListener]
 * re-prepares the player at the same position, which starts from a clean cache entry.
 */
class CachedFormatMismatchException( videoId: String, cachedItag: Long, itag: Int ) :
    RuntimeException( "Cached bytes of $videoId are itag $cachedItag, resolved stream is itag $itag" )

/**
 * Stops a download before Media3 could append a newly resolved format to incompatible bytes.
 * The exception deliberately is not an [IOException]: Media3 must not retry the same CacheWriter
 * whose next position was calculated from the old cache layout. The listener clears the old spans
 * only after that writer has stopped.
 */
internal class DownloadFormatMismatchException( videoId: String ) :
    RuntimeException( "Partial download of $videoId belongs to another audio format" )

/**
 * Remember the last format written to Room. Purpose-separated stream resolution can legitimately
 * resolve the same song twice with different itags (playback Low/Auto, download High), so the id
 * alone is not enough for deduplication.
 */
private data class StoredFormatKey(
    val videoId: String,
    val itag: Int,
    val contentLength: Long?,
)

private val lastStoredFormat = AtomicReference<StoredFormatKey?>( null )

private enum class StreamPurpose( val logLabel: String ) {
    PLAYBACK( "Playback" ),
    DOWNLOAD( "Download" ),
}

/** Every input that can alter Auto/High/Low stream selection belongs to the cache identity. */
private data class PlaybackStreamVariant(
    val audioQuality: AudioQualityFormat,
    val isConnectionMetered: Boolean,
    val saveDataOnMeteredConnections: Boolean,
)

private data class StreamCacheKey(
    val videoId: String,
    val purpose: StreamPurpose,
    val playbackVariant: PlaybackStreamVariant? = null,
)

/**
 * Resolved streams are separated by purpose. A download must never reuse a Low/Auto playback URL,
 * because offline files are always requested in the highest audio quality available.
 */
private val streamCache = ConcurrentHashMap<StreamCacheKey, InnerTubeXPlayer.PlaybackData>()
private val logger = Logger.withTag("dataspec")

/**
 * Acts as a lock to keep [upsertSongFormat] from starting before
 * [upsertSongInfo] finishes.
 */
private var databaseWorker: Job = Job()

//<editor-fold desc="Database handlers">
/**
 * Reach out to [me.knighthat.innertube.Endpoints.NEXT] endpoint for song's information.
 *
 * Info includes:
 * - Titles
 * - Artist(s)
 * - Album
 * - Thumbnails
 * - Duration
 *
 * ### If song IS already inside database
 *
 * It'll replace unmodified columns with fetched data
 *
 * ### If song IS NOT already inside database
 *
 * New record will be created and insert into database
 *
 */
private fun upsertSongInfo( context: Context, videoId: String ) {       // Use this to prevent suspension of thread while waiting for response from YT
    // Skip adding if it's just added in previous call
    if( lastStoredFormat.get()?.videoId == videoId || !isNetworkAvailable( context ) )
        return

    logger.v { "fetching and upserting $videoId's information to the database" }

    databaseWorker = CoroutineScope(Dispatchers.IO ).launch {
        Innertube.songBasicInfo( videoId, CURRENT_LOCALE )
            .onSuccess{
                logger.v { "$videoId's information successfully found and parsed" }

                Database.upsert( it )

                logger.d { "$videoId's information successfully upserted to the database" }
            }
            .onFailure {
                logger.e( "failed to upsert $videoId's information to database", it )
                Toaster.e( R.string.error_failed_to_fetch_songs_info )
            }
    }

    // The stored-format marker is updated only after the format transaction succeeds.
}

/**
 * Persist the format that is being played (itag, mime type, bitrate, size, loudness)
 * so "Stats for nerds" and volume normalization have data to work with.
 */
private fun upsertSongFormat( videoId: String, data: InnerTubeXPlayer.PlaybackData ) {
    val formatKey = StoredFormatKey( videoId, data.itag, data.contentLength )
    if( formatKey == lastStoredFormat.get() ) return

    logger.v { "upserting format ${data.itag} of song $videoId to the database" }

    CoroutineScope(Dispatchers.IO ).launch {
        // Wait until this job is finish to make sure song's info
        // is in the database before continuing
        databaseWorker.join()

        Database.asyncTransaction {
            formatTable.upsert(
                Format(
                    videoId,
                    data.itag,
                    data.mimeType,
                    data.bitrate,
                    data.contentLength,
                    System.currentTimeMillis(),
                    data.loudnessDb
                )
            )

            logger.d { "$videoId is successfully upserted to the database" }

            // Format must be added successfully before marking it as stored.
            lastStoredFormat.set( formatKey )
        }
    }
}
//</editor-fold>
//<editor-fold desc="Get response">
private fun getPlayableStream(
    songId: String,
    purpose: StreamPurpose,
): InnerTubeXPlayer.PlaybackData {
    logger.v { "Processing $songId for ${purpose.logLabel.lowercase()}" }

    val playbackVariant = if( purpose == StreamPurpose.PLAYBACK ) {
        val context = get<Context>(Context::class.java)
        PlaybackStreamVariant(
            audioQuality = Preferences.AUDIO_QUALITY.value,
            isConnectionMetered =
                context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: false,
            saveDataOnMeteredConnections = Preferences.IS_CONNECTION_METERED.value,
        )
    } else {
        null
    }
    val cacheKey = StreamCacheKey( songId, purpose, playbackVariant )

    streamCache[cacheKey]?.let { cached ->
        if( isResolvedStreamFresh(cached.expiresAtMillis, System.currentTimeMillis()) ) {
            logger.d { "${purpose.logLabel} stream of $songId is cached (client ${cached.streamClient})" }
            return cached
        }

        logger.d { "Cached ${purpose.logLabel.lowercase()} stream of $songId expired" }
        streamCache.remove( cacheKey, cached )
    }

    return runBlocking( Dispatchers.IO ) {
        // Explicit songs need a PO token on most clients; the hint lets InnerTubeX prefetch it.
        // Prefer the originating MediaItem: on first play its asynchronous Room upsert may still
        // be in flight. Database data remains the fallback for restored and legacy queue entries.
        val isExplicit = explicitPlaybackHintFor( songId ) ?: runCatching {
            Database.songTable.findById( songId ).first()?.isExplicit
        }.getOrNull()

        when( purpose ) {
            StreamPurpose.PLAYBACK -> {
                val variant = requireNotNull( playbackVariant )

                InnerTubeXPlayer.playerResponseForPlayback(
                    videoId = songId,
                    audioQuality = variant.audioQuality,
                    isConnectionMetered = variant.isConnectionMetered,
                    saveDataOnMeteredConnections = variant.saveDataOnMeteredConnections,
                    isExplicit = isExplicit,
                )
            }

            StreamPurpose.DOWNLOAD -> InnerTubeXPlayer.playerResponseForDownload(
                videoId = songId,
                isExplicit = isExplicit,
            )
        }.getOrElse {
            throw mapExtractionFailure(
                error = it,
                isOffline = !ConnectivityUtils.isAvailable.value,
            )
        }
    }.also { data ->
        logger.i {
            "${purpose.logLabel}: client=${data.streamClient}, itag=${data.itag}, " +
                    "bitrate=${data.bitrate}, videoId=$songId"
        }

        // Keep at most one playback variant per song. A preference or metered-network change
        // therefore causes a miss and atomically replaces the formerly selected URL.
        if( purpose == StreamPurpose.PLAYBACK ) {
            streamCache.forEach { (key, value) ->
                if( key.videoId == songId && key.purpose == StreamPurpose.PLAYBACK && key != cacheKey )
                    streamCache.remove( key, value )
            }
        }
        streamCache[cacheKey] = data
        if( purpose == StreamPurpose.PLAYBACK )
            upsertSongFormat( songId, data )
    }
}

/**
 * Point [this] at the resolved CDN url and carry the client's request headers
 * (InnerTubeX supplies User-Agent/Referer/Origin for the web clients; they override
 * the default header set in PlayerModule).
 *
 * Deliberately **no** sub-range chunking here, unlike Metrolist's version of this function:
 * in Kreate this resolver is the *upstream* of [androidx.media3.datasource.cache.CacheDataSource]
 * (see PlayerModule). When CacheDataSource opens its upstream with an unknown length and gets a
 * bounded length back, it records `position + length` as the total content length in the cache
 * index and reports end-of-stream after that chunk - the song would be cut off and stay truncated
 * in the cache. Clients that only serve bounded ranges are therefore excluded up front in
 * [InnerTubeXPlayer.playerResponseForPlayback] (`allowBoundedRange = false`), which also checks
 * that InnerTubeX did not hand one back anyway.
 */
private fun DataSpec.withResolvedStream( stream: InnerTubeXPlayer.PlaybackData ): DataSpec =
    withUri( stream.streamUrl.toUri() )
        .withRequestHeaders( httpRequestHeaders + stream.streamHeaders )

/**
 * The player cache is keyed by video id only, but the resolved file is not always the same:
 * quality (High/Low/Auto on a metered connection) and the InnerTube client that serves a song
 * decide the itag, e.g. 140 (m4a) on mobile data and 251 (webm) on Wi-Fi. CacheDataSource would
 * happily stitch cached bytes of one file to network bytes of the other, and the extractor then
 * chokes on garbage ("Skipping atom with length > 2147483647", "Unrecognized input format").
 *
 * So the itag of the cached bytes is recorded in the cache's content metadata. When a resolution
 * comes back with a different itag while bytes are cached, the stale spans are dropped and
 * [CachedFormatMismatchException] aborts this load so the player is re-prepared cleanly.
 * Cache entries written before this metadata existed cannot be checked here; the listener's
 * parse-error recovery covers those.
 */
private fun reconcileCachedFormat( cache: Cache, songId: String, itag: Int ) {
    val cachedItag = cache.getContentMetadata( songId ).get( METADATA_KEY_ITAG, -1L )
    if( cachedItag == itag.toLong() ) return

    val cachedBytes = cache.getCachedBytes( songId, 0L, C.LENGTH_UNSET.toLong() )
    if( cachedItag != -1L && cachedBytes > 0L ) {
        logger.w { "Cached bytes of $songId are itag $cachedItag but the stream is itag $itag - dropping $cachedBytes cached bytes" }
        runCatching { cache.removeResource( songId ) }
            .onFailure { logger.e( "failed to drop cached spans of $songId", it ) }
        throw CachedFormatMismatchException( songId, cachedItag, itag )
    }

    runCatching {
        cache.applyContentMetadataMutations( songId, ContentMetadataMutations().set( METADATA_KEY_ITAG, itag.toLong() ) )
    }.onFailure { logger.w( "failed to record itag $itag of $songId in cache metadata", it ) }
}

/**
 * InnerTubeX normally supplies the complete byte length. If a client omits it, request just byte
 * zero and obtain the total from Content-Range. Supplying this length to Media3 makes its OkHttp
 * data source send a bounded `Range: bytes=start-end` request instead of an open-ended CDN stream.
 */
private fun getDownloadContentLength(
    client: OkHttpClient,
    stream: InnerTubeXPlayer.PlaybackData,
): Long {
    stream.contentLength?.takeIf { it > 0L }?.let { return it }

    val request = Request.Builder()
        .url( stream.streamUrl )
        .apply {
            stream.streamHeaders.forEach { (name, value) -> header( name, value ) }
        }
        .header( "Accept-Encoding", "identity" )
        .header( "Range", "bytes=0-0" )
        .get()
        .build()

    return client.newCall( request ).execute().use { response ->
        if( isRejectedStreamHttpStatus(response.code) ) {
            invalidateRejectedDownloadStream( stream.videoId, stream )
            throw IOException( "Download stream was rejected by the CDN (HTTP ${response.code})" )
        }
        if( !response.isSuccessful )
            throw IOException( "Download length probe failed (HTTP ${response.code})" )

        val rangeLength = HttpUtil.getDocumentSize( response.header( "Content-Range" ) )
        val fullResponseLength = if( response.code == 200 )
            response.header( "Content-Length" )?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        else
            C.LENGTH_UNSET.toLong()
        val contentLength = maxOf( rangeLength, fullResponseLength )

        if( contentLength <= 0L )
            throw IOException( "Could not determine download length (HTTP ${response.code})" )

        logger.d { "Download length probe returned $contentLength bytes for ${stream.videoId}" }
        contentLength
    }
}

private fun invalidateRejectedDownloadStream(
    songId: String,
    stream: InnerTubeXPlayer.PlaybackData,
): Boolean {
    val cacheKey = StreamCacheKey( songId, StreamPurpose.DOWNLOAD, playbackVariant = null )
    if( !streamCache.remove( cacheKey, stream ) ) return false

    if( stream.streamClient == "WEB_REMIX" )
        InnerTubeXPlayer.markWebRemixFailed( songId )

    logger.w {
        "Rejected download stream invalidated for $songId (client ${stream.streamClient})"
    }
    return true
}

/**
 * Called by the download-only OkHttp interceptor before Media3 handles a rejected HTTP response.
 * Matching is kept in memory; signed stream URLs are never logged or persisted.
 */
internal fun invalidateRejectedDownloadStreamUrl( streamUrl: String ): Boolean {
    val entry = streamCache.entries.firstOrNull { (key, value) ->
        key.purpose == StreamPurpose.DOWNLOAD && sameStreamUrl( value.streamUrl, streamUrl )
    } ?: return false

    return invalidateRejectedDownloadStream( entry.key.videoId, entry.value )
}

/** OkHttp canonicalizes a URL before sending it, so compare parsed values as a fallback. */
private fun sameStreamUrl( cachedUrl: String, requestUrl: String ): Boolean {
    if( cachedUrl == requestUrl ) return true

    val cachedHttpUrl = cachedUrl.toHttpUrlOrNull() ?: return false
    val requestHttpUrl = requestUrl.toHttpUrlOrNull() ?: return false
    return cachedHttpUrl == requestHttpUrl
}

/**
 * Drop partial data written before the dedicated high-quality download policy existed.
 *
 * This must run before DownloadManager is created (and therefore before CacheWriter scans its
 * cached spans). Completed downloads are filtered by the caller and are never silently replaced.
 */
internal fun resetLegacyPartialDownload( cache: Cache, songId: String ): Boolean {
    val metadata = cache.getContentMetadata( songId )
    val policy = metadata.get( METADATA_KEY_DOWNLOAD_POLICY, -1L )
    val cachedBytes = cache.getCachedBytes( songId, 0L, C.LENGTH_UNSET.toLong() )
    if( cachedBytes <= 0L || policy == DOWNLOAD_POLICY_HIGH_QUALITY ) return false

    logger.w {
        "Dropping $cachedBytes legacy partial bytes of $songId before high-quality resume"
    }
    return runCatching {
        cache.removeResource( songId )
        true
    }.onFailure {
        logger.e( "failed to reset legacy partial download of $songId", it )
    }.getOrDefault( false )
}

/** Prevent a partial download from mixing bytes belonging to different audio formats. */
private fun prepareDownloadCache(
    cache: Cache,
    songId: String,
    stream: InnerTubeXPlayer.PlaybackData,
    contentLength: Long,
    requestPosition: Long,
) {
    val metadata = cache.getContentMetadata( songId )
    val cachedItag = metadata.get( METADATA_KEY_ITAG, -1L )
    val cachedLength = ContentMetadata.getContentLength( metadata )
    val cachedBytes = cache.getCachedBytes( songId, 0L, C.LENGTH_UNSET.toLong() )
    val cachedPolicy = metadata.get( METADATA_KEY_DOWNLOAD_POLICY, -1L )
    val legacyPolicy = cachedBytes > 0L && cachedPolicy != DOWNLOAD_POLICY_HIGH_QUALITY
    val formatChanged = cachedBytes > 0L && cachedItag != stream.itag.toLong()
    val lengthChanged = cachedBytes > 0L &&
            cachedLength != C.LENGTH_UNSET.toLong() && cachedLength != contentLength
    val resetRequired = legacyPolicy || formatChanged || lengthChanged

    if( resetRequired ) {
        logger.w {
            "Partial download of $songId is incompatible " +
                    "(cached itag=$cachedItag, length=$cachedLength; " +
                    "resolved itag=${stream.itag}, length=$contentLength) - restarting"
        }

        // CacheWriter has already skipped the old prefix at this point. Removing it here and
        // letting the same writer retry would leave its internal nextPosition unchanged, so it
        // could incorrectly finish with a hole at the beginning. Fail safely instead; the
        // DownloadManager listener clears the incompatible spans only after this task has stopped.
        if( requestPosition > 0L )
            throw DownloadFormatMismatchException( songId )

        try {
            cache.removeResource( songId )
        } catch( error: Exception ) {
            throw IOException( "Could not reset incompatible download of $songId", error )
        }
    }

    val mutations = ContentMetadataMutations()
        .set( METADATA_KEY_ITAG, stream.itag.toLong() )
        .set( METADATA_KEY_DOWNLOAD_POLICY, DOWNLOAD_POLICY_HIGH_QUALITY )
    ContentMetadataMutations.setContentLength( mutations, contentLength )
    try {
        cache.applyContentMetadataMutations( songId, mutations )
    } catch( error: Exception ) {
        throw IOException( "Could not record download format for $songId", error )
    }
}

/**
 * Reuse playback-cache bytes only when they belong to exactly the high-quality format selected for
 * this download. A private miss-only key bypasses stale Low/Auto bytes without deleting them.
 */
private fun playerCacheKeyForDownload(
    cache: Cache,
    songId: String,
    stream: InnerTubeXPlayer.PlaybackData,
    contentLength: Long,
): String {
    val metadata = cache.getContentMetadata( songId )
    val cachedItag = metadata.get( METADATA_KEY_ITAG, -1L )
    val cachedLength = ContentMetadata.getContentLength( metadata )
    val cachedBytes = cache.getCachedBytes( songId, 0L, C.LENGTH_UNSET.toLong() )
    val compatible = cachedBytes > 0L &&
            cachedItag == stream.itag.toLong() &&
            (cachedLength == C.LENGTH_UNSET.toLong() || cachedLength == contentLength)

    if( compatible ) {
        logger.d { "Download of $songId can reuse $cachedBytes player-cache bytes" }
        return songId
    }

    if( cachedBytes > 0L )
        logger.d { "Download of $songId bypasses incompatible player-cache bytes" }

    return "$DOWNLOAD_NETWORK_CACHE_KEY_PREFIX/$songId/${stream.itag}/$contentLength"
}

private fun DataSpec.withResolvedDownloadStream(
    stream: InnerTubeXPlayer.PlaybackData,
    contentLength: Long,
    playerCacheKey: String,
): DataSpec {
    val remainingLength = contentLength - position
    if( remainingLength <= 0L )
        throw IOException( "Download position $position is outside ${stream.videoId} ($contentLength bytes)" )

    val requestLength =
        if( length == C.LENGTH_UNSET.toLong() ) remainingLength else minOf( length, remainingLength )
    if( requestLength <= 0L )
        throw IOException( "Download request for ${stream.videoId} has no remaining bytes" )

    return withUri( stream.streamUrl.toUri() )
        .withRequestHeaders( httpRequestHeaders + stream.streamHeaders )
        .buildUpon()
        .setKey( playerCacheKey )
        .setLength( requestLength )
        .build()
}
//</editor-fold>

fun Scope.resolveInnertubeMedia( dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }
    upsertSongInfo( get(), songId )

    val stream = getPlayableStream( songId, StreamPurpose.PLAYBACK )
    reconcileCachedFormat( get<Cache>( CacheType.CACHE ), songId, stream.itag )

    return dataSpec.withResolvedStream( stream )
}

/** Resolve a download independently from playback, always using the highest audio quality. */
fun Scope.resolveInnertubeDownload( dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) { "Online download doesn't contain cache key" }
    val initiallyResolvedStream = getPlayableStream( songId, StreamPurpose.DOWNLOAD )
    val contentLength = getDownloadContentLength( get(), initiallyResolvedStream )
    val stream = if( initiallyResolvedStream.contentLength == contentLength )
        initiallyResolvedStream
    else
        initiallyResolvedStream.copy( contentLength = contentLength )
    val downloadCache = get<Cache>( CacheType.DOWNLOAD )
    val playerCache = get<Cache>( CacheType.CACHE )

    // Store a probed length too, so retries do not repeat the probe.
    if( stream !== initiallyResolvedStream )
        streamCache[StreamCacheKey( songId, StreamPurpose.DOWNLOAD, playbackVariant = null )] = stream

    // Persist the final, length-complete high-quality format exactly once per resolved variant.
    upsertSongFormat( songId, stream )

    prepareDownloadCache(
        cache = downloadCache,
        songId = songId,
        stream = stream,
        contentLength = contentLength,
        requestPosition = dataSpec.position,
    )

    return dataSpec.withResolvedDownloadStream(
        stream = stream,
        contentLength = contentLength,
        playerCacheKey = playerCacheKeyForDownload( playerCache, songId, stream, contentLength ),
    )
}

/**
 * Remove cached url of [songId].
 *
 * @return `true` if song's url was cached, and is deleted, `false` otherwise.
 */
fun clearCachedStreamUrlOf( songId: String ): Boolean {
    var removed = false
    streamCache.forEach { (key, value) ->
        if( key.videoId == songId && streamCache.remove(key, value) ) removed = true
    }
    return removed
}

/** Drop every signed playback URL after stream-selection preferences change. */
fun clearCachedPlaybackStreamUrls(): Int {
    var removed = 0
    streamCache.forEach { (key, value) ->
        if( key.purpose == StreamPurpose.PLAYBACK && streamCache.remove(key, value) ) removed++
    }
    return removed
}

/**
 * Drop the cached url of [songId] after the CDN rejected it (HTTP 403/410...).
 *
 * @return name of the InnerTube client that produced the rejected url, or `null` if nothing was cached.
 */
fun invalidateRejectedStreamOf( songId: String ): String? {
    var rejectedClient: String? = null
    streamCache.forEach { (key, value) ->
        if( key.videoId == songId && streamCache.remove(key, value) )
            rejectedClient = rejectedClient ?: value.streamClient
    }
    return rejectedClient
}
