package app.kreate.android.service

import androidx.media3.exoplayer.offline.Download
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadStateTest {

    @Test
    fun `queued downloading and restarting are shown as pending`() {
        assertTrue(Download.STATE_QUEUED.isDownloadPending())
        assertTrue(Download.STATE_DOWNLOADING.isDownloadPending())
        assertTrue(Download.STATE_RESTARTING.isDownloadPending())
    }

    @Test
    fun `completed and failed downloads are not shown as pending`() {
        assertFalse(Download.STATE_COMPLETED.isDownloadPending())
        assertFalse(Download.STATE_FAILED.isDownloadPending())
    }

    @Test
    fun `completed and pending downloads can be removed`() {
        assertTrue(Download.STATE_COMPLETED.isDownloadRemovable())
        assertTrue(Download.STATE_QUEUED.isDownloadRemovable())
        assertFalse(Download.STATE_FAILED.isDownloadRemovable())
    }
}
