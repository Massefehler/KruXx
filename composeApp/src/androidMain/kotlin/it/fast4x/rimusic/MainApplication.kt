package it.fast4x.rimusic

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import app.kreate.android.Preferences
import app.kreate.android.drawable.AppIcon
import app.kreate.android.service.innertube.InnertubeProvider
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.CrashHandler
import app.kreate.di.THUMBNAIL_SIZE
import app.kreate.di.initKoin
import app.kreate.logging.CoilLogger
import app.kreate.logging.KoinBufferedLogger
import app.kreate.logging.setupLogging
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.metrolist.innertube.YouTube
import com.metrolist.music.utils.InnerTubeXPlayer
import io.ktor.client.HttpClient
import it.fast4x.rimusic.utils.AppLifecycleTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import it.fast4x.innertube.Innertube as LegacyInnertube
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext


class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler(this) )

        val koinLogger = KoinBufferedLogger()
        initKoin {
            logger( koinLogger )

            androidContext( this@MainApplication )
        }

        setupLogging( koinLogger )
        Preferences.applyProductDefaults()

        Innertube.setProvider( InnertubeProvider() )
        YouTube.cookie = Preferences.YOUTUBE_COOKIES.value
        YouTube.visitorData = Preferences.YOUTUBE_VISITOR_DATA.value
        YouTube.dataSyncId = Preferences.YOUTUBE_SYNC_ID.value
        // SearchResultScreen still uses extensions/innertube (oldtube). Keep that client's
        // session in sync too, otherwise the visible search stays anonymous after YTM login.
        LegacyInnertube.cookie = Preferences.YOUTUBE_COOKIES.value
            .takeIf { Preferences.YOUTUBE_LOGIN.value && it.isNotBlank() }
        LegacyInnertube.visitorData = Preferences.YOUTUBE_VISITOR_DATA.value
        LegacyInnertube.dataSyncId = Preferences.YOUTUBE_SYNC_ID.value.takeIf(String::isNotBlank)

        // Stream extraction (InnerTubeX). Warm up player config, cipher solver and
        // PO-token WebView in the background so the first play doesn't pay for it.
        InnerTubeXPlayer.initialize( this )
        CoroutineScope( Dispatchers.IO ).launch {
            delay( 2_500 )
            runCatching { InnerTubeXPlayer.prewarm() }
                .onFailure { Logger.withTag( "InnerTubeXPlayer" ).w( "prewarm failed (${it::class.simpleName})" ) }
        }

        // Register network callback
        getSystemService<ConnectivityManager>()?.run {
            val networkRequest: NetworkRequest = NetworkRequest.Builder()
                                                               .addCapability( NetworkCapabilities.NET_CAPABILITY_INTERNET )
                                                               .build()
            registerNetworkCallback( networkRequest, ConnectivityUtils )
        }
        // Register app lifecycle tracker
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleTracker)
    }

    override fun onTerminate() {
        Preferences.unload()

        super.onTerminate()
    }

    override fun newImageLoader( context: PlatformContext ): ImageLoader {
        val client: HttpClient by inject()
        val diskCache: DiskCache by inject()
        val memoryCache: MemoryCache by inject()
        val appIcon = AppIcon.bitmap( context, THUMBNAIL_SIZE ).asImage()

        // TODO: Add a toggle in setting that let user enable network caching
        // This feature will set an expiration date on cache, forcing
        // user to "re-fetch" the image data again after a period of time.
        // This will potentially double the storage.
        return ImageLoader.Builder(context)
                          .logger( CoilLogger() )
                          .coroutineContext( Dispatchers.IO )
                          .decoderCoroutineContext( Dispatchers.Default )
                          .crossfade( true )
                          .error( appIcon )
                          .memoryCache( memoryCache )
                          .diskCache {
                              if( diskCache.maxSize > 1 )
                                  diskCache
                              else
                                  null
                          }
                          .components {
                              add(
                                  KtorNetworkFetcherFactory(client)
                              )
                          }
                          .build()
    }
}
