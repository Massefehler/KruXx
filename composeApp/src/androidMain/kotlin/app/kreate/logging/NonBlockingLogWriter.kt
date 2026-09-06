package app.kreate.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** File logging must never hold up playback or UI when storage or the writer stops responding. */
internal class NonBlockingLogWriter(
    private val delegate: LogWriter,
    capacity: Int = 64,
) : LogWriter(), Closeable {
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(capacity),
        { task -> Thread(task, "KruXxFileLogging").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    override fun isLoggable(tag: String, severity: Severity) = delegate.isLoggable(tag, severity)

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val boundedMessage = message.take(32_768)
        executor.execute {
            try {
                delegate.log(severity, boundedMessage, tag, throwable)
            } catch (_: Exception) {
                // The independent platform writer still receives the message. Never log recursively.
            }
        }
    }

    override fun close() { executor.shutdownNow() }
}
