package it.fast4x.rimusic.ui.screens.player.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.kreate.android.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar


@Composable
fun YoutubePlayer(
    ytVideoId: String,
    lifecycleOwner: LifecycleOwner,
    startSeconds: Float = 0f,
    showPlayer: Boolean = true,
    onCurrentSecond: (second: Float) -> Unit,
    onSwitchToAudioPlayer: () -> Unit,
    onPlaybackActiveChanged: (isActive: Boolean) -> Unit,
    onPlaybackError: () -> Unit
) {

    if (!showPlayer) return

    if (app.kreate.android.BuildConfig.INDEPENDENT_FORK) {
        val assets by app.kreate.android.downloads.DownloadCenter.saved.collectAsState()
        val savedVideo = assets.firstOrNull {
            it.track.id == ytVideoId && it.kind == app.kreate.android.downloads.DownloadKind.VIDEO &&
                app.kreate.android.downloads.DownloadCenter.file(it).isFile
        }
        if (savedVideo != null) {
            app.kreate.android.downloads.OfflineVideoPlayer(savedVideo, lifecycleOwner, startSeconds,
                onCurrentSecond, onSwitchToAudioPlayer, onPlaybackActiveChanged, onPlaybackError)
            return
        }
    }

    val context = LocalContext.current
    val currentStartSeconds = rememberUpdatedState(startSeconds.coerceAtLeast(0f))
    val currentOnSecond = rememberUpdatedState(onCurrentSecond)
    val currentOnPlaybackActiveChanged = rememberUpdatedState(onPlaybackActiveChanged)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    var embeddedPlayer by remember(ytVideoId) { mutableStateOf<YouTubePlayer?>(null) }
    val embeddedPlaybackActive = remember(ytVideoId) { mutableStateOf(false) }
    val reportPlaybackActive = remember(ytVideoId) {
        { isActive: Boolean ->
            if (embeddedPlaybackActive.value != isActive) {
                embeddedPlaybackActive.value = isActive
                currentOnPlaybackActiveChanged.value(isActive)
            }
        }
    }

    val youtubePlayerView = remember(context, lifecycleOwner, ytVideoId) {
        var createdView: YouTubePlayerView? = null
        try {
            val iFramePlayerOptions = IFramePlayerOptions.Builder(context)
                .controls(1)
                .build()
            val listener = object : AbstractYouTubePlayerListener() {
                private var fallbackRequested = false

                override fun onReady(youTubePlayer: YouTubePlayer) {
                    embeddedPlayer = youTubePlayer
                    youTubePlayer.loadVideo(ytVideoId, currentStartSeconds.value)
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    currentOnSecond.value(second)
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    when( state ) {
                        PlayerConstants.PlayerState.PLAYING -> reportPlaybackActive( true )
                        PlayerConstants.PlayerState.PAUSED,
                        PlayerConstants.PlayerState.ENDED,
                        PlayerConstants.PlayerState.UNSTARTED,
                        PlayerConstants.PlayerState.VIDEO_CUED -> reportPlaybackActive( false )
                        PlayerConstants.PlayerState.BUFFERING,
                        PlayerConstants.PlayerState.UNKNOWN -> Unit
                    }
                }

                override fun onError(
                    youTubePlayer: YouTubePlayer,
                    error: PlayerConstants.PlayerError
                ) {
                    reportPlaybackActive( false )
                    if (!fallbackRequested) {
                        fallbackRequested = true
                        currentOnPlaybackError.value()
                    }
                }
            }

            YouTubePlayerView(context).also { createdView = it }.apply {
                enableAutomaticInitialization = false
                initialize(listener, true, iFramePlayerOptions)
            }
        } catch (_: Exception) {
            createdView?.release()
            null
        }
    }

    LaunchedEffect(youtubePlayerView) {
        if (youtubePlayerView == null) currentOnPlaybackError.value()
    }

    if (youtubePlayerView == null) return

    DisposableEffect(youtubePlayerView, lifecycleOwner) {
        val playbackLifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY)
                reportPlaybackActive(false)
        }
        lifecycleOwner.lifecycle.addObserver(youtubePlayerView)
        lifecycleOwner.lifecycle.addObserver(playbackLifecycleObserver)
        onDispose {
            reportPlaybackActive(false)
            embeddedPlayer?.pause()
            embeddedPlayer = null
            lifecycleOwner.lifecycle.removeObserver(playbackLifecycleObserver)
            lifecycleOwner.lifecycle.removeObserver(youtubePlayerView)
            youtubePlayerView.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        AndroidView(
            factory = { youtubePlayerView },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        )

        IconButton(
            onClick = {
                embeddedPlayer?.pause()
                onSwitchToAudioPlayer()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 4.dp)
                .size(48.dp)
                .zIndex(2f)
        ) {
            Image(
                painter = painterResource(R.drawable.musical_notes),
                contentDescription = stringResource(R.string.switch_to_audio_playback),
                colorFilter = ColorFilter.tint(colorPalette().collapsedPlayerProgressBar),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
