package app.kreate.logging

import android.system.ErrnoException
import android.system.Os
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Called by one NonBlockingLogWriter thread; rotation also works before Android 8. */
internal class RuntimeFileLogWriter(
    private val directory: File,
    maxFileSize: Long,
    maxFiles: Int,
    private val move: (File, File) -> Unit = { source, destination ->
        try {
            Os.rename(source.path, destination.path)
        } catch (error: ErrnoException) {
            throw IOException("Cannot rotate runtime log", error)
        }
    },
) : LogWriter() {
    private val sizeLimit = maxFileSize.coerceAtLeast(1)
    private val fileCount = maxFiles.coerceIn(1, 100)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private fun file(index: Int) = File(directory, if (index == 0) "logs.log" else "logs-$index.log")

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create runtime log directory")
        val current = file(0)
        if (current.length() >= sizeLimit) {
            val oldest = file(fileCount - 1)
            if (oldest.exists() && !oldest.delete()) throw IOException("Cannot remove oldest runtime log")
            for (index in fileCount - 2 downTo 0) {
                val source = file(index)
                if (source.exists()) move(source, file(index + 1))
            }
        }
        val line = buildString {
            append(dateFormat.format(Date())).append(' ')
            append(severity.name).append('/').append(tag).append(": ").append(message).append('\n')
            throwable?.let { append(it.stackTraceToString().take(32_768)).append('\n') }
        }
        current.appendText(line, Charsets.UTF_8)
    }
}
