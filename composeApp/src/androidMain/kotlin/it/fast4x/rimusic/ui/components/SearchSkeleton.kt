package it.fast4x.rimusic.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import app.kreate.android.BuildConfig
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.components.navigation.nav.HomeNavigation
import it.fast4x.rimusic.ui.styling.Dimensions

private val LocalSearchNavigationReserved = staticCompositionLocalOf { false }

/** Reserve space for a right rail once, whether the page has search filters or stands alone. */
val searchContentWidth: Float
    @Composable get() = if (!LocalSearchNavigationReserved.current && NavigationBarPosition.Right.isCurrent())
        Dimensions.contentWidthRightBar else 1f

/** Search categories are filters; the KruXx library navigation stays reachable during search. */
@Composable
fun SearchSkeleton(
    navController: NavController,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    tabs: List<Pair<String, Int>>,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit,
) {
    // Old installations may still have the removed Featured/Podcasts category selected.
    val selected = tabIndex.takeIf { it in tabs.indices } ?: 0
    SideEffect { if (selected != tabIndex) onTabChanged(selected) }
    val keepHomeNavigation = BuildConfig.INDEPENDENT_FORK
    Skeleton(
        navController = navController,
        tabIndex = selected,
        onTabChanged = onTabChanged,
        miniPlayer = miniPlayer,
        navigationTabIndex = if (keepHomeNavigation) -1 else selected,
        onNavigationTabChanged = { if (keepHomeNavigation) HomeNavigation.select(navController, it) else onTabChanged(it) },
        navBarContent = { item ->
            if (keepHomeNavigation) HomeNavigation.Items(item)
            else tabs.forEachIndexed { index, (label, icon) -> item(index, label, icon) }
        },
    ) { currentTab ->
        if (keepHomeNavigation) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(searchContentWidth)) {
                CompositionLocalProvider(LocalSearchNavigationReserved provides true) {
                    ButtonsRow(tabs.mapIndexed { index, (label, _) -> index to label }, currentTab, onTabChanged)
                    Box(Modifier.weight(1f)) { content(currentTab) }
                }
            }
        } else content(currentTab)
    }
}
