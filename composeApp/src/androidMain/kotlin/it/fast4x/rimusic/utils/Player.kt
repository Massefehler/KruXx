package it.fast4x.rimusic.utils


import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastDistinctBy
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import app.kreate.android.Preferences
import app.kreate.android.R
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.DurationInMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.utils.Toaster
import java.util.ArrayDeque

var GlobalVolume: Float = 0.5f

fun Player.restoreGlobalVolume() {
    volume = GlobalVolume
}

fun Player.saveGlobalVolume() {
    GlobalVolume = volume
}

fun Player.setGlobalVolume(v: Float) {
    GlobalVolume = v
}

fun Player.getGlobalVolume(): Float {
    return GlobalVolume
}

fun Player.isNowPlaying(mediaId: String): Boolean {
    return mediaId == currentMediaItem?.mediaId
}

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

val Timeline.mediaItems: List<MediaItem>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window()).mediaItem
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window())
    }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.removeMediaItems(range: IntRange) = removeMediaItems(range.first, range.last + 1)

//fun Player.seamlessPlay(mediaItem: MediaItem) {
//    if (mediaItem.mediaId == currentMediaItem?.mediaId) {
//        if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex)
//        if (currentMediaItemIndex < mediaItemCount - 1) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
//    } else {
//        forcePlay(mediaItem)
//    }
//}

fun Player.seamlessPlay(mediaItem: MediaItem) {
    if (mediaItem.mediaId == currentMediaItem?.mediaId) {
        if (currentMediaItemIndex > 0) removeMediaItems(0 until currentMediaItemIndex)
        if (currentMediaItemIndex < mediaItemCount - 1)
            removeMediaItems(currentMediaItemIndex + 1 until mediaItemCount)
    } else forcePlay(mediaItem)
}


/**
 * Number of songs queued after the current one **in the order they will actually play**,
 * counted at most up to [limit].
 *
 * [Player.getCurrentMediaItemIndex] is a position in the playlist, not in the playback
 * order. With shuffle mode on the two drift apart, so a plain `mediaItemCount - index`
 * reports an almost empty queue as soon as shuffle happens to start on a song near the
 * end of the playlist - even though most of the queue is still ahead.
 */
@UnstableApi
fun Player.remainingInPlayOrder( limit: Int ): Int {
    val timeline = currentTimeline
    if( timeline.isEmpty ) return 0

    var remaining = 0
    var index = currentMediaItemIndex
    while( remaining < limit ) {
        index = timeline.getNextWindowIndex( index, REPEAT_MODE_OFF, shuffleModeEnabled )
        if( index == C.INDEX_UNSET ) break

        remaining++
    }

    return remaining
}

/**
 * Moves the [addedCount] songs that were just appended to the **end of the shuffle order**.
 *
 * media3 splices new entries into random positions of the shuffle order
 * ([DefaultShuffleOrder.cloneAndInsert]). A queue top-up would therefore play between
 * songs the listener has not heard yet - shuffling an album started with a song of some
 * other artist. Topping up is meant to extend the queue, so under shuffle it has to
 * extend its end as well. Without shuffle the appended songs already come last.
 */
@UnstableApi
fun ExoPlayer.appendedSongsPlayLast( addedCount: Int ) {
    if( addedCount < 1 || !shuffleModeEnabled ) return

    val timeline = currentTimeline
    val total = timeline.windowCount
    val firstAdded = total - addedCount
    // Nothing worth preserving when the whole queue is new
    if( firstAdded < 1 ) return

    val order = ArrayList<Int>( total )
    var index = timeline.getFirstWindowIndex( true )
    while( index != C.INDEX_UNSET ) {
        if( index < firstAdded )
            order.add( index )

        index = timeline.getNextWindowIndex( index, REPEAT_MODE_OFF, true )
    }
    // Bail out instead of handing media3 an order of the wrong length
    if( order.size != firstAdded ) return

    order.addAll( (firstAdded until total).shuffled() )
    setShuffleOrder( DefaultShuffleOrder( order.toIntArray(), System.currentTimeMillis() ) )
}

/**
 * Keeps the currently playing song, at index `0`, and drops every other entry so that
 * a freshly resolved radio queue can be appended behind it.
 *
 * The removal range has to be derived **after** the move. Using the original index
 * instead left that many songs of the replaced queue in front of the new ones.
 */
@UnstableApi
fun Player.keepOnlyCurrentMediaItem() {
    if( mediaItemCount <= 1 ) return

    moveMediaItem( currentMediaItemIndex, 0 )
    removeMediaItems( 1, mediaItemCount )
}

@SuppressLint("Range")
@UnstableApi
fun Player.playAtMedia(mediaItems: List<MediaItem>, mediaId: String) {
    Log.d("mediaItem-playAtMedia","${mediaItems.size}")
    if (mediaItems.isEmpty()) return
    val itemIndex = findMediaItemIndexById(mediaId)

    Log.d("mediaItem-playAtMedia",itemIndex.toString())
    setMediaItems(mediaItems, itemIndex, C.TIME_UNSET)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true

}

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem, true)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

