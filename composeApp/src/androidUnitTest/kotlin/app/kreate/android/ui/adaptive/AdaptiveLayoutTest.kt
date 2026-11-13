package app.kreate.android.ui.adaptive

import android.view.Surface
import app.kreate.android.enums.AppLayoutMode
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun radioPanelsAtNormalDensityUseTheWideLayout() {
        for ((width, height) in listOf(1280f to 648f, 1024f to 528f, 960f to 486f)) {
            val layout = AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height, naturallyLandscape = true)
            assertTrue(layout.isCarRadio)
            assertTrue(layout.useSideNavigation)
            assertTrue(layout.useTwoPanePlayer)
        }
    }

    @Test
    fun radioPanelsWithOverreportedDensityAreDetectedAutomatically() {
        // 1024×600 and 1280×720 at 240 dpi and an ultra-wide 1920×720 panel, after system bars.
        for ((width, height) in listOf(682f to 328f, 853f to 408f, 1280f to 408f)) {
            assertTrue(AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height, naturallyLandscape = true).isCarRadio)
        }
    }

    @Test
    fun phonesNeverSwitchAutomaticallyInAnyOrientation() {
        // Includes a Galaxy S25 held sideways (2340×1080 at 420 dpi), wider than any old breakpoint.
        for ((width, height) in listOf(360f to 728f, 728f to 336f, 412f to 844f, 820f to 380f, 891f to 371f, 998f to 432f)) {
            assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height, naturallyLandscape = false).isCarRadio)
        }
    }

    @Test
    fun nearSquareAndPortraitWindowsKeepThePhoneLayout() {
        // An unfolded naturally landscape foldable, a 4:3 tablet, a portrait and a square window.
        for ((width, height) in listOf(841f to 650f, 1024f to 720f, 600f to 900f, 900f to 900f)) {
            assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height, naturallyLandscape = true).isCarRadio)
        }
    }

    @Test
    fun windowResizeReevaluatesTheLayoutAtTheBreakpoints() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 599f, 360f, naturallyLandscape = true).isCarRadio)
        assertTrue(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 600f, 360f, naturallyLandscape = true).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1000f, 319f, naturallyLandscape = true).isCarRadio)
        assertTrue(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1000f, 320f, naturallyLandscape = true).isCarRadio)
    }

    @Test
    fun manualRadioModeWorksOnAnyDisplay() {
        val layout = AdaptiveLayout.resolve(AppLayoutMode.CarRadio, 682f, 352f, naturallyLandscape = false)
        assertTrue(layout.isCarRadio)
        assertTrue(layout.useTwoPanePlayer)
        assertTrue(layout.compactHeight)
    }

    @Test
    fun manualPhoneModeWorksEvenOnAWidePanel() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Phone, 1280f, 648f, naturallyLandscape = true).isCarRadio)
    }

    @Test
    fun forcedRadioInANarrowOrPortraitWindowStacksThePlayer() {
        for ((width, height) in listOf(360f to 728f, 599f to 400f, 720f to 1280f)) {
            val layout = AdaptiveLayout.resolve(AppLayoutMode.CarRadio, width, height, naturallyLandscape = false)
            assertTrue(layout.isCarRadio)
            assertFalse(layout.useTwoPanePlayer)
            assertFalse(layout.useSideNavigation)
        }
    }

    @Test
    fun aWindowThatIsNotYetMeasuredDoesNotActivateRadioMode() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.CarRadio, 0f, 480f, naturallyLandscape = true).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1280f, 0f, naturallyLandscape = true).isCarRadio)
    }

    @Test
    fun naturalOrientationIgnoresHowTheDeviceIsHeld() {
        assertFalse(isNaturallyLandscape(Surface.ROTATION_0, 1080, 2340))
        assertFalse(isNaturallyLandscape(Surface.ROTATION_90, 2340, 1080))
        assertFalse(isNaturallyLandscape(Surface.ROTATION_270, 2340, 1080))
        assertTrue(isNaturallyLandscape(Surface.ROTATION_0, 1024, 600))
        assertTrue(isNaturallyLandscape(Surface.ROTATION_180, 1280, 720))
        // A naturally landscape tablet held upright.
        assertTrue(isNaturallyLandscape(Surface.ROTATION_90, 800, 1280))
    }

    @Test
    fun libraryColumnsRespectContentWidthAndLargerFonts() {
        assertEquals(3, radioSongColumns(1152f, 1f))
        assertEquals(2, radioSongColumns(896f, 1f))
        assertEquals(1, radioSongColumns(554f, 1f))
        assertEquals(1, radioSongColumns(896f, 1.5f))
        assertEquals(1, radioSongColumns(200f, 2f))
        assertEquals(3, radioSongColumns(2400f, 1f))
    }
}
