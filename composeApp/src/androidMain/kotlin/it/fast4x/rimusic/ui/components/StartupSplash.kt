package it.fast4x.rimusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.kreate.android.R
import kotlinx.coroutines.delay

private const val SplashHoldMillis = 2_400L
private const val SplashExitMillis = 420
private const val SplashFrameMillis = 120L

/**
 * A short branded bridge between Android's launch window and the already composing app.
 * Keeping the app underneath avoids turning the requested display time into startup latency.
 */
@Composable
fun AnimatedStartupSplash(
    visible: Boolean,
    appName: String,
    backgroundColor: Color,
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

        // Give every frame its own stable composition slot so bitmap decoding only happens once.
        val framePainters = listOf(
            painterResource(R.drawable.startup_splash_frame_1),
            painterResource(R.drawable.startup_splash_frame_2),
            painterResource(R.drawable.startup_splash_frame_3),
            painterResource(R.drawable.startup_splash_frame_4),
            painterResource(R.drawable.startup_splash_frame_5),
            painterResource(R.drawable.startup_splash_frame_6),
            painterResource(R.drawable.startup_splash_frame_7),
            painterResource(R.drawable.startup_splash_frame_8),
            painterResource(R.drawable.startup_splash_frame_9),
            painterResource(R.drawable.startup_splash_frame_10),
        )
        var frameIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(SplashFrameMillis)
                frameIndex = (frameIndex + 1) % framePainters.size
            }
        }

        val iconScale by animateFloatAsState(
            targetValue = if (introStarted) 1f else 0.94f,
            animationSpec = tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing,
            ),
            label = "splash icon scale",
        )
        val wordmarkAlpha by animateFloatAsState(
            targetValue = if (introStarted) 1f else 0f,
            animationSpec = tween(
                durationMillis = 450,
                delayMillis = 160,
            ),
            label = "splash wordmark alpha",
        )

        val pulseTransition = rememberInfiniteTransition(label = "splash pulse")
        val pulse by pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 760,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "splash pulse progress",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.12f + pulse * 0.12f
                                val glowScale = 0.88f + pulse * 0.08f
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

                    Image(
                        painter = framePainters[frameIndex],
                        contentDescription = appName,
                        modifier = Modifier
                            .size(176.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .graphicsLayer {
                                val animatedScale = iconScale * (1f + pulse * 0.018f)
                                scaleX = animatedScale
                                scaleY = animatedScale
                            },
                        contentScale = ContentScale.Fit,
                    )
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
