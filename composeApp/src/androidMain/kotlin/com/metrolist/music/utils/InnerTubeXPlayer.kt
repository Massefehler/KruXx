@file:OptIn(ExperimentalTime::class)

package com.metrolist.music.utils

import android.content.Context
import android.content.SharedPreferences
import app.kreate.android.Preferences
import app.kreate.android.utils.innertube.GEO_LOCATION
import app.kreate.android.utils.innertube.HOST_LANGUAGE
import co.touchlab.kermit.Logger
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.models.YouTubeLocale
import com.metrolist.music.utils.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.rimusic.enums.AudioQualityFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import me.knighthat.innertube.Constants
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ExperimentalTime
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality

/**
 * Kreate's single entry point for resolving a YouTube video id into a playable audio stream.
 *
 * Stream extraction (client selection, PO-token contracts, signature/n-parameter cipher via
 * QuickJS + yt-dlp EJS, remote player configs) is delegated to the InnerTubeX library, which is
 * maintained together with Metrolist. Updating YouTube-side breakage therefore boils down to
 * bumping `innertubex` in `gradle/libs.versions.toml`.
 *
 * Ported from Metrolist's `InnerTubeXPlayer`; Kreate specifics are the session sync from
 * [Preferences] (cookie / visitorData / dataSyncId set by the YouTube login flow) and the
 * app-wide OkHttp client (proxy, DNS-over-HTTPS) used as transport.
 */
object InnerTubeXPlayer {
    private const val TAG = "InnerTubeXPlayer"
    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60
    private const val SESSION_PREFS = "innertubex_session"
    private const val KEY_ANONYMOUS_VISITOR_DATA = "anonymous_visitor_data"

    private val logger = Logger.withTag( TAG )

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var currentBundle: ExtractionBundle? = null

    private val bundleMutex = Mutex()
    private val webRemixFailures = ConcurrentHashMap<String, Long>()

    /** Must be called once from [android.app.Application.onCreate]. */
    @Synchronized
    fun initialize( context: Context ) {
        if( applicationContext == null )
            applicationContext = context.applicationContext
    }

    private fun requireContext(): Context =
        requireNotNull( applicationContext ) { "InnerTubeXPlayer.initialize() has not been called" }

    //<editor-fold desc="Transport & session">
    /**
     * Ktor client dedicated to InnerTubeX. It reuses Kreate's app-wide [OkHttpClient]
     * (proxy, DNS-over-HTTPS, logging) as engine, but has its own plugin set because
     * InnerTubeX validates HTTP status codes itself (`expectSuccess = false`).
     */
    private val httpClient: HttpClient by lazy {
        createClient( KoinJavaComponent.get( OkHttpClient::class.java ) )
    }

