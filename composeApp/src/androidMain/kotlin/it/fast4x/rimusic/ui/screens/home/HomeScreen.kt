package it.fast4x.rimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.themed.rimusic.screen.home.HomeSongsScreen
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.toUiMood
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.navigation.nav.HomeNavigation


@ExperimentalMaterial3Api
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistUrl: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {}
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    val (tabIndex, onTabChanged) = Preferences.HOME_TAB_INDEX

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { HomeNavigation.Items(it) }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            when (currentTabIndex) {
                0 -> HomeQuickPicks(
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onMoodClick = { mood ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("mood", mood.toUiMood())
                        NavRoutes.mood.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    },
                    navController = navController

                )

                1 -> HomeSongsScreen( navController )

                2 -> HomeArtists(
                    navController = navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                3 -> HomeAlbums(
                    navController = navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                4 -> HomeLibrary(
                    navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }

                )
                5 -> if (app.kreate.android.BuildConfig.INDEPENDENT_FORK)
                    app.kreate.android.downloads.DownloadsScreen()
            }
        }
    }
}
