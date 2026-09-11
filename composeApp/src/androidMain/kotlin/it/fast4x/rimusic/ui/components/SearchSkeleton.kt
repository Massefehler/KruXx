package it.fast4x.rimusic.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.navigation.NavController
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions

/** Search pages keep their own content width when the navigation sits on the right. */
val searchContentWidth: Float
    @Composable get() = if (NavigationBarPosition.Right.isCurrent())
        Dimensions.contentWidthRightBar else 1f

/**
 * The navigation bar of a search page selects its categories: sources on the search
 * entry, and songs/albums/artists/videos/playlists on the results. Leaving search
 * stays on the header's home button, so the bar itself is free for the categories.
 */
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
    Skeleton(
        navController = navController,
        tabIndex = selected,
        onTabChanged = onTabChanged,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            tabs.forEachIndexed { index, (label, icon) -> item(index, label, icon) }
        },
        content = content,
    )
}
