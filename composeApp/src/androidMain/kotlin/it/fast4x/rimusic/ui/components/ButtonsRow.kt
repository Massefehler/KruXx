package it.fast4x.rimusic.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled

@Composable
fun <E> ButtonsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorPaletteMode by Preferences.THEME_MODE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            val palette = colorPalette()
            FilterChip(
                label = { Text(label) },
                selected = currentValue == value,
                colors = FilterChipDefaults
                    .filterChipColors(
                        containerColor = if( isKruxxGlassEnabled )
                            palette.background1.copy( alpha = if( palette.isDark ) .52f else .72f )
                        else
                            palette.background1,
                        labelColor = palette.text,
                        selectedContainerColor = if( isKruxxGlassEnabled )
                            KruxxGlass.electricBlue.copy( alpha = if( palette.isDark ) .34f else .24f )
                        else when (colorPaletteMode) {
                            ColorPaletteMode.Dark, ColorPaletteMode.PitchBlack -> palette.textDisabled
                            else -> palette.background3
                        },
                        selectedLabelColor = palette.text,
                    ),
                onClick = { onValueUpdate(value) }
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun ButtonsRow(
    chips: List<BuiltInPlaylist>,
    currentValue: BuiltInPlaylist,
    onValueUpdate: (BuiltInPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorPaletteMode by Preferences.THEME_MODE
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { playlistType ->
            val palette = colorPalette()
            FilterChip(
                label = { Text( playlistType.text ) },
                selected = currentValue == playlistType,
                colors = FilterChipDefaults
                    .filterChipColors(
                        containerColor = if( isKruxxGlassEnabled )
                            palette.background1.copy( alpha = if( palette.isDark ) .52f else .72f )
                        else
                            palette.background1,
                        labelColor = palette.text,
                        selectedContainerColor = if( isKruxxGlassEnabled )
                            KruxxGlass.electricBlue.copy( alpha = if( palette.isDark ) .34f else .24f )
                        else when (colorPaletteMode) {
                            ColorPaletteMode.Dark, ColorPaletteMode.PitchBlack -> palette.textDisabled
                            else -> palette.background3
                        },
                        selectedLabelColor = palette.text,
                    ),
                onClick = { onValueUpdate(playlistType) }
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}
