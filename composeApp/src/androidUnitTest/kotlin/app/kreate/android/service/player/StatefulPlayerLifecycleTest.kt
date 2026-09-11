package app.kreate.android.service.player

import android.app.Application
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.di.preferencesModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class StatefulPlayerLifecycleTest {

    private fun withPlayer( block: (StatefulPlayerImpl) -> Unit ) {
        val context: Context = RuntimeEnvironment.getApplication()
        startKoin { modules(module { single<Context> { context } }, preferencesModule) }
        val exoPlayer = ExoPlayer.Builder(context).build()
        try {
            block( StatefulPlayerImpl(exoPlayer) )
        } finally {
            exoPlayer.release()
            stopKoin()
        }
    }

    @Test
    fun releaseBeforeFirstPlaybackDoesNotRequireAudioEffects() {
        val context: Context = RuntimeEnvironment.getApplication()
        startKoin { modules(module { single<Context> { context } }, preferencesModule) }
        val exoPlayer = ExoPlayer.Builder(context).build()
        try {
            // A user can close the app before playing anything. No audio session or effects
            // exist yet; this previously threw and skipped the service's session cleanup.
            StatefulPlayerImpl(exoPlayer).release()
        } finally {
            exoPlayer.release()
            stopKoin()
        }
    }

    @Test
    fun shuffleActionDrivesTheSharedShuffleMode() = withPlayer { player ->
        // The player's "random play" action shares this state with the media notification
        // and Android Auto. It used to reorder the queue once instead, which left no
        // visible state and did nothing at all while shuffle mode was already on.
        player.repeatMode = Player.REPEAT_MODE_ALL
        assertFalse( player.shuffleModeEnabled )

        player.toggleShuffleMode()
        assertTrue( player.shuffleModeEnabled )
        assertEquals( Player.REPEAT_MODE_OFF, player.repeatMode )

        player.toggleShuffleMode()
        assertFalse( player.shuffleModeEnabled )
    }

    @Test
    fun repeatModeAndShuffleModeStayMutuallyExclusive() = withPlayer { player ->
        player.toggleShuffleMode()
        assertTrue( player.shuffleModeEnabled )

        // Off -> One, which has to switch shuffle back off.
        player.cycleRepeatMode()
        assertEquals( Player.REPEAT_MODE_ONE, player.repeatMode )
        assertFalse( player.shuffleModeEnabled )
    }
}
