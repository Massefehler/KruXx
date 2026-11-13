package app.kreate.android.service.player

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow

/**
 * Keeps an embedded video audible while its window cannot be seen.
 *
 * The embedded YouTube player is a WebView inside the activity. It pauses when the activity
 * stops, and it owns neither a media session nor a foreground service, so it cannot keep playing
 * with the display off. KruXx therefore hands a playing video over to its regular audio pipeline
 * when the activity stops (display off, app in the background). Once the activity is visible
 * again the sound stays with that pipeline and the video only follows it silently, see
 * [VideoFollowSync]: the sound of two separate outputs cannot be switched without being heard.
 */
object VideoBackgroundPlayback {

    /** The IFrame player is playing or loading after a play request; paused/ended/cued is not. */
    fun isPlaybackRequested( isPlaying: Boolean, isBuffering: Boolean ): Boolean =
        isPlaying || isBuffering

    /**
     * The native player is meant to play, even if it is buffering right now. A silent video of
     * the same track plays exactly then.
     */
    fun isAudioPlayRequested(
        playWhenReady: Boolean,
        @Player.State playbackState: Int,
        @Player.PlaybackSuppressionReason suppressionReason: Int,
    ): Boolean = playWhenReady &&
            suppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            ( playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING )

    /**
     * The gain YouTube's player applies to a video with the content loudness [loudnessDb]: louder
     * content is turned down to the reference, quieter content is left as it is. Audio continuing
     * a video uses it so that the level does not change at the switch.
     */
    fun youtubeNormalizationGain( loudnessDb: Float? ): Float =
        if( loudnessDb == null || loudnessDb <= 0f ) 1f
        else 10f.pow( -loudnessDb / 20f )

    /**
     * Decides, when the activity stops while the video plays, whether it continues as audio.
     *
     * @param inPictureInPicture the activity is still a floating window
     * @param screenInteractive the display is on
     * @param floatingWindowJustClosed the floating window ended without the activity resuming
     */
    fun continuesAsAudio(
        changingConfigurations: Boolean,
        finishing: Boolean,
        inPictureInPicture: Boolean,
        screenInteractive: Boolean,
        floatingWindowJustClosed: Boolean,
    ): Boolean = !changingConfigurations &&
            !finishing &&
            // A floating window stops with the display still on only when the user closes it.
            !( inPictureInPicture && screenInteractive ) &&
            !floatingWindowJustClosed

    sealed interface Return {
        /** Keep the audio: the user moved on to another track or the track ended. */
        data object Discard : Return

        /**
         * Show the video again without its sound. The audio keeps playing (or stays paused)
         * untouched and the video follows it.
         */
        data object FollowAudio : Return

        /** The audio stopped or failed: show the video paused, with its own sound. */
        data object PausedVideo : Return
    }

    /** Decides what happens to a video that continued as audio once the activity is visible. */
    fun onVisibleAgain(
        videoId: String,
        currentMediaId: String?,
        currentIsVideo: Boolean,
        @Player.State playbackState: Int,
    ): Return = when {
        currentMediaId != videoId || currentIsVideo -> Return.Discard
        playbackState == Player.STATE_ENDED -> Return.Discard
        // Nothing left to follow: leave the decision to the user.
        playbackState == Player.STATE_IDLE -> Return.PausedVideo
        else -> Return.FollowAudio
    }

    /**
     * Whether a play command for the native player (media session, headset, lock screen) belongs
     * to the video window instead. While that window shows a video with its own sound, the native
     * player only holds the video's item and follows its position: playing it would sound next to
     * the video, and every followed position would make it jump back.
     */
    fun playBelongsToVideo(
        windowShowsVideoWithItsSound: Boolean,
        currentIsVideo: Boolean,
        playWhenReady: Boolean,
    ): Boolean = windowShowsVideoWithItsSound && currentIsVideo && playWhenReady

    /**
     * Whether the video shows something else than the track the audio plays, e.g. an advert:
     * its length then differs from the audio's. Unknown lengths count as the same track.
     */
    fun isForeignContent( videoDurationSeconds: Float, audioDurationSeconds: Float? ): Boolean {
        if( audioDurationSeconds == null || audioDurationSeconds <= 0f || videoDurationSeconds <= 0f )
            return false
        val tolerance = maxOf( DURATION_TOLERANCE_SECONDS, audioDurationSeconds * DURATION_TOLERANCE_SHARE )
        return abs( videoDurationSeconds - audioDurationSeconds ) > tolerance
    }

