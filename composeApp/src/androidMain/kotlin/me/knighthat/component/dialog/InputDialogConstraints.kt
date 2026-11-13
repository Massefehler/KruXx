package me.knighthat.component.dialog

import androidx.annotation.StringDef
import me.knighthat.component.dialog.InputDialogConstraints.Companion.ALBUM_THUMBNAIL_SIZE
import me.knighthat.component.dialog.InputDialogConstraints.Companion.ALL
import me.knighthat.component.dialog.InputDialogConstraints.Companion.ANDROID_FILE_PATH
import me.knighthat.component.dialog.InputDialogConstraints.Companion.CAR_RADIO_SIZE_PERCENT
import me.knighthat.component.dialog.InputDialogConstraints.Companion.POSITIVE_DECIMAL
import me.knighthat.component.dialog.InputDialogConstraints.Companion.POSITIVE_INTEGER
import me.knighthat.component.dialog.InputDialogConstraints.Companion.SONG_THUMBNAIL_SIZE
import me.knighthat.component.dialog.InputDialogConstraints.Companion.THUMBNAIL_ROUNDNESS_PERCENT


@StringDef(
    ALL,
    ANDROID_FILE_PATH,
    POSITIVE_DECIMAL,
    POSITIVE_INTEGER,
    THUMBNAIL_ROUNDNESS_PERCENT,
    ALBUM_THUMBNAIL_SIZE,
    SONG_THUMBNAIL_SIZE,
    CAR_RADIO_SIZE_PERCENT
)
annotation class InputDialogConstraints {

    companion object {
        /**
         * This REGEX pattern allows all characters
         */
        const val ALL = ".*";

        const val ANDROID_FILE_PATH = """^\/[\w\-]*(?:\/[\w\-\.]+)*$"""

        const val POSITIVE_DECIMAL = """^(\d+\.\d+|\d+\.|\.\d+|d+|\d+|\.|)$"""

        const val POSITIVE_INTEGER = """^\d*$"""

        /**
         * Matches number from 0 to 50 inclusively. Empty is allowed
         */
        const val THUMBNAIL_ROUNDNESS_PERCENT = """^(50|[1-4]?[0-9]|)$"""

        const val ALBUM_THUMBNAIL_SIZE = """^1[0-6][0-9]$|^170$"""

        const val SONG_THUMBNAIL_SIZE = """^[3-7][0-9]$|^80$"""

        /**
         * Matches number from 0 to 130 inclusively so it can be typed digit by digit.
         * Values below 80 are raised to 80 when the radio layout applies them.
         */
        const val CAR_RADIO_SIZE_PERCENT = """^(130|1[0-2][0-9]|[1-9]?[0-9]|)$"""
    }
}