package it.fast4x.rimusic.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.kreate.android.R
import it.fast4x.rimusic.ui.styling.kruxxAppBackground
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val SplashHoldMillis = 2_400L
private const val SplashExitMillis = 420

/** One pass of the light from the left to the right edge; two passes fill the hold time. */
private const val SplashSweepMillis = 1_200

private val LightCore = Color(0xFFFFFFFF)
private val LightInner = Color(0xFFD8F1FF)
private val LightOuter = Color(0xFF5FB0FF)
private val LightHalo = Color(0xFF2E8BFF)
private val BarHighlight = Color(0xFFD6F0FF)

/**
 * Path of the light point that travels along the waveform of the KruXx artwork.
 *
 * Everything is a pure function of the pass phase (0..1). The light is drawn at the display
 * refresh rate, so it glides instead of stepping through pre-rendered frames.
 */
internal object SplashLight {
    /** Height of the waveform's centre line in the artwork, measured by scripts/make-kruxx-splash.py. */
    const val WAVE_CENTER_Y = 0.547f

    /** Each pass enters just outside the left edge and leaves beyond the right edge. */
    const val START_X = -0.08f
    const val END_X = 1.08f

    /** Share of a pass during which the light fades in or out. */
    private const val FADE = 0.12f

    /** Horizontal position as a fraction of the artwork width; strictly increasing, never pausing. */
    fun x(phase: Float): Float {
        val p = phase.coerceIn(0f, 1f)
        // Mostly constant speed, softened at both ends.
        val eased = 0.65f * p + 0.35f * smoothstep(p)
        return START_X + (END_X - START_X) * eased
    }

    /** Brightness of the light; zero at both ends of a pass, so the restart is invisible. */
    fun intensity(phase: Float): Float {
        val p = phase.coerceIn(0f, 1f)
        return smoothstep((p / FADE).coerceAtMost(1f)) * smoothstep(((1f - p) / FADE).coerceAtMost(1f))
    }

    /** Glow behind the artwork; strongest while the light crosses the middle of the "K". */
    fun pulse(phase: Float): Float {
        val s = sin(PI.toFloat() * phase.coerceIn(0f, 1f))
        return s * s
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
}

/**
 * A short branded bridge between Android's launch window and the already composing app.
 * Keeping the app underneath avoids turning the requested display time into startup latency.
 */
@Composable
fun AnimatedStartupSplash(
    visible: Boolean,
    appName: String,
    backgroundColor: Color,
    secondaryBackgroundColor: Color,
    isDark: Boolean,
    contentColor: Color,
    accentColor: Color,
    onFinished: () -> Unit,
) {
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(visible) {
        if (visible) {
            delay(SplashHoldMillis)
            currentOnFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f),
        enter = EnterTransition.None,
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = SplashExitMillis,
                easing = FastOutSlowInEasing,
            )
        ),
    ) {
        var introStarted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { introStarted = true }

        // "Remove animations" in Android's accessibility settings sets this scale to 0.
        val context = LocalContext.current
        val animate = remember(context) {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }

        // Two decoded bitmaps: the artwork without the light and the waveform used to brighten it.
        val artwork = painterResource(R.drawable.startup_splash_base)
        val waveform = painterResource(R.drawable.startup_splash_wave)

        val sweep: State<Float> = if (animate) {
            rememberInfiniteTransition(label = "splash light").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = SplashSweepMillis,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "splash light pass",
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }

        val iconScale by animateFloatAsState(
            targetValue = if (introStarted) 1f else 0.965f,
            animationSpec = tween(
                durationMillis = 650,
                easing = FastOutSlowInEasing,
            ),
            label = "splash icon scale",
        )
        val wordmarkAlpha by animateFloatAsState(
            targetValue = if (introStarted) 1f else 0f,
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = 180,
            ),
            label = "splash wordmark alpha",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .kruxxAppBackground(
                    backgroundColor = backgroundColor,
                    secondaryBackgroundColor = secondaryBackgroundColor,
                    isDark = isDark,
                )
                // Do not let touches reach controls composing underneath the splash.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .forEach { it.consume() }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(184.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The accent glow breathes with the light instead of on its own clock.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pulse = SplashLight.pulse(sweep.value)
                                alpha = 0.10f + pulse * 0.10f
                                val glowScale = 0.90f + pulse * 0.07f
                                scaleX = glowScale
                                scaleY = glowScale
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.7f),
                                        Color.Transparent,
                                    )
                                ),
                                shape = CircleShape,
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(176.dp)
                            .graphicsLayer {
                                val animatedScale = iconScale * (1f + SplashLight.pulse(sweep.value) * 0.010f)
                                scaleX = animatedScale
                                scaleY = animatedScale
                            },
                    ) {
                        Image(
                            painter = artwork,
                            contentDescription = appName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        // Reads the pass only while drawing, so a frame costs no recomposition.
                        Spacer(
                            modifier = Modifier
                                .fillMaxSize()
                                .travellingLight(waveform) { sweep.value }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_logo_text),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = wordmarkAlpha
                                translationY = (1f - wordmarkAlpha) * 10.dp.toPx()
                            },
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(contentColor),
                    )
                }
            }
        }
    }
}

