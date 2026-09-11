package it.fast4x.rimusic.utils

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Queue order guarantees behind "open an album and tap a track".
 *
 * The album kept its order in the list, but the queue did not: the automatic queue
 * top-up replaced it with a radio, and [forcePlayAtIndex] addressed the requested
 * position in the list it had just deduplicated.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class PlayerQueueOrderTest {

    private lateinit var player: ExoPlayer

    // media3 needs a playable uri to build a MediaSource; only the queue order is asserted.
    private fun item( id: String ): MediaItem = MediaItem.Builder()
                                                         .setMediaId( id )
                                                         .setUri( "https://localhost/$id.mp3" )
                                                         .build()

    private fun album( vararg ids: String ): List<MediaItem> = ids.map( ::item )

    private val queueIds: List<String>
        get() = List( player.mediaItemCount ) { player.getMediaItemAt( it ).mediaId }

    /** [forcePlayAtIndex] hands the queue over through a coroutine. */
    private fun awaitQueue() {
        repeat( 200 ) {
            shadowOf( Looper.getMainLooper() ).idle()
            if( player.mediaItemCount > 0 ) return
            Thread.sleep( 10 )
        }
        throw AssertionError( "Player never received a queue" )
    }

    @Before fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        player = ExoPlayer.Builder( context ).build()
    }

    @After fun tearDown() = player.release()

    @Test fun tappingATrackPlaysTheWholeAlbumInOrder() {
        val tracks = album( "t1", "t2", "t3", "t4", "t5" )

        player.forcePlayAtIndex( tracks, 3 )
        awaitQueue()

        assertEquals( listOf( "t1", "t2", "t3", "t4", "t5" ), queueIds )
        assertEquals( "t4", player.currentMediaItem?.mediaId )
    }

    @Test fun duplicatesDoNotShiftTheTappedTrack() {
        // YTM hands out the same id twice often enough; deduplication used to move
        // every later position up without correcting the requested index.
        val tracks = album( "t1", "t1", "t2", "t3" )

        player.forcePlayAtIndex( tracks, 3 )
        awaitQueue()

        assertEquals( listOf( "t1", "t2", "t3" ), queueIds )
        assertEquals( "t3", player.currentMediaItem?.mediaId )
    }

    @Test fun deduplicationCannotPushTheIndexOutOfBounds() {
        // Every entry collapses into one, so the requested index no longer exists.
        // media3 answers an out of range index with IllegalSeekPositionException.
        val tracks = album( "t1", "t1", "t1" )

        player.forcePlayAtIndex( tracks, 2 )
        awaitQueue()

        assertEquals( listOf( "t1" ), queueIds )
        assertEquals( "t1", player.currentMediaItem?.mediaId )
    }

    @Test fun replacingTheQueueLeavesExactlyTheCurrentSong() {
        player.setMediaItems( album( "t1", "t2", "t3", "t4", "t5" ), 2, 0L )

        player.keepOnlyCurrentMediaItem()

        // Deriving the removal range from the index before the move kept "t1" and "t2".
        assertEquals( listOf( "t3" ), queueIds )
        assertEquals( "t3", player.currentMediaItem?.mediaId )
    }

    @Test fun replacingASingleSongQueueChangesNothing() {
        player.setMediaItems( album( "t1" ), 0, 0L )

        player.keepOnlyCurrentMediaItem()

        assertEquals( listOf( "t1" ), queueIds )
    }

    @Test fun remainingIsCountedAfterTheCurrentSongAndCapped() {
        player.setMediaItems( album( "t1", "t2", "t3", "t4", "t5" ), 2, 0L )

        // t4 and t5 are left.
        assertEquals( 2, player.remainingInPlayOrder( 10 ) )

        player.seekTo( 4, 0L )
        assertEquals( 0, player.remainingInPlayOrder( 10 ) )

        player.seekTo( 0, 0L )
        assertEquals( 3, player.remainingInPlayOrder( 3 ) )
    }

    @Test fun remainingFollowsTheShuffleOrderNotThePlaylistOrder() {
        val tracks = album( "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10", "t11", "t12" )
        player.setMediaItems( tracks, 11, 0L )

        // Shuffle starts on the song that happens to sit last in the playlist, with the
        // whole rest of the album still ahead of it.
        player.shuffleModeEnabled = true
        player.setShuffleOrder(
            DefaultShuffleOrder( intArrayOf( 11, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ), 0L )
        )

        assertEquals( "t12", player.currentMediaItem?.mediaId )
        // `mediaItemCount - currentMediaItemIndex` reported 1 here, so the queue top-up
        // fired at once and continued with unrelated songs instead of the album.
        assertEquals( 10, player.remainingInPlayOrder( 10 ) )
    }

    @Test fun remainingReachesZeroOnTheLastSongOfTheShuffleOrder() {
        val tracks = album( "t1", "t2", "t3", "t4" )
        player.setMediaItems( tracks, 0, 0L )

        player.shuffleModeEnabled = true
        player.setShuffleOrder( DefaultShuffleOrder( intArrayOf( 1, 2, 3, 0 ), 0L ) )

        // t1 sits at the end of the shuffle order even though it is first in the playlist.
        assertEquals( "t1", player.currentMediaItem?.mediaId )
        assertEquals( 0, player.remainingInPlayOrder( 10 ) )
    }

    @Test fun appendingRadioKeepsTheRemainingAlbumTracks() {
        // What the automatic queue top-up does once it appends instead of replacing.
        player.setMediaItems( album( "t1", "t2", "t3" ), 1, 0L )

        player.addMediaItems( album( "r1", "r2" ) )

        assertEquals( listOf( "t1", "t2", "t3", "r1", "r2" ), queueIds )
        assertEquals( "t2", player.currentMediaItem?.mediaId )
    }

    @Test fun shuffledAlbumFinishesBeforeAppendedRadioSongs() {
        val albumIds = List( 12 ) { "a${it + 1}" }
        val radioIds = List( 12 ) { "r${it + 1}" }
        player.setMediaItems( album( *albumIds.toTypedArray() ), 0, 0L )
        player.shuffleModeEnabled = true

        player.addMediaItems( album( *radioIds.toTypedArray() ) )
        // media3 splices new entries into random positions of the shuffle order, so radio
        // songs would play between album songs that were not heard yet. Shuffling an album
        // therefore started dropping in other artists right away.
        player.appendedSongsPlayLast( radioIds.size )

        val played = playOrderIds()
        assertEquals( 24, played.size )
        assertEquals( albumIds.toSet(), played.take( 12 ).toSet() )
        assertEquals( radioIds.sorted(), played.drop( 12 ).sorted() )
    }

    @Test fun appendingWithoutShuffleLeavesTheOrderAlone() {
        player.setMediaItems( album( "a1", "a2", "a3" ), 0, 0L )

        player.addMediaItems( album( "r1" ) )
        player.appendedSongsPlayLast( 1 )

        assertEquals( listOf( "a1", "a2", "a3", "r1" ), playOrderIds() )
    }

    /** Media ids in the order they will actually play, following the shuffle order. */
    private fun playOrderIds(): List<String> {
        val timeline = player.currentTimeline
        val ids = mutableListOf<String>()
        var index = timeline.getFirstWindowIndex( player.shuffleModeEnabled )
        while( index != C.INDEX_UNSET ) {
            ids += player.getMediaItemAt( index ).mediaId
            index = timeline.getNextWindowIndex( index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled )
        }
        return ids
    }
}
