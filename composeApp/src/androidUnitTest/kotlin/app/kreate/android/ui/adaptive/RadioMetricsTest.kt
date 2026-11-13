package app.kreate.android.ui.adaptive

import app.kreate.android.enums.AppLayoutMode
import org.junit.Assert.*
import org.junit.Test

class RadioMetricsTest {
    @Test
    fun theReferenceHeadUnitUsesTheBaseSizes() {
        val metrics = RadioMetrics.from(RadioMetrics.REFERENCE_HEIGHT_DP)
        assertEquals(1f, metrics.scale, 0.001f)
        assertEquals(1f, metrics.textScale, 0.001f)
        assertEquals(88f, metrics.railWidth.value, 0.001f)
        assertEquals(52f, metrics.controlButton.value, 0.001f)
        assertEquals(68f, metrics.playButton.value, 0.001f)
        assertEquals(24f, metrics.title.value, 0.001f)
    }

    @Test
    fun onePanelLooksAlikeAtEveryDensity() {
        // A 1280×720 head unit (Dynavin D9 class) after system bars, at 160 and at 240 dpi.
        val at160 = RadioMetrics.from(648f)
        val at240 = RadioMetrics.from(408f)
        fun px(dp: Float, dpi: Int) = dp * dpi / 160f
        assertEquals(px(at160.playButton.value, 160), px(at240.playButton.value, 240), 8f)
        assertEquals(px(at160.tileSize.value, 160), px(at240.tileSize.value, 240), 12f)
        // The rail keeps Material's 80 dp so its labels still fit.
        assertEquals(80f, at240.railWidth.value, 0.001f)
    }

    @Test
    fun shortWindowsShrinkButKeepTouchTargetsAndReadableText() {
        // 1024×600 at 240 dpi after system bars.
        val metrics = RadioMetrics.from(328f)
        assertEquals(RadioMetrics.MIN_SCALE, metrics.scale, 0.001f)
        assertEquals(RadioMetrics.MIN_TOUCH_DP, metrics.controlButton.value, 0.001f)
        assertEquals(RadioMetrics.MIN_TOUCH_DP, metrics.railItemHeight.value, 0.001f)
        assertEquals(RadioMetrics.MIN_TOUCH_DP, metrics.seekBarHeight.value, 0.001f)
        assertEquals(56f, metrics.playButton.value, 0.001f)
        // Reading text never drops below the phone sizes.
        assertEquals(1f, metrics.textScale, 0.001f)
        assertEquals(20f, metrics.title.value, 0.001f)
    }

    @Test
    fun theSizeSettingScalesEverythingWithinItsRange() {
        assertEquals(.8f, RadioMetrics.from(528f, 80f).scale, 0.001f)
        assertEquals(.8f, RadioMetrics.from(528f, 80f).textScale, 0.001f)
        assertEquals(1.3f, RadioMetrics.from(528f, 130f).scale, 0.001f)
        assertEquals(RadioMetrics.from(528f, 130f), RadioMetrics.from(528f, 200f))
        assertEquals(RadioMetrics.from(528f, 80f), RadioMetrics.from(528f, 10f))
    }

    @Test
    fun metricsAreComparedByValue() {
        assertEquals(RadioMetrics.from(500f), RadioMetrics.from(500f))
        assertNotEquals(RadioMetrics.from(500f), RadioMetrics.from(450f))
        assertNotEquals(RadioMetrics.from(500f), RadioMetrics.from(500f, 90f))
    }

    @Test
    fun contentWidthLeavesRoomForTheSideNavigation() {
        val layout = AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1024f, 528f, naturallyLandscape = true)
        assertEquals(1024f - layout.metrics.railWidth.value, layout.contentWidthDp, 0.001f)
        assertEquals(2, radioSongColumns(layout.contentWidthDp, 1f, layout.metrics.songColumnWidth.value))
    }

    @Test
    fun phonesKeepTheReferenceMetricsAndTheirFullWidth() {
        val layout = AdaptiveLayout.resolve(AppLayoutMode.Automatic, 891f, 371f, naturallyLandscape = false, radioSizePercent = 130f)
        assertEquals(RadioMetrics.Reference, layout.metrics)
        assertEquals(891f, layout.contentWidthDp, 0.001f)
    }
}
