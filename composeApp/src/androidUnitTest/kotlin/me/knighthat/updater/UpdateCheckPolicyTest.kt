package me.knighthat.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdateCheckPolicyTest {
    private val interval = TimeUnit.HOURS.toMillis(24)

    @Test
    fun `automatic check runs initially and at interval boundary`() {
        val now = 10L * interval

        assertTrue(UpdateCheckPolicy.isAutomaticCheckDue(now, 0L, interval))
        assertFalse(UpdateCheckPolicy.isAutomaticCheckDue(now, now - interval + 1L, interval))
        assertTrue(UpdateCheckPolicy.isAutomaticCheckDue(now, now - interval, interval))
    }

    @Test
    fun `future timestamp cannot suppress checks after wall clock correction`() {
        val now = 10L * interval

        assertTrue(UpdateCheckPolicy.isAutomaticCheckDue(now, now + interval, interval))
    }

    @Test
    fun `automatic failures have two bounded retries`() {
        assertEquals(2_000L, UpdateCheckPolicy.retryDelayMillis(failedAttempt = 1))
        assertEquals(8_000L, UpdateCheckPolicy.retryDelayMillis(failedAttempt = 2))
        assertNull(UpdateCheckPolicy.retryDelayMillis(failedAttempt = 3))
        assertNull(UpdateCheckPolicy.retryDelayMillis(failedAttempt = 0))
    }

    @Test
    fun `only transient http responses are retried`() {
        listOf(408, 425, 429, 500, 502, 503).forEach { status ->
            assertTrue("Expected $status to be retryable", UpdateCheckPolicy.isRetryableHttpStatus(status))
        }
        listOf(200, 301, 400, 401, 403, 404, 422, 600).forEach { status ->
            assertFalse("Expected $status to be final", UpdateCheckPolicy.isRetryableHttpStatus(status))
        }
    }
}
