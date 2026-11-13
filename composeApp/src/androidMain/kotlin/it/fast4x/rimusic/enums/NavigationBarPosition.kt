package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.ui.adaptive.CarRadioMode
import me.knighthat.enums.TextView

enum class NavigationBarPosition(
    @field:StringRes override val androidTextId: Int
): TextView {

    Left( R.string.direction_left ),
    Right( R.string.direction_right ),
    Top( R.string.direction_top ),
    Bottom( R.string.direction_bottom );

    companion object {

        /**
         * The car radio's rail sits on the left, whatever the phone setting is, so screens
         * reserve no space for a phone navigation bar that is not shown there.
         */
        fun current() =
            if( CarRadioMode.sideNavigation ) Left
            else Preferences.NAVIGATION_BAR_POSITION.value
    }

    fun isCurrent(): Boolean = current() == this
}