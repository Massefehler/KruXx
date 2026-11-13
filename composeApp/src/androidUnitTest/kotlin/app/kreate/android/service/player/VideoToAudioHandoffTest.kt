package app.kreate.android.service.player

import android.app.Application
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The display turns off while a video plays: the native player starts silently ahead of the
 * video, waits there once buffered and becomes audible exactly when the video arrives.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class VideoToAudioHandoffTest {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: Player
    private var audioReady = false
    private var audioBufferedMs = 0L
    private var ends = 0

    @Before fun setup() {
        exoPlayer = ExoPlayer.Builder( RuntimeEnvironment.getApplication() ).build()
        exoPlayer.setMediaItem( MediaItem.Builder().setMediaId( "set" ).setUri( "https://localhost/set" ).build() )
        // Started silently and playing, as at the beginning of a handoff.
        exoPlayer.volume = 0f
        exoPlayer.playWhenReady = true
        player = object : ForwardingPlayer( exoPlayer ) {
            override fun getPlaybackState(): Int = if( audioReady ) Player.STATE_READY else Player.STATE_BUFFERING
            override fun isPlaying(): Boolean = audioReady && exoPlayer.playWhenReady
            override fun getBufferedPosition(): Long = audioBufferedMs
        }
    }

    @After fun tearDown() = exoPlayer.release()

    private fun handoff( timeoutMs: Long = 2_000L ) = VideoToAudioHandoff(
        player = player,
        // Restores the volume (and, in the app, the audio focus handling).
        makeAudible = { exoPlayer.volume = 0.8f },
        onEnd = { ends++ },
        timeoutMs = timeoutMs,
        pollIntervalMs = 5L,
        now = System::currentTimeMillis
    )

    @Test
    fun theAudioStartsAheadOfTheVideoUnlessTheVideoEndsTooSoon() {
        assertEquals( 31_000L, VideoToAudioHandoff.startPositionMs( 25_000, 3_600_000 ) )
        assertEquals( 31_000L, VideoToAudioHandoff.startPositionMs( 25_000, null ) )
        // Unknown duration in the metadata.
        assertEquals( 31_000L, VideoToAudioHandoff.startPositionMs( 25_000, 0 ) )
        assertEquals( 3_590_000L, VideoToAudioHandoff.startPositionMs( 3_590_000, 3_600_000 ) )
    }

    @Test
    fun aheadOfTheVideoTheAudioWaitsPausedAndResumesWhenTheVideoArrives() = runBlocking {
        val handoff = handoff()
        var videoMs = 25_000L
        var videoPaused = false
        exoPlayer.seekTo( 31_000 )
        audioReady = true

        val job = launch {
            assertNotNull( handoff.run( { videoMs }, { true }, { videoPaused = true }, { true } ) )
        }
        delay( 30 )
        // Holding: paused, silent, the video still sounds.
        assertTrue( handoff.isActive )
        assertFalse( exoPlayer.playWhenReady )
        assertFalse( videoPaused )
        assertEquals( 0f, exoPlayer.volume, 0.0001f )

        videoMs = 30_965
        job.join()

        assertTrue( videoPaused )
        assertTrue( exoPlayer.playWhenReady )
        // Resumed where it waited, no jump.
        assertEquals( 31_000L, exoPlayer.currentPosition )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
        assertEquals( 1, ends )
    }

    @Test
    fun audioReadyOnlyBehindTheVideoJumpsToItWithinTheBuffer() = runBlocking {
        val handoff = handoff()
        var videoPaused = false
        exoPlayer.seekTo( 25_000 )
        audioReady = true
        audioBufferedMs = 27_000

        val job = launch {
            assertNotNull( handoff.run( { 26_000L }, { true }, { videoPaused = true }, { true } ) )
        }
        delay( 30 )
        // Not enough buffer beyond the video yet: a jump now would rebuffer.
        assertTrue( handoff.isActive )
        assertFalse( videoPaused )

        audioBufferedMs = 26_000 + VideoToAudioHandoff.CATCH_UP_MARGIN_MS
        job.join()

        assertTrue( videoPaused )
        assertEquals( 26_000L, exoPlayer.currentPosition )
        assertTrue( exoPlayer.playWhenReady )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
    }

    @Test
    fun theVideoSoundsOnWhileTheAudioIsStillLoading() = runBlocking {
        val handoff = handoff()
        var videoPaused = false

        val job = launch { handoff.run( { 25_000L }, { true }, { videoPaused = true }, { true } ) }
        delay( 40 )

        assertTrue( handoff.isActive )
        assertFalse( videoPaused )
        assertEquals( 0f, exoPlayer.volume, 0.0001f )
        handoff.cancel()
        job.join()
    }

    @Test
    fun completingTakesOverOnce() {
        val handoff = handoff()

        handoff.complete( 42_000 )

        assertEquals( 42_000L, exoPlayer.currentPosition )
        assertTrue( exoPlayer.playWhenReady )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
        assertFalse( handoff.isActive )
        // The video window may close only now.
        assertEquals( 1, ends )

        exoPlayer.seekTo( 43_000 )
        handoff.complete( 1_000 )
        handoff.cancel()
        assertEquals( 43_000L, exoPlayer.currentPosition )
        assertEquals( 1, ends )
    }

    @Test
    fun aVideoThatNoLongerPlaysHandsOverAtOnce() = runBlocking {
        var videoPaused = false

        val sounded = handoff().run( { 30_000L }, { false }, { videoPaused = true }, { true } )

        assertNotNull( sounded )
        assertTrue( videoPaused )
        assertEquals( 30_000L, exoPlayer.currentPosition )
        assertTrue( exoPlayer.playWhenReady )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
    }

    @Test
    fun theAudioTakesOverAfterTheTimeoutEvenWhenNotBuffered() = runBlocking {
        var videoPaused = false

        val sounded = handoff( timeoutMs = 60 ).run( { 5_000L }, { true }, { videoPaused = true }, { true } )

        assertTrue( requireNotNull( sounded ) >= 60 )
        assertTrue( videoPaused )
        assertEquals( 5_000L, exoPlayer.currentPosition )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
    }

    @Test
    fun aCancelledHandoffKeepsTheVideoWhenVisibleAndPausesItWhenStillStopped() = runBlocking {
        var videoPaused = false

        // The display came back before the audio took over: the video simply goes on.
        val visible = handoff()
        val visibleJob = launch {
            assertNull( visible.run( { 1_000L }, { true }, { videoPaused = true }, { false } ) )
        }
        delay( 20 )
        exoPlayer.playWhenReady = false
        visible.cancel()
        visibleJob.join()
        assertFalse( videoPaused )
        assertFalse( exoPlayer.playWhenReady )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )

        // The floating window was closed while stopped: closing means stop.
        exoPlayer.volume = 0f
        val closed = handoff()
        val closedJob = launch {
            assertNull( closed.run( { 1_000L }, { true }, { videoPaused = true }, { true } ) )
        }
        delay( 20 )
        closed.cancel()
        closedJob.join()
        assertTrue( videoPaused )
    }

    @Test
    fun anAbandonedHandoffNeverLeavesTheAudioSilentOrWaiting() = runBlocking {
        val handoff = handoff()
        exoPlayer.seekTo( 31_000 )
        audioReady = true

        val job = launch { handoff.run( { 25_000L }, { true }, {}, { true } ) }
        delay( 20 )
        assertFalse( exoPlayer.playWhenReady )
        job.cancelAndJoin()

        assertFalse( handoff.isActive )
        assertTrue( exoPlayer.playWhenReady )
        assertEquals( 0.8f, exoPlayer.volume, 0.0001f )
        assertEquals( 1, ends )
    }
}
