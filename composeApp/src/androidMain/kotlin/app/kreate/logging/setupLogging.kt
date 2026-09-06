package app.kreate.logging


import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.util.getRuntimeLogDir
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter


fun setupLogging( vararg bufferedLoggers: BufferedLogger ) {
    val dir = getRuntimeLogDir()
    val maxSize = Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE.value
    val numFiles = Preferences.RUNTIME_LOG_FILE_COUNT.value
    val severity = Preferences.RUNTIME_LOG_SEVERITY.value

    val fileWriter = RuntimeFileLogWriter(dir, maxSize, numFiles)

    Logger.setLogWriters( platformLogWriter(), NonBlockingLogWriter(fileWriter) )
    Logger.setMinSeverity(
        // Override severity when in debug mode
        if( BuildConfig.DEBUG ) Severity.Verbose else severity
    )

    if( Logger.config.minSeverity == Severity.Verbose )
        Logger.v( tag = "System" ) { "Verbose mode enabled!" }

    // Dump logs from BufferedLogger to current logger
    bufferedLoggers.forEach { it.flushTo( Logger ) }
}