fun Player.playVideo(mediaItem: MediaItem) {
    // Future callers may accidentally pass a queued/audio-only representation. In that case,
    // stay on the normal resolver instead of leaving ExoPlayer paused without a visible video.
    if (!mediaItem.isVideo) {
        forcePlay(mediaItem)
        return
    }

    val startPositionMs = if (currentMediaItem?.mediaId == mediaItem.mediaId) {
        currentPosition.coerceAtLeast(0L)
    } else {
        0L
    }

    pause()
    setMediaItem(mediaItem, startPositionMs)
}

/**
 * Hands the current YouTube video id back to KruXx's audio-only resolver without resetting the
 * position mirrored from the embedded player.
 */
fun Player.resumeVideoAsAudio() {
    val currentItem = currentMediaItem ?: return
    val positionMs = currentPosition.coerceAtLeast(0L)
    val audioExtras = Bundle(currentItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putBoolean("isVideo", false)
    }
    val audioItem = currentItem.buildUpon()
        .setMediaMetadata(
            currentItem.mediaMetadata.buildUpon()
                .setExtras(audioExtras)
                .build()
        )
        .build()

    pause()
    setMediaItem(audioItem, positionMs)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

fun Player.playAtIndex(mediaItemIndex: Int) {
    seekTo(mediaItemIndex, C.TIME_UNSET)
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

@SuppressLint("Range")
@UnstableApi
fun Player.forcePlayAtIndex(mediaItems: List<MediaItem>, mediaItemIndex: Int) {
    if ( mediaItems.isEmpty() ) return

    // This will prevent UI from freezing up during conversion
    CoroutineScope( Dispatchers.Default ).launch {
        val cleanedMediaItems = mediaItems.fastDistinctBy( MediaItem::mediaId )
        /*
            [mediaItemIndex] addresses the original list. Dropping duplicates shifts
            every later position, so the requested song has to be located again -
            otherwise a list with duplicates starts the wrong song, or media3 throws
            IllegalSeekPositionException once the index falls outside the shorter list.
         */
        val startIndex = mediaItems.getOrNull( mediaItemIndex )
                                   ?.mediaId
                                   ?.let { mediaId ->
                                       cleanedMediaItems.indexOfFirst { it.mediaId == mediaId }
                                   }
                                   ?.takeIf { it > -1 }
                                   ?: 0

        runBlocking( Dispatchers.Main ) {
            setMediaItems( cleanedMediaItems, startIndex, C.TIME_UNSET )
            prepare()
            restoreGlobalVolume()
            playWhenReady = true
        }
    }
}
@UnstableApi
fun Player.forcePlayFromBeginning(mediaItems: List<MediaItem>) =
    forcePlayAtIndex(mediaItems, 0)

fun Player.forceSeekToPrevious() {
    if (hasPreviousMediaItem() || currentPosition > maxSeekToPreviousPosition) {
        seekToPrevious()
    } else if (mediaItemCount > 0) {
        seekTo(mediaItemCount - 1, C.TIME_UNSET)
    }
}

fun Player.forceSeekToNext() =
    if (hasNextMediaItem()) seekToNext() else seekTo(0, C.TIME_UNSET)

fun Player.playNext() {
    seekToNextMediaItem()
    //seekToNext()
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

fun Player.playPrevious() {
    seekToPreviousMediaItem()
    //seekToPrevious()
    prepare()
    restoreGlobalVolume()
    playWhenReady = true
}

/**
 * If there's no [MediaItem] before this, or, when [Player.getCurrentPosition]
 * exceeded [Preferences.SMART_REWIND]'s value, player will be automatically
 * starts at the beginning.
 *
 * Else, it will move to previous [MediaItem]
 */
fun Player.smartRewind() =
    if( !hasPreviousMediaItem() || currentPosition > (Preferences.SMART_REWIND.value * 1000) )
        seekTo( 0 )
    else
        seekToPreviousMediaItem()

@UnstableApi
fun Player.addNext( mediaItem: MediaItem ) {
    if (excludeMediaItem(mediaItem)) return

    val itemIndex = findMediaItemIndexById(mediaItem.mediaId)
    if (itemIndex >= 0) removeMediaItem(itemIndex)

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(currentMediaItemIndex + 1, mediaItem)
    }
}

@UnstableApi
fun Player.addNext(mediaItems: List<MediaItem>, context: Context? = null) {
    val filteredMediaItems = if (context != null) excludeMediaItems(mediaItems, context)
    else mediaItems

    filteredMediaItems.forEach { mediaItem ->
        val itemIndex = findMediaItemIndexById(mediaItem.mediaId)
        if (itemIndex >= 0) removeMediaItem(itemIndex)
    }

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        setMediaItems(filteredMediaItems)

        if( playbackState == Player.STATE_IDLE )
            prepare()

        play()
    } else {
        addMediaItems(currentMediaItemIndex + 1, filteredMediaItems)
    }

}


fun Player.enqueue( mediaItem: MediaItem ) {
    if ( excludeMediaItem(mediaItem) ) return

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(mediaItemCount, mediaItem)
    }
}


