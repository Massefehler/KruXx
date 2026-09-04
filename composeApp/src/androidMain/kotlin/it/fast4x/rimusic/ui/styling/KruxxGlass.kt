package it.fast4x.rimusic.ui.styling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import it.fast4x.rimusic.colorPalette
import kotlin.math.max

/**
 * Shared visual tokens for the KruXx skin.
 *
 * The skin is deliberately tied to the independent KruXx flavor so the
 * appearance of upstream Kreate builds remains unchanged.
 */
object KruxxGlass {
    val electricBlue = Color(0xFF3297FF)
    val signalRed = Color(0xFFF04455)

    val headerShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val navigationShape = RoundedCornerShape(24.dp)
    val miniPlayerShape = RoundedCornerShape(20.dp)
    val cardShape = RoundedCornerShape(16.dp)
    val settingsEntryShape = RoundedCornerShape(14.dp)
    val settingsHeaderShape = RoundedCornerShape(14.dp)
    val playerControlsShape = RoundedCornerShape(28.dp)
    val dialogShape = RoundedCornerShape(22.dp)
    val menuShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val surfaceBorderWidth = 1.dp
    val surfaceElevation = 8.dp

    // Only the full-player track menu needs a touch more separation from the
    // artwork below it; keeping this small preserves the glass appearance.
    const val playerMenuBackdropAlpha = 0.08f
}

val isKruxxGlassEnabled: Boolean
    get() = BuildConfig.INDEPENDENT_FORK

/**
 * Keeps content surfaces transparent only in KruXx, allowing the shared
 * atmospheric backdrop to remain visible. Other flavors retain their color.
 */
fun kruxxContentColor(fallback: Color): Color =
    if (isKruxxGlassEnabled) Color.Transparent else fallback

/**
 * One inexpensive, non-animated backdrop for the complete app. The soft
 * radial gradients provide the visual fallback on every supported Android
 * version without applying blur to scrolling content.
 */
@Composable
fun Modifier.kruxxAppBackground(): Modifier {
    val palette = colorPalette()

    return kruxxAppBackground(
        backgroundColor = palette.background0,
        secondaryBackgroundColor = palette.background1,
        isDark = palette.isDark,
    )
}

/**
 * Explicit-color variant for UI that lives outside [LocalAppearance], such as
 * the cold-start overlay. It renders the exact same backdrop without reading
 * a composition local that is not available there yet.
 */
fun Modifier.kruxxAppBackground(
    backgroundColor: Color,
    secondaryBackgroundColor: Color,
    isDark: Boolean,
): Modifier {

    if (!isKruxxGlassEnabled)
        return background(backgroundColor)

    val graphite = if (isDark) Color(0xFF080A10) else backgroundColor
    val lowerSurface = if (isDark) Color(0xFF10131C) else secondaryBackgroundColor
    val blueAlpha = if (isDark) 0.16f else 0.10f
    val redAlpha = if (isDark) 0.11f else 0.07f

    return drawWithCache {
        val extent = max(size.width, size.height)
        val base = Brush.verticalGradient(
            colors = listOf(backgroundColor, graphite, lowerSurface)
        )
        val blueAura = Brush.radialGradient(
            colors = listOf(
                KruxxGlass.electricBlue.copy(alpha = blueAlpha),
                Color.Transparent
            ),
            center = Offset(size.width * 0.92f, size.height * 0.12f),
            radius = extent * 0.58f
        )
        val redAura = Brush.radialGradient(
            colors = listOf(
                KruxxGlass.signalRed.copy(alpha = redAlpha),
                Color.Transparent
            ),
            center = Offset(size.width * 0.08f, size.height * 0.82f),
            radius = extent * 0.52f
        )

        onDrawBehind {
            drawRect(base)
            drawRect(blueAura)
            drawRect(redAura)
        }
    }
}

/**
 * Central glass surface used by static chrome such as headers, navigation and
 * the mini player. It normally uses transparency, contour and shadow rather
 * than a blur pass per component, which keeps the API 23 fallback fast.
 * [backdropAlpha] adds a subtle base beneath the glass gradient when a surface
 * needs slightly more separation from its underlying content. [opaqueBackdrop]
 * uses a fully solid base for sheets whose underlying content must not remain
 * visible.
 */
