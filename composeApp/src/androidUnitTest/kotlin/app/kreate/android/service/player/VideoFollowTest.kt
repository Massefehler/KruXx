package app.kreate.android.service.player

import app.kreate.android.service.player.VideoFollowControl.Action
import app.kreate.android.service.player.VideoFollowControl.VideoState
import app.kreate.android.service.player.VideoFollowSync.Speed
import app.kreate.android.service.player.VideoFollowSync.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A video whose sound moved to the native player returns without sound and follows that audio.
 * The audio is never touched, unless the user operates the video's own controls.
 */
class VideoFollowTest {

    // region VideoFollowSync

    /**
     * Feeds the same drift [count] times, the video advancing like the audio.
     *
     * @return what was decided on the way; [Step.Wait] if nothing was
     */
    private fun VideoFollowSync.feed( audioSeconds: Float, drift: Float, count: Int = VideoFollowSync.DRIFT_SAMPLES ): Step {
        val decisions = ( 0 until count )
            .map { onProgress( audioSeconds + it * 0.1f, audioSeconds + it * 0.1f - drift ) }
            .filter { it != Step.Wait }
        assertTrue( "more than one decision: $decisions", decisions.size <= 1 )
        return decisions.firstOrNull() ?: Step.Wait
    }

    private fun assertSeekTo( expectedSeconds: Float, step: Step ) {
        assertTrue( "$step", step is Step.SeekTo )
        assertEquals( expectedSeconds, ( step as Step.SeekTo ).seconds, 0.001f )
    }

    private fun VideoFollowSync.settle() =
        repeat( VideoFollowSync.SETTLE_UPDATES ) { assertEquals( Step.Wait, onProgress( 0f, 0f ) ) }

    @Test
    fun aVideoInStepIsLeftAlone() {
        val sync = VideoFollowSync()

        assertEquals( Step.Wait, sync.feed( audioSeconds = 100f, drift = 0.05f, count = 20 ) )
        assertEquals( Step.Wait, sync.feed( audioSeconds = 102f, drift = -0.05f, count = 20 ) )
        assertEquals( Speed.Normal, sync.speed )
    }

    @Test
    fun aSingleLateReportChangesNothing() {
        val sync = VideoFollowSync()

        assertEquals( Step.Wait, sync.onProgress( 100.0f, 99.95f ) )
        // Delivered 0.6 s late while the device was unlocking.
        assertEquals( Step.Wait, sync.onProgress( 100.7f, 100.1f ) )
        assertEquals( Step.Wait, sync.onProgress( 100.3f, 100.25f ) )
        assertEquals( Step.Wait, sync.onProgress( 100.4f, 100.35f ) )
    }

    @Test
    fun aVideoSlightlyBehindRunsFasterUntilItCaughtUp() {
        val sync = VideoFollowSync()

        assertEquals( Step.SetSpeed( Speed.Faster ), sync.feed( audioSeconds = 100f, drift = 0.4f ) )

        // A quarter faster gains 25 ms with every report of 100 ms.
        var drift = 0.4f
        var audio = 100.3f
        var step: Step = Step.Wait
        var reports = 0
        while( step == Step.Wait ) {
            drift -= VideoFollowSync.SPEED_GAIN_PER_UPDATE
            audio += 0.1f
            step = sync.onProgress( audio, audio - drift )
            reports++
        }

        assertEquals( Step.SetSpeed( Speed.Normal ), step )
        assertEquals( Speed.Normal, sync.speed )
        // About 16 reports for 0.4 s; the median lags one report behind.
        assertTrue( "took $reports reports", reports in 14..18 )
        assertTrue( "ended $drift s apart", drift in -0.03f..0.03f )
        assertEquals( Step.Wait, sync.feed( audioSeconds = audio + 0.1f, drift = drift, count = 10 ) )
    }

