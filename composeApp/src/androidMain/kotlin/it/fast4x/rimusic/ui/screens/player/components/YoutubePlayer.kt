package it.fast4x.rimusic.ui.screens.player.components

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.media3.common.MediaItem
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.kreate.android.R
import app.kreate.android.service.player.VideoBackgroundPlayback
import app.kreate.android.service.player.VideoFollowControl
import app.kreate.android.service.player.VideoFollowSync
import app.kreate.android.service.player.VideoToAudioHandoff
import co.touchlab.kermit.Logger
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val logger = Logger.withTag( "VideoBackground" )

/**
 * The silent video of a track whose audio plays in the native player. The audio leads and is
 * never interrupted; it follows the video only where the user operates the video's own controls,
 * see [VideoFollowControl].
 *
 * @param audioPlayRequested whether the native player is meant to play; backed by snapshot state,
 * so that the video follows a change at once
 * @param audioAdvancing whether the native player actually plays, i.e. it is not buffering
 * @param playAudio start the audio, from the given position if there is one
 * @param onFailed the video cannot be shown; the audio keeps playing
 */
@Stable
class VideoFollower(
    val item: MediaItem,
    val audioPositionSeconds: () -> Float,
    val audioDurationSeconds: () -> Float?,
    val audioPlayRequested: () -> Boolean,
    val audioAdvancing: () -> Boolean,
    val playAudio: ( atSeconds: Float? ) -> Unit,
    val pauseAudio: () -> Unit,
    val seekAudioTo: ( seconds: Float ) -> Unit,
    val onFailed: () -> Unit,
) {
    companion object {
        /**
         * Whether a WebView can be silenced as a whole. Without that, a video unmuted in its own
         * controls takes the audio focus, and the native player must not react to losing it.
         */
        val canSilenceWebView: Boolean
            get() = runCatching { WebViewFeature.isFeatureSupported( WebViewFeature.MUTE_AUDIO ) }
                .getOrDefault( false )
    }
}

/** How far ahead of the audio a silent video starts; the time the previous one needed to load. */
private var followStartLeadSeconds = VideoFollowSync.START_LEAD_SECONDS

/** Reports every touch that reaches the video, without consuming it. */
private class TouchObservingFrame( context: Context ) : FrameLayout( context ) {

    var onTouch: () -> Unit = {}

    override fun dispatchTouchEvent( event: MotionEvent ): Boolean {
        onTouch()
        return super.dispatchTouchEvent( event )
    }
}

private fun View.findWebView(): WebView? = when( this ) {
    is WebView -> this
    is ViewGroup -> ( 0 until childCount ).firstNotNullOfOrNull { getChildAt( it ).findWebView() }
    else -> null
}

/**
 * Silences everything this player's WebView plays, whatever its page does. Chromium requests no
 * audio focus for a muted WebView, so the native player keeps playing next to it.
 *
 * @return `false` on a WebView that cannot do this
 */
private fun YouTubePlayerView.silence(): Boolean {
    val webView = findWebView() ?: return false
    if( !WebViewFeature.isFeatureSupported( WebViewFeature.MUTE_AUDIO ) ) return false
    return try {
        WebViewCompat.setAudioMuted( webView, true )
        WebViewCompat.isAudioMuted( webView )
    } catch( _: RuntimeException ) {
        false
    }
}

private fun VideoFollowSync.Speed.toPlaybackRate(): PlayerConstants.PlaybackRate = when( this ) {
    VideoFollowSync.Speed.Slower -> PlayerConstants.PlaybackRate.RATE_0_75
    VideoFollowSync.Speed.Normal -> PlayerConstants.PlaybackRate.RATE_1
    VideoFollowSync.Speed.Faster -> PlayerConstants.PlaybackRate.RATE_1_25
}

