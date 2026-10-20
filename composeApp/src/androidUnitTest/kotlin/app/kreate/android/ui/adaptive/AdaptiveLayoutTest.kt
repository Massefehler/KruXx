package app.kreate.android.ui.adaptive

import app.kreate.android.enums.AppLayoutMode
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun radioPanelsAtNormalDensityUseTheWideLayout() {
        for ((width, height) in listOf(1280f to 648f, 1024f to 528f, 960f to 486f)) {
            val layout = AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height)
            assertTrue(layout.isCarRadio)
            assertTrue(layout.useSideNavigation)
            assertTrue(layout.useTwoPanePlayer)
        }
    }

    @Test
    fun phonesInBothOrientationsKeepTheirExistingPlayer() {
        for ((width, height) in listOf(360f to 728f, 728f to 336f, 412f to 844f, 820f to 380f)) {
            assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, width, height).isCarRadio)
        }
    }

    @Test
    fun windowResizeReevaluatesTheLayoutAtTheExpandedBreakpoint() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 839f, 480f).isCarRadio)
        assertTrue(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 840f, 480f).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 600f, 900f).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 900f, 900f).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1000f, 280f).isCarRadio)
    }

    @Test
    fun manualRadioModeHandlesOverreportedDensity() {
        // 1024×600 at 240dpi, with space removed for Android's system bars.
        val layout = AdaptiveLayout.resolve(AppLayoutMode.CarRadio, 682f, 352f)
        assertTrue(layout.isCarRadio)
        assertTrue(layout.useTwoPanePlayer)
        assertTrue(layout.compactHeight)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 682f, 352f).isCarRadio)
    }

    @Test
    fun manualPhoneModeWorksEvenOnAWidePanel() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Phone, 1280f, 648f).isCarRadio)
    }

    @Test
    fun forcedRadioInANarrowOrPortraitWindowStacksThePlayer() {
        for ((width, height) in listOf(360f to 728f, 599f to 400f, 720f to 1280f)) {
            val layout = AdaptiveLayout.resolve(AppLayoutMode.CarRadio, width, height)
            assertTrue(layout.isCarRadio)
            assertFalse(layout.useTwoPanePlayer)
            assertFalse(layout.useSideNavigation)
        }
    }

    @Test
    fun aWindowThatIsNotYetMeasuredDoesNotActivateRadioMode() {
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.CarRadio, 0f, 480f).isCarRadio)
        assertFalse(AdaptiveLayout.resolve(AppLayoutMode.Automatic, 1280f, 0f).isCarRadio)
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