    @Test
    fun aVideoAheadRunsSlowerUntilTheAudioCaughtUp() {
        val sync = VideoFollowSync()

        assertEquals( Step.SetSpeed( Speed.Slower ), sync.feed( audioSeconds = 100f, drift = -0.3f ) )
        assertEquals( Step.Wait, sync.feed( audioSeconds = 100.3f, drift = -0.2f ) )
        // Overshot: the audio is ahead now.
        assertEquals( Step.SetSpeed( Speed.Normal ), sync.feed( audioSeconds = 100.6f, drift = 0.01f ) )
    }

    @Test
    fun aVideoFarOffSeeksAheadAndWaitsForTheSeek() {
        val sync = VideoFollowSync()

        // The video needed 2 s to load and still stands at the position the audio had back then.
        assertSeekTo( 102.2f + VideoFollowSync.INITIAL_LEAD_SECONDS, sync.feed( audioSeconds = 102f, drift = 2f ) )

        // Progress right after the seek is not judged yet.
        sync.settle()

        assertEquals( Step.Wait, sync.feed( audioSeconds = 103f, drift = -0.05f ) )
        assertEquals( Speed.Normal, sync.speed )
    }

    @Test
    fun theNextSeekUsesTheMeasuredSeekLatency() {
        val sync = VideoFollowSync()

        sync.feed( audioSeconds = 100f, drift = 2f )
        sync.settle()
        // Buffering after the seek took 1.3 s: the video lags 1 s behind despite the 0.3 s lead.
        // That is close enough for a changed speed, but the next seek knows better.
        assertEquals( Step.SetSpeed( Speed.Faster ), sync.feed( audioSeconds = 101.6f, drift = 1f ) )
        assertEquals( 1.3f, sync.leadSeconds, 0.001f )

        // The audio was moved through the notification: back to normal speed, then a seek.
        assertEquals( Step.SetSpeed( Speed.Normal ), sync.feed( audioSeconds = 500f, drift = 400f, count = 2 ) )
        assertSeekTo( 500.2f + 1.3f, sync.feed( audioSeconds = 500.2f, drift = 400f, count = 1 ) )
    }

    @Test
    fun aVideoThatCannotBeLinedUpIsLeftAloneForAWhile() {
        val sync = VideoFollowSync()

        repeat( VideoFollowSync.MAX_SEEKS_IN_A_ROW ) {
            assertTrue( sync.feed( 200f, drift = 200f ) is Step.SeekTo )
            sync.settle()
        }

        // An advert or a stalled stream: no endless seeking.
        assertEquals(
            Step.Wait,
            sync.feed( 200f, drift = 200f, count = VideoFollowSync.DRIFT_SAMPLES + VideoFollowSync.RETRY_AFTER_UPDATES )
        )
        assertTrue( sync.feed( 200f, drift = 200f ) is Step.SeekTo )
    }

    @Test
    fun aPlayerThatIgnoresSpeedChangesIsCorrectedBySeeksAlone() {
        val sync = VideoFollowSync()

        repeat( VideoFollowSync.MAX_FAILED_SPEED_CHANGES ) {
            assertEquals( Step.SetSpeed( Speed.Faster ), sync.feed( audioSeconds = 100f, drift = 0.4f ) )
            // The distance never shrinks.
            var step: Step = Step.Wait
            var reports = 0
            while( step == Step.Wait && reports < 100 ) {
                step = sync.onProgress( 100f, 99.6f )
                reports++
            }
            assertEquals( Step.SetSpeed( Speed.Normal ), step )
        }

        assertTrue( sync.feed( audioSeconds = 100f, drift = 0.4f ) is Step.SeekTo )
        sync.settle()
        // Seeks land less precisely; closer than that is good enough then.
        assertEquals( Step.Wait, sync.feed( audioSeconds = 101f, drift = 0.2f, count = 10 ) )
    }

