package it.fast4x.rimusic.ui.components.themed

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.kruxxDialogSurface

/** Material dialog layout with the same palette and glass as the other app dialogs. */
@Composable
fun ThemedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    val palette = colorPalette()
    // Material controls otherwise inherit the default purple light palette even
    // when the app uses dark glass, making radio outlines and actions too dim.
    val colors = if (isKruxxGlassEnabled) MaterialTheme.colorScheme.copy(
        primary = if (palette.isDark) KruxxGlass.electricBlue else Color(0xFF1768B0),
        onPrimary = Color.White,
        onSurface = palette.text,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.textSecondary,
    ) else MaterialTheme.colorScheme
    MaterialTheme(colorScheme = colors) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
            title = title,
            text = text,
            modifier = if (isKruxxGlassEnabled) modifier.kruxxDialogSurface(palette.background1) else modifier,
            shape = if (isKruxxGlassEnabled) KruxxGlass.dialogShape else AlertDialogDefaults.shape,
            containerColor = if (isKruxxGlassEnabled) Color.Transparent else palette.background1,
            tonalElevation = if (isKruxxGlassEnabled) 0.dp else AlertDialogDefaults.TonalElevation,
            titleContentColor = palette.text,
            textContentColor = palette.text,
        )
    }
}
