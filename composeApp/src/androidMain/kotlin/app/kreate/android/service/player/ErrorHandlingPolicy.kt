package app.kreate.android.service.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import java.io.IOException


/** 403: signature/PO token rejected, 410: URL gone, 416: requested range refused. */
internal val STREAM_REJECTION_HTTP_CODES = setOf( 403, 410, 416 )

internal fun isRejectedStreamHttpStatus( responseCode: Int? ): Boolean =
    responseCode in STREAM_REJECTION_HTTP_CODES

internal fun Throwable.httpResponseCodeOrNull(): Int? {
    var current: Throwable? = this
    while( current != null ) {
        if( current is HttpDataSource.InvalidResponseCodeException ) return current.responseCode
        current = current.cause
    }
    return null
}

@UnstableApi
class ErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    /**
     * Retrying the same rejected signed URL cannot recover. Surface these responses immediately
     * so [ExoPlayerListener] can invalidate the URL and prepare a freshly resolved stream.
     */
    override fun getRetryDelayMsFor( loadErrorInfo: LoadErrorInfo ): Long =
        if( isRejectedStreamHttpStatus(loadErrorInfo.exception.httpResponseCodeOrNull()) )
            C.TIME_UNSET
        else
            super.getRetryDelayMsFor( loadErrorInfo )

    // Keep Media3's location/track fallback available; signed-URL recovery is handled above.
    override fun isEligibleForFallback( exception: IOException ): Boolean = true
}
