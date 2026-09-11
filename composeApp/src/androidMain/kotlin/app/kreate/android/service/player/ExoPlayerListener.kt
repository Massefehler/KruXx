package app.kreate.android.service.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexed
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.isLocalFile
import app.kreate.database.models.PersistentQueue
import app.kreate.di.CacheType
import app.kreate.di.CachedFormatMismatchException
import app.kreate.di.invalidateRejectedStreamOf
import co.touchlab.kermit.Logger
import com.metrolist.music.utils.InnerTubeXPlayer
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MissingDecipherKeyException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.remainingInPlayOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
@UnstableApi
class ExoPlayerListener(
    private val player: StatefulPlayer,
    private val mediaSession: MediaSession,
    private val waitingForNetwork: MutableStateFlow<Boolean>,
    private val sendOpenEqualizerIntent: () -> Unit,
    private val sendCloseEqualizerIntent: () -> Unit,
    private val onMediaTransition: (MediaItem?) -> Unit
): Player.Listener, KoinComponent {

    private val context: Context by inject()
    /** Player (not download) cache, see PlayerModule. */
    private val playerCache: Cache by inject( CacheType.CACHE )

    private var volumeNormalizationJob: Job = Job()
    private var errorTimestamp = 0L
    private var lastErrorMessage = ""

    /** Per-song count of automatic recoveries (see [tryRecoverPlaybackError]); cleared on song change. */
    private val recoveryAttempts = HashMap<String, Int>()
    private val recoveryScope = CoroutineScope( Dispatchers.IO )

    var loudnessEnhancer: LoudnessEnhancer? = null
        private set

    /**
     * Requires [Preferences.ENABLE_PERSISTENT_QUEUE] to be **enabled** to work.
     */
    @AnyThread
    fun saveQueueToDatabase() {
        if( !Preferences.ENABLE_PERSISTENT_QUEUE.value ) return

        CoroutineScope( Dispatchers.Default ).launch {
            val (queue, index, playerPos) = withContext(Dispatchers.Main ) {
                // Any call related to [Player] must happen on main thread
                with( player ) {
                    Triple(currentTimeline.mediaItems, currentMediaItemIndex, currentPosition)
                }
            }
            if( queue.isEmpty() ) return@launch

            val queueItems = queue.fastMapIndexed { i, m ->
                PersistentQueue(
                    songId = m.mediaId,
                    position = if( i == index ) playerPos else null
                )
            }
            Database.asyncTransaction {
                queueTable.deleteAll()
                queue.forEach( ::insertIgnore )
                queueTable.insertIgnore( queueItems )
            }
        }
    }

    /**
     * (Re)render media control in notification area.
     */
    @AnyThread
    fun updateMediaControl( context: Context, player: Player ) {
        CoroutineScope(Dispatchers.Default ).launch {
            var firstButton: CommandButton? = null
            var secondButton: CommandButton? = null
            val buttons = mutableListOf<CommandButton>()

            NotificationButtons.entries
                .fastMap { it to PlaybackController.makeButton( context, player, it ) }
                .fastForEach { (nBtn, cmdBtn) ->
                    when (nBtn) {
                        Preferences.MEDIA_NOTIFICATION_FIRST_ICON.value -> firstButton = cmdBtn
                        Preferences.MEDIA_NOTIFICATION_SECOND_ICON.value -> secondButton = cmdBtn
                        else -> buttons.add( cmdBtn )
                    }
                }

            val layoutButton = buildList {
                firstButton?.also( ::add )
                secondButton?.also( ::add )
                addAll( buttons )
            }

            withContext( Dispatchers.Main ) {
                mediaSession.setMediaButtonPreferences( layoutButton )
            }
        }
    }

    private fun loadFromRadio( reason: Int ) {
        // Don't fetch more item if:
        // - Feature is disabled
        // - When song is repeated
        // - Start new queue
        if( !Preferences.QUEUE_AUTO_APPEND.value
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        ) return

        // Make sure only add when about 10 songs to the last song in queue
        // TODO: Add slider in settings to let user change number of songs
        /*
            Counted in playback order, not in playlist order. `mediaItemCount - index`
            ignores the shuffle order: switching shuffle on while a song near the end of
            the playlist plays made this look like an almost empty queue, so the radio
            appended immediately and the rest of the album was never reached.
         */
        if( player.remainingInPlayOrder( SONGS_LEFT_BEFORE_RADIO ) >= SONGS_LEFT_BEFORE_RADIO
            || player.isLoadingRadio()
        ) return

        /*
            This setting only tops the queue up, so the radio must append.
            The parameterless [StatefulPlayer.startRadio] defaults to append=false,
            which replaces the queue: playing an album dropped its remaining tracks
            and continued with unrelated songs after the very first transition.
            That variant stays reserved for an explicit "start radio" action.
         */
        player.currentMediaItem?.let { player.startRadio( it, append = true ) }
    }

    private enum class Recovery {
        /** Signed url expired or rejected by the CDN (HTTP 403/410/416): re-resolve the stream. */
        STREAM_REJECTED,
        /** Cached bytes don't belong to the resolved file, or the cache file is gone: drop the cache entry. */
        CORRUPT_CACHE
    }

    private fun classifyRecoverable( error: PlaybackException ): Recovery? {
        val responseCode = error.httpResponseCodeOrNull()
        if( isRejectedStreamHttpStatus(responseCode)
            || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        ) return Recovery.STREAM_REJECTED

        if( findCause<CachedFormatMismatchException>( error ) != null
            || error.errorCode in CORRUPT_CACHE_ERROR_CODES
        ) return Recovery.CORRUPT_CACHE

        return null
    }

    /**
     * Automatic recovery for two failure classes of online songs, resuming at the current position:
     *
     * - [Recovery.STREAM_REJECTED]: signed stream urls expire or get rejected (HTTP 403/410) when
     *   YouTube rotates keys or a client stops being served. Drop the cached url and let InnerTubeX
     *   re-resolve the song, possibly with another client.
     * - [Recovery.CORRUPT_CACHE]: the player cache is keyed by video id, so it can hold bytes of a
     *   different itag than the stream being played (quality/client changed since they were cached);
     *   the extractor then fails on garbage. Drop everything cached for the song and load it again.
     *
     * At most [MAX_RECOVERY_ATTEMPTS] per song, then the error is surfaced as usual.
     *
     * @return `true` if a retry was scheduled and the error must not be reported.
     */
    @MainThread
    private fun tryRecoverPlaybackError( error: PlaybackException ): Boolean {
        val mediaItem = player.currentMediaItem ?: return false
        val mediaId = mediaItem.mediaId
        // Local files: nothing to re-resolve or to drop from the player cache
        if( mediaItem.localConfiguration?.uri?.isLocalFile() == true ) return false

        val recovery = classifyRecoverable( error ) ?: return false

        val attempts = recoveryAttempts[mediaId] ?: 0
        if( attempts >= MAX_RECOVERY_ATTEMPTS ) return false
        recoveryAttempts[mediaId] = attempts + 1
        val logger = Logger.withTag( "ExoPlayerListener" )

        when( recovery ) {
            Recovery.STREAM_REJECTED -> {
                val responseCode = error.httpResponseCodeOrNull()
                val failedClient = invalidateRejectedStreamOf( mediaId )
                if( failedClient == "WEB_REMIX" )
                    InnerTubeXPlayer.markWebRemixFailed( mediaId )
                recoveryScope.launch {
                    runCatching { InnerTubeXPlayer.refreshAfterStreamRejection() }
                }
                logger.w( "Stream of $mediaId rejected (HTTP $responseCode, client=$failedClient) - re-resolving, attempt ${attempts + 1}/$MAX_RECOVERY_ATTEMPTS" )
            }

            Recovery.CORRUPT_CACHE -> {
                // The resolved url stays valid; only the cached bytes are unusable.
                runCatching { playerCache.removeResource( mediaId ) }
                    .onFailure { logger.e( "failed to drop cached spans of $mediaId", it ) }
                logger.w( "Cached data of $mediaId unusable (${error.errorCodeName}: ${error.cause?.message ?: error.message}) - dropped cache entry, reloading, attempt ${attempts + 1}/$MAX_RECOVERY_ATTEMPTS" )
            }
        }

        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        player.prepare()
        player.seekTo( index, position )
        player.playWhenReady = playWhenReady
        return true
    }

    private inline fun <reified T: Throwable> findCause( t: Throwable? ): T? {
        var cause = t
        while( cause != null ) {
            if( cause is T ) return cause
            cause = cause.cause
        }
        return null
    }

    @MainThread
    private fun traverseErrorStack( t: Throwable ): Throwable =
        when( t ) {
            is PlayableFormatNotFoundException,
            is UnplayableException,
            is LoginRequiredException,
            is NoInternetException,
            is UnknownException,
            is MissingDecipherKeyException -> t

            else -> t.cause?.let( ::traverseErrorStack ) ?: t
        }

    @MainThread
    private fun printErrorMessage( errMsg: String )  {
        // If the same error is set within 10s, it'll be ignored.
        val timeWindow = errorTimestamp + 10.seconds.inWholeMilliseconds

        if( errMsg == lastErrorMessage
            && System.currentTimeMillis() <= timeWindow
        ) return

        lastErrorMessage = errMsg
        // When field is successfully set, update timestamp.
        errorTimestamp = System.currentTimeMillis()
        // Finally, print the error if not blank
        if( errMsg.isNotBlank() )
            Toaster.e( errMsg, Toast.LENGTH_LONG )
    }

    override fun onPlayWhenReadyChanged( playWhenReady: Boolean, reason: Int ) = saveQueueToDatabase()

    override fun onRepeatModeChanged( repeatMode: Int ) {
        updateMediaControl( context, this.player )
        Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from( repeatMode )
    }

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        // Every song gets a fresh recovery budget (see tryRecoverPlaybackError)
        recoveryAttempts.clear()
        if ( player.playerError != null ) player.prepare()

        loadFromRadio(reason)
        onMediaTransition( mediaItem )
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if ( reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED )
            saveQueueToDatabase()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateMediaControl( context, this.player )
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlayerError( error: PlaybackException ) {
        if( tryRecoverPlaybackError( error ) ) return

        val rootCause = traverseErrorStack( error )

        when( rootCause ) {
            is PlayableFormatNotFoundException -> context.getString( R.string.error_couldn_t_find_a_playable_audio_format )
            is NoInternetException -> context.getString( R.string.no_connection )
            is MissingDecipherKeyException -> context.getString( R.string.error_failed_to_decipher_signature )

            else -> rootCause.message ?: context.getString( R.string.error_unknown )
        }.also( ::printErrorMessage )

        // TODO: Add additional recovery step if type of error allows it

        if ( Preferences.PLAYBACK_SKIP_ON_ERROR.value && player.hasNextMediaItem() )
            player.playNext()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
                if (!player.playWhenReady) {
                    waitingForNetwork.value = false
                }
            }
        }
    }

    private companion object {
        const val MAX_RECOVERY_ATTEMPTS = 3
        /** Queue top-up starts once fewer than this many songs follow the current one. */
        const val SONGS_LEFT_BEFORE_RADIO = 10
        /** Extractor choked on the bytes, or CacheDataSource tripped over its own index/files. */
        val CORRUPT_CACHE_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )
    }
}
