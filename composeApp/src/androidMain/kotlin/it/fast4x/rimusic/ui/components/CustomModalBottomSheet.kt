package it.fast4x.rimusic.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.LocalKruxxGlassSheet
import it.fast4x.rimusic.ui.styling.kruxxGlassSurface
import it.fast4x.rimusic.utils.isLandscape
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    ),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.ime },
    glassSurfaceEnabled: Boolean = true,
    glassSurfaceOpaque: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottomInsets = if (isLandscape) Modifier else Modifier.navigationBarsPadding()
    val palette = colorPalette()
    val useGlassSurface = isKruxxGlassEnabled && glassSurfaceEnabled
    val resolvedShape = if (useGlassSurface) KruxxGlass.menuShape else shape
    // Material applies the sheet's translation AFTER its public modifier. An outer
    // glass clip would stay at the top of the window and cut off the moving sheet.
    // Keep the complete glass pane, including its handle, inside the sheet instead.
    val surfaceModifier = if (useGlassSurface) {
        Modifier.fillMaxWidth().kruxxGlassSurface(
            fallbackColor = containerColor.takeUnless { it == Color.Transparent } ?: palette.background1,
            shape = resolvedShape,
            strong = true,
            backdropAlpha = KruxxGlass.modalBackdropAlpha,
            opaqueBackdrop = glassSurfaceOpaque
        )
    } else {
        Modifier
    }
    val resolvedContainerColor = if (useGlassSurface) Color.Transparent else containerColor
    val resolvedContentColor = if (useGlassSurface) palette.text else contentColor
    val resolvedElevation = if (useGlassSurface) 0.dp else tonalElevation

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = resolvedShape,
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
            tonalElevation = resolvedElevation,
            scrimColor = scrimColor,
            dragHandle = if (useGlassSurface) null else dragHandle,
            contentWindowInsets = contentWindowInsets
        ) {
            val colorPaletteMode by Preferences.THEME_MODE
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
            val isDark =
                colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme())

            Column(modifier = surfaceModifier.then(bottomInsets)) {

                val view = LocalView.current
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    SideEffect {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    }
                }

                if (useGlassSurface && dragHandle != null) {
                    val scope = rememberCoroutineScope()
                    fun dismissSheet() {
                        scope.launch {
                            sheetState.hide()
                            if (!sheetState.isVisible) onDismissRequest()
                        }
                    }
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .clickable {
                                if (sheetState.currentValue == SheetValue.Expanded) dismissSheet()
                                else scope.launch { sheetState.expand() }
                            }
                            .semantics(mergeDescendants = true) {
                                dismiss { dismissSheet(); true }
                                if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                                    expand { scope.launch { sheetState.expand() }; true }
                                } else if (sheetState.hasPartiallyExpandedState) {
                                    collapse { scope.launch { sheetState.partialExpand() }; true }
                                }
                            }
                    ) {
                        dragHandle()
                    }
                }
                CompositionLocalProvider(LocalKruxxGlassSheet provides useGlassSurface) {
                    content()
                }
            }
        }
    }
}
