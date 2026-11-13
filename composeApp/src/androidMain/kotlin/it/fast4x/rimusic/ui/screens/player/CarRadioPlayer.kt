package it.fast4x.rimusic.ui.screens.player

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.BlurTransformation
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.constant.MenuPage
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.ui.adaptive.LocalAdaptiveLayout
import app.kreate.android.ui.adaptive.RadioMetrics
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.playPrevious
import it.fast4x.rimusic.utils.positionAndDurationState
import it.fast4x.rimusic.utils.resize
import it.fast4x.rimusic.utils.shouldBePlaying
import org.koin.compose.koinInject

/**
 * Car-radio player: cover and track details share the upper area, while the seek bar and the
 * transport controls span the full width below, as in Android Auto and CarPlay. All sizes come
 * from [RadioMetrics]; the regular player stays available via Phone mode and uses the same service.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
@UnstableApi
@Composable
fun CarRadioPlayer(navController: NavController, onDismiss: () -> Unit, player: StatefulPlayer = koinInject()) {
    val textStyle = it.fast4x.rimusic.typography().m
    val layout = LocalAdaptiveLayout.current
    val metrics = layout.metrics
    val mediaItem by player.currentMediaItemState.collectAsState()
    val item = mediaItem
    if (item == null) {
        Box(Modifier.fillMaxSize().background(colorPalette().background0).safeDrawingPadding().padding(metrics.padding)) {
            Text(stringResource(R.string.no_songs_playing), Modifier.align(Alignment.Center), style = textStyle, color = colorPalette().text)
            RadioPlayerButton(R.drawable.chevron_down, stringResource(R.string.radio_minimize_player), metrics, onClick = onDismiss)
        }
        return
    }
    val menu = LocalBottomMenu.current
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(player.shouldBePlaying) }
    var error by remember(player) { mutableStateOf(player.playerError != null) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var shuffle by remember(player) { mutableStateOf(player.shuffleModeEnabled) }
    var repeat by remember(player) { mutableIntStateOf(player.repeatMode) }
    player.DisposableListener {
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.shouldBePlaying
                error = player.playerError != null
                buffering = player.playbackState == Player.STATE_BUFFERING
                shuffle = player.shuffleModeEnabled
                repeat = player.repeatMode
            }
        }
    }
    val position by player.positionAndDurationState()
    val duration = position.second.coerceAtLeast(0L)
    var seekFraction by remember(item.mediaId) { mutableStateOf<Float?>(null) }
    val repeatLabel = stringResource(when (repeat) {
        Player.REPEAT_MODE_ONE -> R.string.radio_repeat_one
        Player.REPEAT_MODE_ALL -> R.string.radio_repeat_all
        else -> R.string.radio_repeat_off
    })

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0D14))) {
        val windowHeight = maxHeight
        // Bitmap blur also works on Android 7–11; RenderEffect only exists on Android 12+.
        val transformations = remember { if (isAtLeastAndroid12) emptyList() else listOf(BlurTransformation(25, .5f)) }
        ImageFactory.AsyncImage(
            item.mediaMetadata.artworkUri?.toString()?.resize(600, 600),
            transformations = transformations,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (isAtLeastAndroid12) Modifier.blur(28.dp) else Modifier),
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .64f)))

        @Composable
        fun Cover(modifier: Modifier) {
            ImageFactory.AsyncImage(
                item.mediaMetadata.artworkUri?.toString()?.resize(900, 900),
                contentDescription = stringResource(R.string.radio_cover),
                contentScale = ContentScale.Fit,
                modifier = modifier.clip(RoundedCornerShape(12.dp * metrics.scale)),
            )
        }

        /** Minimize on the left, the secondary actions on the right. */
        @Composable
        fun Actions() {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioPlayerButton(R.drawable.chevron_down, stringResource(R.string.radio_minimize_player), metrics, onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                RadioPlayerButton(R.drawable.musical_notes, stringResource(R.string.radio_queue), metrics) { showQueue = true }
                RadioPlayerButton(R.drawable.ellipsis_horizontal, stringResource(R.string.radio_more), metrics) {
                    menu.show(if (item.isLocal) MenuPage.LocalSong(item) else MenuPage.Song(item), true)
                }
            }
        }

        @Composable
        fun Details(modifier: Modifier, centered: Boolean) {
            BoxWithConstraints(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                // Short windows and large system fonts drop the album line; the rest scrolls.
                val roomy = maxHeight >= 120.dp * metrics.scale
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(item.mediaMetadata.title?.toString().orEmpty(), style = textStyle, color = Color.White,
                        fontSize = metrics.title, fontWeight = FontWeight.Bold,
                        maxLines = if (roomy) 2 else 1, overflow = TextOverflow.Ellipsis)
                    Text(item.mediaMetadata.artist?.toString().orEmpty(), style = textStyle, color = Color.White.copy(alpha = .85f),
                        fontSize = metrics.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (roomy && !item.mediaMetadata.albumTitle.isNullOrBlank()) {
                        Text(item.mediaMetadata.albumTitle.toString(), style = textStyle, color = Color.White.copy(alpha = .7f),
                            fontSize = metrics.detail, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (error || buffering) {
                        Text(stringResource(if (error) R.string.radio_playback_error else R.string.radio_loading),
                            style = textStyle, color = Color.White, fontSize = metrics.detail)
                    }
                }
            }
        }

        @Composable
        fun SeekBar() {
            val seekLabel = stringResource(R.string.radio_seek)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                Text(radioTime(((seekFraction ?: 0f) * duration).toLong().takeIf { seekFraction != null } ?: position.first),
                    style = textStyle, color = Color.White, fontSize = metrics.time)
                Slider(
                    value = seekFraction ?: if (duration > 0) (position.first.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { seekFraction = it },
                    onValueChangeFinished = {
                        seekFraction?.let { player.seekTo((it * duration).toLong()) }
                        seekFraction = null
                    },
                    enabled = duration > 0 && player.isCurrentMediaItemSeekable,
                    modifier = Modifier.weight(1f).height(metrics.seekBarHeight).semantics { contentDescription = seekLabel },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = .3f)),
                )
                Text(radioTime(duration), style = textStyle, color = Color.White.copy(alpha = .8f), fontSize = metrics.time)
            }
        }

        @Composable
        fun Transport(modifier: Modifier) {
            Row(modifier.widthIn(max = metrics.transportMaxWidth).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RadioPlayerButton(R.drawable.shuffle, stringResource(R.string.shuffle), metrics,
                    Modifier.semantics { selected = shuffle }, active = shuffle) {
                    player.toggleShuffleMode()
                    Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from(player.repeatMode)
                }
                RadioPlayerButton(R.drawable.play_skip_back, stringResource(R.string.radio_previous), metrics) { player.playPrevious() }
                RadioPlayerButton(if (playing && !error) R.drawable.pause else R.drawable.play,
                    stringResource(if (playing && !error) R.string.radio_pause else R.string.radio_play), metrics,
                    Modifier.background(Color.White.copy(alpha = .18f), CircleShape), primary = true) {
                    if (playing && !error) player.pause() else {
                        if (player.playbackState == Player.STATE_IDLE) player.prepare()
                        player.play()
                    }
                }
                RadioPlayerButton(R.drawable.play_skip_forward, stringResource(R.string.radio_next), metrics) { player.playNext() }
                RadioPlayerButton(if (repeat == Player.REPEAT_MODE_ONE) R.drawable.repeatone else R.drawable.repeat,
                    repeatLabel, metrics, active = repeat != Player.REPEAT_MODE_OFF) {
                    player.cycleRepeatMode()
                    Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from(player.repeatMode)
                }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(metrics.padding),
            verticalArrangement = Arrangement.spacedBy(metrics.gap / 2)) {
            Actions()
            if (layout.useTwoPanePlayer) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Square and modest, as in Android Auto: the details keep most of the width.
                    val coverSide = minOf(maxHeight, windowHeight * metrics.coverHeightFraction, maxWidth * .4f)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(metrics.gap * 1.5f),
                        verticalAlignment = Alignment.CenterVertically) {
                        Cover(Modifier.size(coverSide))
                        Details(Modifier.weight(1f).fillMaxHeight(), centered = false)
                    }
                }
            } else {
                Cover(Modifier.weight(1f).fillMaxWidth())
                Details(Modifier.fillMaxWidth(), centered = true)
            }
            SeekBar()
            Transport(Modifier.align(Alignment.CenterHorizontally))
        }
    }
    CustomModalBottomSheet(
        showSheet = showQueue,
        onDismissRequest = { showQueue = false },
        containerColor = colorPalette().background0,
        sheetMaxWidth = Dp.Unspecified,
        glassSurfaceOpaque = true,
    ) {
        Queue(navController, onDismiss = {
            Preferences.QUEUE_LOOP_TYPE.value = it
            showQueue = false
        }, onDiscoverClick = { Preferences.ENABLE_DISCOVER.value = it })
    }
}

@Composable
private fun RadioPlayerButton(
    icon: Int, label: String, metrics: RadioMetrics, modifier: Modifier = Modifier,
    active: Boolean = false, primary: Boolean = false, onClick: () -> Unit,
) {
    IconButton(onClick, modifier.size(if (primary) metrics.playButton else metrics.controlButton)) {
        Icon(painterResource(icon), label, Modifier.size(if (primary) metrics.playIcon else metrics.controlIcon),
            tint = if (active) Color(0xFF8DC6FF) else Color.White)
    }
}

private fun radioTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
