@file:kotlin.OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadataMutations
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.Format
import co.touchlab.kermit.Logger
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.music.utils.InnerTubeXPlayer
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.TimeoutException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.innertube.Innertube
import me.knighthat.utils.Toaster
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference


/** Re-resolve a little before the CDN considers the signed URL expired. */
private const val EXPIRY_MARGIN_MS = 30_000L

/** Cache metadata key: itag of the bytes stored under a song's cache key. */
private const val METADATA_KEY_ITAG = "kruxx_itag"

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
 * Store id of song just added to the database.
 * This is created to reduce load to Room
 */
private val justInserted = AtomicReference("")

/** Resolved streams per video id; entries are dropped when expired or rejected by the CDN. */
private val streamCache = ConcurrentHashMap<String, InnerTubeXPlayer.PlaybackData>()
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
    if( videoId == justInserted.load() || !isNetworkAvailable( context ) )
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

    // Must not modify [JustInserted] to [upsertSongFormat] let execute later
}

/**
 * Persist the format that is being played (itag, mime type, bitrate, size, loudness)
 * so "Stats for nerds" and volume normalization have data to work with.
 */
private fun upsertSongFormat( videoId: String, data: InnerTubeXPlayer.PlaybackData ) {
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() ) return

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

            // Format must be added successfully before setting variable
            justInserted.store( videoId )
        }
    }
}
//</editor-fold>
//<editor-fold desc="Get response">
/**
 * Translate extraction failures into Kreate's [PlaybackException] family so the
 * player listener can show a meaningful message (and skip/retry accordingly).
 */
private fun mapExtractionFailure( error: Throwable ): Throwable {
    val offline = !ConnectivityUtils.isAvailable.value

    return when( error ) {
        is PlaybackException -> error

        is StreamResolveException -> when( error.reason ) {
            StreamResolveException.Reason.NETWORK ->
                if( offline ) NoInternetException( error ) else UnknownException( error.message, error )

            StreamResolveException.Reason.AGE_RESTRICTED -> LoginRequiredException( error.message, error )

            StreamResolveException.Reason.UNAVAILABLE,
            StreamResolveException.Reason.NO_PLAYABLE_STREAM,
            StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
            StreamResolveException.Reason.NO_MUSIC_VIDEO -> UnplayableException( error.message, error )

            StreamResolveException.Reason.UNKNOWN -> UnknownException( error.message, error )
        }

        is UnknownHostException,
        is UnresolvedAddressException,
        is ConnectException ->
            if( offline ) NoInternetException( error ) else UnknownException( error.message, error )

        is SocketTimeoutException -> TimeoutException()

        else -> UnknownException( error.message, error )
    }
}

private fun getPlayableStream( songId: String ): InnerTubeXPlayer.PlaybackData {
    logger.v { "Processing $songId" }

    streamCache[songId]?.let { cached ->
        if( cached.expiresAtMillis - EXPIRY_MARGIN_MS > System.currentTimeMillis() ) {
            logger.d { "Stream url of $songId is cached (client ${cached.streamClient})" }
            return cached
        }

        logger.d { "Cached stream url of $songId expired" }
        streamCache.remove( songId, cached )
    }

    val context = get<Context>(Context::class.java)
    val isConnectionMetered = context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: false
    val audioQuality = Preferences.AUDIO_QUALITY.value

    return runBlocking( Dispatchers.IO ) {
        // Explicit songs need a PO token on most clients; the hint lets InnerTubeX prefetch it.
        val isExplicit = runCatching {
            Database.songTable.findById( songId ).first()?.isExplicit
        }.getOrNull()

        InnerTubeXPlayer.playerResponseForPlayback(
            videoId = songId,
            audioQuality = audioQuality,
            isConnectionMetered = isConnectionMetered,
            isExplicit = isExplicit
        ).getOrElse { throw mapExtractionFailure( it ) }
    }.also { data ->
        logger.i { "Playback: client=${data.streamClient}, itag=${data.itag}, videoId=$songId" }

        streamCache[songId] = data
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
//</editor-fold>

fun Scope.resolveInnertubeMedia( dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }
    upsertSongInfo( get(), songId )

    val stream = getPlayableStream( songId )
    reconcileCachedFormat( get<Cache>( CacheType.CACHE ), songId, stream.itag )

    return dataSpec.withResolvedStream( stream )
}

/**
 * Remove cached url of [songId].
 *
 * @return `true` if song's url was cached, and is deleted, `false` otherwise.
 */
fun clearCachedStreamUrlOf( songId: String ): Boolean =
    streamCache.remove( songId ) != null

/**
 * Drop the cached url of [songId] after the CDN rejected it (HTTP 403/410...).
 *
 * @return name of the InnerTube client that produced the rejected url, or `null` if nothing was cached.
 */
fun invalidateRejectedStreamOf( songId: String ): String? =
    streamCache.remove( songId )?.streamClient
