package app.kreate.android.utils

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.kreate.android.Preferences
import app.kreate.android.ui.adaptive.CarRadioMode

/**
 * Apply marquee effect (scrolling text) if
 * size exceeds viewable area and only when
 * setting [Preferences.MARQUEE_TEXT_EFFECT] is **enabled**.
 *
 * The car radio layout never scrolls text: in-vehicle guidelines rule out automatically
 * moving text while driving, so long text is cut there instead (see [scrollingTextOverflow]).
 *
 * @param iterations The number of times to repeat the animation. `Int.MAX_VALUE` will repeat
 *   forever, and 0 will disable animation
 *
 * @see basicMarquee
 */
fun Modifier.scrollingText( iterations: Int = Int.MAX_VALUE ) =
    if( Preferences.MARQUEE_TEXT_EFFECT.value && !CarRadioMode.active )
        this.basicMarquee( iterations = iterations )
    else
        this

/** Overflow for single-line text using [scrollingText]: ellipsis where the radio layout cuts it. */
val scrollingTextOverflow: TextOverflow
    get() = if( CarRadioMode.active ) TextOverflow.Ellipsis else TextOverflow.Clip
