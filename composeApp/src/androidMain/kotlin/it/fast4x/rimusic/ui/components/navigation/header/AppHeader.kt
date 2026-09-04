package it.fast4x.rimusic.ui.components.navigation.header

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.extensions.games.pacman.Pacman
import it.fast4x.rimusic.ui.components.themed.Button
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.kruxxContentColor
import it.fast4x.rimusic.ui.styling.kruxxGlassSurface

class AppHeader(
    val navController: NavController
) {

    companion object {

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun colors(): TopAppBarColors {
            val palette = colorPalette()
            val containerColor = kruxxContentColor(palette.background0)

            return TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                titleContentColor = palette.text,
                scrolledContainerColor = containerColor,
                navigationIconContentColor = palette.background0,
                actionIconContentColor = palette.text
            )
        }
    }

    @Composable
    private fun BackButton() {
        if( navController.previousBackStackEntry == null ) return

        androidx.compose.material3.IconButton(
            onClick = navController::navigateUp
        ) {
            Button(
                R.drawable.chevron_back,
                colorPalette().favoritesIcon,
                0.dp,
                24.dp
            ).Draw()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Draw() {
        val showGames by remember { mutableStateOf(false) }
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        val context = LocalContext.current
        val headerModifier = if (isKruxxGlassEnabled) {
            Modifier.kruxxGlassSurface(
                fallbackColor = colorPalette().background0,
                shape = KruxxGlass.headerShape,
                strong = true
            )
        } else {
            Modifier
        }

        if (showGames) Pacman()

        TopAppBar(
            modifier = headerModifier,
            title = { AppTitle( navController, context ) },
            actions = { ActionBar( navController ) },
            navigationIcon = { BackButton() },
            scrollBehavior = scrollBehavior,
            colors = colors()
        )
    }
}
