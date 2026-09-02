package me.knighthat.updater

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.android.themed.common.component.dialog.Dialog
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.bold
import kotlinx.coroutines.launch

open class ChangelogsDialog(context: Context): Dialog() {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.update_changelogs, BuildConfig.VERSION_NAME )

    private lateinit var pagerState: PagerState
    private val sections = mutableListOf<ChangelogSection>()
    override var isActive: Boolean by mutableStateOf( false )

    init {
        context.resources
               .openRawResource( R.raw.release_notes )
               .bufferedReader( Charsets.UTF_8 )
               .useLines { lines ->
                   sections.addAll( parseChangelog(lines) )
               }
    }

    @Composable
    override fun Render() {
        // TabRow's indicator cannot address page 0 when no section was parsed.
        // A missing or malformed release note must never take down the app.
        if( BuildConfig.DEBUG || sections.isEmpty() ) return

        // Initialize this ASAP
        if( !::pagerState.isInitialized )
            this.pagerState = rememberPagerState { sections.size }

        super.Render()
    }

    @Composable
    override fun DialogHeader() {
        val scope = rememberCoroutineScope()
        val (colorPalette, typography) = LocalAppearance.current

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // "vX.X.X changelogs" title
            BasicText(
                text = dialogTitle,
                style = typography.m.bold,
                modifier = Modifier.padding( bottom = VERTICAL_PADDING.dp )
                                   .fillMaxWidth( .9f )
            )

            TabRow(
                selectedTabIndex = pagerState.targetPage,
                containerColor = Color.Transparent,
                contentColor = colorPalette.text,
                indicator = { tabPositions ->
                    val selectedPosition = tabPositions[pagerState.targetPage]
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset( selectedPosition ),
                        color = colorPalette.accent,
                        width = selectedPosition.width
                    )
                }
            ) {
                sections.forEachIndexed { index, section ->
                    val isSelected = index == pagerState.targetPage
                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                this@ChangelogsDialog.pagerState.animateScrollToPage( index )
                            }
                        }
                    ) {
                        Text(
                            text = section.title,
                            style = typography.m,
                            color = if( isSelected ) colorPalette.text else colorPalette.textSecondary,
                            fontWeight = if( isSelected ) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun DialogBody() {
        val sectionTextStyle = LocalAppearance.current.typography.xs

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = sections.size,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth( .9f )
        ) { selectedTab ->
            Column {
                sections[selectedTab].changes.forEach { line ->
                    BasicText(
                        text = line,
                        style = sectionTextStyle
                    )
                }
            }
        }
    }
}
