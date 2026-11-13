package app.kreate.android.service.player

import androidx.media3.common.Player
import app.kreate.android.service.player.VideoBackgroundPlayback.Return
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A playing video continues as audio while the activity cannot be seen and returns once it is
 * visible, silently and following that audio. See [VideoFollowTest] for how it follows.
 */
class VideoBackgroundPlaybackTest {

    private fun continues(
        changingConfigurations: Boolean = false,
        finishing: Boolean = false,
        inPictureInPicture: Boolean = false,
        screenInteractive: Boolean = false,
        floatingWindowJustClosed: Boolean = false,
    ) = VideoBackgroundPlayback.continuesAsAudio(
        changingConfigurations = changingConfigurations,
        finishing = finishing,
        inPictureInPicture = inPictureInPicture,
        screenInteractive = screenInteractive,
        floatingWindowJustClosed = floatingWindowJustClosed,
    )

    @Test
    fun onlyAPlayingOrLoadingVideoCountsAsRequestedPlayback() {
        assertTrue( VideoBackgroundPlayback.isPlaybackRequested( isPlaying = true, isBuffering = false ) )
        assertTrue( VideoBackgroundPlayback.isPlaybackRequested( isPlaying = false, isBuffering = true ) )
        assertFalse( VideoBackgroundPlayback.isPlaybackRequested( isPlaying = false, isBuffering = false ) )
    }

    @Test
    fun audioContinuingAVideoMatchesYouTubesLoudnessNormalization() {
        // Content at or below the reference loudness stays as it is (most DJ sets).
        assertEquals( 1f, VideoBackgroundPlayback.youtubeNormalizationGain( null ), 0f )
        assertEquals( 1f, VideoBackgroundPlayback.youtubeNormalizationGain( -5.28f ), 0f )
        assertEquals( 1f, VideoBackgroundPlayback.youtubeNormalizationGain( 0f ), 0f )
        // Louder content is turned down: +5.7 dB plays at about 52 %, +6.02 dB at half amplitude.
        assertEquals( 0.519f, VideoBackgroundPlayback.youtubeNormalizationGain( 5.7f ), 0.001f )
        assertEquals( 0.5f, VideoBackgroundPlayback.youtubeNormalizationGain( 6.0206f ), 0.001f )
    }

    @Test
    fun aVideoContinuesAsAudioWhenTheDisplayTurnsOffOrTheAppIsLeft() {
        assertTrue( continues( screenInteractive = false ) )
        // Home or another app while the display stays on.
        assertTrue( continues( screenInteractive = true ) )
        // A floating window keeps its audio when the display turns off.
        assertTrue( continues( inPictureInPicture = true, screenInteractive = false ) )
    }

    @Test
    fun closingTheFloatingWindowStopsInsteadOfContinuing() {
        assertFalse( continues( inPictureInPicture = true, screenInteractive = true ) )
        // The mode change may arrive before the stop: the window ended without resuming.
        assertFalse( continues( floatingWindowJustClosed = true ) )
    }

    @Test
    fun aRecreatedOrFinishingActivityDoesNotHandOver() {
        assertFalse( continues( changingConfigurations = true ) )
        assertFalse( continues( finishing = true ) )
    }

    @Test
    fun theVideoReturnsSilentlyWhileItsAudioLives() {
        assertEquals(
            Return.FollowAudio,
            VideoBackgroundPlayback.onVisibleAgain( "set", "set", false, Player.STATE_READY )
        )
        // Buffering, or paused through the notification or a headset while the display was off:
        // the video follows whatever the audio does.
        assertEquals(
            Return.FollowAudio,
            VideoBackgroundPlayback.onVisibleAgain( "set", "set", false, Player.STATE_BUFFERING )
        )
    }

    @Test
    fun theVideoReturnsPausedWithItsOwnSoundOnceItsAudioStopped() {
        assertEquals(
            Return.PausedVideo,
            VideoBackgroundPlayback.onVisibleAgain( "set", "set", false, Player.STATE_IDLE )
        )
    }

    @Test
    fun theVideoDoesNotReturnOnceTheUserMovedOnOrTheTrackEnded() {
        assertEquals(
            Return.Discard,
            VideoBackgroundPlayback.onVisibleAgain( "set", "next", false, Player.STATE_READY )
        )
        assertEquals(
            Return.Discard,
            VideoBackgroundPlayback.onVisibleAgain( "set", null, false, Player.STATE_IDLE )
        )
        assertEquals(
            Return.Discard,
            VideoBackgroundPlayback.onVisibleAgain( "set", "set", false, Player.STATE_ENDED )
        )
        // Already a video again, e.g. reopened from search in the meantime.
        assertEquals(
            Return.Discard,
            VideoBackgroundPlayback.onVisibleAgain( "set", "set", true, Player.STATE_READY )
        )
    }

    @Test
    fun theAudioIsMeantToPlayWhileItPlaysOrBuffers() {
        fun requested(
            playWhenReady: Boolean = true,
            state: Int = Player.STATE_READY,
            suppression: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        ) = VideoBackgroundPlayback.isAudioPlayRequested( playWhenReady, state, suppression )

        assertTrue( requested() )
        assertTrue( requested( state = Player.STATE_BUFFERING ) )
        assertFalse( requested( playWhenReady = false ) )
        assertFalse( requested( state = Player.STATE_IDLE ) )
        assertFalse( requested( state = Player.STATE_ENDED ) )
        // E.g. a phone call that took the audio focus for a moment.
        assertFalse(
            requested( suppression = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS )
        )
    }

    @Test
    fun aPlayCommandWhileTheVideoSoundsItselfGoesToTheVideo() {
        // Notification, lock screen or headset while the video window shows a video with sound.
        assertTrue( VideoBackgroundPlayback.playBelongsToVideo( true, currentIsVideo = true, playWhenReady = true ) )
        // Window closed (mini player): the audio of the track may play as usual.
        assertFalse( VideoBackgroundPlayback.playBelongsToVideo( false, currentIsVideo = true, playWhenReady = true ) )
        // The audio item of a silent video, or audio after "play audio only".
        assertFalse( VideoBackgroundPlayback.playBelongsToVideo( true, currentIsVideo = false, playWhenReady = true ) )
        // A pause never needs redirecting.
        assertFalse( VideoBackgroundPlayback.playBelongsToVideo( true, currentIsVideo = true, playWhenReady = false ) )
    }

    @Test
    fun anotherLengthThanTheAudioMeansTheVideoShowsSomethingElse() {
        // An advert before a two-hour set.
        assertTrue( VideoBackgroundPlayback.isForeignContent( 15f, 7200f ) )
        assertTrue( VideoBackgroundPlayback.isForeignContent( 7200f, 200f ) )
        // The same track: video and audio stream differ by fractions of a second.
        assertFalse( VideoBackgroundPlayback.isForeignContent( 7200.4f, 7200f ) )
        assertFalse( VideoBackgroundPlayback.isForeignContent( 203f, 200f ) )
        // Long tracks may differ by a little more.
        assertFalse( VideoBackgroundPlayback.isForeignContent( 7300f, 7200f ) )
        // Unknown lengths never count as something else.
        assertFalse( VideoBackgroundPlayback.isForeignContent( 0f, 7200f ) )
        assertFalse( VideoBackgroundPlayback.isForeignContent( 15f, null ) )
        assertFalse( VideoBackgroundPlayback.isForeignContent( 15f, 0f ) )
    }
}
