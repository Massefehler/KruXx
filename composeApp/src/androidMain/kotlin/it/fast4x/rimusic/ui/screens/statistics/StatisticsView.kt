package it.fast4x.rimusic.ui.screens.statistics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.downloads.AudioDownloadButton
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.database.models.Song
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.kruxxGlassCard
import it.fast4x.rimusic.ui.styling.kruxxTrackCard
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.semiBold

@Composable
internal fun StatisticsViewSwitch(
    grid: Boolean,
    onGridChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = colorPalette()
    Row(
        modifier = modifier.kruxxGlassCard().padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(false to R.string.style_list, true to R.string.style_grid).forEach { (useGrid, label) ->
            val selected = grid == useGrid
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) KruxxGlass.electricBlue.copy(alpha = if (palette.isDark) .34f else .24f)
                        else Color.Transparent
                    )
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onGridChange(useGrid) })
                    .padding(horizontal = 10.dp)
            ) {
                BasicText(
                    text = stringResource(label),
                    style = typography().xs.semiBold.copy(color = palette.text),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, UnstableApi::class)
@Composable
internal fun StatisticsSongGridItem(
    song: Song,
    rank: Int,
    isPlaying: Boolean,
    values: SongItem.Values,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .kruxxTrackCard()
            .background(if (isPlaying) values.nowPlayingOverlayColor else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            SongItem.Thumbnail(
                thumbnailUrl = song.cleanThumbnailUrl(),
                isLiked = song.likedAt != null,
                isPlaying = isPlaying,
                values = values,
                sizeDp = DpSize(40.dp, 40.dp)
            ) {
                BasicText(
                    text = rank.toString(),
                    style = values.titleTextStyle.copy(color = Color.White),
                    modifier = Modifier.align(Alignment.Center)
                        .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            SongItem.Duration(song.durationText, values, Modifier.weight(1f).padding(horizontal = 4.dp))
            if (!song.isLocal) AudioDownloadButton(song.asMediaItem)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongItem.Badges(
                songId = song.id,
                isRecommended = false,
                isInPlaylistScreen = false,
                isExplicit = song.isExplicit,
                values = values
            )
            BasicText(
                text = song.cleanTitle(),
                style = values.titleTextStyle.copy(color = values.titleColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        BasicText(
            text = song.cleanArtistsText(),
            style = values.artistsTextStyle.copy(color = values.artistsColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
