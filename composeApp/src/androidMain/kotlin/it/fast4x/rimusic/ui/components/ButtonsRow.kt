package it.fast4x.rimusic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.kruxxFilterBar

@Composable
fun <E> ButtonsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val colorPaletteMode by Preferences.THEME_MODE
    val palette = colorPalette()
    val glass = isKruxxGlassEnabled

    Row(
        modifier = modifier.fillMaxWidth().kruxxFilterBar(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Scroll only the chips. The glass outline and optional source filter stay put.
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(if (glass) 8.dp else 12.dp))

            chips.forEach { (value, label) ->
                val selected = currentValue == value
                FilterChip(
                    label = { Text(label, maxLines = 1) },
                    selected = selected,
                    shape = if (glass) RoundedCornerShape(12.dp) else FilterChipDefaults.shape,
                    border = if (glass) {
                        if (selected) BorderStroke(1.dp, KruxxGlass.electricBlue.copy(alpha = .55f))
                        else null
                    } else FilterChipDefaults.filterChipBorder(enabled = true, selected = selected),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (glass) Color.Transparent else palette.background1,
                        labelColor = palette.text,
                        selectedContainerColor = if (glass)
                            KruxxGlass.electricBlue.copy(alpha = if (palette.isDark) .34f else .24f)
                        else when (colorPaletteMode) {
                            ColorPaletteMode.Dark, ColorPaletteMode.PitchBlack -> palette.textDisabled
                            else -> palette.background3
                        },
                        selectedLabelColor = palette.text,
                    ),
                    onClick = { onValueUpdate(value) },
                )

                Spacer(Modifier.width(8.dp))
            }
        }
        trailingContent?.invoke()
    }
}

@Composable
fun ButtonsRow(
    chips: List<BuiltInPlaylist>,
    currentValue: BuiltInPlaylist,
    onValueUpdate: (BuiltInPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonsRow(
        chips = chips.map { it to it.text },
        currentValue = currentValue,
        onValueUpdate = onValueUpdate,
        modifier = modifier,
    )
}