@UnstableApi
fun Player.enqueue(mediaItems: List<MediaItem>, context: Context? = null) {
    val filteredMediaItems = if (context != null) excludeMediaItems(mediaItems, context)
    else mediaItems

    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        //forcePlayFromBeginning(mediaItems)
        forcePlayFromBeginning(filteredMediaItems)
    } else {
        //addMediaItems(mediaItemCount, mediaItems)
        addMediaItems(mediaItemCount, filteredMediaItems)
    }
}

/*
fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}
*/

fun Player.findNextMediaItemById(mediaId: String): MediaItem? = runCatching {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) return getMediaItemAt(i)
    }
    return null
}.getOrNull()

fun Player.findMediaItemIndexById(mediaId: String): Int {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return i
        }
    }
    return -1
}

fun Player.excludeMediaItems(mediaItems: List<MediaItem>, context: Context): List<MediaItem> {
    var filteredMediaItems = mediaItems
    runCatching {
        val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION

        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            filteredMediaItems = mediaItems.filter {
                (it.mediaMetadata.durationMs ?: Long.MAX_VALUE) < excludeSongWithDurationLimit.asMillis
            }

            val excludedSongs = mediaItems.size - filteredMediaItems.size
            if (excludedSongs > 0)
                Toaster.n( R.string.message_excluded_s_songs, arrayOf( excludedSongs ) )
        }
    }.onFailure {
        Logger.e( "", it, "Player" )
    }

    return filteredMediaItems
}
fun Player.excludeMediaItem(mediaItem: MediaItem): Boolean {
    runCatching {
        val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION
        if (excludeSongWithDurationLimit != DurationInMinutes.Disabled) {
            val excludedSong =
                (mediaItem.mediaMetadata.durationMs ?: Long.MAX_VALUE) <= excludeSongWithDurationLimit.asMillis

            if (excludedSong)
                Toaster.n( R.string.message_excluded_s_songs, arrayOf( 1 ) )

            return excludedSong
        }
    }.onFailure {
        Logger.e( "", it, "Player" )
        return false
    }

    return false

}

val Player.mediaItems: List<MediaItem>
    get() = object : AbstractList<MediaItem>() {
        override val size: Int
            get() = mediaItemCount

        override fun get(index: Int): MediaItem = getMediaItemAt(index)
    }

fun Player.getCurrentQueueIndex(): Int {
    if (currentTimeline.isEmpty) {
        return -1
    }
    var index = 0
    var currentMediaItemIndex = currentMediaItemIndex
    while (currentMediaItemIndex != C.INDEX_UNSET) {
        currentMediaItemIndex = currentTimeline.getPreviousWindowIndex(currentMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
        if (currentMediaItemIndex != C.INDEX_UNSET) {
            index++
        }
    }
    return index
}

fun Player.togglePlayPause() {
    if (!playWhenReady && playbackState == Player.STATE_IDLE) {
        prepare()
    }
    playWhenReady = !playWhenReady
}

fun Player.toggleRepeatMode() {
    repeatMode = when (repeatMode) {
        REPEAT_MODE_OFF -> REPEAT_MODE_ALL
        REPEAT_MODE_ALL -> REPEAT_MODE_ONE
        REPEAT_MODE_ONE -> REPEAT_MODE_OFF
        else -> throw IllegalStateException()
    }
}

fun Player.toggleShuffleMode() {
    shuffleModeEnabled = !shuffleModeEnabled
}

fun Player.getQueueWindows(): List<Timeline.Window> {
    val timeline = currentTimeline
    if (timeline.isEmpty) {
        return emptyList()
    }
    val queue = ArrayDeque<Timeline.Window>()
    val queueSize = timeline.windowCount

    val currentMediaItemIndex: Int = currentMediaItemIndex
    queue.add(timeline.getWindow(currentMediaItemIndex, Timeline.Window()))

    var firstMediaItemIndex = currentMediaItemIndex
    var lastMediaItemIndex = currentMediaItemIndex
    val shuffleModeEnabled = shuffleModeEnabled
    while ((firstMediaItemIndex != C.INDEX_UNSET || lastMediaItemIndex != C.INDEX_UNSET) && queue.size < queueSize) {
        if (lastMediaItemIndex != C.INDEX_UNSET) {
            lastMediaItemIndex = timeline.getNextWindowIndex(lastMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (lastMediaItemIndex != C.INDEX_UNSET) {
                queue.add(timeline.getWindow(lastMediaItemIndex, Timeline.Window()))
            }
        }
        if (firstMediaItemIndex != C.INDEX_UNSET && queue.size < queueSize) {
            firstMediaItemIndex = timeline.getPreviousWindowIndex(firstMediaItemIndex, REPEAT_MODE_OFF, shuffleModeEnabled)
            if (firstMediaItemIndex != C.INDEX_UNSET) {
                queue.addFirst(timeline.getWindow(firstMediaItemIndex, Timeline.Window()))
            }
        }
    }
    return queue.toList()
}
