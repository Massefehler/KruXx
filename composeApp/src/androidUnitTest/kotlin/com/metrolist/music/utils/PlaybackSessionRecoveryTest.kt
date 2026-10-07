package com.metrolist.music.utils

import com.metrolist.innertubex.InnerTubeHttpException
import com.metrolist.innertubex.extraction.StreamResolveException
import io.ktor.http.HttpStatusCode
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackSessionRecoveryTest {
    @Test
    fun aWorkingAccountDoesNotMakeAnonymousRequests() = runBlocking {
        val result = withAnonymousPlaybackFallback(
            hasAccountSession = true,
            resolve = { "account stream" },
            resolveAnonymously = { error("Unexpected anonymous request") },
        )
        assertEquals("account stream", result)
    }

    @Test
    fun aRecentlyRecoveredSessionSkipsRepeatedRejectedAccountRequests() = runBlocking {
        val result = withAnonymousPlaybackFallback(
            hasAccountSession = true,
            preferAnonymous = true,
            resolve = { error("Unexpected repeated account request") },
            resolveAnonymously = { "public stream" },
        )
        assertEquals("public stream", result)
    }

    @Test
    fun anAnonymousPreferenceStillAllowsAccountOnlyContent() = runBlocking {
        val result = withAnonymousPlaybackFallback(
            hasAccountSession = true,
            preferAnonymous = true,
            resolve = { "account-only stream" },
            resolveAnonymously = { throw failure(StreamResolveException.Reason.AGE_RESTRICTED) },
        )
        assertEquals("account-only stream", result)
    }

    @Test
    fun aFailedPreferredAnonymousAttemptIsNeverRepeated() = runBlocking {
        var anonymousRequests = 0
        val original = failure(StreamResolveException.Reason.AGE_RESTRICTED)
        val result = runCatching {
            withAnonymousPlaybackFallback(
                hasAccountSession = true,
                preferAnonymous = true,
                resolve = { throw original },
                resolveAnonymously = {
                    anonymousRequests++
                    throw failure(StreamResolveException.Reason.UNAVAILABLE)
                },
            )
        }
        assertSame(original, result.exceptionOrNull())
        assertEquals(1, anonymousRequests)
    }

    @Test
    fun aRejectedAccountCanStillPlayPublicMusic() = runBlocking {
        for (reason in listOf(
            StreamResolveException.Reason.NO_PLAYABLE_STREAM,
            StreamResolveException.Reason.EXPLICIT_UNSUPPORTED,
        )) {
            val result = withAnonymousPlaybackFallback(
                hasAccountSession = true,
                resolve = { throw failure(reason) },
                resolveAnonymously = { "public stream" },
            )
            assertEquals("public stream", result)
        }
    }

    @Test
    fun rejectedPlayerHttpRequestsAllowOnePublicRetry() = runBlocking {
        for (status in listOf(400, 401, 403)) {
            var retries = 0
            val result = withAnonymousPlaybackFallback(
                hasAccountSession = true,
                resolve = { throw failure(
                    StreamResolveException.Reason.NETWORK,
                    InnerTubeHttpException("player", HttpStatusCode.fromValue(status)),
                ) },
                resolveAnonymously = { retries++; "public stream" },
            )
            assertEquals("public stream", result)
            assertEquals(1, retries)
        }
    }

    @Test
    fun signedOutFailuresAreNotRetriedAgain() = runBlocking {
        val original = failure(StreamResolveException.Reason.NO_PLAYABLE_STREAM)
        val result = runCatching {
            withAnonymousPlaybackFallback(
                hasAccountSession = false,
                resolve = { throw original },
                resolveAnonymously = { error("Unexpected repeated request") },
            )
        }
        assertSame(original, result.exceptionOrNull())
    }

    @Test
    fun restrictedMissingOfflineAndThrottledTracksDoNotAddRequests() = runBlocking {
        val failures = listOf(
            failure(StreamResolveException.Reason.AGE_RESTRICTED),
            failure(StreamResolveException.Reason.UNAVAILABLE),
            failure(StreamResolveException.Reason.NETWORK, UnknownHostException("offline")),
            failure(StreamResolveException.Reason.NETWORK,
                InnerTubeHttpException("player", HttpStatusCode.TooManyRequests)),
        )
        for (original in failures) {
            var retried = false
            val result = runCatching {
                withAnonymousPlaybackFallback(
                    hasAccountSession = true,
                    resolve = { throw original },
                    resolveAnonymously = { retried = true },
                )
            }
            assertSame(original, result.exceptionOrNull())
            assertFalse(retried)
        }
    }

    @Test
    fun failedPublicRetryPreservesTheOriginalPlaybackError() = runBlocking {
        val original = failure(StreamResolveException.Reason.NO_PLAYABLE_STREAM)
        val result = runCatching {
            withAnonymousPlaybackFallback(
                hasAccountSession = true,
                resolve = { throw original },
                resolveAnonymously = { throw failure(StreamResolveException.Reason.UNAVAILABLE) },
            )
        }
        assertSame(original, result.exceptionOrNull())
    }

    @Test
    fun cancellationOfEitherAttemptReachesThePlayer() = runBlocking {
        val cancelled = CancellationException("Track changed")
        val first = runCatching {
            withAnonymousPlaybackFallback(
                hasAccountSession = true,
                resolve = { throw cancelled },
                resolveAnonymously = { error("Unexpected retry after cancellation") },
            )
        }
        assertSame(cancelled, first.exceptionOrNull())

        val retry = runCatching {
            withAnonymousPlaybackFallback(
                hasAccountSession = true,
                resolve = { throw failure(StreamResolveException.Reason.NO_PLAYABLE_STREAM) },
                resolveAnonymously = { throw cancelled },
            )
        }
        assertSame(cancelled, retry.exceptionOrNull())
    }

    private fun failure(reason: StreamResolveException.Reason, cause: Throwable? = null) =
        StreamResolveException(reason, "Cannot resolve test track", cause)
}
