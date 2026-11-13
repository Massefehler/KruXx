package app.kreate.android.ui.adaptive

import android.app.Activity
import android.graphics.Point
import android.os.Build

/** Reads the whole display, not the window, so split-screen does not change the answer. */
fun Activity.isDisplayNaturallyLandscape(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.maximumWindowMetrics.bounds
        isNaturallyLandscape(display?.rotation ?: 0, bounds.width(), bounds.height())
    } else {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        isNaturallyLandscape(display.rotation, size.x, size.y)
    }
