package it.fast4x.rimusic.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import kotlinx.coroutines.launch

/** A fixed pair of arrow slots surrounds overflowing content; the bar itself never scrolls. */
@Composable
internal fun HorizontalScrollWithArrows(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier, propagateMinConstraints = true) {
        var contentWidth by remember { mutableIntStateOf(0) }
        // Compare against the entire available lane, including the potential arrow slots.
        // Using maxValue alone would keep the arrows after resizing when the items fit again.
        val showArrows = BuildConfig.INDEPENDENT_FORK && contentWidth > constraints.maxWidth
        val scope = rememberCoroutineScope()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showArrows) ScrollArrow(forward = false, visible = scrollState.canScrollBackward) {
                scope.launch { scrollState.animateScrollBy(-scrollState.viewportSize * .75f) }
            }
            content(Modifier.weight(1f).horizontalScroll(scrollState)
                .onSizeChanged { contentWidth = it.width })
            if (showArrows) ScrollArrow(forward = true, visible = scrollState.canScrollForward) {
                scope.launch { scrollState.animateScrollBy(scrollState.viewportSize * .75f) }
            }
        }
    }
}

@Composable
private fun ScrollArrow(forward: Boolean, visible: Boolean, onClick: () -> Unit) {
    val pointsRight = forward == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    val description = stringResource(if (pointsRight) R.string.kruxx_tabs_more_right else R.string.kruxx_tabs_more_left)
    // Keep both edges reserved at the scroll boundaries, so the tabs do not jump sideways.
    Box(Modifier.width(32.dp).height(48.dp).then(if (visible)
        Modifier.clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
        else Modifier), contentAlignment = Alignment.Center) {
        if (visible) Text(if (pointsRight) ">>" else "<<", modifier = Modifier.clearAndSetSemantics {},
            color = colorPalette().text, fontSize = 18.sp)
    }
}