    private const val DURATION_TOLERANCE_SECONDS = 5f
    private const val DURATION_TOLERANCE_SHARE = 0.02f
}

/** The level of native audio that continues the video [videoId], see [VideoBackgroundPlayback.youtubeNormalizationGain]. */
class VideoAudioLevel {
    var videoId: String? = null
    var gain: Float = 1f
}

/**
 * Moves a playing video over to the native [player] without an audible gap.
 *
 * The native player starts silently, without audio focus and [LEAD_MS] ahead of the video: the
 * WebView holds the focus and pauses as soon as it loses it. Starting playback right away also
 * starts the foreground service while Android still allows it after the activity stopped; Media3
 * keeps it in the foreground while the native player then waits paused. Once buffered, the
 * native player holds at its position until the still sounding video arrives there, then resumes
 * audibly while the video pauses. If the native player is ready only after the video passed that
 * point, it jumps to the video within its buffer instead.
 *
 * @param makeAudible restores the volume and the audio focus handling of the native player
 * @param onEnd called once when the handoff completes or is cancelled; until then the video must
 * stay on screen, since closing its window would stop it
 */
class VideoToAudioHandoff(
    private val player: Player,
    private val makeAudible: () -> Unit,
    private val onEnd: () -> Unit = {},
    private val timeoutMs: Long = TIMEOUT_MS,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {

    var isActive = true
        private set

    /** Takes over now: at [videoPositionMs], or where the native player holds if `null`. */
    fun complete( videoPositionMs: Long? ) {
        if( !isActive ) return
        isActive = false
        if( videoPositionMs != null ) player.seekTo( videoPositionMs )
        player.playWhenReady = true
        makeAudible()
        onEnd()
    }

    /**
     * Ends the handoff without taking over and restores the native player. To keep the video,
     * pause the native player first: a playing one takes the audio focus from the video.
     */
    fun cancel() {
        if( !isActive ) return
        isActive = false
        makeAudible()
        onEnd()
    }

    /**
     * Keeps the video sounding until the native player can continue seamlessly, then takes over.
     *
     * @param videoPlaying whether the video still plays; a video that stopped hands over at once
     * @param isStopped whether the activity is still stopped; only then does a handoff that was
     * cancelled from outside also pause the video (a closed floating window)
     * @return the time the video kept sounding, or `null` if the handoff was cancelled
     */
    suspend fun run(
        videoPositionMs: () -> Long,
        videoPlaying: () -> Boolean,
        pauseVideo: () -> Unit,
        isStopped: () -> Boolean,
    ): Long? {
        val startedAt = now()
        var holdingAtMs: Long? = null
        try {
            while( true ) {
                if( !isActive ) {
                    if( isStopped() ) pauseVideo()
                    return null
                }

                val video = videoPositionMs()
                val elapsed = now() - startedAt
                if( !videoPlaying() || elapsed >= timeoutMs ) {
                    // The video stopped, or the audio never got ready: take over where the video is.
                    complete( video )
                    pauseVideo()
                    return elapsed
                }

                if( holdingAtMs == null
                    && player.playbackState == Player.STATE_READY
                    && player.playWhenReady
                ) {
                    if( player.currentPosition >= video ) {
                        // Buffered ahead of the video: wait there for it.
                        player.playWhenReady = false
                        holdingAtMs = player.currentPosition
                    } else if( player.bufferedPosition >= video + CATCH_UP_MARGIN_MS ) {
                        // Ready only after the video passed the start point: jump to it within
                        // the buffer, far enough ahead that playback resumes without rebuffering.
                        complete( video )
                        pauseVideo()
                        return elapsed
                    }
                }

                val holding = holdingAtMs
                if( holding != null ) {
                    // Resuming takes a moment, so start just before the video arrives.
                    val untilArrival = holding - START_LATENCY_MS - video
                    if( untilArrival <= 0 ) {
                        complete( null )
                        pauseVideo()
                        return elapsed
                    }
                    delay( untilArrival.coerceAtMost( pollIntervalMs ) )
                } else {
                    delay( pollIntervalMs )
                }
            }
        } finally {
            // Never leave the native player silent or waiting, e.g. when the composition goes away.
            if( isActive ) complete( null )
        }
    }

    companion object {
        /** The native player starts this far ahead of the video, about its usual start-up time. */
        const val LEAD_MS = 6_000L
        /** Native buffer needed beyond a jump target so that playback resumes without rebuffering. */
        const val CATCH_UP_MARGIN_MS = 3_000L
        /** Time a paused native player needs to sound again. */
        const val START_LATENCY_MS = 40L
        /** After this the native player takes over as it is, buffered or not. */
        const val TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 50L
        /** A video without progress reports for this long counts as stopped (throttled WebView). */
        const val STALLED_MS = 1_500L

        /**
         * Where the native player starts for a video at [videoPositionMs]: [LEAD_MS] ahead, unless
         * the video ends too soon for that, then at the video position.
         */
        fun startPositionMs( videoPositionMs: Long, videoDurationMs: Long? ): Long {
            val ahead = videoPositionMs + LEAD_MS
            val endsTooSoon = videoDurationMs != null && videoDurationMs > 0 &&
                    ahead + LEAD_MS > videoDurationMs
            return if( endsTooSoon ) videoPositionMs else ahead
        }
    }
}

/**
 * Keeps a silent video in step with the audio of the same track in the native player.
 *
 * Only the video is corrected, which nobody hears; the audio is never touched. Small offsets are
 * closed by letting the video run a quarter faster or slower, large ones by a seek whose latency
 * is measured and used as the lead of the next one.
 *
 * Fed with the video position on every progress report (the IFrame player reports about every
 * 100 ms while it plays). The drift is the median of [DRIFT_SAMPLES] reports, so a single report
 * that reached the main thread late (e.g. while the device unlocks) does not count.
 */
class VideoFollowSync(
    initialLeadSeconds: Float = INITIAL_LEAD_SECONDS,
) {

    enum class Speed( val factor: Float ) {
        Slower( 0.75f ), Normal( 1f ), Faster( 1.25f )
    }

    sealed interface Step {
        data object Wait : Step
        /** Seek the video to [seconds] and let it play at normal speed. */
        data class SeekTo( val seconds: Float ) : Step
        data class SetSpeed( val speed: Speed ) : Step
    }

    /** The speed the video was last told to play at. */
    var speed = Speed.Normal
        private set

    /** The time a seek takes until the video plays again, as measured so far. */
    var leadSeconds = initialLeadSeconds
        private set

    /** The last measured distance; positive while the video is behind the audio. */
    var driftSeconds: Float? = null
        private set

    /** The first distance measured, i.e. how far off the video started. */
    var firstDriftSeconds: Float? = null
        private set

    private var seeksInARow = 0
    private var measuringSeek = false
    private var updatesToSkip = 0
    private var speedUpdatesLeft = 0
    private var failedSpeedChanges = 0
    private val drifts = ArrayDeque<Float>( DRIFT_SAMPLES )

    /** Drops the reports seen so far: the audio or the video paused or buffered in between. */
    fun forgetReports() = drifts.clear()

    /**
     * The audio or the video was moved from outside. Drops the reports seen so far, ignores the
     * next [updatesToSkip] and does not take the next distance for the latency of a seek.
     */
    fun onRepositioned( updatesToSkip: Int = 0 ) {
        drifts.clear()
        measuringSeek = false
        this.updatesToSkip = maxOf( this.updatesToSkip, updatesToSkip )
    }

    /** The video was sent somewhere at normal speed, outside the steps of this class. */
    fun onVideoRestarted() {
        speed = Speed.Normal
        onRepositioned( SETTLE_UPDATES )
    }

    /** The video was set to normal speed from outside, e.g. because it pauses; ends a speed change. */
    fun resetSpeed() {
        speed = Speed.Normal
    }

    fun onProgress( audioSeconds: Float, videoSeconds: Float ): Step {
        if( updatesToSkip > 0 ) {
            updatesToSkip--
            return Step.Wait
        }

        drifts.addLast( audioSeconds - videoSeconds )
        if( drifts.size < DRIFT_SAMPLES ) return Step.Wait
        val drift = drifts.sorted()[DRIFT_SAMPLES / 2]
        drifts.removeFirst()
        val offset = abs( drift )
        driftSeconds = drift
        if( firstDriftSeconds == null ) firstDriftSeconds = drift

        if( measuringSeek ) {
            // The seek landed `drift` seconds off, so it took the lead plus the drift.
            measuringSeek = false
            leadSeconds = ( leadSeconds + drift ).coerceIn( 0f, MAX_LEAD_SECONDS )
        }

        if( speed != Speed.Normal ) {
            val arrived = offset <= IN_SYNC_SECONDS || ( speed == Speed.Faster ) != ( drift > 0f )
            val tooFar = offset >= SEEK_FROM_SECONDS
            if( !arrived && !tooFar && --speedUpdatesLeft > 0 ) return Step.Wait
            // Far longer than the speed change needs: the player ignores it.
            if( !arrived && !tooFar ) failedSpeedChanges++
            speed = Speed.Normal
            return Step.SetSpeed( Speed.Normal )
        }

        val seeksOnly = failedSpeedChanges >= MAX_FAILED_SPEED_CHANGES
        if( offset <= ( if( seeksOnly ) COARSE_TOLERANCE_SECONDS else TOLERANCE_SECONDS ) ) {
            seeksInARow = 0
            return Step.Wait
        }

        if( offset < SEEK_FROM_SECONDS && !seeksOnly ) {
            speed = if( drift > 0f ) Speed.Faster else Speed.Slower
            speedUpdatesLeft = ( offset / SPEED_GAIN_PER_UPDATE ).toInt() + SPEED_GRACE_UPDATES
            return Step.SetSpeed( speed )
        }

        drifts.clear()
        if( seeksInARow >= MAX_SEEKS_IN_A_ROW ) {
            // Cannot be lined up (an advert, a stalled stream): leave the video alone for a while.
            seeksInARow = 0
            updatesToSkip = RETRY_AFTER_UPDATES
            return Step.Wait
        }
        seeksInARow++
        measuringSeek = true
        updatesToSkip = SETTLE_UPDATES
        return Step.SeekTo( ( audioSeconds + leadSeconds ).coerceAtLeast( 0f ) )
    }

    companion object {
        /** Picture and sound this close together count as in step; a speed change starts beyond. */
        const val TOLERANCE_SECONDS = 0.08f
        /** A speed change ends this close to the audio, so that it does not start again at once. */
        const val IN_SYNC_SECONDS = 0.03f
        /** Tolerance when only seeks are left to correct with: they do not land more precisely. */
        const val COARSE_TOLERANCE_SECONDS = 0.25f
        /** From here on a seek is quicker than a changed speed. */
        const val SEEK_FROM_SECONDS = 1.5f
        const val INITIAL_LEAD_SECONDS = 0.3f
        const val MAX_LEAD_SECONDS = 3f
        /** A video starts this far ahead of the audio by default, about the time it needs to load. */
        const val START_LEAD_SECONDS = 1f
        const val MIN_START_LEAD_SECONDS = 0.3f
        const val MAX_START_LEAD_SECONDS = 4f
        const val MAX_SEEKS_IN_A_ROW = 4
        /** Progress reports to wait after [MAX_SEEKS_IN_A_ROW] seeks that did not help. */
        const val RETRY_AFTER_UPDATES = 50
        /** Progress updates that must pass after a seek before the drift is measured again. */
        const val SETTLE_UPDATES = 3
        /** Progress reports whose median drift decides; odd. */
        const val DRIFT_SAMPLES = 3
        /** What a quarter more or less speed gains per progress report of 100 ms. */
        const val SPEED_GAIN_PER_UPDATE = 0.025f
        /** Reports a speed change may take beyond its calculated time before it counts as ignored. */
        const val SPEED_GRACE_UPDATES = 15
        /** After this many ignored speed changes only seeks are used. */
        const val MAX_FAILED_SPEED_CHANGES = 2

        /** The lead for the next start, given the [lead] used and how far off the video then was. */
        fun nextStartLeadSeconds( lead: Float, firstDriftSeconds: Float ): Float =
            ( lead + firstDriftSeconds ).coerceIn( MIN_START_LEAD_SECONDS, MAX_START_LEAD_SECONDS )
    }
}

/**
 * Decides how a silent video and the native audio of the same track stay together.
 *
 * The audio leads and the video follows it, see [VideoFollowSync]. The audio follows the video
 * only when the user operates the video's own controls, which shows as a touch on the video
 * shortly before: pausing, resuming and moving the progress bar there act on the audio. Whatever
 * the video does by itself (an advert, a stall, a pause caused by the system) never reaches the
 * audio. While the audio is meant to play but buffers, the video waits for it paused.
 *
 * Every action that pauses or moves the video or the audio also returns the video to normal
 * speed, see [Action].
 */
class VideoFollowControl(
    val sync: VideoFollowSync = VideoFollowSync(),
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {

    enum class VideoState { Idle, Buffering, Playing, Paused, Ended }

    /** All actions but [None], [PlayVideo] and [SetVideoSpeed] set the video to normal speed. */
    sealed interface Action {
        data object None : Action
        data object PlayVideo : Action
        data object PauseVideo : Action
        /** Seek the video to [seconds] and let it play at normal speed. */
        data class SeekVideoTo( val seconds: Float ) : Action
        data class SetVideoSpeed( val speed: VideoFollowSync.Speed ) : Action
        /** The user started the video: play the audio, from [atSeconds] if the video was moved. */
        data class PlayAudio( val atSeconds: Float? ) : Action
        data object PauseAudio : Action
        data class SeekAudioTo( val seconds: Float ) : Action
    }

    var state = VideoState.Idle
        private set

    private var touchedAt: Long? = null
    private var commandedAt: Long? = null
    private var foreignContent = false
    private var videoDurationSeconds = 0f
    private var userPlayPendingSince: Long? = null
    private var lastVideoSeconds: Float? = null
    private var lastVideoAt = 0L
    /** Where the video was loaded or cued, until it reports its first position. */
    private var loadedAtSeconds: Float? = null
    /** Where this class last sent the video, see [landedFromOwnSeek]. */
    private var ownSeekTarget: Float? = null
    private var ownSeekAt = 0L
    /** What the audio was last asked to do for the user, until it did so (e.g. while fading). */
    private var expectedAudioPlay: Boolean? = null
    private var audioCommandedAt = 0L
    /** The video was paused because the audio, meant to play, buffers. */
    private var waitingForAudio = false

    private fun userActedRecently(): Boolean =
        touchedAt?.let { now() - it <= USER_ACTION_WINDOW_MS } == true

    private fun commandedRecently(): Boolean =
        commandedAt?.let { now() - it <= COMMAND_WINDOW_MS } == true

    private fun command( action: Action ): Action {
        commandedAt = now()
        if( action != Action.PlayVideo && action !is Action.SetVideoSpeed ) sync.resetSpeed()
        return action
    }

    private fun pauseVideo(): Action = command( Action.PauseVideo )

    private fun seekVideo( seconds: Float ): Action {
        ownSeekTarget = seconds
        ownSeekAt = now()
        return command( Action.SeekVideoTo( seconds ) )
    }

    /** Whether the video arriving at [videoSeconds] comes from a seek of this class. */
    private fun landedFromOwnSeek( videoSeconds: Float ): Boolean {
        val target = ownSeekTarget ?: return false
        val since = ( now() - ownSeekAt ) / 1000f
        if( since > OWN_SEEK_WINDOW_SECONDS ) {
            ownSeekTarget = null
            return false
        }
        return videoSeconds >= target - OWN_SEEK_SLACK_SECONDS &&
                videoSeconds <= target + since * MAX_SPEED_FACTOR + OWN_SEEK_SLACK_SECONDS
    }

    private fun commandAudio( play: Boolean, action: Action ): Action {
        expectedAudioPlay = play
        audioCommandedAt = now()
        sync.resetSpeed()
        return action
    }

    /** Whether the audio has yet to do what it was asked for the user. */
    private fun audioCommandPending( audioPlayRequested: Boolean ): Boolean {
        val expected = expectedAudioPlay ?: return false
        if( expected == audioPlayRequested || now() - audioCommandedAt > AUDIO_COMMAND_TIMEOUT_MS ) {
            expectedAudioPlay = null
            return false
        }
        return true
    }

    /** The user touched the video. */
    fun onTouch() {
        touchedAt = now()
    }

    /** The video was told to load or cue at [atSeconds]; what its player reports next answers that. */
    fun onVideoLoading( atSeconds: Float ) {
        commandedAt = now()
        loadedAtSeconds = atSeconds
        lastVideoSeconds = null
        lastVideoAt = now()
    }

    /** The video player reported the length of what it shows. */
    fun onVideoDuration( videoDurationSeconds: Float, audioDurationSeconds: Float? ) {
        this.videoDurationSeconds = videoDurationSeconds
        val foreign = VideoBackgroundPlayback.isForeignContent( videoDurationSeconds, audioDurationSeconds )
        if( foreign == foreignContent ) return
        foreignContent = foreign
        // Another timeline: positions before and after do not belong together.
        lastVideoSeconds = null
        loadedAtSeconds = null
        sync.onRepositioned()
    }

    fun onVideoState( newState: VideoState, audioPlayRequested: Boolean ): Action {
        state = newState
        sync.forgetReports()
        // The user paused the video and the audio is still on its way there.
        val pausingForUser = expectedAudioPlay == false && audioCommandPending( audioPlayRequested )
        return when( newState ) {
            VideoState.Playing -> when {
                foreignContent || userPlayPendingSince != null -> Action.None
                // Started again by the user before the audio paused: it plays on.
                pausingForUser && userActedRecently() -> commandAudio( true, Action.PlayAudio( null ) )
                audioPlayRequested -> Action.None
                // Started by the user: the audio starts with the first position report, so
                // that it starts where the video stands if its progress bar was moved meanwhile.
                userActedRecently() -> {
                    userPlayPendingSince = now()
                    Action.None
                }
                // Started by itself while the audio is paused.
                else -> pauseVideo()
            }

            VideoState.Buffering -> Action.None

            VideoState.Paused -> {
                userPlayPendingSince = null
                if( audioPlayRequested && !pausingForUser && !foreignContent &&
                    userActedRecently() && !commandedRecently()
                )
                    commandAudio( false, Action.PauseAudio )
                else
                    Action.None
            }

            VideoState.Idle, VideoState.Ended -> {
                // New content or a restart: its positions do not continue the previous ones.
                userPlayPendingSince = null
                lastVideoSeconds = null
                Action.None
            }
        }
    }

    /** The native player was started or paused, by whatever means; pass its current state. */
    fun onAudioPlayRequested( requested: Boolean ): Action {
        if( !requested ) waitingForAudio = false
        // The audio did what it was asked for the user; the video is there already.
        if( expectedAudioPlay == requested ) {
            expectedAudioPlay = null
            return Action.None
        }
        val videoRuns = state == VideoState.Playing || state == VideoState.Buffering
        return when {
            requested && !videoRuns && !waitingForAudio -> command( Action.PlayVideo )
            !requested && videoRuns && userPlayPendingSince == null -> pauseVideo()
            else -> Action.None
        }
    }

    /**
     * Whether the audio, meant to play, has buffered for [AUDIO_STALL_MS] by now ([stalled]) or
     * plays again. The video waits for it paused instead of running ahead.
     */
    fun onAudioStalled( stalled: Boolean, audioPlayRequested: Boolean ): Action {
        val videoRuns = state == VideoState.Playing || state == VideoState.Buffering
        return when {
            stalled && videoRuns && !waitingForAudio && userPlayPendingSince == null && !foreignContent -> {
                waitingForAudio = true
                pauseVideo()
            }
            !stalled && waitingForAudio -> {
                waitingForAudio = false
                if( audioPlayRequested && !videoRuns ) command( Action.PlayVideo ) else Action.None
            }
            else -> Action.None
        }
    }

    /**
     * @param audioPlayRequested the native player is meant to play
     * @param audioAdvancing the native player actually plays, i.e. it is not buffering
     */
    fun onProgress(
        audioSeconds: Float,
        videoSeconds: Float,
        audioPlayRequested: Boolean,
        audioAdvancing: Boolean,
    ): Action {
        val previousSeconds = lastVideoSeconds ?: loadedAtSeconds
        val sincePrevious = ( ( now() - lastVideoAt ) / 1000f ).coerceIn( 0f, MAX_REPORT_GAP_SECONDS )
        lastVideoSeconds = videoSeconds
        loadedAtSeconds = null
        lastVideoAt = now()
        if( foreignContent ) return Action.None

        // The video's timeline jumped although this class did not send it there: its progress
        // bar. A seek of this class lands next to its target and so never counts.
        val advanced = previousSeconds?.let { videoSeconds - it }
        val jumped = advanced != null &&
                ( advanced < -JUMP_SECONDS || advanced > sincePrevious * MAX_SPEED_FACTOR + JUMP_SECONDS ) &&
                !landedFromOwnSeek( videoSeconds )
        val distance = abs( audioSeconds - videoSeconds )
        val movedByUser = jumped && userActedRecently() && distance >= USER_SEEK_MIN_SECONDS

        if( userPlayPendingSince != null ) {
            userPlayPendingSince = null
            if( !audioPlayRequested ) {
                sync.onRepositioned( AUDIO_SETTLE_UPDATES )
                return commandAudio( true, Action.PlayAudio( videoSeconds.takeIf { movedByUser } ) )
            }
        }
        if( !audioPlayRequested ) return Action.None

        if( movedByUser ) {
            sync.onRepositioned( AUDIO_SETTLE_UPDATES )
            sync.resetSpeed()
            return Action.SeekAudioTo( videoSeconds )
        }

        if( !audioAdvancing ) {
            sync.forgetReports()
            return Action.None
        }
        return when( val step = sync.onProgress( audioSeconds, videoSeconds ) ) {
            VideoFollowSync.Step.Wait -> Action.None
            is VideoFollowSync.Step.SeekTo -> seekVideo( step.seconds )
            is VideoFollowSync.Step.SetSpeed -> Action.SetVideoSpeed( step.speed )
        }
    }

    /** Call about every [TICK_MS]: restores what the events above could not, e.g. a lost command. */
    fun onTick( audioSeconds: Float, audioPlayRequested: Boolean ): Action {
        val pendingSince = userPlayPendingSince
        if( pendingSince != null ) {
            if( now() - pendingSince < USER_PLAY_TIMEOUT_MS ) return Action.None
            // No position came with the start: play the audio where it stands.
            userPlayPendingSince = null
            if( !audioPlayRequested && state == VideoState.Playing )
                return commandAudio( true, Action.PlayAudio( null ) )
        }
        if( audioCommandPending( audioPlayRequested ) || commandedRecently() ) return Action.None

        return if( audioPlayRequested ) when( state ) {
            // While the audio buffers, the video waits paused on purpose.
            VideoState.Paused, VideoState.Idle ->
                if( waitingForAudio ) Action.None else command( Action.PlayVideo )
            // The audio is not at its end, e.g. it repeats: let the video start again there.
            VideoState.Ended ->
                if( videoDurationSeconds > 0f && audioSeconds < videoDurationSeconds - ENDED_MARGIN_SECONDS ) {
                    sync.onVideoRestarted()
                    seekVideo( audioSeconds + sync.leadSeconds )
                } else {
                    Action.None
                }
            VideoState.Playing, VideoState.Buffering -> Action.None
        } else when( state ) {
            VideoState.Playing, VideoState.Buffering ->
                if( userActedRecently() || foreignContent ) Action.None else pauseVideo()
            else -> Action.None
        }
    }

    companion object {
        const val TICK_MS = 1_000L
        /** A touch on the video this long ago still explains what its player does next. */
        const val USER_ACTION_WINDOW_MS = 6_000L
        /** What the video player reports this soon after a command is the answer to it. */
        const val COMMAND_WINDOW_MS = 1_500L
        /** A video started by the user without a position report starts the audio after this. */
        const val USER_PLAY_TIMEOUT_MS = 500L
        /** A change of the video position beyond normal playback by this much is a jump. */
        const val JUMP_SECONDS = 1f
        /** A jump counts as the user's seek only if it ends this far from the audio. */
        const val USER_SEEK_MIN_SECONDS = 2.5f
        /** Reports come only while the video plays, at most this far apart in playing time. */
        const val MAX_REPORT_GAP_SECONDS = 0.5f
        const val MAX_SPEED_FACTOR = 1.25f
        const val ENDED_MARGIN_SECONDS = 3f
        /** Progress reports to ignore after the audio was moved: it buffers first. */
        const val AUDIO_SETTLE_UPDATES = 5
        /** A jump of the video this soon after a seek of this class may be that seek arriving. */
        const val OWN_SEEK_WINDOW_SECONDS = 10f
        const val OWN_SEEK_SLACK_SECONDS = 1f
        /** The audio may take this long to do what the user asked for, e.g. to fade out. */
        const val AUDIO_COMMAND_TIMEOUT_MS = 15_000L
        /** Audio that is meant to play but buffers this long makes the video wait for it. */
        const val AUDIO_STALL_MS = 400L
    }
}