private fun PlayerConstants.PlayerState.toFollowState(): VideoFollowControl.VideoState = when( this ) {
    PlayerConstants.PlayerState.PLAYING -> VideoFollowControl.VideoState.Playing
    PlayerConstants.PlayerState.BUFFERING -> VideoFollowControl.VideoState.Buffering
    PlayerConstants.PlayerState.PAUSED -> VideoFollowControl.VideoState.Paused
    PlayerConstants.PlayerState.ENDED -> VideoFollowControl.VideoState.Ended
    PlayerConstants.PlayerState.UNSTARTED,
    PlayerConstants.PlayerState.VIDEO_CUED,
    PlayerConstants.PlayerState.UNKNOWN -> VideoFollowControl.VideoState.Idle
}

/** Bookkeeping read from WebView callbacks; it belongs to the player created with it. */
private class EmbeddedPlaybackSession(
    /** Created without sound for audio that plays in the native player; it never sounds itself. */
    val silent: Boolean
) {
    var state = PlayerConstants.PlayerState.UNKNOWN
    /** The silent video follows the native audio; ends with its window or the activity. */
    var following = silent
    /** The WebView is silenced as a whole, not only by the mute of its IFrame player. */
    var silenced = false
    /** An audible video was set to normal speed once; a silent one before may have changed it. */
    var speedReset = false
    /** The lead a playing video was loaded with, until its first distance is measured. */
    var startLeadSeconds: Float? = null
    val control = VideoFollowControl()
    var handoff: VideoToAudioHandoff? = null

    // The IFrame player reports its position about every 100 ms while it plays.
    private var lastSecond = 0f
    private var lastSecondAt = 0L
    private var driftLoggedAt = 0L

    fun onProgress( second: Float ) {
        lastSecond = second
        lastSecondAt = SystemClock.elapsedRealtime()
    }

    fun reportAgeMs(): Long = SystemClock.elapsedRealtime() - lastSecondAt

    fun isPlaying(): Boolean =
        state == PlayerConstants.PlayerState.PLAYING &&
                SystemClock.elapsedRealtime() - lastSecondAt <= VideoToAudioHandoff.STALLED_MS

    fun positionMs(): Long {
        val reported = ( lastSecond * 1000 ).toLong()
        if( state != PlayerConstants.PlayerState.PLAYING ) return reported
        val sinceReport = SystemClock.elapsedRealtime() - lastSecondAt
        return reported + sinceReport.coerceIn( 0L, VideoToAudioHandoff.STALLED_MS )
    }

    private fun driftMs(): Int? = control.sync.driftSeconds?.let { ( it * 1000 ).toInt() }

    /** Applies what [VideoFollowControl] decided. */
    fun perform( youTubePlayer: YouTubePlayer, follower: VideoFollower, action: VideoFollowControl.Action ) {
        val normalSpeed = action != VideoFollowControl.Action.None &&
                action != VideoFollowControl.Action.PlayVideo &&
                action !is VideoFollowControl.Action.SetVideoSpeed
        if( normalSpeed ) youTubePlayer.setPlaybackRate( PlayerConstants.PlaybackRate.RATE_1 )
        when( action ) {
            VideoFollowControl.Action.None -> Unit
            VideoFollowControl.Action.PlayVideo -> {
                logger.d { "Silent video plays, following the audio" }
                youTubePlayer.play()
            }
            VideoFollowControl.Action.PauseVideo -> {
                logger.d { "Silent video pauses, following the audio" }
                youTubePlayer.pause()
            }
            is VideoFollowControl.Action.SeekVideoTo -> {
                logger.d { "Silent video ${driftMs()} ms behind the audio: seeking it" }
                youTubePlayer.seekTo( action.seconds )
            }
            is VideoFollowControl.Action.SetVideoSpeed -> {
                logger.d { "Silent video ${driftMs()} ms behind the audio: speed ${action.speed.factor}" }
                youTubePlayer.setPlaybackRate( action.speed.toPlaybackRate() )
            }
            is VideoFollowControl.Action.PlayAudio -> {
                logger.i { "Silent video started by the user; the audio follows" }
                follower.playAudio( action.atSeconds )
            }
            VideoFollowControl.Action.PauseAudio -> {
                logger.i { "Silent video paused by the user; the audio follows" }
                follower.pauseAudio()
            }
            is VideoFollowControl.Action.SeekAudioTo -> {
                logger.i { "Silent video moved by the user; the audio follows" }
                follower.seekAudioTo( action.seconds )
            }
        }
    }

    /** Remembers how long this video needed to start, as the lead of the next one. */
    fun learnStartLead() {
        val lead = startLeadSeconds ?: return
        val firstDrift = control.sync.firstDriftSeconds ?: return
        startLeadSeconds = null
        followStartLeadSeconds = VideoFollowSync.nextStartLeadSeconds( lead, firstDrift )
        logger.i { "Silent video started ${( firstDrift * 1000 ).toInt()} ms behind the audio" }
    }

    fun logDriftNowAndThen() {
        val now = SystemClock.elapsedRealtime()
        if( now - driftLoggedAt < DRIFT_LOG_INTERVAL_MS ) return
        val drift = driftMs() ?: return
        driftLoggedAt = now
        logger.d { "Silent video follows the audio: $drift ms behind, speed ${control.sync.speed.factor}" }
    }

    private companion object {
        const val DRIFT_LOG_INTERVAL_MS = 5_000L
    }
}

