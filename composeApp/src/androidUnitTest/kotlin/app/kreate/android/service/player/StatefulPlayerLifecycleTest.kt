package app.kreate.android.service.player

import android.app.Application
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.di.preferencesModule
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
}
