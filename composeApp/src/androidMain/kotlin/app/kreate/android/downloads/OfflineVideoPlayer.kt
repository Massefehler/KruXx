@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.view.LayoutInflater
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
import app.kreate.android.service.player.VideoBackgroundPlayback
import app.kreate.android.service.player.VideoToAudioHandoff
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.ui.screens.player.components.VideoFollower
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val logger = Logger.withTag( "VideoBackground" )

/**
 * A saved video without sound that follows the audio in the native player, see [VideoFollower].
 * A local file reports its position exactly and changes speed steplessly, so the distance to the
 * audio is closed in proportion to it.
 */
private class OfflineFollowState( var active: Boolean ) {
    /** Set while this code moves the video, to tell that from the user's controls. */
    var applying = false

    inline fun moveVideo( block: () -> Unit ) {
        applying = true
        try { block() } finally { applying = false }
    }

    companion object {
        const val POLL_MS = 200L
        /** From here on the video seeks instead of changing its speed. */
        const val SEEK_FROM_MS = 1_000L
        /** Picture and sound this close together play at normal speed. */
        const val IN_SYNC_MS = 40L
        /** Share of the distance closed per second. */
        const val SPEED_GAIN = 0.5f
        const val MAX_SPEED_CHANGE = 0.25f
        const val MIN_SPEED_STEP = 0.01f
        const val DRIFT_LOG_INTERVAL_MS = 5_000L

        fun speedFor( driftMs: Long ): Float =
            if( abs( driftMs ) <= IN_SYNC_MS ) 1f
            else 1f + ( driftMs / 1000f * SPEED_GAIN ).coerceIn( -MAX_SPEED_CHANGE, MAX_SPEED_CHANGE )
    }
}

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
    /** Shows the video without sound, following the audio that plays in the native player. */
    follower: VideoFollower? = null,
    onContinueInBackground: () -> VideoToAudioHandoff? = { null },
    /** KruXx' playback volume (0–1), so the video sounds as loud as the audio it switches with. */
    volume: Float = 1f,
    /** `false` shows the video paused at [startSeconds] until the user starts it. */
    autoPlay: Boolean = true,
    /** Increases with every play command from outside (media session, headset) meant for the video. */
    playRequest: Int = 0,
) {
    val context = LocalContext.current
    val onSecond by rememberUpdatedState(onCurrentSecond)
    val onActive by rememberUpdatedState(onPlaybackActiveChanged)
    val onError by rememberUpdatedState(onPlaybackError)
    val onContinue by rememberUpdatedState(onContinueInBackground)
    val currentFollower = rememberUpdatedState(follower)
    val audibleVolume by rememberUpdatedState(volume.coerceIn(0f, 1f))
    val player = remember(asset, context) { ExoPlayer.Builder(context).build() }
    // Created without sound for audio that plays in the native player; it never sounds itself.
    val silent = remember(player) { follower != null }
    val following = remember(player) { OfflineFollowState(active = silent) }
    val handoffScope = rememberCoroutineScope()
    DisposableEffect(player, lifecycleOwner) {
        fun isStopped() = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // The native player is the one that plays; a silent video only follows it.
                if (!silent) onActive(isPlaying)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // The video's own play button: the audio follows.
                if (!following.active || following.applying) return
                val audio = currentFollower.value ?: return
                if (playWhenReady == audio.audioPlayRequested()) return
                logger.i { "Saved video ${if (playWhenReady) "started" else "paused"} by the user; the audio follows" }
                if (playWhenReady) audio.playAudio(null) else audio.pauseAudio()
            }
            override fun onPlayerError(error: PlaybackException) {
                if (silent) {
                    if (following.active) {
                        following.active = false
                        currentFollower.value?.onFailed()
                    }
                } else {
                    onActive(false)
                    onError()
                }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (!silent) {
                    onSecond(newPosition.positionMs / 1000f)
                    return
                }
                // The video's own progress bar: the audio follows.
                if (following.active && !following.applying && reason == Player.DISCONTINUITY_REASON_SEEK) {
                    logger.i { "Saved video moved by the user; the audio follows" }
                    currentFollower.value?.seekAudioTo(newPosition.positionMs / 1000f)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (silent) {
                    // The audio plays on by itself; this video returns with the activity.
                    following.active = false
                } else if (
                    VideoBackgroundPlayback.isPlaybackRequested(
                        isPlaying = player.isPlaying,
                        isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
                    )
                ) {
                    onSecond(player.currentPosition / 1000f)
                    val handoff = onContinue()
                    if (handoff != null) {
                        // The saved video keeps sounding until the native audio has taken over.
                        handoffScope.launch {
                            handoff.run(
                                videoPositionMs = { player.currentPosition },
                                videoPlaying = { player.isPlaying },
                                pauseVideo = { player.pause() },
                                isStopped = ::isStopped
                            )
                        }
                        return@LifecycleEventObserver
                    }
                }
                player.pause()
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(DownloadCenter.file(asset))))
        val audio = follower
        if (silent && audio != null) {
            logger.i { "Saved video returns silently and follows the audio (playing=${audio.audioPlayRequested()})" }
            player.volume = 0f
            // Lining the video up with the audio is no seek of the user: it must not move the audio.
            following.moveVideo {
                player.seekTo((audio.audioPositionSeconds() * 1000).toLong())
                player.prepare()
                player.playWhenReady = audio.audioPlayRequested()
            }
        } else {
            player.volume = audibleVolume
            player.seekTo((startSeconds.coerceAtLeast(0f) * 1000).toLong())
            player.prepare()
            player.playWhenReady = autoPlay
        }
        onDispose {
            if (!silent) {
                onSecond(player.currentPosition / 1000f)
                onActive(false)
            }
            following.active = false
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    // A play command from outside reaches the video, which sounds itself; only new ones count.
    val initialPlayRequest = remember(player) { playRequest }
    LaunchedEffect(playRequest) {
        if (playRequest != initialPlayRequest && !silent) player.play()
    }
    LaunchedEffect(player) {
        if (!silent) {
            while (true) {
                onSecond(player.currentPosition / 1000f)
                delay(500)
            }
        }

        // Keeps the silent video in step with the audio; the audio is never touched here.
        var driftLoggedAt = 0L
        while (following.active) {
            val audio = currentFollower.value
            if (audio != null) following.moveVideo {
                val requested = audio.audioPlayRequested()
                // While the audio buffers, the video waits for it.
                val play = requested && audio.audioAdvancing()
                if (player.playWhenReady != play) player.playWhenReady = play
                if (play && player.isPlaying) {
                    val audioMs = (audio.audioPositionSeconds() * 1000).toLong()
                    val driftMs = audioMs - player.currentPosition
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - driftLoggedAt >= OfflineFollowState.DRIFT_LOG_INTERVAL_MS) {
                        driftLoggedAt = now
                        logger.d { "Saved video follows the audio: $driftMs ms behind, speed ${player.playbackParameters.speed}" }
                    }
                    if (abs(driftMs) >= OfflineFollowState.SEEK_FROM_MS) {
                        logger.d { "Saved video $driftMs ms behind the audio: seeking it" }
                        player.setPlaybackSpeed(1f)
                        player.seekTo(audioMs)
                    } else {
                        val speed = OfflineFollowState.speedFor(driftMs)
                        // Every change re-times the renderers; leave tiny ones out.
                        if (abs(player.playbackParameters.speed - speed) >= OfflineFollowState.MIN_SPEED_STEP)
                            player.setPlaybackSpeed(speed)
                    }
                }
            }
            delay(OfflineFollowState.POLL_MS)
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AndroidView(
            // Inflated for its texture surface, which follows the sheet's rounded corners.
            factory = {
                // The parent only resolves the layout parameters; AndroidView adds the view itself.
                (LayoutInflater.from(it).inflate(R.layout.offline_video_player_view, android.widget.FrameLayout(it), false) as PlayerView)
                    .apply { this.player = player }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = {
                // A silent video just goes away: its audio already plays in the native player.
                following.active = false
                player.pause()
                onSwitchToAudioPlayer()
            },
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) {
            Icon(painterResource(R.drawable.musical_notes), stringResource(R.string.switch_to_audio_playback))
        }
    }
}
