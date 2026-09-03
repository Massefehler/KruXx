package it.fast4x.rimusic.extensions.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.OnPictureInPictureModeChangedProvider
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.graphics.drawable.toIcon
import androidx.core.graphics.toRect
import app.kreate.android.Preferences
import app.kreate.android.drawable.AppIcon
import it.fast4x.compose.persist.findActivityNullable
import it.fast4x.rimusic.utils.ActionReceiver
import it.fast4x.rimusic.utils.findActivity
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.isAtLeastAndroid8


private fun logError(throwable: Throwable) = Log.e("PipHandler", "An error occurred", throwable)

@Suppress("DEPRECATION")
fun Activity.maybeEnterPip() = when {
    !isAtLeastAndroid7 -> false
    !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) -> false
    else -> runCatching {
        if (isAtLeastAndroid8) enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        else enterPictureInPictureMode()
    }.onFailure(::logError).isSuccess
}

fun Activity.setPipParams(
    rect: Rect?,
    targetNumerator: Int,
    targetDenominator: Int,
    actions: ActionReceiver? = null,
    autoEnterIfPossible: Boolean = false,
) {
    if (!isAtLeastAndroid8) return

    val activity = this
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(targetNumerator, targetDenominator))

    if (rect != null && rect.width() > 0 && rect.height() > 0)
        params.setSourceRectHint(rect)

    if (actions != null)
        params.setActions(
            actions.all.values.map {
                RemoteAction(
                    it.icon ?: AppIcon.Round.bitmap(activity).toIcon(),
                    it.title.orEmpty(),
                    it.contentDescription.orEmpty(),
                    with(activity) { it.pendingIntent }
                )
            }
        )

    if (isAtLeastAndroid12)
        params
            .setAutoEnterEnabled(autoEnterIfPossible)
            .setSeamlessResizeEnabled(true)

    setPictureInPictureParams(params.build())
}

fun Activity.maybeExitPip() = when {
    !isAtLeastAndroid7 -> false
    !isInPictureInPictureMode -> false
    else -> runCatching {
        moveTaskToBack(false)
        application.startActivity(
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }.onFailure(::logError).isSuccess
}

@Composable
fun rememberPipHandler(key: Any = Unit): PipHandler {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityNullable() }
    return remember(activity, key) {
        PipHandler(
            enterPip = { activity?.maybeEnterPip() },
            exitPip = { activity?.maybeExitPip() }
        )
    }
}

@Immutable
data class PipHandler (
    private val enterPip: () -> Boolean?,
    private val exitPip: () -> Boolean?
) {
    fun enterPictureInPictureMode() = enterPip() == true
    fun exitPictureInPictureMode() = exitPip() == true
}

private val Activity?.pip get() = if (isAtLeastAndroid7) this?.isInPictureInPictureMode == true else false

@Composable
fun isInPip(
    onChange: (Boolean) -> Unit = { }
): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityNullable() }
    val currentOnChange by rememberUpdatedState(onChange)
    var pip by rememberSaveable { mutableStateOf(activity.pip) }

    DisposableEffect(activity, currentOnChange) {
        if (activity !is OnPictureInPictureModeChangedProvider) return@DisposableEffect onDispose { }

        val listener: (PictureInPictureModeChangedInfo) -> Unit = {
            pip = it.isInPictureInPictureMode
            currentOnChange(pip)
        }
        activity.addOnPictureInPictureModeChangedListener(listener)

        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    println("isInPIp pip: $pip")

    return pip
}

@Composable
fun Pip(
    numerator: Int,
    denominator: Int,
    modifier: Modifier = Modifier,
    actions: ActionReceiver? = null,
    autoEnterIfPossible: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val enablePictureInPicture by Preferences.IS_PIP_ENABLED
    val enablePictureInPictureAuto by Preferences.IS_AUTO_PIP_ENABLED
    var sourceRect by remember(activity) { mutableStateOf<Rect?>(null) }
    val shouldAutoEnter = autoEnterIfPossible &&
            enablePictureInPicture &&
            enablePictureInPictureAuto

    // Android 12+ handles automatic PiP through PictureInPictureParams. Older
    // supported versions require an explicit request when the user leaves the app.
    DisposableEffect(activity, shouldAutoEnter) {
        val componentActivity = activity as? ComponentActivity
        if (
            componentActivity == null ||
            !shouldAutoEnter ||
            !isAtLeastAndroid7 ||
            isAtLeastAndroid12
        ) return@DisposableEffect onDispose { }

        val enterPipOnLeave = Runnable { activity.maybeEnterPip() }
        componentActivity.addOnUserLeaveHintListener(enterPipOnLeave)

        onDispose {
            componentActivity.removeOnUserLeaveHintListener(enterPipOnLeave)
        }
    }

    DisposableEffect(context, actions) {
        actions?.register(context)
        onDispose {
            actions?.let { context.unregisterReceiver(it) }
        }
    }

    // Keep Android's PiP parameters in sync with preference and playback
    // changes even when the composable's position itself did not change.
    DisposableEffect(
        activity,
        sourceRect,
        numerator,
        denominator,
        actions,
        shouldAutoEnter
    ) {
        activity.setPipParams(
            rect = sourceRect,
            targetNumerator = numerator,
            targetDenominator = denominator,
            actions = actions,
            autoEnterIfPossible = shouldAutoEnter
        )
        onDispose { }
    }

    Box(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            val newRect = layoutCoordinates
                .boundsInWindow()
                .toAndroidRectF()
                .toRect()
            if (sourceRect != newRect) sourceRect = newRect
        },
        content = content
    )
}