@Composable
fun YoutubePlayer(
    ytVideoId: String,
    lifecycleOwner: LifecycleOwner,
    startSeconds: Float = 0f,
    showPlayer: Boolean = true,
    /** `false` shows the video paused at [startSeconds] until the user starts it. */
    autoPlay: Boolean = true,
    /** Shows the video without sound, following the audio that plays in the native player. */
    follower: VideoFollower? = null,
    /** KruXx' playback volume (0–1), so the video sounds as loud as the audio it switches with. */
    volume: Float = 1f,
    /** Increases with every play command from outside (media session, headset) meant for the video. */
    playRequest: Int = 0,
    onCurrentSecond: (second: Float) -> Unit,
    onSwitchToAudioPlayer: () -> Unit,
    onPlaybackActiveChanged: (isActive: Boolean) -> Unit,
    onPlaybackError: () -> Unit,
    onContinueInBackground: () -> VideoToAudioHandoff? = { null }
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
                onCurrentSecond, onSwitchToAudioPlayer, onPlaybackActiveChanged, onPlaybackError,
                follower, onContinueInBackground, volume, autoPlay, playRequest)
            return
        }
    }

    val context = LocalContext.current
    val currentStartSeconds = rememberUpdatedState(startSeconds.coerceAtLeast(0f))
    val currentAutoPlay = rememberUpdatedState(autoPlay)
    val currentOnSecond = rememberUpdatedState(onCurrentSecond)
    val currentOnPlaybackActiveChanged = rememberUpdatedState(onPlaybackActiveChanged)
    val currentOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val currentOnContinueInBackground = rememberUpdatedState(onContinueInBackground)
    val currentFollower = rememberUpdatedState(follower)
    val currentVolume = rememberUpdatedState(volume)
    val session = remember(ytVideoId) { EmbeddedPlaybackSession(silent = follower != null) }
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
    val failFollowing = remember(ytVideoId) {
        {
            if (session.following) {
                session.following = false
                logger.w { "Silent video cannot be shown; the audio keeps playing" }
                currentFollower.value?.onFailed()
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
                    if (!session.silent) {
                        // Linear like the native player's volume; YouTube keeps it across players.
                        youTubePlayer.setVolume((currentVolume.value.coerceIn(0f, 1f) * 100).toInt())
                        // YouTube keeps its mute state across players; never inherit a silent video.
                        youTubePlayer.unMute()
                        val start = currentStartSeconds.value
                        if (currentAutoPlay.value) youTubePlayer.loadVideo(ytVideoId, start)
                        else youTubePlayer.cueVideo(ytVideoId, start)
                        return
                    }

                    // The sound of this track stays with the native player: this video never sounds.
                    youTubePlayer.mute()
                    // Ended while it loaded (activity stopped, window closed): it goes away unstarted.
                    val following = currentFollower.value.takeIf { session.following } ?: return
                    val audioSeconds = following.audioPositionSeconds()
                    val playing = following.audioPlayRequested()
                    if (playing) {
                        // Ahead by its loading time, so that it starts next to the audio.
                        val start = audioSeconds + followStartLeadSeconds
                        session.startLeadSeconds = followStartLeadSeconds
                        session.control.onVideoLoading(start)
                        youTubePlayer.loadVideo(ytVideoId, start)
                    } else {
                        session.control.onVideoLoading(audioSeconds)
                        youTubePlayer.cueVideo(ytVideoId, audioSeconds)
                    }
                    logger.i {
                        "Video returns silently and follows the audio (playing=$playing, WebView silenced=${session.silenced})"
                    }
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    session.onProgress(second)
                    if (!session.silent) {
                        currentOnSecond.value(second)
                        return
                    }

                    val following = currentFollower.value.takeIf { session.following } ?: return
                    val action = session.control.onProgress(
                        audioSeconds = following.audioPositionSeconds(),
                        videoSeconds = second,
                        audioPlayRequested = following.audioPlayRequested(),
                        audioAdvancing = following.audioAdvancing()
                    )
                    session.learnStartLead()
                    session.logDriftNowAndThen()
                    session.perform(youTubePlayer, following, action)
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                    if (!session.following) return
                    val following = currentFollower.value ?: return
                    session.control.onVideoDuration(duration, following.audioDurationSeconds())
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    session.state = state
                    if (session.silent) {
                        // The native player is the one that plays; this video only follows it.
                        val following = currentFollower.value.takeIf { session.following } ?: return
                        val action = session.control.onVideoState(
                            newState = state.toFollowState(),
                            audioPlayRequested = following.audioPlayRequested()
                        )
                        session.perform(youTubePlayer, following, action)
                        return
                    }

                    when( state ) {
                        PlayerConstants.PlayerState.PLAYING -> {
                            if (!session.speedReset) {
                                // YouTube may keep the speed a silent video used before.
                                session.speedReset = true
                                youTubePlayer.setPlaybackRate( PlayerConstants.PlaybackRate.RATE_1 )
                            }
                            reportPlaybackActive( true )
                        }
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
                    if (session.silent) {
                        failFollowing()
                        return
                    }
                    if (!fallbackRequested) {
                        fallbackRequested = true
                        currentOnPlaybackError.value()
                    }
                }
            }

            YouTubePlayerView(context).also { createdView = it }.apply {
                enableAutomaticInitialization = false
                // Hidden windows must not pause the WebView: it keeps sounding while the native
                // player takes over. Every other stop still pauses it through ON_STOP below.
                enableBackgroundPlayback(true)
                if (session.silent) session.silenced = silence()
                initialize(listener, true, iFramePlayerOptions)
            }
        } catch (_: Exception) {
            createdView?.release()
            null
        }
    }

    LaunchedEffect(youtubePlayerView) {
        if (youtubePlayerView == null) {
            if (session.silent) failFollowing()
            else currentOnPlaybackError.value()
            return@LaunchedEffect
        }
        if (!session.following) return@LaunchedEffect

        // The audio starts, pauses and buffers without the video noticing: tell it at once.
        launch {
            snapshotFlow {
                currentFollower.value?.let { it.audioPlayRequested() to it.audioAdvancing() }
            }.collectLatest {
                val following = currentFollower.value ?: return@collectLatest
                if (embeddedPlayer == null || !session.following) return@collectLatest
                // The state now, not the one this emission reports: it may have changed again.
                session.perform(
                    embeddedPlayer ?: return@collectLatest,
                    following,
                    session.control.onAudioPlayRequested(following.audioPlayRequested())
                )
                fun stalled() = following.audioPlayRequested() && !following.audioAdvancing()
                // Short gaps, e.g. while starting, do not stop the video; a new state cancels this.
                if (stalled()) delay(VideoFollowControl.AUDIO_STALL_MS)
                val youTubePlayer = embeddedPlayer ?: return@collectLatest
                if (!session.following) return@collectLatest
                if (stalled()) logger.d { "Audio buffers; the silent video waits" }
                session.perform(
                    youTubePlayer,
                    following,
                    session.control.onAudioStalled(stalled(), following.audioPlayRequested())
                )
            }
        }
        while (session.following) {
            delay(VideoFollowControl.TICK_MS)
            val following = currentFollower.value ?: continue
            val youTubePlayer = embeddedPlayer ?: continue
            if (!session.following) break
            // Unmuted in its controls, the video would sound if its WebView is not silenced,
            // and its mute button would claim a sound that does not come.
            youTubePlayer.mute()
            val action = session.control.onTick(
                audioSeconds = following.audioPositionSeconds(),
                audioPlayRequested = following.audioPlayRequested()
            )
            session.perform(youTubePlayer, following, action)
        }
    }

    if (youtubePlayerView == null) return

    // A play command from outside reaches the video, which sounds itself; only new ones count.
    val initialPlayRequest = remember(youtubePlayerView) { playRequest }
    LaunchedEffect(playRequest) {
        if (playRequest != initialPlayRequest && !session.silent) embeddedPlayer?.play()
    }

    val handoffScope = rememberCoroutineScope()
    DisposableEffect(youtubePlayerView, lifecycleOwner) {
        fun isStopped() = !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

        fun handOverToAudio(handoff: VideoToAudioHandoff) {
            session.handoff = handoff
            handoffScope.launch {
                val soundedMs = handoff.run(
                    videoPositionMs = session::positionMs,
                    videoPlaying = session::isPlaying,
                    pauseVideo = {
                        // The library has not seen this ON_STOP yet; hand it over now.
                        if (isStopped())
                            youtubePlayerView.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_STOP)
                        else
                            embeddedPlayer?.pause()
                    },
                    isStopped = ::isStopped
                )
                if (session.handoff === handoff) session.handoff = null
                logger.i {
                    if (soundedMs == null) "Video to audio handoff cancelled"
                    else "Audio took over; the video sounded on for $soundedMs ms"
                }
            }
        }

        // Forwards the lifecycle to the library, which pauses the WebView on ON_STOP. While the
        // native player takes over, the video keeps sounding instead and is paused afterwards.
        val lifecycleObserver = LifecycleEventObserver { source, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    logger.d { "Activity stopped; video ${session.state}, last position ${session.reportAgeMs()} ms ago" }
                    reportPlaybackActive(false)
                    if (session.silent) {
                        // The audio plays on by itself. This video ends here, before the library
                        // pauses it, so that its pause is not taken for the user's.
                        session.following = false
                        embeddedPlayer?.setPlaybackRate(PlayerConstants.PlaybackRate.RATE_1)
                    } else if (
                        VideoBackgroundPlayback.isPlaybackRequested(
                            isPlaying = session.state == PlayerConstants.PlayerState.PLAYING,
                            isBuffering = session.state == PlayerConstants.PlayerState.BUFFERING
                        )
                    ) {
                        val handoff = currentOnContinueInBackground.value()
                        if (handoff != null) {
                            handOverToAudio(handoff)
                            return@LifecycleEventObserver
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> reportPlaybackActive(false)
                else -> Unit
            }
            youtubePlayerView.onStateChanged(source, event)
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            reportPlaybackActive(false)
            // The video goes away: the native player takes over wherever it is.
            session.handoff?.complete(null)
            session.following = false
            embeddedPlayer?.pause()
            embeddedPlayer = null
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            youtubePlayerView.release()
        }
    }

    // Touches tell the user's pause or seek in the video's own controls from what it does itself.
    val playerContainer: FrameLayout = remember(youtubePlayerView) {
        val container = TouchObservingFrame(context)
        container.onTouch = session.control::onTouch
        container.addView(
            youtubePlayerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        container
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        AndroidView(
            factory = { playerContainer },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        )

        IconButton(
            onClick = {
                // A silent video just goes away: its audio already plays in the native player.
                if (session.following) {
                    session.following = false
                    embeddedPlayer?.setPlaybackRate(PlayerConstants.PlaybackRate.RATE_1)
                }
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
