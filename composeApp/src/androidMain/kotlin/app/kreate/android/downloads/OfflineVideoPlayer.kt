@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.kreate.android.R
import kotlinx.coroutines.delay

/** Saved videos never instantiate the YouTube WebView or resolve a network stream. */
@Composable
fun OfflineVideoPlayer(
    asset: SavedMedia,
    lifecycleOwner: LifecycleOwner,
    startSeconds: Float,
    onCurrentSecond: (Float) -> Unit,
    onSwitchToAudioPlayer: () -> Unit,
    onPlaybackActiveChanged: (Boolean) -> Unit,
    onPlaybackError: () -> Unit,
) {
    val context = LocalContext.current
    val onSecond by rememberUpdatedState(onCurrentSecond)
    val onActive by rememberUpdatedState(onPlaybackActiveChanged)
    val onError by rememberUpdatedState(onPlaybackError)
    val player = remember(asset, context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { onActive(isPlaying) }
            override fun onPlayerError(error: PlaybackException) { onActive(false); onError() }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                onSecond(newPosition.positionMs / 1000f)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(DownloadCenter.file(asset))))
        player.seekTo((startSeconds.coerceAtLeast(0f) * 1000).toLong())
        player.prepare()
        player.playWhenReady = true
        onDispose {
            onSecond(player.currentPosition / 1000f)
            onActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            onSecond(player.currentPosition / 1000f)
            delay(500)
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = { player.pause(); onSwitchToAudioPlayer() },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) {
            Icon(painterResource(R.drawable.musical_notes), stringResource(R.string.switch_to_audio_playback))
        }
    }
}
