@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.app.Application
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.metrolist.innertubex.extraction.StreamResolveException
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.TimeoutException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaybackResolutionPolicyTest {

    @After
    fun clearHints() = clearPlaybackContentHints()

    @Test
    fun streamExpiryIncludesTheSafetyMarginBoundary() {
        val now = 1_000_000L

        assertTrue(isResolvedStreamFresh(now + STREAM_EXPIRY_MARGIN_MS + 1L, now))
        assertFalse(isResolvedStreamFresh(now + STREAM_EXPIRY_MARGIN_MS, now))
        assertFalse(isResolvedStreamFresh(now - 1L, now))
    }

    @Test
    fun extractionReasonsMapToActionablePlaybackErrors() {
        assertTrue(
            mapExtractionFailure(streamFailure(StreamResolveException.Reason.AGE_RESTRICTED), false)
                is LoginRequiredException
        )
        assertTrue(
            mapExtractionFailure(streamFailure(StreamResolveException.Reason.NO_PLAYABLE_STREAM), false)
                is UnplayableException
        )
        assertTrue(
            mapExtractionFailure(streamFailure(StreamResolveException.Reason.UNKNOWN), false)
                is UnknownException
        )
    }

    @Test
    fun networkFailuresRespectConnectivityAndTimeoutClassification() {
        assertTrue(mapExtractionFailure(UnknownHostException("offline"), true) is NoInternetException)
        assertTrue(mapExtractionFailure(UnknownHostException("dns"), false) is UnknownException)
        assertTrue(mapExtractionFailure(SocketTimeoutException("slow"), false) is TimeoutException)
    }

    @Test
    fun anExistingPlaybackExceptionIsNotWrappedAgain() {
        val original = UnplayableException("already classified")

        assertSame(original, mapExtractionFailure(original, isOffline = false))
    }

    @Test
    fun mediaItemExplicitHintIsAvailableBeforeDatabasePersistence() {
        rememberPlaybackContentHint(mediaItem("explicit-id", explicit = true))

        assertTrue(explicitPlaybackHintFor("explicit-id") == true)

        // An item without the key carries no new information and must not erase a known hint.
        rememberPlaybackContentHint(mediaItem("explicit-id", explicit = null))
        assertTrue(explicitPlaybackHintFor("explicit-id") == true)

        rememberPlaybackContentHint(mediaItem("explicit-id", explicit = false))
        assertFalse(explicitPlaybackHintFor("explicit-id") ?: true)
    }

    @Test
    fun mediaTransportDropsHttpLoggersButKeepsFunctionalInterceptors() {
        val functionalInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
            )
            .addInterceptor(functionalInterceptor)
            .addNetworkInterceptor(
                HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS)
            )
            .build()

        val mediaClient = client.forMediaTransport()

        assertFalse(mediaClient.interceptors.any { it is HttpLoggingInterceptor })
        assertFalse(mediaClient.networkInterceptors.any { it is HttpLoggingInterceptor })
        assertEquals(listOf(functionalInterceptor), mediaClient.interceptors)
    }

    private fun streamFailure(
        reason: StreamResolveException.Reason,
    ) = StreamResolveException(reason = reason, message = "resolve failed")

    private fun mediaItem( id: String, explicit: Boolean? ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(explicit?.let { bundleOf(EXPLICIT_BUNDLE_TAG to it) })
                    .build()
            )
            .build()
}