/**
 * Adds the light point to the artwork underneath: a blue halo, a long horizontal streak, a white
 * core with a short vertical glint, and brighter waveform bars next to it. All parts are added
 * light ([BlendMode.Plus]), like the light in the source frames.
 */
private fun Modifier.travellingLight(waveform: Painter, phase: () -> Float): Modifier =
    drawWithCache {
        val width = size.width
        val centerY = size.height * SplashLight.WAVE_CENTER_Y
        val bounds = Rect(Offset.Zero, size)
        val addLight = Paint().apply { blendMode = BlendMode.Plus }
        val barTint = ColorFilter.tint(BarHighlight)

        // Brushes are centred on the light, which is then moved with translate().
        val barWindow = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.3f to Color.White.copy(alpha = 0.35f),
            0.5f to Color.White,
            0.7f to Color.White.copy(alpha = 0.35f),
            1f to Color.Transparent,
            startX = -0.16f * width,
            endX = 0.16f * width,
        )
        val halo = radialLight(0.17f * width, 0f to LightHalo.copy(alpha = 0.26f), 1f to Color.Transparent)
        val streak = radialLight(
            0.30f * width,
            0f to LightCore,
            0.12f to LightInner.copy(alpha = 0.9f),
            0.35f to LightOuter.copy(alpha = 0.45f),
            1f to Color.Transparent,
        )
        val core = radialLight(
            0.03f * width,
            0f to LightCore,
            0.5f to Color(0xFFBDE6FF).copy(alpha = 0.55f),
            1f to Color.Transparent,
        )
        val glint = radialLight(0.007f * width, 0f to LightCore.copy(alpha = 0.55f), 1f to Color.Transparent)

        onDrawBehind {
            val pass = phase()
            val strength = SplashLight.intensity(pass)
            if (strength <= 0f) return@onDrawBehind
            val x = SplashLight.x(pass) * width

            clipRect {
                // Waveform bars near the light, masked to a soft window and added to the artwork.
                val canvas = drawContext.canvas
                canvas.saveLayer(bounds, addLight)
                with(waveform) { draw(size, alpha = 0.30f * strength, colorFilter = barTint) }
                translate(left = x) {
                    drawRect(
                        brush = barWindow,
                        topLeft = Offset(-x, 0f),
                        size = size,
                        blendMode = BlendMode.DstIn,
                    )
                }
                canvas.restore()

                translate(left = x, top = centerY) {
                    ellipse(halo, radiusX = 0.17f * width, radiusY = 0.075f * width, strength)
                    ellipse(streak, radiusX = 0.30f * width, radiusY = 0.026f * width, strength)
                    ellipse(core, radiusX = 0.03f * width, radiusY = 0.03f * width, strength)
                    ellipse(glint, radiusX = 0.007f * width, radiusY = 0.055f * width, strength)
                }
            }
        }
    }

private fun radialLight(radius: Float, vararg stops: Pair<Float, Color>): Brush =
    Brush.radialGradient(*stops, center = Offset.Zero, radius = radius)

/** Draws [brush] (a radial gradient of radius [radiusX] around the origin) as an ellipse. */
private fun DrawScope.ellipse(brush: Brush, radiusX: Float, radiusY: Float, strength: Float) {
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = Offset.Zero) {
        drawCircle(
            brush = brush,
            radius = radiusX,
            center = Offset.Zero,
            alpha = strength,
            blendMode = BlendMode.Plus,
        )
    }
}
