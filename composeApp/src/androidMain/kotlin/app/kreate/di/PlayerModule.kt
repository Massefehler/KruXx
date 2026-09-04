@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import app.kreate.android.Preferences
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.player.ErrorHandlingPolicy
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.StatefulPlayerImpl
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.service.player.isRejectedStreamHttpStatus
import app.kreate.android.utils.isLocalFile
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import me.knighthat.impl.DownloadHelperImpl
import me.knighthat.innertube.UserAgents
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory


const val CHUNK_LENGTH = 512 * 1024L     // 512KB

private const val CACHE_DIRNAME = "exo_cache"
private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

/**
 * The app-wide client uses BODY logging in debug builds. OkHttp's logging interceptor buffers a
 * response body completely before returning it to the caller; doing that to a throttled media
 * response makes ExoPlayer wait roughly a song's duration before it receives the first byte.
 *
 * Keep the shared client's proxy, DNS and all functional interceptors, but never attach an HTTP
 * logger to the long-lived playback/download transport. Signed CDN urls should not be logged
 * either.
 */
internal fun OkHttpClient.forMediaTransport(): OkHttpClient =
    newBuilder()
        .apply {
            interceptors().removeAll { it is HttpLoggingInterceptor }
            networkInterceptors().removeAll { it is HttpLoggingInterceptor }
        }
        .build()

private fun initCache( context: Context, size: Long, cacheDirName: String ): Cache {
    val cacheEvictor = when( size ) {
        0L, Long.MAX_VALUE -> NoOpCacheEvictor()
        else -> LeastRecentlyUsedCacheEvictor( size )
    }
    val cacheDir = when( size ) {
        // Temporary directory deletes itself after close
        // It means songs remain on device as long as it's open
        0L -> createTempDirectory( cacheDirName ).toFile()

        // Looks a bit ugly but what it does is
        // check location set by user and return
        // appropriate path with [cacheDirName] appended.
        else -> when( Preferences.EXO_CACHE_LOCATION.value ) {
            ExoPlayerCacheLocation.System   -> context.cacheDir
            ExoPlayerCacheLocation.Private  -> context.filesDir
            ExoPlayerCacheLocation.SPLIT    -> if( cacheDirName == DOWNLOAD_CACHE_DIRNAME ) context.filesDir else context.cacheDir
        }.resolve( cacheDirName )
    }
    // Ensure this location exists
    cacheDir.mkdirs()

    return SimpleCache( cacheDir, cacheEvictor, StandaloneDatabaseProvider(context) )
}

private fun innertubeNetworkDataSourceFactory(
    context: Context,
    client: OkHttpClient,
) = DefaultDataSource.Factory(
    context,
    OkHttpDataSource.Factory( client.forMediaTransport() )
        // Default header only: InnerTubeX hands the resolver client-specific headers
        // (User-Agent, Referer, Origin) per stream, and DataSpec headers override
        // defaults. setUserAgent() would instead be *added* on top of them (media3
        // uses addHeader for it), sending two User-Agent lines to the CDN.
        .setDefaultRequestProperties( mapOf( "User-Agent" to UserAgents.CHROME_WINDOWS ) )
)

private fun innertubeDownloadNetworkDataSourceFactory(
    context: Context,
    client: OkHttpClient,
) = innertubeNetworkDataSourceFactory(
    context,
    client.newBuilder()
        .addInterceptor { chain ->
            chain.proceed( chain.request() ).also { response ->
                if( isRejectedStreamHttpStatus(response.code) ) {
                    // Prefer the original request URL; also cover a rare redirected final URL.
                    if( !invalidateRejectedDownloadStreamUrl( chain.request().url.toString() ) )
                        invalidateRejectedDownloadStreamUrl( response.request.url.toString() )
                }
            }
        }
        .build(),
)

/**
 * Capture metadata that is intentionally absent from DataSpec before Media3 creates the source.
 * Registration happens synchronously, before the resolving data source can request the stream.
 */
private class PlaybackHintMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory by delegate {
    override fun createMediaSource( mediaItem: MediaItem ): MediaSource {
        rememberPlaybackContentHint( mediaItem )
        return delegate.createMediaSource( mediaItem )
    }
}

