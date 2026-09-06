package app.kreate.android.downloads

import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    @Test fun downloadedSourceIsNotAFinishedExternalMp3() {
        val plan = FileProgressPlan(DownloadKind.MP3, existing = false, copy = false, external = true)
        assertTrue(plan.fraction(DownloadStep.DOWNLOADING, 1.0) < 1.0)
        assertTrue(plan.fraction(DownloadStep.CONVERTING, 1.0) < 1.0)
        assertTrue(plan.fraction(DownloadStep.SAVING, 1.0) < 1.0)
        assertEquals(99, batchPercent(0, 1, plan.fraction(DownloadStep.VERIFYING, 1.0)))
    }

    @Test fun cachedAssetStillNeedsExportAndVerification() {
        val plan = FileProgressPlan(DownloadKind.MP3, existing = true, copy = true, external = true)
        assertEquals(listOf(DownloadStep.SAVING, DownloadStep.VERIFYING), plan.steps)
        assertTrue(plan.fraction(DownloadStep.SAVING, 1.0) < 1.0)
    }

    @Test fun internalOriginalDoesNotRenderOrExport() {
        val plan = FileProgressPlan(DownloadKind.ORIGINAL, existing = false, copy = false, external = false)
        assertEquals(listOf(DownloadStep.DOWNLOADING), plan.steps)
        assertEquals(50, batchPercent(0, 1, plan.fraction(DownloadStep.DOWNLOADING, 0.5)))
    }

    @Test fun copyUsesCachedSourceWithoutNetworkStep() {
        val plan = FileProgressPlan(DownloadKind.MP3, existing = false, copy = true, external = true)
        assertFalse(DownloadStep.DOWNLOADING in plan.steps)
        assertTrue(DownloadStep.CONVERTING in plan.steps)
    }

    @Test fun videoIncludesTransferAndMuxing() {
        val plan = FileProgressPlan(DownloadKind.VIDEO, existing = false, copy = false, external = true)
        assertTrue(DownloadStep.VIDEO in plan.steps)
        assertTrue(DownloadStep.JOINING in plan.steps)
        assertTrue(plan.fraction(DownloadStep.JOINING, 0.0) >= plan.fraction(DownloadStep.VIDEO, 1.0))
    }

    @Test fun completeFirstTrackDoesNotCompleteBatch() {
        assertEquals(50, batchPercent(0, 2, 1.0))
        assertEquals(75, batchPercent(1, 2, 0.5))
        assertEquals(99, batchPercent(1, 2, 1.0))
    }

    @Test fun unknownLengthNeverInventsProgress() {
        assertEquals(0.0, byteFraction(100, -1), 0.0)
        assertEquals(0.0, byteFraction(100, 0), 0.0)
        assertEquals(1.0, byteFraction(300, 200), 0.0)
        assertEquals(0.5, byteFraction(Long.MAX_VALUE / 2, Long.MAX_VALUE), 0.00001)
    }
}
