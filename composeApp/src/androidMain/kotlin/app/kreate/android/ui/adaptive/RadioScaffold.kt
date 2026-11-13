package app.kreate.android.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.kruxxAppBackground
import it.fast4x.rimusic.ui.styling.kruxxContentColor
import it.fast4x.rimusic.ui.styling.kruxxGlassSurface
import it.fast4x.rimusic.ui.components.navigation.nav.HomeNavigation
import it.fast4x.rimusic.utils.transition

/** Shared by home, search, settings and detail screens; uses the same tabs and callbacks. */
@Composable
fun RadioScaffold(
    navController: NavController,
    tabIndex: Int,
    navigationTabIndex: Int,
    onNavigationTabChanged: (Int) -> Unit,
    miniPlayer: @Composable (() -> Unit)?,
    navBarContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit,
) {
    val palette = colorPalette()
    val layout = LocalAdaptiveLayout.current
    val metrics = layout.metrics
    var menuExpanded by remember { mutableStateOf(false) }
    // Collected first so that, as on phones, a page with a single tab shows no navigation.
    val tabs = mutableListOf<Triple<Int, String, Int>>()
    navBarContent { index, title, icon -> tabs += Triple(index, title, icon) }
    val showRail = tabs.size >= 2
    // Wide enough for the longest single word at the current font size, so labels such as
    // "Downloads" only wrap between words, also with larger system fonts or the size setting.
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = LocalTextStyle.current.copy(fontSize = metrics.railLabel)
    val longestWord = with(LocalDensity.current) {
        tabs.flatMap { it.second.split(' ') }
            .maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width }?.toDp() ?: 0.dp
    }
    val railWidth = maxOf(metrics.railWidth, longestWord + 14.dp * metrics.scale + 6.dp)
    Row(Modifier.fillMaxSize().kruxxAppBackground().safeDrawingPadding()) {
        if (showRail) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp * metrics.scale),
            modifier = Modifier.width(railWidth).fillMaxHeight()
                .padding(start = 6.dp * metrics.scale, top = 6.dp * metrics.scale, bottom = 6.dp * metrics.scale)
                .kruxxGlassSurface(palette.background1, RoundedCornerShape(18.dp * metrics.scale), strong = true)
                .verticalScroll(rememberScrollState()).padding(4.dp * metrics.scale),
        ) {
            tabs.forEach { (index, title, icon) ->
                val active = index == navigationTabIndex
                TextButton(
                    onClick = { onNavigationTabChanged(index) },
                    shape = RoundedCornerShape(14.dp * metrics.scale),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp * metrics.scale),
                    modifier = Modifier.fillMaxWidth().heightIn(min = metrics.railItemHeight)
                        .background(if (active) KruxxGlass.electricBlue.copy(alpha = .18f) else palette.background0.copy(alpha = 0f), RoundedCornerShape(14.dp * metrics.scale))
                        .semantics { selected = active },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(icon), null, Modifier.size(metrics.railIcon), tint = palette.text)
                        Spacer(Modifier.height(2.dp * metrics.scale))
                        Text(title, color = palette.text, fontSize = metrics.railLabel, textAlign = TextAlign.Center,
                            lineHeight = metrics.railLabel * 1.15f, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(kruxxContentColor(palette.background0))) {
            Row(Modifier.fillMaxWidth().height(metrics.topBarHeight).padding(horizontal = 4.dp * metrics.scale), verticalAlignment = Alignment.CenterVertically) {
                if (navController.previousBackStackEntry != null) {
                    RadioIconButton(R.drawable.chevron_back, stringResource(R.string.radio_back)) { navController.navigateUp() }
                }
                val homeLabel = stringResource(R.string.kruxx_go_to_home)
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { HomeNavigation.goHome(navController) },
                        modifier = Modifier.heightIn(min = RadioMetrics.MIN_TOUCH_DP.dp).semantics { contentDescription = homeLabel },
                    ) {
                        Text("KruXx", color = palette.text, fontSize = metrics.wordmark, fontWeight = FontWeight.Bold)
                    }
                }
                RadioIconButton(R.drawable.search, stringResource(R.string.radio_search)) { NavRoutes.search.navigateHere(navController) }
                RadioIconButton(R.drawable.settings, stringResource(R.string.settings)) { NavRoutes.settings.navigateHere(navController) }
                Box {
                    RadioIconButton(R.drawable.burger, stringResource(R.string.radio_app_menu)) { menuExpanded = true }
                    it.fast4x.rimusic.ui.components.navigation.header.HamburgerMenu(
                        menuExpanded, { menuExpanded = false; it.navigateHere(navController) }, { menuExpanded = false }
                    )
                }
            }
            // AdaptiveLayout assumes the standard rail; correct the width for the rail actually shown.
            val shownRail = if (showRail) railWidth.value else 0f
            CompositionLocalProvider(
                LocalAdaptiveLayout provides
                    layout.copy(contentWidthDp = layout.contentWidthDp + metrics.railWidth.value - shownRail)
            ) {
                AnimatedContent(
                    targetState = tabIndex,
                    transitionSpec = transition(),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    content = content,
                    label = "radio tab",
                )
            }
            Box(Modifier.fillMaxWidth().padding(4.dp * metrics.scale)) { miniPlayer?.invoke() }
        }
    }
}

@Composable
internal fun RadioIconButton(icon: Int, label: String, onClick: () -> Unit) {
    val metrics = LocalAdaptiveLayout.current.metrics
    IconButton(onClick, Modifier.size(RadioMetrics.MIN_TOUCH_DP.dp)) {
        Icon(painterResource(icon), label, Modifier.size(metrics.topBarIcon), tint = colorPalette().text)
    }
}