val playerModule = module {
    //<editor-fold desc="Cache">
    single( CacheType.CACHE ) {
        initCache( get(), Preferences.EXO_CACHE_SIZE.value, CACHE_DIRNAME )
    }
    single( CacheType.DOWNLOAD ) {
        initCache( get(), Preferences.EXO_DOWNLOAD_SIZE.value, DOWNLOAD_CACHE_DIRNAME )
    }
    factory( CacheType.CACHE ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.CACHE) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    factory( CacheType.DOWNLOAD ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.DOWNLOAD) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    //</editor-fold>

    single( InnertubeDataSourceType.PLAYBACK ) {
        ResolvingDataSource.Factory(
            innertubeNetworkDataSourceFactory( get(), get() )
        ) { dataSpec ->
            if ( dataSpec.uri.isLocalFile() )
                // If this is a local file, no conversion needed
                // because its uri already points to a physical file
                dataSpec
            else
                resolveInnertubeMedia( dataSpec )
        }
    }

    single( InnertubeDataSourceType.DOWNLOAD ) {
        ResolvingDataSource.Factory(
            // Bytes already streamed in the same high-quality itag can be copied from the regular
            // player cache. The resolver gives incompatible entries a private miss-only key.
            get<CacheDataSource.Factory>( CacheType.CACHE )
                .setCacheWriteDataSinkFactory( null )
                .setUpstreamDataSourceFactory(
                    innertubeDownloadNetworkDataSourceFactory( get(), get() )
                )
        ) { dataSpec ->
            if( dataSpec.uri.isLocalFile() ) dataSpec else resolveInnertubeDownload( dataSpec )
        }
    }

    // FIXME: This is technically usable but not recommended,
    //  new instance should be created on each injection.
    //  subscribers should use [PlaybackService]'s player instead of injecting
    //  an instance from Koin.
    // TODO: Convert this into factory
    single<StatefulPlayer> {
        //<editor-fold desc="DataSource">
        val mediaSourceFactory = DefaultMediaSourceFactory(
            // At the bottom of the stack, it's download cache
            get<CacheDataSource.Factory>(CacheType.DOWNLOAD)
                // Read-only cache, player doesn't get to write anything in here
                .setCacheWriteDataSinkFactory( null )
                .setUpstreamDataSourceFactory(
                    // Next up is regular cache
                    get<CacheDataSource.Factory>(CacheType.CACHE)
                        // The final upstream handles 2 cases, local files and remote files
                        .setUpstreamDataSourceFactory(
                            get<ResolvingDataSource.Factory>( InnertubeDataSourceType.PLAYBACK )
                        )
                        // Player is allowed to write chunks into this storage.
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache( get(CacheType.CACHE) )
                                // Chunks are small so recovery can work better
                                .setFragmentSize( CHUNK_LENGTH )
                                // Bigger than default buffer size to avoid
                                // constant write to disk, but small enough
                                // to avoid data loss if app crashes
                                .setBufferSize( 64 * 1024 )     // 64KiB
                        )
                )
        )
        mediaSourceFactory.setLoadErrorHandlingPolicy( ErrorHandlingPolicy() )
        //</editor-fold>
        //<editor-fold desc="Audio handlers">
        val handleAudioFocus by Preferences.AUDIO_SMART_PAUSE_DURING_CALLS
        val audioAttributes = AudioAttributes.Builder()
            .setUsage( C.USAGE_MEDIA )
            .setContentType( C.AUDIO_CONTENT_TYPE_MUSIC )
            .build()
        //</editor-fold>

        StatefulPlayerImpl(
            ExoPlayer.Builder( get() )
                .setMediaSourceFactory( PlaybackHintMediaSourceFactory(mediaSourceFactory) )
                .setHandleAudioBecomingNoisy( true )
                .setWakeMode( C.WAKE_MODE_NETWORK )
                .setAudioAttributes( audioAttributes, handleAudioFocus )
                .setUsePlatformDiagnostics( false )
                .build()
        )
    }

    singleOf( ::VolumeObserver )
    singleOf( ::DownloadHelperImpl ) bind DownloadHelper::class
}

enum class CacheType : Qualifier {
    CACHE, DOWNLOAD;

    override val value: QualifierValue = toString().lowercase()
}

enum class InnertubeDataSourceType : Qualifier {
    PLAYBACK, DOWNLOAD;

    override val value: QualifierValue = "innertube_${toString().lowercase()}"
}