    @Test
    fun aSeekNeverTargetsANegativePositionOrAnUnboundedLead() {
        val backwards = VideoFollowSync( initialLeadSeconds = 0f )
        assertSeekTo( 0.2f, backwards.feed( audioSeconds = 0f, drift = -5f ) )

        val runaway = VideoFollowSync()
        runaway.feed( 10f, drift = 10f )
        runaway.settle()
        assertSeekTo( 60.2f + VideoFollowSync.MAX_LEAD_SECONDS, runaway.feed( audioSeconds = 60f, drift = 50f ) )
    }

    @Test
    fun reportsFromBeforeAnInterruptionDoNotCount() {
        val sync = VideoFollowSync()

        sync.onProgress( 100.0f, 99.5f )
        sync.onProgress( 100.1f, 99.6f )
        // The video buffered for a moment.
        sync.forgetReports()

        assertEquals( Step.Wait, sync.feed( audioSeconds = 101f, drift = 0.02f ) )
    }

    @Test
    fun aMoveFromOutsideIsNotTakenForTheLatencyOfASeek() {
        val sync = VideoFollowSync()

        sync.feed( audioSeconds = 100f, drift = 2f )
        // The user moved the audio before the seek could be measured.
        sync.onRepositioned( updatesToSkip = 5 )

        assertEquals( Step.Wait, sync.feed( audioSeconds = 300f, drift = 1f, count = 5 ) )
        sync.feed( audioSeconds = 300.5f, drift = 1f )
        assertEquals( VideoFollowSync.INITIAL_LEAD_SECONDS, sync.leadSeconds, 0f )
    }

    @Test
    fun theNextVideoStartsAheadByTheTimeThisOneNeededToLoad() {
        // Loaded with 1 s lead and started 0.8 s behind: it needed 1.8 s.
        assertEquals( 1.8f, VideoFollowSync.nextStartLeadSeconds( 1f, 0.8f ), 0.001f )
        // Started ahead: it needed less.
        assertEquals( 0.6f, VideoFollowSync.nextStartLeadSeconds( 1f, -0.4f ), 0.001f )
        assertEquals( VideoFollowSync.MIN_START_LEAD_SECONDS, VideoFollowSync.nextStartLeadSeconds( 1f, -5f ), 0f )
        assertEquals( VideoFollowSync.MAX_START_LEAD_SECONDS, VideoFollowSync.nextStartLeadSeconds( 1f, 30f ), 0f )
    }

    // endregion

    // region VideoFollowControl

    private var nowMs = 10_000L

    private fun control() = VideoFollowControl( now = { nowMs } )

    /** A video that plays next to the audio at [seconds], reports 100 ms apart. */
    private fun VideoFollowControl.playInStep( seconds: Float, reports: Int = 4 ): Float {
        assertEquals( Action.None, onVideoState( VideoState.Playing, audioPlayRequested = true ) )
        var position = seconds
        repeat( reports ) {
            assertEquals( Action.None, onProgress( position, position, audioPlayRequested = true, audioAdvancing = true ) )
            nowMs += 100
            position += 0.1f
        }
        return position
    }

