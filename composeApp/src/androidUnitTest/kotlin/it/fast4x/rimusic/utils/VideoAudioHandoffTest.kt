package it.fast4x.rimusic.utils

import android.app.Application
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The player side of a video that continues as audio while the display is off and takes over
 * again afterwards: same track, same position, only the video flag changes.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class VideoAudioHandoffTest {

    private lateinit var player: ExoPlayer

    private fun video( id: String ): MediaItem = MediaItem.Builder()
                                                         .setMediaId( id )
                                                         .setUri( "https://localhost/$id" )
                                                         .setCustomCacheKey( id )
                                                         .setMediaMetadata(
                                                             MediaMetadata.Builder()
                                                                          .setExtras(
                                                                              bundleOf(
                                                                                  "isVideo" to true,
                                                                                  "supportsVideoPlayback" to true,
                                                                                  "musicVideoType" to "MUSIC_VIDEO_TYPE_UGC"
                                                                              )
                                                                          )
                                                                          .build()
                                                         )
                                                         .build()

    @Before fun setup() {
        player = ExoPlayer.Builder( RuntimeEnvironment.getApplication() ).build()
    }

    @After fun tearDown() = player.release()

    @Test
    fun aVideoContinuesAsAudioAtTheMirroredPosition() {
        player.playVideo( video( "set" ) )
        // The embedded player mirrors its position into the paused player.
        player.seekTo( 754_000 )

        player.resumeVideoAsAudio()

        val audio = requireNotNull( player.currentMediaItem )
        assertEquals( "set", audio.mediaId )
        assertFalse( audio.isVideo )
        assertTrue( audio.supportsVideoPlayback )
        assertEquals( "MUSIC_VIDEO_TYPE_UGC", audio.mediaMetadata.extras?.getString( "musicVideoType" ) )
        assertEquals( 754_000L, player.currentPosition )
        assertTrue( player.playWhenReady )
        assertEquals( 1, player.mediaItemCount )
    }

    @Test
    fun theDisplayOffHandoffStartsTheAudioSilently() {
        player.volume = 0.8f
        player.playVideo( video( "set" ) )
        player.seekTo( 754_000 )

        player.resumeVideoAsAudio( startMuted = true )

        assertEquals( 0f, player.volume, 0.0001f )
        assertTrue( player.playWhenReady )
        assertEquals( 754_000L, player.currentPosition )
    }

    @Test
    fun theVideoTakesOverAtTheAudioPositionWithThePlayerPaused() {
        val video = video( "set" )
        player.playVideo( video )
        player.seekTo( 754_000 )
        player.resumeVideoAsAudio()
        // The audio played on while the display was off.
        player.seekTo( 1_812_000 )

        player.playVideo( video )

        val current = requireNotNull( player.currentMediaItem )
        assertEquals( "set", current.mediaId )
        assertTrue( current.isVideo )
        // The embedded player starts here and plays; the native player only mirrors it.
        assertEquals( 1_812_000L, player.currentPosition )
        assertFalse( player.playWhenReady )
        // Unloaded: no parallel buffering, and mirrored seeks do not cycle the playback state.
        assertEquals( Player.STATE_IDLE, player.playbackState )
        player.seekTo( 1_812_100 )
        assertEquals( Player.STATE_IDLE, player.playbackState )
    }
}
