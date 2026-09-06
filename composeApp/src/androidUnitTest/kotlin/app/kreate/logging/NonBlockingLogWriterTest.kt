package app.kreate.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NonBlockingLogWriterTest {
    @Test(timeout = 5_000) fun blockedStorageDoesNotBlockCallersAndKeepsOnlyRecentQueuedMessages() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(3)
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val sink = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                if (message == "first") {
                    entered.countDown()
                    release.await()
                }
                messages += message
                completed.countDown()
            }
        }
        NonBlockingLogWriter(sink, capacity = 2).use { writer ->
            try {
                writer.log(Severity.Info, "first", "test", null)
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                repeat(10) { writer.log(Severity.Info, "message$it", "test", null) }
                // Every producer call returned while the sink was still blocked.
                assertEquals(1L, release.count)
                release.countDown()
                assertTrue(completed.await(2, TimeUnit.SECONDS))
                assertEquals(listOf("first", "message8", "message9"), messages.toList())
            } finally { release.countDown() }
        }
    }

    @Test(timeout = 5_000) fun writerFailureDoesNotStopLaterMessages() {
        val received = CountDownLatch(1)
        val sink = object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                if (message == "fail") throw IOException("Storage unavailable")
                received.countDown()
            }
        }
        NonBlockingLogWriter(sink).use { writer ->
            writer.log(Severity.Info, "fail", "test", null)
            writer.log(Severity.Info, "recovered", "test", null)
            assertTrue(received.await(2, TimeUnit.SECONDS))
        }
    }
}
