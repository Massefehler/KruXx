package app.kreate.android.downloads

import android.app.Application
import android.content.Context
import androidx.work.WorkInfo
import androidx.work.workDataOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class DownloadStatusTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("kruxx_download_options", Context.MODE_PRIVATE).edit().clear().commit()
        DownloadCenter.initialize(context)
        DownloadCenter.shownJob.value = null
    }

    @Test fun hiddenActiveJobStaysHiddenAfterRestartAndCompletion() {
        val id = UUID.randomUUID()
        DownloadCenter.shownJob.value = id
        DownloadCenter.hideStatus(id)
        assertNull(DownloadCenter.shownJob.value)

        // Simulate a fresh UI state loading the stored choice again.
        DownloadCenter.hiddenStatusJobs.value = emptySet()
        DownloadCenter.initialize(context)
        assertNull(visibleDownloadJob(listOf(job(id, WorkInfo.State.RUNNING, 1)), DownloadCenter.hiddenStatusJobs.value))
        assertNull(visibleDownloadJob(listOf(job(id, WorkInfo.State.SUCCEEDED, 1)), DownloadCenter.hiddenStatusJobs.value))

        val next = job(UUID.randomUUID(), WorkInfo.State.ENQUEUED, 2)
        assertEquals(next.id, visibleDownloadJob(listOf(next, job(id, WorkInfo.State.SUCCEEDED, 1)),
            DownloadCenter.hiddenStatusJobs.value)?.id)
    }

    @Test fun dismissingLatestDoesNotExposeOlderResultsButKeepsHistory() {
        val older = job(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, 1)
        val latest = job(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, 2)
        val history = listOf(older, latest)
        assertEquals(latest.id, visibleDownloadJob(history, emptySet())?.id)
        DownloadCenter.hideStatus(latest.id)
        assertNull(visibleDownloadJob(history, DownloadCenter.hiddenStatusJobs.value))
        assertEquals(listOf(latest.id, older.id), sortedDownloadJobs(history).map { it.id })
    }

    @Test fun hidingOneJobDoesNotCloseAnotherJobsDialog() {
        val shown = UUID.randomUUID()
        DownloadCenter.shownJob.value = shown
        DownloadCenter.hideStatus(UUID.randomUUID())
        assertEquals(shown, DownloadCenter.shownJob.value)
        DownloadCenter.hideStatus(shown)
        assertNull(DownloadCenter.shownJob.value)
        assertEquals(2, DownloadCenter.hiddenStatusJobs.value.size)
    }

    private fun job(id: UUID, state: WorkInfo.State, created: Long) = WorkInfo(
        id = id, state = state, tags = setOf("kruxx_created:$created"),
        outputData = workDataOf("destination" to "Download/KruXx-Downloads/Audio/"),
    )
}
