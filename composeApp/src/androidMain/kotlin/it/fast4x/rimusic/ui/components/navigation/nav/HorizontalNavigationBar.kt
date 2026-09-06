package it.fast4x.rimusic.ui.components.navigation.nav

import android.annotation.SuppressLint
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.navigation.NavController
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.NavigationBarType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.showSearchIconInNav
import it.fast4x.rimusic.showStatsIconInNav
import it.fast4x.rimusic.ui.components.themed.Button
import it.fast4x.rimusic.ui.components.themed.TextIconButton
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.kruxxGlassSurface
import kotlin.math.roundToInt

// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    private fun navButtonProperties(): Modifier {
        val padding: Dp = 4.dp
        val size: Dp = 24.dp
        val border: Shape = CircleShape

        return Modifier.padding( all = padding )
                       .size( size )
                       .clip( shape = border )
    }

    @Composable
    private fun addButton(button: Button, modifier: Modifier = Modifier ) =
        // buttonList() duplicates button instead of updating them.
        // Do NOT use it
        buttonList.add {
            Box(modifier, contentAlignment = Alignment.Center,
                propagateMinConstraints = BuildConfig.INDEPENDENT_FORK) { button.Draw() }
        }

    @SuppressLint("ComposableNaming")
    @Composable
    private fun addButton(index: Int, button: Button, modifier: Modifier = Modifier ) =
        // buttonList() duplicates button instead of updating them
        // Do NOT use it
        buttonList.add( index ) {
            Box(modifier, contentAlignment = Alignment.Center,
                propagateMinConstraints = BuildConfig.INDEPENDENT_FORK) { button.Draw() }
        }

    @Composable
    private fun bottomPadding(): Dp {
        return if ( NavigationBarPosition.Bottom.isCurrent() )
            with( LocalDensity.current ) {
                WindowInsets.systemBars.getBottom( this ).toDp()
            }
        else
            5.dp
    }

    private fun topPadding(): Dp = 0.dp

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        val transition = updateTransition(targetState = tabIndex, label = null)

        buttons { index, text, iconId ->

            val color by transition.animateColor(label = "") {
                when {
                    it == index && isKruxxGlassEnabled -> KruxxGlass.electricBlue
                    it == index -> colorPalette().text
                    else -> colorPalette().textDisabled
                }
            }
            val itemBackground by transition.animateColor(label = "navigationItemBackground") {
                if (it == index && isKruxxGlassEnabled)
                    KruxxGlass.electricBlue.copy(alpha = 0.16f)
                else
                    Color.Transparent
            }

            val button: Button =
                if ( NavigationBarType.IconOnly.isCurrent() )
                    Button( iconId, color, 12.dp, 20.dp )
                else
                    TextIconButton( text, iconId, color, 0.dp, Dimensions.navigationRailIconOffset * 3 )

            val bringIntoView = remember { BringIntoViewRequester() }
            LaunchedEffect(tabIndex) {
                if (BuildConfig.INDEPENDENT_FORK && tabIndex == index) bringIntoView.bringIntoView()
            }
            val contentModifier = Modifier
                .bringIntoViewRequester(bringIntoView)
                .clip(RoundedCornerShape(if (isKruxxGlassEnabled) 14.dp else 12.dp))
                .background(itemBackground)
                .clickable(onClick = { onTabChanged(index) })

            addButton( button, contentModifier )
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun Draw() {
        if( buttonList.size < 2 ) return

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier.padding( top = topPadding(), bottom = bottomPadding() )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.navigationBarHeight - 10.dp)
            ) {

                val scrollState = rememberScrollState()
                val roundedCornerShape = if (isKruxxGlassEnabled) {
                    KruxxGlass.navigationShape
                } else if ( NavigationBarPosition.Bottom.isCurrent() ) {
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                } else {
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                }

                // Settings button only visible when
                // UI is not RiMusic and current location isn't home screen
                if( UiType.ViMusic.isCurrent() && NavRoutes.home.isNotHere( navController ) )
                    BackButton().Draw()

                Box(
                    modifier = if (isKruxxGlassEnabled) {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .kruxxGlassSurface(
                                fallbackColor = colorPalette().background1,
                                shape = roundedCornerShape,
                                strong = true
                            )
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .clip(roundedCornerShape)
                            .background(colorPalette().background1)
                    }
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        var contentWidth by remember { mutableIntStateOf(0) }
                        val showHints = BuildConfig.INDEPENDENT_FORK && contentWidth > constraints.maxWidth
                        val scope = rememberCoroutineScope()
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            if (showHints) ScrollHint(false, scrollState.canScrollBackward) {
                                scope.launch { scrollState.animateScrollBy(-scrollState.viewportSize * .75f) }
                            }
                            val itemsModifier = Modifier.weight(1f).fillMaxSize().horizontalScroll(scrollState)
                                .onSizeChanged { contentWidth = it.width }
                            if (BuildConfig.INDEPENDENT_FORK && !NavigationBarType.IconOnly.isCurrent()) {
                                UniformNavigationRow(itemsModifier) { buttonList().forEach { it() } }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = itemsModifier,
                                    content = { buttonList().forEach { it() } })
                            }
                            if (showHints) ScrollHint(true, scrollState.canScrollForward) {
                                scope.launch { scrollState.animateScrollBy(scrollState.viewportSize * .75f) }
                            }
                        }
                    }
                }

                // Search button only visible when
                // UI is not RiMusic and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showSearchIconInNav() )
                    SearchButton()

                // Settings button only visible when
                // UI is not RiMusic
                if( UiType.ViMusic.isCurrent() )
                    SettingsButton().Draw()

                // Statistics button only visible when
                // UI is not RiMusic and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showStatsIconInNav() )
                    StatsButton()
            }
        }
    }

    @Composable
    private fun ScrollHint(forward: Boolean, visible: Boolean, onClick: () -> Unit) {
        // Reserve both edges while overflowing, so reaching a boundary never shifts the tabs.
        val description = stringResource(if (forward) R.string.kruxx_tabs_more_right else R.string.kruxx_tabs_more_left)
        Box(Modifier.width(32.dp).height(48.dp).then(if (visible)
            Modifier.clickable(onClick = onClick).semantics { contentDescription = description } else Modifier),
            contentAlignment = Alignment.Center) {
            if (visible) Text(if (forward) ">>" else "<<", color = colorPalette().text, fontSize = 18.sp)
        }
    }
}

/** Measure the actual labels, including the selected font and scale, before sizing every tab alike. */
@Composable
private fun UniformNavigationRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val widest = measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        // Equal breathing room on both sides of the longest label, in addition to its own padding.
        val itemWidth = (widest + 16.dp.roundToPx()).coerceAtLeast(76.dp.roundToPx())
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = itemWidth, maxWidth = itemWidth)) }
        val width = constraints.constrainWidth(itemWidth * placeables.size)
        val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
        val spacing = (width - itemWidth * placeables.size).toFloat() / (placeables.size + 1)
        layout(width, height) {
            placeables.forEachIndexed { index, item ->
                item.placeRelative((spacing * (index + 1) + itemWidth * index).roundToInt(), (height - item.height) / 2)
            }
        }
    }
}
