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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var menuExpanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().kruxxAppBackground().safeDrawingPadding()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.width(128.dp).fillMaxHeight()
                .kruxxGlassSurface(palette.background1, RoundedCornerShape(20.dp), strong = true)
                .verticalScroll(rememberScrollState()).padding(8.dp),
        ) {
            navBarContent { index, title, icon ->
                val active = index == navigationTabIndex
                TextButton(
                    onClick = { onNavigationTabChanged(index) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .background(if (active) KruxxGlass.electricBlue.copy(alpha = .18f) else palette.background0.copy(alpha = 0f), RoundedCornerShape(16.dp))
                        .semantics { selected = active },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(icon), null, Modifier.size(26.dp), tint = palette.text)
                        Spacer(Modifier.height(4.dp))
                        Text(title, color = palette.text, fontSize = 14.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().background(kruxxContentColor(palette.background0))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (navController.previousBackStackEntry != null) {
                    RadioIconButton(R.drawable.chevron_back, stringResource(R.string.radio_back)) { navController.navigateUp() }
                }
                val homeLabel = stringResource(R.string.kruxx_go_to_home)
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { HomeNavigation.goHome(navController) },
                        modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = homeLabel },
                    ) {
                        Text("KruXx", color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                RadioIconButton(R.drawable.search, stringResource(R.string.radio_search)) { NavRoutes.search.navigateHere(navController) }
                Spacer(Modifier.width(8.dp))
                RadioIconButton(R.drawable.settings, stringResource(R.string.settings)) { NavRoutes.settings.navigateHere(navController) }
                Spacer(Modifier.width(8.dp))
                Box {
                    RadioIconButton(R.drawable.burger, stringResource(R.string.radio_app_menu)) { menuExpanded = true }
                    it.fast4x.rimusic.ui.components.navigation.header.HamburgerMenu(
                        menuExpanded, { menuExpanded = false; it.navigateHere(navController) }, { menuExpanded = false }
                    )
                }
            }
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = transition(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                content = content,
                label = "radio tab",
            )
            Box(Modifier.fillMaxWidth().padding(4.dp)) { miniPlayer?.invoke() }
        }
    }
}

@Composable
internal fun RadioIconButton(icon: Int, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(56.dp)) {
        Icon(painterResource(icon), label, Modifier.size(28.dp), tint = colorPalette().text)
    }
}