    private val innerTube: InnerTube by lazy { InnerTube( httpClient, logger = innerTubeLogger ) }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient( okHttpClient: OkHttpClient ): HttpClient =
        HttpClient( OkHttp ) {
            expectSuccess = false

            install( ContentNegotiation ) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                    }
                )
            }
            install( ContentEncoding ) {
                gzip( 0.9F )
                deflate( 0.8F )
            }
            install( HttpTimeout ) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            engine {
                preconfigured = okHttpClient
            }

            defaultRequest {
                url( "https://music.youtube.com/youtubei/v1/" )
                header( HttpHeaders.Accept, "application/json" )
                header( HttpHeaders.CacheControl, "no-cache" )
            }
        }

    private fun sessionPrefs(): SharedPreferences =
        requireContext().getSharedPreferences( SESSION_PREFS, Context.MODE_PRIVATE )

    /**
     * Pushes Kreate's stored YouTube session into InnerTubeX.
     *
     * Cookie, visitorData and dataSyncId are written by the login flow
     * ([it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin]) and cleared on logout,
     * so re-reading them before every extraction keeps both in sync without extra hooks.
     * The hard-coded default visitorData ([Constants.CHROME_WINDOWS_VISITOR_DATA]) is treated
     * as "no visitorData": anonymous sessions get a fresh, per-install one instead
     * (see [ensureVisitorData]).
     */
    fun syncSession() {
        val cookie = Preferences.YOUTUBE_COOKIES.value.takeIf( String::isNotBlank )
        val loginVisitorData = Preferences.YOUTUBE_VISITOR_DATA
                                          .value
                                          .takeIf { it.isNotBlank() && it != Constants.CHROME_WINDOWS_VISITOR_DATA }
        val anonymousVisitorData = sessionPrefs().getString( KEY_ANONYMOUS_VISITOR_DATA, null )
                                                 ?.takeIf( String::isNotBlank )
        val dataSyncId = Preferences.YOUTUBE_SYNC_ID
                                    .value
                                    .takeIf { it.isNotBlank() && cookie != null }

        with( innerTube ) {
            this.locale = YouTubeLocale( gl = GEO_LOCATION, hl = HOST_LANGUAGE )
            this.cookie = cookie
            this.dataSyncId = dataSyncId
            this.visitorData = loginVisitorData ?: anonymousVisitorData
        }
    }

    /** Fetches (and remembers) a visitorData for anonymous sessions that don't have one yet. */
    private suspend fun ensureVisitorData() {
        if( !innerTube.visitorData.isNullOrBlank() ) return

        val fresh = try {
            innerTube.fetchFreshVisitorData()
        } catch( e: CancellationException ) {
            throw e
        } catch( e: Exception ) {
            logger.w( "Could not fetch fresh visitorData (${e::class.simpleName})" )
            null
        } ?: return

        innerTube.visitorData = fresh
        sessionPrefs().edit().putString( KEY_ANONYMOUS_VISITOR_DATA, fresh ).apply()
        logger.d( "Fetched fresh anonymous visitorData" )
    }
    //</editor-fold>

    //<editor-fold desc="Extraction">
    /**
     * Everything the resolver needs to play a song. Signed URL and headers are
     * sensitive and therefore left out of [toString].
     */
    data class PlaybackData(
        val videoId: String,
        val streamUrl: String,
        val streamHeaders: Map<String, String>,
        val streamExpiresInSeconds: Int,
        val streamClient: String,
        val itag: Int,
        val mimeType: String,
        val bitrate: Long?,
        val contentLength: Long?,
        val loudnessDb: Float?,
        val durationSeconds: Long?,
        /**
         * Range-request contract reported by InnerTubeX, kept for diagnostics only.
         * Kreate never chunks requests (see [playerResponseForPlayback] and
         * `InnertubeResolvingDataSource.withResolvedStream`), so both flags are always `false` here.
         */
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
    ) {
        /** Wall-clock time in millis after which [streamUrl] must not be used anymore. */
        val expiresAtMillis: Long = System.currentTimeMillis() + streamExpiresInSeconds * 1000L

        override fun toString(): String =
            "PlaybackData(videoId=$videoId, client=$streamClient, itag=$itag, mimeType=$mimeType, " +
            "bitrate=$bitrate, contentLength=$contentLength, expiresIn=${streamExpiresInSeconds}s, " +
            "boundedRange=$requireBoundedRange, chunk=$rangeChunkSizeBytes)"
    }

    /** Warms up player config, cipher solver and PO-token WebView off the first-play path. */
    suspend fun prewarm() {
        syncSession()
        ensureVisitorData()
        bundle().extractor.prewarm()
    }

    /**
     * Resolves [videoId] to a direct (non-SABR, non-HLS, unbounded-range) audio stream that
     * ExoPlayer can read through Kreate's cache chain.
     *
     * @param isExplicit lets InnerTubeX prefetch a PO token for explicit content, which is
     *                   refused by several clients otherwise.
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        audioQuality: AudioQualityFormat,
        isConnectionMetered: Boolean,
        saveDataOnMeteredConnections: Boolean = Preferences.IS_CONNECTION_METERED.value,
        isExplicit: Boolean? = null,
    ): Result<PlaybackData> =
        try {
            syncSession()
            ensureVisitorData()

            // Kreate's player chain puts this resolver *inside* CacheDataSource (see PlayerModule).
            // A bounded sub-range returned from there would be recorded by CacheDataSource as the
            // song's total length and cut playback after the first chunk, so clients that only
            // serve bounded ranges (ANDROID_VR, IOS, TVHTML5_SIMPLY in InnerTubeX 0.3) are excluded.
            // Metrolist can allow them because its resolver wraps the cache instead.
            val hints = ContentHints( isExplicit = isExplicit )
                .withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = false,
                )
            val excludedClients = buildSet {
                if( hasRecentWebRemixFailure( videoId ) ) add( "WEB_REMIX" )
            }

            val stream = requireNotNull(
                bundle().extractor.extract(
                    videoId = videoId,
                    hints = hints,
                    excludedClients = excludedClients,
                    audioQuality = audioQuality.toInnerTubeX(
                        isConnectionMetered = isConnectionMetered,
                        saveDataOnMeteredConnections = saveDataOnMeteredConnections,
                    ),
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                )
            ) { "InnerTubeX returned no playable stream" }
            // allowSabr=false above should already prevent this; ExoPlayer can only read plain https urls
            check( !stream.audioUrl.startsWith( "sabr://" ) ) { "SABR is not supported by this playback engine" }
            // Same contract for allowBoundedRange=false: the resolver must be able to hand ExoPlayer
            // the unbounded url (see InnertubeResolvingDataSource.withResolvedStream).
            check( !stream.requireBoundedRange && !stream.useRangeChunks ) {
                "Client ${stream.clientName} only serves bounded ranges, which this playback chain does not support"
            }

            Result.success( stream.toPlaybackData() )
        } catch( e: CancellationException ) {
            throw e
        } catch( e: StreamResolveException ) {
            val cause = e.cause
            Result.failure(
                if( e.reason == StreamResolveException.Reason.NETWORK && cause != null ) cause else e
            )
        } catch( e: Exception ) {
            Result.failure( e )
        }

    /**
     * Resolve the highest-quality direct audio stream available for an offline download.
     *
     * Downloads deliberately ignore both the playback quality preference and Android's metered
     * network state. Keeping this as a separate entry point also prevents a future playback-policy
     * change from silently lowering the quality of newly downloaded files.
     */
    suspend fun playerResponseForDownload(
        videoId: String,
        isExplicit: Boolean? = null,
    ): Result<PlaybackData> =
        playerResponseForPlayback(
            videoId = videoId,
            audioQuality = AudioQualityFormat.High,
            isConnectionMetered = false,
            saveDataOnMeteredConnections = false,
            isExplicit = isExplicit,
        )

    /** Remember that the WEB_REMIX URL of [videoId] was rejected by the CDN (e.g. HTTP 403). */
    fun markWebRemixFailed( videoId: String ) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    fun clearWebRemixFailures() = webRemixFailures.clear()

    /**
     * A CDN rejection can mean the cipher produced a wrong-but-non-throwing signature.
     * Asks InnerTubeX for a (rate-limited) player-config refresh.
     *
     * @return `true` if the config table changed, in which case WEB_REMIX is allowed again.
     */
    suspend fun refreshAfterStreamRejection(): Boolean {
        val changed = bundle().cipherService.refreshAfterStreamRejection()
        if( changed ) clearWebRemixFailures()
        return changed
    }

    private fun hasRecentWebRemixFailure( videoId: String ): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if( (System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS ) {
            webRemixFailures.remove( videoId, failedAt )
            return false
        }
        return true
    }

    private suspend fun bundle(): ExtractionBundle {
        currentBundle?.let { return it }

        return bundleMutex.withLock {
            currentBundle?.let { return@withLock it }

            val remoteStore = RemotePlayerConfigStore( httpClient, configRepository, innerTubeLogger )
            val cipherService = YouTubeCipherService( httpClient, remoteStore, innerTubeLogger )
            val extractor = InnerTubeExtractor(
                configParser = YtConfigParserImpl( httpClient, innerTube, remoteStore, innerTubeLogger ),
                cipherService = cipherService,
                innerTube = innerTube,
                tokenProvider = tokenProvider,
                logger = innerTubeLogger,
            )
            ExtractionBundle( cipherService, extractor ).also { currentBundle = it }
        }
    }

    private data class ExtractionBundle(
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private fun AudioQualityFormat.toInnerTubeX(
        isConnectionMetered: Boolean,
        saveDataOnMeteredConnections: Boolean,
    ): InnerTubeXAudioQuality =
        when( this ) {
            AudioQualityFormat.High -> InnerTubeXAudioQuality.HIGH
            AudioQualityFormat.Low  -> InnerTubeXAudioQuality.LOW
            AudioQualityFormat.Auto ->
                if( isConnectionMetered && saveDataOnMeteredConnections )
                    InnerTubeXAudioQuality.LOW
                else
                    InnerTubeXAudioQuality.AUTO
        }

    private fun ExtractedStream.toPlaybackData(): PlaybackData {
        val fullMimeType =
            if( codecs.isNullOrBlank() ) mimeType.orEmpty()
            else "${mimeType.orEmpty()}; codecs=\"$codecs\""

        val expiresInSeconds = expiresAt?.let {
            ((it.toEpochMilliseconds() - System.currentTimeMillis()) / 1000L).toInt()
        }?.coerceAtLeast( 1 ) ?: DEFAULT_STREAM_TTL_SECONDS

        return PlaybackData(
            videoId = videoId,
            streamUrl = audioUrl,
            streamHeaders = headers,
            streamExpiresInSeconds = expiresInSeconds,
            streamClient = clientName,
            itag = itag,
            mimeType = fullMimeType,
            bitrate = bitrate?.toLong(),
            contentLength = contentLengthBytes,
            loudnessDb = (loudnessDb ?: perceptualLoudnessDb)?.toFloat(),
            durationSeconds = mediaMetadata?.durationSeconds,
            requireBoundedRange = requireBoundedRange,
            rangeChunkSizeBytes = rangeChunkSizeBytes,
            useRangeChunks = useRangeChunks,
        )
    }
    //</editor-fold>

    //<editor-fold desc="Collaborators">
    /** Remote player-config table (Zemer/faraday registry), cached in SharedPreferences. */
    private val configRepository: PlayerConfigRepository by lazy {
        AndroidPlayerConfigRepository( requireContext() )
    }

    /** BotGuard PO tokens minted in a hidden WebView (see [PoTokenGenerator]). */
    private val poTokenGenerator: PoTokenGenerator by lazy { PoTokenGenerator( requireContext() ) }

    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(
            providers = setOf( PoTokenProviderKind.WEB_BOTGUARD ),
            usesWebView = true,
        )

        override suspend fun getPoToken(
            videoId: String,
            visitorData: String,
            cookie: String?,
        ): PoTokenResult? =
            poTokenGenerator.getWebClientPoToken( videoId, visitorData )?.let { token ->
                PoTokenResult(
                    playerRequestToken = token.playerRequestPoToken,
                    streamingDataToken = token.streamingDataPoToken,
                    visitorData = visitorData,
                )
            }

        override suspend fun close() = poTokenGenerator.close()
    }

    private val innerTubeLogger = InnerTubeLogger { event ->
        val details =
            if( event.details.isEmpty() ) ""
            else event.details.entries.joinToString( prefix = " [", postfix = "]" ) { "${it.key}=${it.value}" }
        val message = event.message + details
        val tagged = Logger.withTag( event.tag )

        when( event.level ) {
            InnerTubeLogLevel.DEBUG -> tagged.d( message )
            InnerTubeLogLevel.INFO  -> tagged.i( message )
            InnerTubeLogLevel.WARN  -> tagged.w( message )
            InnerTubeLogLevel.ERROR -> tagged.e( message )
        }
    }

    private class AndroidPlayerConfigRepository( context: Context ) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences( "innertubex_player_config", Context.MODE_PRIVATE )

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString( "json", "" ).orEmpty()
            set(value) = preferences.edit().putString( "json", value ).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong( "cached_at_ms", 0L )
            set(value) = preferences.edit().putLong( "cached_at_ms", value ).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString( "source_url", "" ).orEmpty()
            set(value) = preferences.edit().putString( "source_url", value ).apply()
        override var cachedEtag: String
            get() = preferences.getString( "etag", "" ).orEmpty()
            set(value) = preferences.edit().putString( "etag", value ).apply()

        private companion object {
            // Maintained player-config table (MetrolistGroup/faraday, updated several times a
            // week). NOTE: InnerTubeX only accepts MetrolistGroup/faraday URLs here
            // (RemotePlayerConfigStore.validatedSourceUrlOrNull) - anything else, e.g. the
            // ZemerTeam url Metrolist's app passes, is silently ignored. Per-player fallbacks
            // (faraday releases / jsDelivr) are resolved by InnerTubeX itself.
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/MetrolistGroup/faraday/master/registry/player_configs.json"
        }
    }
    //</editor-fold>
}