    @Test
    fun theVideoPausesAndResumesWithTheAudio() {
        val control = control()
        control.playInStep( 100f )

        // Paused through the notification.
        assertEquals( Action.PauseVideo, control.onAudioPlayRequested( false ) )
        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = false ) )

        nowMs += 5_000
        assertEquals( Action.PlayVideo, control.onAudioPlayRequested( true ) )
        assertEquals( Action.None, control.onVideoState( VideoState.Playing, audioPlayRequested = true ) )
    }

    @Test
    fun aVideoThatPausesByItselfNeverPausesTheAudio() {
        val control = control()
        control.playInStep( 100f )

        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )

        // Instead the video is started again.
        nowMs += VideoFollowControl.COMMAND_WINDOW_MS + 1
        assertEquals( Action.PlayVideo, control.onTick( audioSeconds = 102f, audioPlayRequested = true ) )
        // Not again right away: the command needs a moment.
        assertEquals( Action.None, control.onTick( audioSeconds = 102f, audioPlayRequested = true ) )
    }

    @Test
    fun pausingTheVideoInItsOwnControlsPausesTheAudio() {
        val control = control()
        control.playInStep( 100f )

        control.onTouch()
        nowMs += 300
        assertEquals( Action.PauseAudio, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )
        // The audio pauses in turn; the video is paused already.
        assertEquals( Action.None, control.onAudioPlayRequested( false ) )
    }

    @Test
    fun aTouchLongAgoDoesNotExplainAPause() {
        val control = control()
        control.playInStep( 100f )

        control.onTouch()
        nowMs += VideoFollowControl.USER_ACTION_WINDOW_MS + 1
        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )
    }

    @Test
    fun thePauseAnsweringOurOwnCommandIsNotTheUsers() {
        val control = control()
        control.playInStep( 100f )
        control.onTouch()

        // Paused and resumed through the notification in quick succession.
        assertEquals( Action.PauseVideo, control.onAudioPlayRequested( false ) )
        nowMs += 100
        assertEquals( Action.None, control.onAudioPlayRequested( true ) )
        nowMs += 100
        // The answer to the pause arrives only now, while the audio plays again.
        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )

        nowMs += VideoFollowControl.COMMAND_WINDOW_MS + 1
        assertEquals( Action.PlayVideo, control.onTick( audioSeconds = 101f, audioPlayRequested = true ) )
    }

    @Test
    fun startingThePausedVideoStartsTheAudioWithItsFirstPosition() {
        val control = control()
        control.onVideoState( VideoState.Paused, audioPlayRequested = false )

        control.onTouch()
        nowMs += 200
        assertEquals( Action.None, control.onVideoState( VideoState.Playing, audioPlayRequested = false ) )
        // Not paused again while its position is awaited.
        assertEquals( Action.None, control.onTick( audioSeconds = 100f, audioPlayRequested = false ) )

        nowMs += 100
        assertEquals(
            Action.PlayAudio( atSeconds = null ),
            control.onProgress( 100f, 100.1f, audioPlayRequested = false, audioAdvancing = false )
        )
    }

    @Test
    fun startingAVideoThatWasMovedWhilePausedStartsTheAudioThere() {
        val control = control()
        // Shown paused next to the paused audio.
        control.onVideoLoading( atSeconds = 100f )
        control.onVideoState( VideoState.Idle, audioPlayRequested = false )

        // The user drags the progress bar and taps play.
        nowMs += 3_000
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = false )
        nowMs += 100

        assertEquals(
            Action.PlayAudio( atSeconds = 1800f ),
            control.onProgress( 100f, 1800f, audioPlayRequested = false, audioAdvancing = false )
        )
    }

    @Test
    fun startingAVideoWhoseAudioWasMovedMeanwhilePlaysTheAudioWhereItIs() {
        val control = control()
        control.onVideoLoading( atSeconds = 100f )
        control.onVideoState( VideoState.Idle, audioPlayRequested = false )

        // The paused audio is moved through the notification, then the user taps play on the video.
        nowMs += 3_000
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = false )
        nowMs += 100

        assertEquals(
            Action.PlayAudio( atSeconds = null ),
            control.onProgress( 500f, 100.1f, audioPlayRequested = false, audioAdvancing = false )
        )
    }

    @Test
    fun theTickDoesNotFightTheAudioThatStillFadesOutForTheUser() {
        val control = control()
        control.playInStep( 100f )

        control.onTouch()
        assertEquals( Action.PauseAudio, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )

        // A fade-out of several seconds: the audio is still meant to play meanwhile.
        repeat( 5 ) {
            nowMs += VideoFollowControl.TICK_MS
            assertEquals( Action.None, control.onTick( audioSeconds = 101f, audioPlayRequested = true ) )
        }
        assertEquals( Action.None, control.onAudioPlayRequested( false ) )
        nowMs += VideoFollowControl.TICK_MS
        assertEquals( Action.None, control.onTick( audioSeconds = 101f, audioPlayRequested = false ) )
    }

    @Test
    fun resumingTheVideoWhileTheAudioStillFadesOutKeepsTheAudioPlaying() {
        val control = control()
        control.playInStep( 100f )

        control.onTouch()
        assertEquals( Action.PauseAudio, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )
        nowMs += 400
        control.onTouch()
        assertEquals(
            Action.PlayAudio( atSeconds = null ),
            control.onVideoState( VideoState.Playing, audioPlayRequested = true )
        )
        // The audio never stopped.
        assertEquals( Action.None, control.onAudioPlayRequested( true ) )
    }

    @Test
    fun aSeekOfItsOwnFarFromTheAudioIsNeverTakenForTheUsers() {
        // A slow seek before made the lead large.
        val control = VideoFollowControl( sync = VideoFollowSync( initialLeadSeconds = 3f ), now = { nowMs } )
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )

        var audio = 100f
        var action: Action = Action.None
        repeat( VideoFollowSync.DRIFT_SAMPLES ) {
            action = control.onProgress( audio, audio - 2f, audioPlayRequested = true, audioAdvancing = true )
            nowMs += 100
            audio += 0.1f
        }
        val target = ( action as Action.SeekVideoTo ).seconds
        assertEquals( 100.2f + 3f, target, 0.001f )

        // This time the seek is quick: the video arrives 3 s ahead of the audio.
        repeat( 10 ) {
            val next = control.onProgress( audio, target + it * 0.1f, audioPlayRequested = true, audioAdvancing = true )
            assertFalse( "$next", next is Action.SeekAudioTo || next is Action.PlayAudio || next == Action.PauseAudio )
            nowMs += 100
            audio += 0.1f
        }
    }

    @Test
    fun aVideoStartedByTheUserWithoutAPositionStartsTheAudioAfterAMoment() {
        val control = control()
        control.onVideoState( VideoState.Paused, audioPlayRequested = false )
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = false )

        nowMs += VideoFollowControl.USER_PLAY_TIMEOUT_MS - 1
        assertEquals( Action.None, control.onTick( audioSeconds = 100f, audioPlayRequested = false ) )
        nowMs += 1
        assertEquals( Action.PlayAudio( atSeconds = null ), control.onTick( audioSeconds = 100f, audioPlayRequested = false ) )
    }

    @Test
    fun aVideoThatStartsByItselfWhileTheAudioIsPausedIsPausedAgain() {
        val control = control()
        control.onVideoState( VideoState.Paused, audioPlayRequested = false )

        assertEquals( Action.PauseVideo, control.onVideoState( VideoState.Playing, audioPlayRequested = false ) )
    }

    @Test
    fun movingTheVideosProgressBarMovesTheAudio() {
        val control = control()
        val position = control.playInStep( 100f )

        control.onTouch()
        nowMs += 800
        control.onVideoState( VideoState.Buffering, audioPlayRequested = true )
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )

        assertEquals(
            Action.SeekAudioTo( 3600f ),
            control.onProgress( position + 0.8f, 3600f, audioPlayRequested = true, audioAdvancing = true )
        )
    }

    @Test
    fun aJumpOfTheVideoWithoutATouchNeverMovesTheAudio() {
        val control = control()
        var position = control.playInStep( 100f )

        repeat( 10 ) {
            val action = control.onProgress( position, 3600f + it * 0.1f, audioPlayRequested = true, audioAdvancing = true )
            assertFalse( "$action", action is Action.SeekAudioTo || action is Action.PlayAudio || action == Action.PauseAudio )
            nowMs += 100
            position += 0.1f
        }
    }

    @Test
    fun theVideosOwnSeekNextToTheAudioNeverMovesTheAudio() {
        val control = control()
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )

        // Loaded 2 s behind the audio.
        var audio = 102f
        var action: Action = Action.None
        repeat( VideoFollowSync.DRIFT_SAMPLES ) {
            action = control.onProgress( audio, audio - 2f, audioPlayRequested = true, audioAdvancing = true )
            nowMs += 100
            audio += 0.1f
        }
        assertTrue( "$action", action is Action.SeekVideoTo )

        // The seek lands next to the audio: a jump of the video, but not the user's.
        repeat( 10 ) {
            val next = control.onProgress( audio, audio + 0.05f, audioPlayRequested = true, audioAdvancing = true )
            assertEquals( Action.None, next )
            nowMs += 100
            audio += 0.1f
        }
    }

    @Test
    fun whatAnAdvertDoesNeverReachesTheAudio() {
        val control = control()
        val position = control.playInStep( 100f )

        // An advert of 15 s starts in the video of a two-hour set; the user taps "skip".
        control.onVideoState( VideoState.Idle, audioPlayRequested = true )
        control.onVideoDuration( videoDurationSeconds = 15f, audioDurationSeconds = 7200f )
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )
        control.onTouch()
        assertEquals( Action.None, control.onProgress( position + 5f, 3f, audioPlayRequested = true, audioAdvancing = true ) )
        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )

        // The video itself continues where it was loaded, far behind the audio by now.
        control.onVideoState( VideoState.Idle, audioPlayRequested = true )
        control.onVideoDuration( videoDurationSeconds = 7200.3f, audioDurationSeconds = 7200f )
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )
        nowMs += 100
        val action = control.onProgress( position + 20f, position, audioPlayRequested = true, audioAdvancing = true )
        assertEquals( Action.None, action )
    }

    @Test
    fun positionsOfNewlyLoadedContentDoNotContinueTheOldOnes() {
        val control = control()
        val position = control.playInStep( 100f )

        control.onTouch()
        control.onVideoState( VideoState.Idle, audioPlayRequested = true )
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )

        val action = control.onProgress( position, 0f, audioPlayRequested = true, audioAdvancing = true )
        assertEquals( Action.None, action )
    }

    @Test
    fun audioThatBuffersIsNotChased() {
        val control = control()
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )

        // The audio stands still while it loads; the video runs ahead meanwhile.
        repeat( 20 ) {
            val action = control.onProgress( 100f, 100f + it * 0.1f, audioPlayRequested = true, audioAdvancing = false )
            assertEquals( Action.None, action )
            nowMs += 100
        }
        assertNull( control.sync.driftSeconds )
    }

    @Test
    fun anEndedVideoStartsAgainWhereTheAudioPlaysOn() {
        val control = control()
        control.playInStep( 100f )
        control.onVideoDuration( videoDurationSeconds = 300f, audioDurationSeconds = 300f )
        control.onVideoState( VideoState.Ended, audioPlayRequested = true )
        nowMs += VideoFollowControl.COMMAND_WINDOW_MS + 1

        // The audio is about to end as well: nothing to do.
        assertEquals( Action.None, control.onTick( audioSeconds = 299f, audioPlayRequested = true ) )
        // The track repeats.
        assertEquals(
            Action.SeekVideoTo( 2f + VideoFollowSync.INITIAL_LEAD_SECONDS ),
            control.onTick( audioSeconds = 2f, audioPlayRequested = true )
        )
    }

    @Test
    fun theVideoWaitsPausedWhileTheAudioBuffers() {
        val control = control()
        control.playInStep( 100f )

        // The audio is meant to play but buffers, e.g. after a jump or a network gap.
        assertEquals( Action.PauseVideo, control.onAudioStalled( stalled = true, audioPlayRequested = true ) )
        assertEquals( Action.None, control.onVideoState( VideoState.Paused, audioPlayRequested = true ) )
        // Waiting on purpose: the tick must not start it again.
        repeat( 5 ) {
            nowMs += VideoFollowControl.TICK_MS
            assertEquals( Action.None, control.onTick( audioSeconds = 101f, audioPlayRequested = true ) )
        }

        assertEquals( Action.PlayVideo, control.onAudioStalled( stalled = false, audioPlayRequested = true ) )
        assertEquals( Action.None, control.onVideoState( VideoState.Playing, audioPlayRequested = true ) )
    }

    @Test
    fun audioPausedWhileTheVideoWaitedLeavesTheVideoPaused() {
        val control = control()
        control.playInStep( 100f )
        control.onAudioStalled( stalled = true, audioPlayRequested = true )
        control.onVideoState( VideoState.Paused, audioPlayRequested = true )

        assertEquals( Action.None, control.onAudioPlayRequested( false ) )
        assertEquals( Action.None, control.onAudioStalled( stalled = false, audioPlayRequested = false ) )
        nowMs += VideoFollowControl.USER_ACTION_WINDOW_MS + 1
        assertEquals( Action.None, control.onTick( audioSeconds = 101f, audioPlayRequested = false ) )

        // Played again later: the video plays along.
        assertEquals( Action.PlayVideo, control.onAudioPlayRequested( true ) )
    }

    @Test
    fun aStallOfAudioTheUserJustStartedDoesNotPauseTheVideo() {
        val control = control()
        control.onVideoState( VideoState.Paused, audioPlayRequested = false )
        control.onTouch()
        control.onVideoState( VideoState.Playing, audioPlayRequested = false )

        // The audio has not been started yet; the video must keep playing for the user.
        assertEquals( Action.None, control.onAudioStalled( stalled = true, audioPlayRequested = true ) )
    }

    @Test
    fun noStallWithoutARunningVideo() {
        val control = control()
        control.onVideoState( VideoState.Paused, audioPlayRequested = true )

        assertEquals( Action.None, control.onAudioStalled( stalled = true, audioPlayRequested = true ) )
        assertEquals( Action.None, control.onAudioStalled( stalled = false, audioPlayRequested = true ) )
    }

    @Test
    fun aPauseOrAJumpEndsAChangedSpeed() {
        val control = control()
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )
        var audio = 100f
        var action: Action = Action.None
        repeat( VideoFollowSync.DRIFT_SAMPLES ) {
            action = control.onProgress( audio, audio - 0.4f, audioPlayRequested = true, audioAdvancing = true )
            nowMs += 100
            audio += 0.1f
        }
        assertEquals( Action.SetVideoSpeed( Speed.Faster ), action )

        // Paused through the notification: the video pauses at normal speed.
        assertEquals( Action.PauseVideo, control.onAudioPlayRequested( false ) )
        assertEquals( Speed.Normal, control.sync.speed )
        control.onVideoState( VideoState.Paused, audioPlayRequested = false )

        // Again faster, then the user moves the progress bar.
        assertEquals( Action.PlayVideo, control.onAudioPlayRequested( true ) )
        control.onVideoState( VideoState.Playing, audioPlayRequested = true )
        repeat( VideoFollowSync.DRIFT_SAMPLES ) {
            action = control.onProgress( audio, audio - 0.4f, audioPlayRequested = true, audioAdvancing = true )
            nowMs += 100
            audio += 0.1f
        }
        assertEquals( Action.SetVideoSpeed( Speed.Faster ), action )
        control.onTouch()
        nowMs += 100
        assertEquals(
            Action.SeekAudioTo( 3000f ),
            control.onProgress( audio, 3000f, audioPlayRequested = true, audioAdvancing = true )
        )
        assertEquals( Speed.Normal, control.sync.speed )
    }

    @Test
    fun aVideoStillPlayingAfterTheAudioPausedIsPausedByTheTick() {
        val control = control()
        control.playInStep( 100f )

        // The pause command got lost.
        nowMs += VideoFollowControl.USER_ACTION_WINDOW_MS + 1
        assertEquals( Action.PauseVideo, control.onTick( audioSeconds = 101f, audioPlayRequested = false ) )
    }

    // endregion
}
