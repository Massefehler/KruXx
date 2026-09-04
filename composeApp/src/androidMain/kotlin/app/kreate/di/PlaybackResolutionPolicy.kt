@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import com.metrolist.innertubex.extraction.StreamResolveException
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.TimeoutException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap


/** Re-resolve a little before the CDN considers the signed URL expired. */
internal const val STREAM_EXPIRY_MARGIN_MS = 30_000L

/**
 * Keep the expiry boundary deterministic and independently testable. At exactly the safety
 * margin the URL is already considered stale; attempting it would only delay a fresh resolve.
 */
internal fun isResolvedStreamFresh(
    expiresAtMillis: Long,
    nowMillis: Long,
    expiryMarginMillis: Long = STREAM_EXPIRY_MARGIN_MS,
): Boolean = expiresAtMillis - expiryMarginMillis > nowMillis

/**
 * Translate extraction failures into Kreate's [PlaybackException] family so the player listener
 * can show a meaningful message and apply its recovery policy.
 */
internal fun mapExtractionFailure(
    error: Throwable,
    isOffline: Boolean,
): Throwable = when( error ) {
    is PlaybackException -> error

    is StreamResolveException -> when( error.reason ) {
        StreamResolveException.Reason.NETWORK ->
            if( isOffline ) NoInternetException( error )
            else UnknownException( error.message, error )

        StreamResolveException.Reason.AGE_RESTRICTED ->
            LoginRequiredException( error.message, error )

        StreamResolveException.Reason.UNAVAILABLE,
        StreamResolveException.Reason.NO_PLAYABLE_STREAM,
        StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
        StreamResolveException.Reason.NO_MUSIC_VIDEO ->
            UnplayableException( error.message, error )

        StreamResolveException.Reason.UNKNOWN -> UnknownException( error.message, error )
    }

    is UnknownHostException,
    is UnresolvedAddressException,
    is ConnectException ->
        if( isOffline ) NoInternetException( error )
        else UnknownException( error.message, error )

    is SocketTimeoutException -> TimeoutException()

    else -> UnknownException( error.message, error )
}

/**
 * Media3's resolving data source only receives a [androidx.media3.datasource.DataSpec], not the
 * originating [MediaItem]. Remember the explicit flag while the media source is created so a
 * song can receive the required PO-token hint even before its asynchronous Room upsert finishes.
 *
 * Explicitness is intrinsic to a YouTube video id. Keeping the latest value for the lifetime of
 * the player is therefore safe; [clearPlaybackContentHints] drops all values on player release.
 */
private val explicitPlaybackHints = ConcurrentHashMap<String, Boolean>()

internal fun rememberPlaybackContentHint( mediaItem: MediaItem ) {
    val extras = mediaItem.mediaMetadata.extras ?: return
    if( mediaItem.mediaId.isBlank() || !extras.containsKey(EXPLICIT_BUNDLE_TAG) ) return

    explicitPlaybackHints[mediaItem.mediaId] = extras.getBoolean(EXPLICIT_BUNDLE_TAG)
}

internal fun explicitPlaybackHintFor( videoId: String ): Boolean? = explicitPlaybackHints[videoId]

internal fun clearPlaybackContentHints() = explicitPlaybackHints.clear()
