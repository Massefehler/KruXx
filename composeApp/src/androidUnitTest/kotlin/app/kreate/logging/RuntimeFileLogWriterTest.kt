package app.kreate.logging

import co.touchlab.kermit.Severity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RuntimeFileLogWriterTest {
    @get:Rule val temporary = TemporaryFolder()
    private val move: (File, File) -> Unit = { source, target ->
        if (!source.renameTo(target)) throw IOException("Cannot rename test log")
    }

    @Test fun rotationRetainsNewestFilesAndReplacesOldArchives() {
        val directory = temporary.newFolder()
        val writer = RuntimeFileLogWriter(directory, 1, 3, move)
        repeat(5) { writer.log(Severity.Info, "message$it", "test", null) }
        assertEquals(setOf("logs.log", "logs-1.log", "logs-2.log"), directory.list()!!.toSet())
        assertTrue(File(directory, "logs.log").readText().contains("message4"))
        assertTrue(File(directory, "logs-1.log").readText().contains("message3"))
        assertTrue(File(directory, "logs-2.log").readText().contains("message2"))
    }

    @Test fun existingLogIsAppendedAfterRestartAndSingleFileLimitIsRespected() {
        val directory = temporary.newFolder()
        File(directory, "logs.log").writeText("existing\n")
        RuntimeFileLogWriter(directory, 1024, 1, move).log(Severity.Warn, "added", "test", null)
        assertTrue(File(directory, "logs.log").readText().startsWith("existing\n"))
        RuntimeFileLogWriter(directory, 1, 1, move).log(Severity.Info, "latest", "test", null)
        val result = File(directory, "logs.log").readText()
        assertTrue(result.contains("latest"))
        assertFalse(result.contains("existing"))
        assertEquals(listOf("logs.log"), directory.list()!!.toList())
    }
}
