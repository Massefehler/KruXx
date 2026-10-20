package app.kreate.android.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.kreate.android.enums.AppLayoutMode

/** Available window space in dp, after system bars. Never infer inches from display pixels. */
@Immutable
data class AdaptiveLayout(
    val isCarRadio: Boolean = false,
    val useSideNavigation: Boolean = false,
    val useTwoPanePlayer: Boolean = false,
    val compactHeight: Boolean = false,
) {
    companion object {
        fun resolve(mode: AppLayoutMode, widthDp: Float, heightDp: Float): AdaptiveLayout {
            val validWindow = widthDp > 0 && heightDp > 0
            val radio = validWindow && when (mode) {
                AppLayoutMode.Automatic -> widthDp >= 840 && widthDp > heightDp && heightDp >= 320
                AppLayoutMode.Phone -> false
                AppLayoutMode.CarRadio -> true
            }
            return AdaptiveLayout(
                isCarRadio = radio,
                useSideNavigation = radio && widthDp >= 600 && widthDp > heightDp,
                useTwoPanePlayer = radio && widthDp >= 600 && widthDp > heightDp,
                compactHeight = heightDp < 480,
            )
        }
    }
}

val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayout() }

/** Leave enough width for readable track titles, including enlarged system fonts. */
internal fun radioSongColumns(widthDp: Float, fontScale: Float): Int =
    (widthDp / (360 * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 3)
