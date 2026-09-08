package it.fast4x.rimusic.ui.components.navigation.header

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.navigation.nav.HomeNavigation
import it.fast4x.rimusic.ui.components.themed.Button
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.utils.Toaster

private fun appIconClickAction(
    navController: NavController,
    countToReveal: MutableIntState,
    context: Context
) {
    countToReveal.intValue++

    val message: String =
        when( countToReveal.intValue ) {
            10 -> {
                countToReveal.intValue = 0
                NavRoutes.gamePacman.navigateHere( navController )
                ""
            }
            3 -> "Do you like clicking? Then continue..."
            6 -> "Okay, you’re looking for something, keep..."
            9 -> "You are a number one, click and enjoy the surprise"
            else -> ""
        }
    if( message.isNotEmpty() )
        Toaster.n( message, Toast.LENGTH_LONG )
}

private fun appIconLongClickAction(
    navController: NavController,
    context: Context
) {
    Toaster.n( "You are a number one, click and enjoy the surprise", Toast.LENGTH_LONG )
    NavRoutes.gameSnake.navigateHere( navController )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppLogo(
    navController: NavController,
    context: Context
) {
    val countToReveal = remember { mutableIntStateOf(0) }
    val modifier = if (BuildConfig.INDEPENDENT_FORK) Modifier else Modifier.combinedClickable(
        onClick = { appIconClickAction( navController, countToReveal, context ) },
        onLongClick = { appIconLongClickAction( navController, context ) }
    )

    Image(
        bitmap = AppIcon.Round.imageBitmap( LocalContext.current ),
        contentDescription = if (BuildConfig.INDEPENDENT_FORK) null else "App's icon",
        modifier = modifier.size( 36.dp )
    )
}

@Composable
private fun AppLogoText( navController: NavController ) {
    Button(
        iconId = R.drawable.app_logo_text,
        color = AppBar.contentColor(),
        padding = 0.dp,
        size = BuildConfig.HEADER_LOGO_HEIGHT_DP.dp,
        forceWidth = BuildConfig.HEADER_LOGO_WIDTH_DP.dp,
        modifier = if (BuildConfig.INDEPENDENT_FORK) Modifier else Modifier.clickable {
            if ( NavRoutes.home.isHere( navController ) ) return@clickable

            // In short, navigates to [NavRoutes.home] then previous stacks
            // effectively make it the start again
            navController.navigate( NavRoutes.home.name ) {
                popUpTo( navController.graph.findStartDestination().id ) {
                    inclusive = true
                }

                launchSingleTop = true
            }
        }
    ).Draw()
}

// START
@Composable
fun AppTitle(
    navController: NavController,
    context: Context
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy( 5.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val homeDescription = stringResource(R.string.kruxx_go_to_home)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (BuildConfig.INDEPENDENT_FORK) Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { HomeNavigation.goHome(navController) }
                .semantics { contentDescription = homeDescription }
            else Modifier
        ) {
            AppLogo(navController, context)
            AppLogoText(navController)
        }

        if(Preference.parentalControl())
            Button(
                iconId = R.drawable.shield_checkmark,
                color = AppBar.contentColor(),
                padding = 0.dp,
                size = 20.dp
            ).Draw()

        if (Preference.debugLog())
            BasicText(
                text = stringResource(R.string.info_debug_mode_enabled),
                style = TextStyle(
                    fontSize = typography().xxs.semiBold.fontSize,
                    fontWeight = typography().xxs.semiBold.fontWeight,
                    fontFamily = typography().xxs.semiBold.fontFamily,
                    color = colorPalette().red
                ),
                modifier = Modifier
                    .clickable {
                        Toaster.s( R.string.info_debug_mode_is_enabled )

                        NavRoutes.settings.navigateHere( navController )
                    }
            )
    }
// END
}