@Composable
fun Modifier.kruxxGlassSurface(
    fallbackColor: Color,
    shape: Shape = KruxxGlass.cardShape,
    elevated: Boolean = true,
    strong: Boolean = false,
    backdropAlpha: Float = 0f,
    opaqueBackdrop: Boolean = false
): Modifier {
    if (!isKruxxGlassEnabled)
        return clip(shape).background(fallbackColor)

    val palette = colorPalette()
    val surfaceTop = if (palette.isDark)
        Color.White.copy(alpha = if (strong) 0.14f else 0.10f)
    else
        Color.White.copy(alpha = if (strong) 0.82f else 0.68f)
    val surfaceBottom = palette.background1.copy(
        alpha = if (palette.isDark) {
            if (strong) 0.78f else 0.62f
        } else {
            if (strong) 0.88f else 0.72f
        }
    )
    val borderAlpha = if (palette.isDark) 0.24f else 0.34f
    val surfaceBrush = Brush.verticalGradient(
        colors = listOf(surfaceTop, surfaceBottom)
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = borderAlpha),
            KruxxGlass.electricBlue.copy(alpha = borderAlpha),
            KruxxGlass.signalRed.copy(alpha = borderAlpha * 0.55f),
            Color.White.copy(alpha = borderAlpha * 0.45f)
        )
    )

    val shadowed = if (elevated) {
        shadow(
            elevation = KruxxGlass.surfaceElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.40f),
            spotColor = KruxxGlass.electricBlue.copy(alpha = 0.12f)
        )
    } else {
        this
    }

    val clipped = shadowed.clip(shape)
    val backed = when {
        opaqueBackdrop -> clipped.background(fallbackColor)
        backdropAlpha > 0f -> clipped.background(
            fallbackColor.copy(alpha = backdropAlpha.coerceIn(0f, 1f))
        )
        else -> clipped
    }

    return backed
        .background(surfaceBrush)
        .border(KruxxGlass.surfaceBorderWidth, borderBrush, shape)
}

/** Lightweight card treatment without an individual shadow or blur pass. */
@Composable
fun Modifier.kruxxGlassCard(shape: Shape = KruxxGlass.cardShape): Modifier =
    if (isKruxxGlassEnabled)
        kruxxGlassSurface(
            fallbackColor = Color.Transparent,
            shape = shape,
            elevated = false
        )
    else
        this

/** A regular content card which preserves the original surface in other builds. */
@Composable
fun Modifier.kruxxCardSurface(
    fallbackColor: Color,
    shape: Shape = KruxxGlass.cardShape
): Modifier =
    if (isKruxxGlassEnabled)
        kruxxGlassCard(shape)
    else
        background(fallbackColor, shape)

/** Opaque enough to keep content below a sticky settings header readable. */
@Composable
fun Modifier.kruxxSettingsHeader(fallbackColor: Color): Modifier =
    if (isKruxxGlassEnabled)
        kruxxGlassSurface(
            fallbackColor = fallbackColor,
            shape = KruxxGlass.settingsHeaderShape,
            elevated = false,
            strong = true
        )
    else
        background(fallbackColor)

/**
 * A compact card for settings rows. Spacing is part of the KruXx skin and is
 * therefore not applied to upstream builds.
 */
@Composable
fun Modifier.kruxxSettingsEntry(): Modifier =
    if (isKruxxGlassEnabled)
        padding(horizontal = 6.dp, vertical = 3.dp)
            .kruxxGlassCard(KruxxGlass.settingsEntryShape)
    else
        this

/** Shared dialog treatment with the original shape retained for other skins. */
@Composable
fun Modifier.kruxxDialogSurface(
    fallbackColor: Color,
    fallbackShape: Shape = RoundedCornerShape(8.dp)
): Modifier =
    if (isKruxxGlassEnabled)
        kruxxGlassSurface(
            fallbackColor = fallbackColor,
            shape = KruxxGlass.dialogShape,
            strong = true
        )
    else
        background(fallbackColor, fallbackShape)

/** Floating glass panel for the metadata, timeline and transport controls. */
@Composable
fun Modifier.kruxxPlayerControls(): Modifier =
    if (isKruxxGlassEnabled)
        fillMaxWidth()
            .padding(horizontal = 10.dp)
            .kruxxGlassSurface(
                fallbackColor = colorPalette().background1,
                shape = KruxxGlass.playerControlsShape,
                elevated = false,
                strong = true
            )
            .padding(vertical = 10.dp)
    else
        this
