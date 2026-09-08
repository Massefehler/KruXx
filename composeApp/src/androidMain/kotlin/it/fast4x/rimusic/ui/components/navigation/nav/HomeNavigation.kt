package it.fast4x.rimusic.ui.components.navigation.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.NavRoutes

object HomeNavigation {
    @Composable
    fun Items(item: @Composable (Int, String, Int) -> Unit) {
        if (Preferences.QUICK_PICKS_PAGE.value)
            item(0, stringResource(R.string.quick_picks), R.drawable.sparkles)
        item(1, stringResource(R.string.songs), R.drawable.musical_notes)
        item(2, stringResource(R.string.artists), R.drawable.people)
        item(3, stringResource(R.string.albums), R.drawable.album)
        item(4, stringResource(R.string.tab_playlists), R.drawable.library)
        if (BuildConfig.INDEPENDENT_FORK)
            item(5, stringResource(R.string.kruxx_downloads_tab), R.drawable.download)
    }

    fun select(navController: NavController, index: Int) {
        Preferences.HOME_TAB_INDEX.value = index
        navController.navigate(NavRoutes.home.name) {
            popUpTo(NavRoutes.home.name)
            launchSingleTop = true
        }
    }

    fun goHome(navController: NavController) {
        Preferences.HOME_TAB_INDEX.value = if (Preferences.QUICK_PICKS_PAGE.value)
            HomeScreenTabs.QuickPics.index
        else
            HomeScreenTabs.Songs.index

        if (NavRoutes.home.isHere(navController) && navController.previousBackStackEntry == null)
            return

        navController.navigate(NavRoutes.home.name) {
            // The app can start directly in search, so clear to the graph instead of a fixed route.
            popUpTo(navController.graph.id)
            launchSingleTop = true
        }
    }
}
