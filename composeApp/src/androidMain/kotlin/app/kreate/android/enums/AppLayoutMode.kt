package app.kreate.android.enums

import app.kreate.android.R
import me.knighthat.enums.TextView

enum class AppLayoutMode(override val androidTextId: Int) : TextView {
    Automatic(R.string.layout_mode_automatic),
    Phone(R.string.layout_mode_phone),
    CarRadio(R.string.layout_mode_car_radio)
}
