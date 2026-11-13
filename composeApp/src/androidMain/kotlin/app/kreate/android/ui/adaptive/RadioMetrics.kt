package app.kreate.android.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every car-radio size, derived from the window instead of being tuned for one panel.
 *
 * Sizes are proportional to the window height, so one head unit looks the same whichever
 * density its maker chose: a 1280×720 panel is about 648 dp tall at 160 dpi and 408 dp at
 * 240 dpi. The reference is a 1024×600 unit at 160 dpi, [REFERENCE_HEIGHT_DP] tall after
 * Android's system bars. Touch targets never drop below [MIN_TOUCH_DP] and reading text never
 * below the phone sizes (ISO 15008 legibility); [userScale] is the size setting on top.
 * The phone layout never reads these values.
 */
@Immutable
class RadioMetrics private constructor(val scale: Float, val textScale: Float) {
    // Navigation shell; Material's navigation rail is 80 dp, which still fits two-line labels.
    val railWidth: Dp = maxOf(80f, 88 * scale).dp
    val railItemHeight: Dp = touchTarget(56 * scale)
    val railIcon: Dp = (24 * scale).dp
    val railLabel: TextUnit = (12 * textScale).sp
    val topBarHeight: Dp = touchTarget(52 * scale)
    val topBarIcon: Dp = (24 * scale).dp
    val wordmark: TextUnit = (20 * textScale).sp

    // Player
    val padding: Dp = (16 * scale).dp
    val gap: Dp = (12 * scale).dp
    val controlButton: Dp = touchTarget(52 * scale)
    val controlIcon: Dp = (26 * scale).dp
    val playButton: Dp = maxOf(56f, 68 * scale).dp
    val playIcon: Dp = (34 * scale).dp
    val seekBarHeight: Dp = touchTarget(40 * scale)
    /** Android Auto and Android Automotive keep the artwork to about a third of the height. */
    val coverHeightFraction: Float = .45f
    /** Android Automotive caps its control bar at about 1028 dp and centres it. */
    val transportMaxWidth: Dp = (840 * scale).dp
    val title: TextUnit = maxOf(20f, 24 * scale).sp
    val artist: TextUnit = maxOf(15f, 18 * scale).sp
    val detail: TextUnit = maxOf(13f, 15 * scale).sp
    val time: TextUnit = maxOf(12f, 13 * scale).sp

    // Library
    val tileSize: Dp = (128 * scale).dp
    val songColumnWidth: Dp = (360 * scale).dp

    // Compared by value only: an equal value must not invalidate everything reading the local.
    override fun equals(other: Any?) = other is RadioMetrics && other.scale == scale && other.textScale == textScale
    override fun hashCode() = 31 * scale.hashCode() + textScale.hashCode()

    companion object {
        const val REFERENCE_HEIGHT_DP = 528f
        const val MIN_TOUCH_DP = 48f
        const val MIN_SCALE = .75f
        const val MAX_SCALE = 1.25f
        const val MIN_USER_PERCENT = 80f
        const val MAX_USER_PERCENT = 130f

        val Reference = RadioMetrics(1f, 1f)

        fun from(heightDp: Float, userPercent: Float = 100f): RadioMetrics {
            val user = userPercent.coerceIn(MIN_USER_PERCENT, MAX_USER_PERCENT) / 100f
            val window = (heightDp / REFERENCE_HEIGHT_DP).coerceIn(MIN_SCALE, MAX_SCALE)
            return RadioMetrics(window * user, maxOf(1f, window) * user)
        }

        private fun touchTarget(size: Float): Dp = maxOf(MIN_TOUCH_DP, size).dp
    }
}
