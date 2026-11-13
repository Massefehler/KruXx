package app.kreate.android.ui.adaptive

import android.view.Surface
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.kreate.android.enums.AppLayoutMode

/** Available window space in dp, after system bars. Never infer inches from display pixels. */
@Immutable
data class AdaptiveLayout(
    val isCarRadio: Boolean = false,
    val useSideNavigation: Boolean = false,
    val useTwoPanePlayer: Boolean = false,
    val compactHeight: Boolean = false,
    val metrics: RadioMetrics = RadioMetrics.Reference,
    /** Width left for screen content next to the radio's side navigation. */
    val contentWidthDp: Float = 0f,
) {
    companion object {
        /**
         * [naturallyLandscape] keeps phones out of Automatic in every rotation: a phone held
         * sideways can be wider than a head unit in dp, but its display is naturally portrait.
         * The aspect ratio also excludes near-square foldables and 4:3 tablets.
         */
        fun resolve(
            mode: AppLayoutMode,
            widthDp: Float,
            heightDp: Float,
            naturallyLandscape: Boolean,
            radioSizePercent: Float = 100f,
        ): AdaptiveLayout {
            val validWindow = widthDp > 0 && heightDp > 0
            val radio = validWindow && when (mode) {
                AppLayoutMode.Automatic -> naturallyLandscape && widthDp >= 600 && heightDp >= 320 &&
                    widthDp >= heightDp * 1.5f
                AppLayoutMode.Phone -> false
                AppLayoutMode.CarRadio -> true
            }
            val sideNavigation = radio && widthDp >= 600 && widthDp > heightDp
            val metrics = if (radio) RadioMetrics.from(heightDp, radioSizePercent) else RadioMetrics.Reference
            return AdaptiveLayout(
                isCarRadio = radio,
                useSideNavigation = sideNavigation,
                useTwoPanePlayer = sideNavigation,
                compactHeight = heightDp < 480,
                metrics = metrics,
                contentWidthDp = if (sideNavigation) widthDp - metrics.railWidth.value else widthDp,
            )
        }
    }
}

val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayout() }

/**
 * Mirrors the car radio layout for helpers without composition access, such as
 * [app.kreate.android.utils.scrollingText] and the item thumbnail sizes; reading it during
 * composition tracks changes. Both values stay at their phone defaults outside radio mode.
 */
object CarRadioMode {
    var active by mutableStateOf(false)
    /** The radio's own navigation rail is shown, always on the left. */
    var sideNavigation by mutableStateOf(false)
    var thumbnailScale by mutableFloatStateOf(1f)

    fun update(layout: AdaptiveLayout) {
        active = layout.isCarRadio
        sideNavigation = layout.useSideNavigation
        thumbnailScale = if (layout.isCarRadio) layout.metrics.scale else 1f
    }
}

/** The display's unrotated shape, from its current [rotation] and size in that rotation. */
fun isNaturallyLandscape(rotation: Int, displayWidthPx: Int, displayHeightPx: Int): Boolean =
    if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) displayHeightPx > displayWidthPx
    else displayWidthPx > displayHeightPx

/** Leave enough width for readable track titles, including enlarged system fonts. */
internal fun radioSongColumns(widthDp: Float, fontScale: Float, columnWidthDp: Float = 360f): Int =
    (widthDp / (columnWidthDp * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
