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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** The regular player stays available via Phone mode; transport and queue use the same service. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
@UnstableApi
@Composable
fun CarRadioPlayer(navController: NavController, onDismiss: () -> Unit, player: StatefulPlayer = koinInject()) {
    val textStyle = it.fast4x.rimusic.typography().m
    val mediaItem by player.currentMediaItemState.collectAsState()
    val item = mediaItem
    if (item == null) {
        Box(Modifier.fillMaxSize().background(colorPalette().background0).safeDrawingPadding().padding(16.dp)) {
            Text(stringResource(R.string.no_songs_playing), Modifier.align(Alignment.Center), style = textStyle, color = colorPalette().text)
            TransportButton(R.drawable.chevron_down, stringResource(R.string.radio_minimize_player), onClick = onDismiss)
        }
        return
    }
    val layout = LocalAdaptiveLayout.current
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
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0D14))) {
        // Bitmap blur also works on Android 7–11; RenderEffect only exists on Android 12+.
        val transformations = remember { if (isAtLeastAndroid12) emptyList() else listOf(BlurTransformation(25, .5f)) }
        ImageFactory.AsyncImage(
            item.mediaMetadata.artworkUri?.toString()?.resize(600, 600),
            transformations = transformations,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (isAtLeastAndroid12) Modifier.blur(28.dp) else Modifier),
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .64f)))
        val compact = maxHeight < 480.dp || fontScale > 1.3f

        @Composable
        fun Cover(modifier: Modifier) {
            Box(modifier, contentAlignment = Alignment.Center) {
                ImageFactory.AsyncImage(
                    item.mediaMetadata.artworkUri?.toString()?.resize(900, 900),
                    contentDescription = stringResource(R.string.radio_cover),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (compact) 8.dp else 24.dp).clip(RoundedCornerShape(16.dp)),
                )
                TransportButton(R.drawable.chevron_down, stringResource(R.string.radio_minimize_player),
                    Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .6f), CircleShape), onClick = onDismiss)
            }
        }

        @Composable
        fun Controls(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                    Text(item.mediaMetadata.title?.toString().orEmpty(), style = textStyle, color = Color.White,
                        fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold,
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Text(item.mediaMetadata.artist?.toString().orEmpty(), style = textStyle, color = Color.White.copy(alpha = .85f),
                        fontSize = if (compact) 18.sp else 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact && !item.mediaMetadata.albumTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(item.mediaMetadata.albumTitle.toString(), style = textStyle, color = Color.White.copy(alpha = .75f),
                            fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (error || buffering) {
                        Text(stringResource(if (error) R.string.radio_playback_error else R.string.radio_loading),
                            style = textStyle, color = Color.White, fontSize = 14.sp)
                    }
                }
                Column {
                    val seekLabel = stringResource(R.string.radio_seek)
                    Slider(
                        value = seekFraction ?: if (duration > 0) (position.first.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seekFraction = it },
                        onValueChangeFinished = {
                            seekFraction?.let { player.seekTo((it * duration).toLong()) }
                            seekFraction = null
                        },
                        enabled = duration > 0 && player.isCurrentMediaItemSeekable,
                        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = seekLabel },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = .3f)),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(radioTime(((seekFraction ?: 0f) * duration).toLong().takeIf { seekFraction != null } ?: position.first),
                            style = textStyle, color = Color.White, fontSize = 14.sp)
                        Text(radioTime(duration), color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    TransportButton(R.drawable.play_skip_back, stringResource(R.string.radio_previous)) { player.playPrevious() }
                    TransportButton(if (playing && !error) R.drawable.pause else R.drawable.play,
                        stringResource(if (playing && !error) R.string.radio_pause else R.string.radio_play),
                        Modifier.background(Color.White.copy(alpha = .18f), CircleShape),
                        size = if (compact) 72.dp else 88.dp) {
                        if (playing && !error) player.pause() else {
                            if (player.playbackState == Player.STATE_IDLE) player.prepare()
                            player.play()
                        }
                    }
                    TransportButton(R.drawable.play_skip_forward, stringResource(R.string.radio_next)) { player.playNext() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TransportButton(R.drawable.shuffle, stringResource(R.string.shuffle),
                        Modifier.semantics { selected = shuffle }, active = shuffle) {
                        player.toggleShuffleMode()
                        Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from(player.repeatMode)
                    }
                    TransportButton(if (repeat == Player.REPEAT_MODE_ONE) R.drawable.repeatone else R.drawable.repeat,
                        repeatLabel, active = repeat != Player.REPEAT_MODE_OFF) {
                        player.cycleRepeatMode()
                        Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from(player.repeatMode)
                    }
                    TransportButton(R.drawable.musical_notes, stringResource(R.string.radio_queue)) { showQueue = true }
                    TransportButton(R.drawable.ellipsis_horizontal, stringResource(R.string.radio_more)) {
                        menu.show(if (item.isLocal) MenuPage.LocalSong(item) else MenuPage.Song(item), true)
                    }
                }
            }
        }

        if (layout.useTwoPanePlayer) {
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Cover(Modifier.weight(.42f).fillMaxHeight())
                Controls(Modifier.weight(.58f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Cover(Modifier.fillMaxWidth().weight(1f))
                Controls(Modifier.fillMaxWidth().weight(if (fontScale > 1.3f) 1.8f else 1.5f))
            }
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
private fun TransportButton(
    icon: Int, label: String, modifier: Modifier = Modifier, size: Dp = 56.dp,
    active: Boolean = false, onClick: () -> Unit,
) {
    IconButton(onClick, modifier.size(size)) {
        Icon(painterResource(icon), label, Modifier.size(if (size > 56.dp) 40.dp else 28.dp),
            tint = if (active) Color(0xFF8DC6FF) else Color.White)
    }
}

private fun radioTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
