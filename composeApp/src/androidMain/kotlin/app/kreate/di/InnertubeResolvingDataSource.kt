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
 * Point [this] at the resolved CDN url, carry the client's request headers and,
 * where the client requires it, cap each request to the chunk size InnerTubeX
 * reported (unbounded requests get throttled or rejected by those CDNs).
 */
private fun DataSpec.withResolvedStream( stream: InnerTubeXPlayer.PlaybackData ): DataSpec {
    val resolved = withUri( stream.streamUrl.toUri() )
                       .withRequestHeaders( httpRequestHeaders + stream.streamHeaders )

    if( (!stream.requireBoundedRange && !stream.useRangeChunks) || stream.rangeChunkSizeBytes <= 0L )
        return resolved

    val boundedLength =
        if( length == C.LENGTH_UNSET.toLong() ) stream.rangeChunkSizeBytes
        else minOf( length, stream.rangeChunkSizeBytes )

    return resolved.subrange( 0, boundedLength )
}
//</editor-fold>

fun Scope.resolveInnertubeMedia( dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }
    upsertSongInfo( get(), songId )

    return dataSpec.withResolvedStream( getPlayableStream( songId ) )
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
