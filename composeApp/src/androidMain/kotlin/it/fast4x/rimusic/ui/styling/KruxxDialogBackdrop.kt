package it.fast4x.rimusic.ui.styling

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import it.fast4x.compose.persist.findActivityNullable
import java.util.WeakHashMap

/** Blur the activity underneath the separate dialog window, keeping its controls sharp. */
@Composable
internal fun KruxxDialogBackdrop() {
    if (!isKruxxGlassEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val background = LocalContext.current.findActivityNullable()?.window?.decorView ?: return
    val radius = with(LocalDensity.current) { 16.dp.toPx().coerceAtMost(64f) }
    DisposableEffect(background, radius) {
        DialogBackgroundBlur.acquire(background, radius)
        onDispose { DialogBackgroundBlur.release(background) }
    }
}

/** All calls run on the UI thread. Overlapping dialogs must not clear each other's blur. */
@RequiresApi(Build.VERSION_CODES.S)
private object DialogBackgroundBlur {
    private val users = WeakHashMap<View, Int>()

    fun acquire(view: View, radius: Float) {
        val count = users[view] ?: 0
        if (count == 0) {
            // This component owns the activity decor's effect; player effects live on
            // their own Compose layers. No cross-window blur support is required.
            view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
        users[view] = count + 1
    }

    fun release(view: View) {
        val remaining = (users[view] ?: return) - 1
        if (remaining == 0) {
            users.remove(view)
            view.setRenderEffect(null)
        } else {
            users[view] = remaining
        }
    }
}
