package it.fast4x.rimusic

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.palette.graphics.Palette
import app.kreate.android.BuildConfig
import app.kreate.android.LocalBottomMenu
import it.fast4x.rimusic.ui.styling.scaledForCarRadio
import app.kreate.android.ui.adaptive.AdaptiveLayout
import app.kreate.android.ui.adaptive.CarRadioMode
import app.kreate.android.ui.adaptive.LocalAdaptiveLayout
import app.kreate.android.ui.adaptive.isDisplayNaturallyLandscape
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.PlaybackNotificationSilencer
import app.kreate.android.service.player.VideoBackgroundPlayback
import app.kreate.android.service.player.VideoAudioLevel
import app.kreate.android.service.player.VideoToAudioHandoff
import app.kreate.android.service.updater.UpdatePlugins
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.PersistentQueue
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import dev.kdrag0n.monet.theme.ColorScheme
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.utils.LocalePreferenceItem
import it.fast4x.innertube.utils.LocalePreferences
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.LogType
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.extensions.pip.PipEventContainer
import it.fast4x.rimusic.extensions.pip.PipModuleContainer
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.AnimatedStartupSplash
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.CrossfadeContainer
import it.fast4x.rimusic.ui.screens.AppNavigation
import it.fast4x.rimusic.ui.screens.player.MiniPlayer
import it.fast4x.rimusic.ui.screens.player.Player
import it.fast4x.rimusic.ui.screens.player.components.YoutubePlayer
import it.fast4x.rimusic.ui.screens.player.components.VideoFollower
import it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.applyPitchBlack
import it.fast4x.rimusic.ui.styling.colorPaletteOf
import it.fast4x.rimusic.ui.styling.customColorPalette
import it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import it.fast4x.rimusic.ui.styling.typographyOf
import it.fast4x.rimusic.utils.LocalMonetCompat
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.getEnum
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.invokeOnReady
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid8
import it.fast4x.rimusic.utils.isVideo
import it.fast4x.rimusic.utils.getGlobalVolume
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import it.fast4x.rimusic.utils.playVideo
import it.fast4x.rimusic.utils.loadAppLog
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.resize
import it.fast4x.rimusic.utils.restoreGlobalVolume
import it.fast4x.rimusic.utils.resumeVideoAsAudio
import it.fast4x.rimusic.utils.setDefaultPalette
import it.fast4x.rimusic.utils.supportsVideoPlayback
import it.fast4x.rimusic.utils.textCopyToClipboard
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.utils.Toaster
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.inject
import java.util.Objects
import kotlin.math.sqrt
import kotlin.system.exitProcess


@UnstableApi
class
MainActivity :
//MonetCompatActivity(),
    AppCompatActivity(),
    MonetColorsChangedListener
//,PersistMapOwner
{
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private var intentUriData by mutableStateOf<Uri?>(null)

    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCounter = 0

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    private val pipState: MutableState<Boolean> = mutableStateOf(false)
    private val logger = Logger.withTag( this::class.java.simpleName )

    override fun onStart() {
        super.onStart()

        runCatching {
            bindService(intent<PlayerServiceModern>(), serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            logger.e( it ) { "Failed to bind PlayerServiceModern" }
        }
    }

    @ExperimentalTextApi
    @UnstableApi
    @ExperimentalComposeUiApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.INDEPENDENT_FORK && intent.getBooleanExtra("kruxxDownloadJobs", false)) {
            app.kreate.android.downloads.DownloadCenter.settings()
            intent.removeExtra("kruxxDownloadJobs")
        }

        UpdatePlugins.execute( this )

        MonetCompat.enablePaletteCompat()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb()
            )
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()
        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(
            listener = this,
            notifySelf = false
        )
        monet.updateMonetColors()

        monet.invokeOnReady {
            startApp(isColdStart = savedInstanceState == null)
        }

        if ( Preferences.AUDIO_SHAKE_TO_SKIP.value ) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            Objects.requireNonNull(sensorManager)
                ?.registerListener(
                    sensorListener,
                    sensorManager!!
                        .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL
                )
        }
    }


    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        pipState.value = isInPictureInPictureMode
        println("MainActivity.onPictureInPictureModeChanged isInPictureInPictureMode: $isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    }


    @Composable
    fun ThemeApp(
        isDark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                (view.context as Activity).window.let { window ->
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        !isDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                        !isDark
                }
            }

        }
        content()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(
        ExperimentalTextApi::class,
        ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
        ExperimentalMaterial3Api::class
    )
    fun startApp(isColdStart: Boolean = false) {
        // Used in QuickPics for load data from remote instead of last saved in SharedPreferences
        Preferences.IS_DATA_KEY_LOADED.value = false

        if ( !Preferences.CLOSE_APP_ON_BACK.value ) {
            if (Build.VERSION.SDK_INT >= 33) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    //Log.d("onBackPress", "yeah")
                }
            }

        }

        /*
            Instead of checking getBoolean() individually, we can use .let() to express condition.
            Or, the whole thing is 'false' if null appears in the process.
         */
        val launchedFromNotification: Boolean =
            intent?.extras?.let {
                it.getBoolean("expandPlayerBottomSheet") || it.getBoolean("fromWidget")
            } ?: false

        println("MainActivity.onCreate launchedFromNotification: $launchedFromNotification intent $intent.action")

        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()

        if ( Preferences.KEEP_SCREEN_ON.value )
            window.addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON )

        setContent {
            val colorPaletteMode by Preferences.THEME_MODE
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack

            // Valid to get log when app crash
            if (intent.action == action_copy_crash_log) {
                Preferences.RUNTIME_LOG.value = true
                loadAppLog(this@MainActivity, type = LogType.Crash).let {
                    if (it != null) textCopyToClipboard(it, this@MainActivity)
                }
                LaunchedEffect(Unit) {
                    delay(5000)
                    exitProcess(0)
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val navController = rememberNavController()
            var showPlayer by rememberSaveable { mutableStateOf(false) }
            var playVideoAsAudio by rememberSaveable { mutableStateOf(false) }
            var showStartupSplash by rememberSaveable {
                mutableStateOf(BuildConfig.INDEPENDENT_FORK && isColdStart)
            }
            var animatedGradient by Preferences.ANIMATED_GRADIENT
            var customColor by Preferences.CUSTOM_COLOR
            val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))


            val currentYouTubeLocale = CURRENT_LOCALE
            LocalePreferences.preference = LocalePreferenceItem(
                hl = currentYouTubeLocale.languageCode(),
                gl = currentYouTubeLocale.regionCode()
            )
            Innertube.locale = it.fast4x.innertube.clients.YouTubeLocale(
                hl = currentYouTubeLocale.languageCode(),
                gl = currentYouTubeLocale.regionCode()
            )

            var appearance by rememberSaveable(
                !lightTheme,
                stateSaver = Appearance.Companion
            ) {
                with(preferences) {
                    val colorPaletteName by Preferences.COLOR_PALETTE
                    val colorPaletteMode by Preferences.THEME_MODE
                    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS
                    val useSystemFont by Preferences.USE_SYSTEM_FONT
                    val applyFontPadding by Preferences.APPLY_FONT_PADDING

                    var colorPalette =
                        colorPaletteOf(colorPaletteName, colorPaletteMode, !lightTheme)

                    val fontType by Preferences.FONT

                    if (colorPaletteName == ColorPaletteName.MaterialYou) {
                        colorPalette = dynamicColorPaletteOf(
                            Color(monet.getAccentColor(this@MainActivity)),
                            !lightTheme
                        )
                    }
                    if (colorPaletteName == ColorPaletteName.CustomColor) {
                        colorPalette = dynamicColorPaletteOf(
                            customColor,
                            !lightTheme
                        )
                    }

                    setSystemBarAppearance(colorPalette.isDark)

                    mutableStateOf(
                        Appearance(
                            colorPalette = colorPalette,
                            typography = typographyOf(
                                colorPalette.text,
                                useSystemFont,
                                applyFontPadding,
                                fontType
                            ),
                            thumbnailShape = thumbnailRoundness.shape
                        )
                    )
                }


            }

            fun setDynamicPalette(url: String) {
                val playerBackgroundColors by Preferences.PLAYER_BACKGROUND
                val colorPaletteName by Preferences.COLOR_PALETTE
                val isDynamicPalette = colorPaletteName == ColorPaletteName.Dynamic
                val isCoverColor =
                    playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
                            playerBackgroundColors == PlayerBackgroundColors.CoverColor ||
                            animatedGradient == AnimatedGradient.FluidCoverColorGradient

                if (!isDynamicPalette) return

                val colorPaletteMode by Preferences.THEME_MODE
                coroutineScope.launch(Dispatchers.Main) {
                    val result = ImageFactory.requestBuilder( url ) {
                        allowHardware( false )
                    }.let { imageLoader.execute( it ) }
                    val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
                    val isDark =
                        colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)

                    val bitmap = result.image?.toBitmap()
                    if (bitmap != null) {
                        val palette = Palette
                            .from(bitmap)
                            .maximumColorCount(8)
                            .addFilter(if (isDark) ({ _, hsl -> hsl[0] !in 36f..100f }) else null)
                            .generate()
                        println("Mainactivity onmediaItemTransition palette dominantSwatch: ${palette.dominantSwatch}")

                        dynamicColorPaletteOf(bitmap, isDark)?.let {
                            withContext(Dispatchers.Main) {
                                setSystemBarAppearance(it.isDark)
                            }
                            appearance = appearance.copy(
                                colorPalette = if (!isPicthBlack) it else it.copy(
                                    background0 = Color.Black,
                                    background1 = Color.Black,
                                    background2 = Color.Black,
                                    background3 = Color.Black,
                                    background4 = Color.Black,
                                    // text = Color.White
                                ),
                                typography = appearance.typography.copy(it.text)
                            )
                            println("Mainactivity onmediaItemTransition appearance inside: ${appearance.colorPalette}")
                        }

                    }
                }
                println("Mainactivity onmediaItemTransition appearance outside: ${appearance.colorPalette}")
            }


            val player: StatefulPlayer = koinInject()
            var currentMediaItemForPip by remember(player) {
                // Media3 deliberately ignores Bundle contents in MediaItem equality. Referential
                // comparison still observes video/audio variants of the same YouTube id.
                mutableStateOf(player.currentMediaItem, referentialEqualityPolicy())
            }
            // A playing video continues as audio while the activity cannot be seen (display off,
            // app in the background) and returns once it is visible. The sound then stays with
            // the native player and the video only follows it silently: two separate outputs
            // cannot be switched without being heard.
            var videoInBackground by remember(player) {
                mutableStateOf<MediaItem?>(null, referentialEqualityPolicy())
            }
            // The native player taking over from a video that still sounds (display just off).
            var videoHandoff by remember(player) { mutableStateOf<VideoToAudioHandoff?>(null) }
            // Keeps the video window open meanwhile: closing it would stop the video at once.
            var handingOffVideo by remember(player) {
                mutableStateOf<MediaItem?>(null, referentialEqualityPolicy())
            }
            // The silent video of the track the native player plays, see VideoFollower.
            var videoFollower by remember(player) { mutableStateOf<VideoFollower?>(null) }
            // That video while its player is minimized; it returns when the player opens again.
            var minimizedVideo by remember(player) {
                mutableStateOf<MediaItem?>(null, referentialEqualityPolicy())
            }
            // A video that returns paused with its own sound because its audio stopped.
            var pausedVideoId by remember(player) { mutableStateOf<String?>(null) }
            // Whether the native player is meant to play; a silent video plays exactly then.
            var audioPlayRequested by remember(player) { mutableStateOf(false) }
            // Whether it actually plays, i.e. does not buffer; until then a silent video waits.
            var audioAdvancing by remember(player) { mutableStateOf(false) }
            // Play commands for the native player that belong to a video with its own sound.
            var videoPlayRequest by remember(player) { mutableIntStateOf(0) }
            // Every returning video gets a new embedded player; the old one ended with the activity.
            var videoSession by remember(player) { mutableIntStateOf(0) }
            val floatingWindowClosed = remember { mutableStateOf(false) }

            // The WebView takes the audio focus as soon as it plays audibly and pauses when it
            // loses it. While video and native audio overlap, the native player must therefore
            // neither take the focus nor react to its loss.
            fun nativeAudioFocusHandling(enabled: Boolean) =
                player.setAudioAttributes(player.audioAttributes, enabled && isHandleAudioFocusEnabled())

            fun endVideoFollower() {
                if (videoFollower == null) return
                videoFollower = null
                // Whatever is shown next is another embedded player.
                videoSession++
                if (!VideoFollower.canSilenceWebView) nativeAudioFocusHandling(true)
            }

            // YouTube turns loud videos down to its reference loudness. Native audio continuing a
            // video plays at that level too, until another track starts.
            val videoAudioLevel = remember(player) { VideoAudioLevel() }
            fun videoAudioVolume(): Float = player.getGlobalVolume() * videoAudioLevel.gain
            fun followVideoLoudness(videoId: String) {
                videoAudioLevel.videoId = videoId
                videoAudioLevel.gain = 1f
                coroutineScope.launch {
                    // The format is stored once the stream is resolved, at the start of the handoff.
                    val loudnessDb = withTimeoutOrNull(VideoToAudioHandoff.LEAD_MS) {
                        Database.formatTable.findBySongId(videoId).mapNotNull { it?.loudnessDb }.first()
                    }
                    if (videoAudioLevel.videoId == videoId)
                        videoAudioLevel.gain = VideoBackgroundPlayback.youtubeNormalizationGain(loudnessDb)
                }
            }

            fun startVideoFollower(video: MediaItem) {
                lateinit var follower: VideoFollower
                fun leads() = videoFollower === follower &&
                        player.currentMediaItem?.mediaId == video.mediaId
                follower = VideoFollower(
                    item = video,
                    audioPositionSeconds = { player.currentPosition.coerceAtLeast(0L) / 1000f },
                    audioDurationSeconds = { player.duration.takeIf { it > 0L }?.let { it / 1000f } },
                    audioPlayRequested = { audioPlayRequested },
                    audioAdvancing = { audioAdvancing },
                    playAudio = { atSeconds ->
                        if (leads()) {
                            if (atSeconds != null) player.seekTo((atSeconds * 1000).toLong())
                            // Audio that failed in the meantime is loaded again.
                            if (player.playbackState == Player.STATE_IDLE) player.prepare()
                            player.play()
                        }
                    },
                    pauseAudio = { if (leads()) player.pause() },
                    seekAudioTo = { seconds -> if (leads()) player.seekTo((seconds * 1000).toLong()) },
                    onFailed = {
                        if (videoFollower === follower) {
                            endVideoFollower()
                            Toaster.w(R.string.video_playback_failed_audio_fallback)
                        }
                    }
                )
                // A WebView that cannot be silenced as a whole takes the audio focus once its
                // video is unmuted in its own controls; the native player must not pause then.
                if (!VideoFollower.canSilenceWebView) nativeAudioFocusHandling(false)
                videoSession++
                showPlayer = true
                videoFollower = follower
            }

            // Shows a video again whose sound moved to the native player.
            fun returnToVideo(video: MediaItem) {
                val currentItem = player.currentMediaItem
                when (
                    VideoBackgroundPlayback.onVisibleAgain(
                        videoId = video.mediaId,
                        currentMediaId = currentItem?.mediaId,
                        currentIsVideo = currentItem?.isVideo == true,
                        playbackState = player.playbackState
                    )
                ) {
                    VideoBackgroundPlayback.Return.Discard -> Unit
                    VideoBackgroundPlayback.Return.FollowAudio -> startVideoFollower(video)
                    VideoBackgroundPlayback.Return.PausedVideo -> {
                        // Nothing plays, so the video gets its sound back without being heard.
                        pausedVideoId = video.mediaId
                        videoSession++
                        player.playVideo(video)
                        player.restoreGlobalVolume()
                        PlaybackNotificationSilencer.setEmbeddedVideoPlaybackActive(false)
                    }
                }
            }

            val activityLifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(activityLifecycle, player) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            val video = videoInBackground ?: return@LifecycleEventObserver
                            videoInBackground = null
                            val handoff = videoHandoff
                            videoHandoff = null
                            if (handoff != null && handoff.isActive) {
                                // Visible again before the audio took over: the video never paused.
                                // Unload the native player before restoring its focus handling,
                                // otherwise it takes the focus and the video pauses after all.
                                if (player.currentMediaItem?.mediaId == video.mediaId)
                                    player.playVideo(video)
                                else
                                    player.playWhenReady = false
                                handoff.cancel()
                                return@LifecycleEventObserver
                            }
                            returnToVideo(video)
                        }

                        Lifecycle.Event.ON_RESUME -> floatingWindowClosed.value = false

                        Lifecycle.Event.ON_STOP -> videoFollower?.let {
                            endVideoFollower()
                            if (
                                VideoBackgroundPlayback.continuesAsAudio(
                                    changingConfigurations = isChangingConfigurations,
                                    finishing = isFinishing,
                                    inPictureInPicture = isAtLeastAndroid7 && isInPictureInPictureMode,
                                    screenInteractive = getSystemService(PowerManager::class.java)
                                        ?.isInteractive != false,
                                    floatingWindowJustClosed = floatingWindowClosed.value
                                )
                            ) {
                                // The audio plays on by itself; its video returns with the activity.
                                videoInBackground = it.item
                            } else if (!isChangingConfigurations && !isFinishing) {
                                // The floating window was closed: that stops, as for any video.
                                player.playWhenReady = false
                            }
                        }

                        else -> Unit
                    }
                }
                activityLifecycle.addObserver(observer)
                onDispose { activityLifecycle.removeObserver(observer) }
            }
            DisposableEffect(player) {
                val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
                    if (info.isInPictureInPictureMode) return@Consumer
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        // Closing the floating window after it stopped means stop, not background.
                        if (videoInBackground != null) {
                            videoInBackground = null
                            player.playWhenReady = false
                            // A running handoff then pauses the still sounding video as well.
                            videoHandoff?.cancel()
                            videoHandoff = null
                        }
                    } else if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        // Expanding resumes the activity next; closing stops it next.
                        floatingWindowClosed.value = true
                    }
                }
                addOnPictureInPictureModeChangedListener(pipListener)
                onDispose { removeOnPictureInPictureModeChangedListener(pipListener) }
            }
            LaunchedEffect(showPlayer) {
                if (!showPlayer) {
                    // Minimizing the player keeps the audio; its video returns with the player.
                    videoFollower?.let {
                        minimizedVideo = it.item
                        endVideoFollower()
                    }
                } else {
                    val video = minimizedVideo ?: return@LaunchedEffect
                    minimizedVideo = null
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) returnToVideo(video)
                }
            }
            DisposableEffect(player, !lightTheme) {
                // Enum preferences persist their defaults on first access. The radio scaffold
                // does not read the legacy navigation settings, and MiniPlayer is composed only
                // after playback starts. Initialize these before listening and ignore delayed
                // notifications for the same values, otherwise first playback recreates the
                // activity and its existing onDestroy cleanup clears the queue.
                val restartPreferenceValues = mapOf(
                    Preferences.Key.MAIN_THEME to Preferences.MAIN_THEME.value.name,
                    Preferences.Key.NAVIGATION_BAR_POSITION to Preferences.NAVIGATION_BAR_POSITION.value.name,
                    Preferences.Key.NAVIGATION_BAR_TYPE to Preferences.NAVIGATION_BAR_TYPE.value.name,
                    Preferences.Key.MINI_PLAYER_TYPE to Preferences.MINI_PLAYER_TYPE.value.name,
                )
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    when (key) {
                        Preferences.Key.MAIN_THEME,
                        Preferences.Key.NAVIGATION_BAR_POSITION,
                        Preferences.Key.NAVIGATION_BAR_TYPE,
                        Preferences.Key.MINI_PLAYER_TYPE -> {
                            if (sharedPreferences.getString(key, null) != restartPreferenceValues[key]) {
                                this@MainActivity.recreate()
                                println("MainActivity.recreate()")
                            }
                        }

                        Preferences.Key.COLOR_PALETTE,
                        Preferences.Key.THEME_MODE,
                        Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_0,
                        Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_1,
                        Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_2,
                        Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_3,
                        Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_4,
                        Preferences.Key.CUSTOM_LIGHT_TEXT,
                        Preferences.Key.CUSTOM_LIGHT_TEXT_SECONDARY,
                        Preferences.Key.CUSTOM_LIGHT_TEXT_DISABLED,
                        Preferences.Key.CUSTOM_LIGHT_PLAY_BUTTON,
                        Preferences.Key.CUSTOM_LIGHT_ACCENT,
                        Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_0,
                        Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_1,
                        Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_2,
                        Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_3,
                        Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_4,
                        Preferences.Key.CUSTOM_DARK_TEXT,
                        Preferences.Key.CUSTOM_DARK_TEXT_SECONDARY,
                        Preferences.Key.CUSTOM_DARK_TEXT_DISABLED,
                        Preferences.Key.CUSTOM_DARK_PLAY_BUTTON,
                        Preferences.Key.CUSTOM_DARK_ACCENT -> {
                            val colorPaletteName = sharedPreferences.getEnum( Preferences.Key.COLOR_PALETTE, Preferences.COLOR_PALETTE.defaultValue )
                            val colorPaletteMode = sharedPreferences.getEnum( Preferences.Key.THEME_MODE, Preferences.THEME_MODE.defaultValue )

                            var colorPalette = colorPaletteOf(
                                colorPaletteName,
                                colorPaletteMode,
                                !lightTheme
                            )

                            if (colorPaletteName == ColorPaletteName.Dynamic) {
                                val artworkUri =
                                    (player.currentMediaItem?.mediaMetadata?.artworkUri.thumbnail(1200)
                                        ?: "").toString()
                                artworkUri.let {
                                    if (it.isNotEmpty())
                                        setDynamicPalette(it)
                                    else {

                                        setSystemBarAppearance(colorPalette.isDark)
                                        appearance = appearance.copy(
                                            colorPalette = if (!isPicthBlack) colorPalette else colorPalette.copy(
                                                background0 = Color.Black,
                                                background1 = Color.Black,
                                                background2 = Color.Black,
                                                background3 = Color.Black,
                                                background4 = Color.Black,
                                                // text = Color.White
                                            ),
                                            typography = appearance.typography.copy(
                                                colorPalette.text
                                            ),
                                        )
                                    }

                                }

                            } else {

                                if (colorPaletteName == ColorPaletteName.MaterialYou) {
                                    colorPalette = dynamicColorPaletteOf(
                                        Color(monet.getAccentColor(this@MainActivity)),
                                        !lightTheme
                                    )
                                }

                                if (colorPaletteName == ColorPaletteName.Customized) {
                                    colorPalette = customColorPalette(
                                        colorPalette,
                                        this@MainActivity,
                                        isSystemInDarkTheme
                                    )
                                }
                                if (colorPaletteName == ColorPaletteName.CustomColor) {
                                    colorPalette = dynamicColorPaletteOf(
                                        customColor,
                                        !lightTheme
                                    )
                                }

                                setSystemBarAppearance(colorPalette.isDark)

                                appearance = appearance.copy(
                                    colorPalette = if (!isPicthBlack) colorPalette else colorPalette.copy(
                                        background0 = Color.Black,
                                        background1 = Color.Black,
                                        background2 = Color.Black,
                                        background3 = Color.Black,
                                        background4 = Color.Black,
                                        text = Color.White
                                    ),
                                    typography = appearance.typography.copy(if (!isPicthBlack) colorPalette.text else Color.White),
                                )
                            }
                        }

                        Preferences.Key.THUMBNAIL_BORDER_RADIUS -> {
                            val thumbnailRoundness =
                                sharedPreferences.getEnum(key, ThumbnailRoundness.Heavy)

                            appearance = appearance.copy(
                                thumbnailShape = thumbnailRoundness.shape
                            )
                        }

                        Preferences.Key.USE_SYSTEM_FONT,
                        Preferences.Key.APPLY_FONT_PADDING,
                        Preferences.Key.FONT -> {
                            val useSystemFont = sharedPreferences.getBoolean( Preferences.Key.USE_SYSTEM_FONT, Preferences.USE_SYSTEM_FONT.defaultValue )
                            val applyFontPadding = sharedPreferences.getBoolean( Preferences.Key.APPLY_FONT_PADDING, Preferences.APPLY_FONT_PADDING.defaultValue )
                            val fontType = sharedPreferences.getEnum( Preferences.Key.FONT, Preferences.FONT.defaultValue )

                            appearance = appearance.copy(
                                typography = typographyOf(
                                    appearance.colorPalette.text,
                                    useSystemFont,
                                    applyFontPadding,
                                    fontType
                                ),
                            )
                        }
                    }
                }

                with(preferences) {
                    registerOnSharedPreferenceChangeListener(listener)

                    val colorPaletteName by Preferences.COLOR_PALETTE
                    if (colorPaletteName == ColorPaletteName.Dynamic) {
                        setDynamicPalette(
                            (player.currentMediaItem?.mediaMetadata?.artworkUri.thumbnail(1200)
                                ?: "").toString()
                        )
                    }

                    onDispose {
                        unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }
            }

            val rippleConfiguration =
                remember(appearance.colorPalette.text, appearance.colorPalette.isDark) {
                    RippleConfiguration(color = appearance.colorPalette.text)
                }

            LaunchedEffect(Unit) {
                val colorPaletteName by Preferences.COLOR_PALETTE
                if (colorPaletteName == ColorPaletteName.Customized) {
                    appearance = appearance.copy(
                        colorPalette = customColorPalette(
                            appearance.colorPalette,
                            this@MainActivity,
                            isSystemInDarkTheme
                        )
                    )
                }
            }


            if (colorPaletteMode == ColorPaletteMode.PitchBlack)
                appearance = appearance.copy(
                    colorPalette = appearance.colorPalette.applyPitchBlack,
                    typography = appearance.typography.copy(appearance.colorPalette.text)
                )




            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.colorPalette.background0)
            ) {


                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars
                val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

                val layoutMode by Preferences.APP_LAYOUT_MODE
                val radioSizePercent by Preferences.CAR_RADIO_SIZE_PERCENT
                val naturallyLandscape = remember(constraints.maxWidth, constraints.maxHeight) {
                    this@MainActivity.isDisplayNaturallyLandscape()
                }
                val adaptiveLayout = AdaptiveLayout.resolve(
                    if (BuildConfig.INDEPENDENT_FORK) layoutMode else app.kreate.android.enums.AppLayoutMode.Phone,
                    with(density) {
                        (constraints.maxWidth - windowsInsets.getLeft(density, LayoutDirection.Ltr) -
                            windowsInsets.getRight(density, LayoutDirection.Ltr)).toDp().value
                    },
                    with(density) {
                        (constraints.maxHeight - windowsInsets.getTop(density) -
                            windowsInsets.getBottom(density)).toDp().value
                    },
                    naturallyLandscape,
                    radioSizePercent,
                )
                SideEffect { CarRadioMode.update(adaptiveLayout) }

                val adaptiveAppearance = remember(appearance, adaptiveLayout.isCarRadio, adaptiveLayout.metrics) {
                    if (adaptiveLayout.isCarRadio)
                        appearance.copy(typography = appearance.typography.scaledForCarRadio(adaptiveLayout.metrics.textScale))
                    else appearance
                }

                val playerSheetState = rememberPlayerSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = Dimensions.collapsedPlayer + bottomDp,
                    expandedBound = maxHeight,
                )

                val playerState =
                    rememberModalBottomSheetState(skipPartiallyExpanded = true)

                val playerAwareWindowInsets by remember(
                    bottomDp,
                    playerSheetState.value
                ) {
                    derivedStateOf {
                        val bottom = playerSheetState.value.coerceIn(
                            bottomDp,
                            playerSheetState.collapsedBound
                        )

                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(bottom = bottom))
                    }
                }

                CrossfadeContainer(state = pipState.value) { isCurrentInPip ->
                    println("MainActivity pipState ${pipState.value} CrossfadeContainer isCurrentInPip $isCurrentInPip ")
                    val pipModule by Preferences.PIP_MODULE
                    if (isCurrentInPip) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                        ) {
                            when (pipModule) {
                                PipModule.Cover -> {
                                    PipModuleContainer {
                                        ImageFactory.AsyncImage(
                                            thumbnailUrl = player
                                                                 ?.currentMediaItem
                                                                 ?.mediaMetadata
                                                                 ?.artworkUri
                                                                 .toString()
                                                                 .resize( 1200, 1200 ),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                            }

                        }

                    } else {
                        val bottomMenu = remember { BottomMenu() }

                        // FIXME: Why is this block getting called twice on start?
                        CompositionLocalProvider(
                            LocalAdaptiveLayout provides adaptiveLayout,
                            LocalAppearance provides adaptiveAppearance,
                            LocalIndication provides ripple(bounded = true),
                            LocalRippleConfiguration provides rippleConfiguration,
                            // The radio shell already keeps content clear of system bars and the mini player.
                            LocalPlayerAwareWindowInsets provides
                                if (adaptiveLayout.useSideNavigation) WindowInsets(0) else playerAwareWindowInsets,
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                            LocalDownloadHelper provides MyDownloadHelper,
                            LocalPlayerSheetState provides playerState,
                            LocalMonetCompat provides monet,
                            LocalBottomMenu provides bottomMenu,
                        ) {
                            // This block gets called twice on startup, first run resets
                            // [intent.action] to empty string, second run sets
                            // [Settings.HOME_TAB_INDEX] to default page, resulting
                            // in default page shows regardless of shortcut
                            val startPage = remember {
                                // This step picks index from shortcut (if applicable)
                                var tab = when( intent.action ) {
                                    action_songs    -> HomeScreenTabs.Songs
                                    action_albums   -> HomeScreenTabs.Albums
                                    actions_artists -> HomeScreenTabs.Artists
                                    action_library  -> HomeScreenTabs.Playlists
                                    action_search   -> HomeScreenTabs.Search
                                    // If not opened from shortcuts, then use default page (from settings)
                                    else            -> Preferences.STARTUP_SCREEN.value
                                }

                                // In case [tabIndex] results to 0 and quick page
                                // isn't enabled change it to Songs page.
                                if( !Preferences.QUICK_PICKS_PAGE.value && tab == HomeScreenTabs.QuickPics )
                                    tab = HomeScreenTabs.Songs

                                // Always set to empty to prevent unwanted outcome
                                intent.action = ""

                                return@remember tab
                            }

                            AppNavigation(
                                navController = navController,
                                startPage = startPage,
                                miniPlayer = {
                                    MiniPlayer(
                                        showPlayer = { showPlayer = true },
                                        hidePlayer = { showPlayer = false },
                                        navController = navController
                                    )
                                }
                            )


                            app.kreate.android.downloads.DownloadDialogs(player)

                            val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

                            val isVideo = currentMediaItemForPip?.isVideo == true
                            // A silent video follows the audio of the same track; while the audio
                            // takes over from a video, that video still sounds.
                            val showsVideo = (isVideo && !playVideoAsAudio) ||
                                    videoFollower != null ||
                                    handingOffVideo != null

                            val youtubePlayer: @Composable () -> Unit = {
                                val follower = videoFollower
                                (follower?.item ?: handingOffVideo ?: currentMediaItemForPip)?.let { mediaItem ->
                                  key(videoSession) {
                                    YoutubePlayer(
                                        ytVideoId = mediaItem.mediaId,
                                        lifecycleOwner = LocalLifecycleOwner.current,
                                        startSeconds = player.currentPosition.coerceAtLeast(0L).toFloat() / 1000f,
                                        autoPlay = mediaItem.mediaId != pausedVideoId,
                                        follower = follower,
                                        volume = player.getGlobalVolume(),
                                        playRequest = videoPlayRequest,
                                        onCurrentSecond = { second ->
                                            val currentItem = player.currentMediaItem
                                            if (currentItem != null &&
                                                currentItem === mediaItem &&
                                                currentItem.isVideo
                                            ) {
                                                player.seekTo((second * 1000f).toLong())
                                            }
                                        },
                                        showPlayer = showPlayer,
                                        onSwitchToAudioPlayer = {
                                            val currentItem = player.currentMediaItem
                                            if (videoFollower != null) {
                                                // The audio of this track already plays by itself.
                                                endVideoFollower()
                                                playVideoAsAudio = true
                                                showPlayer = true
                                            } else if (currentItem != null &&
                                                currentItem === mediaItem &&
                                                currentItem.isVideo
                                            ) {
                                                playVideoAsAudio = true
                                                showPlayer = true
                                                player.resumeVideoAsAudio()
                                            }
                                        },
                                        onPlaybackActiveChanged = { isActive ->
                                            val currentItem = player.currentMediaItem
                                            if (currentItem != null &&
                                                currentItem === mediaItem &&
                                                currentItem.isVideo
                                            ) {
                                                PlaybackNotificationSilencer
                                                    .setEmbeddedVideoPlaybackActive(isActive)
                                                // Started by the user: from now on it plays like
                                                // any video, also after its window was closed.
                                                if (isActive) pausedVideoId = null
                                            }
                                        },
                                        onPlaybackError = {
                                            val currentItem = player.currentMediaItem
                                            if (currentItem != null &&
                                                currentItem === mediaItem &&
                                                currentItem.isVideo
                                            ) {
                                                playVideoAsAudio = true
                                                showPlayer = true
                                                player.resumeVideoAsAudio()
                                                Toaster.w(R.string.video_playback_failed_audio_fallback)
                                            }
                                        },
                                        onContinueInBackground = {
                                            val currentItem = player.currentMediaItem
                                            if (BuildConfig.INDEPENDENT_FORK &&
                                                currentItem != null &&
                                                currentItem === mediaItem &&
                                                currentItem.isVideo &&
                                                VideoBackgroundPlayback.continuesAsAudio(
                                                    changingConfigurations = isChangingConfigurations,
                                                    finishing = isFinishing,
                                                    inPictureInPicture = isAtLeastAndroid7 && isInPictureInPictureMode,
                                                    screenInteractive = getSystemService(PowerManager::class.java)
                                                        ?.isInteractive != false,
                                                    floatingWindowJustClosed = floatingWindowClosed.value
                                                )
                                            ) {
                                                logger.i { "Video continues as audio while the activity is stopped" }
                                                videoInBackground = mediaItem
                                                playVideoAsAudio = true
                                                showPlayer = true
                                                // Silent and without audio focus until it has caught up
                                                // with the still sounding video: the WebView pauses as
                                                // soon as it loses the focus.
                                                handingOffVideo = mediaItem
                                                followVideoLoudness(mediaItem.mediaId)
                                                nativeAudioFocusHandling(false)
                                                player.resumeVideoAsAudio(
                                                    startMuted = true,
                                                    positionMs = VideoToAudioHandoff.startPositionMs(
                                                        videoPositionMs = player.currentPosition.coerceAtLeast(0L),
                                                        videoDurationMs = mediaItem.mediaMetadata.durationMs
                                                    )
                                                )
                                                VideoToAudioHandoff(
                                                    player = player,
                                                    makeAudible = {
                                                        nativeAudioFocusHandling(true)
                                                        player.volume = videoAudioVolume()
                                                    },
                                                    onEnd = { handingOffVideo = null }
                                                ).also { videoHandoff = it }
                                            } else {
                                                null
                                            }
                                        }
                                    )
                                  }
                                }
                            }

                            // The floating window shows only the cover and ends the embedded player.
                            // A video with its own sound therefore continues as audio through the
                            // regular stop instead, seamlessly; once its sound plays in the native
                            // player, the window opens as for music.
                            val videoSoundsItself = isVideo && !playVideoAsAudio
                            PipEventContainer(
                                enable = Preferences.IS_PIP_ENABLED.value &&
                                        currentMediaItemForPip != null &&
                                        !videoSoundsItself,
                                autoEnterIfPossible = currentMediaItemForPip != null && !videoSoundsItself,
                                onPipOutAction = {
                                    showPlayer = false
                                    playVideoAsAudio = false
                                }
                            ) {
                                CustomModalBottomSheet(
                                    showSheet = showPlayer && !showsVideo,
                                    onDismissRequest = {
                                        showPlayer = false
                                    },
                                    containerColor = colorPalette().background0,
                                    contentColor = colorPalette().background0,
                                    modifier = Modifier.fillMaxWidth(),
                                    sheetState = playerState,
                                    dragHandle = {
                                        Surface(
                                            modifier = Modifier.padding(vertical = 0.dp),
                                            color = colorPalette().background0,
                                            shape = thumbnailShape()
                                        ) {}
                                    },
                                    shape = thumbnailRoundness.shape,
                                    glassSurfaceEnabled = false,
                                    sheetMaxWidth = if (adaptiveLayout.isCarRadio) androidx.compose.ui.unit.Dp.Unspecified else androidx.compose.material3.BottomSheetDefaults.SheetMaxWidth
                                ) {
                                    Player( navController ) { showPlayer = false }
                                }
                            }

                            CustomModalBottomSheet(
                                showSheet = showsVideo && showPlayer,
                                onDismissRequest = { showPlayer = false },
                                containerColor = colorPalette().background0,
                                contentColor = colorPalette().background0,
                                modifier = Modifier.fillMaxWidth(),
                                sheetState = playerState,
                                dragHandle = {
                                    Surface(
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        color = colorPalette().background0,
                                        shape = thumbnailShape()
                                    ) {}
                                },
                                shape = thumbnailRoundness.shape,
                                glassSurfaceEnabled = false
                            ) {
                                youtubePlayer()
                            }

                            val menuState = LocalMenuState.current
                            CustomModalBottomSheet(
                                showSheet = menuState.isDisplayed,
                                onDismissRequest = menuState::hide,
                                containerColor = Color.Transparent,
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                dragHandle = {
                                    Surface(
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        color = Color.Transparent,
                                        //shape = thumbnailShape
                                    ) {}
                                },
                                shape = thumbnailRoundness.shape
                            ) {
                                menuState.content()
                            }

                            if( bottomMenu.isVisible )
                                bottomMenu.BottomSheet( navController )
                        }
                    }
                }
                DisposableEffect(player) {
                    val player = player ?: return@DisposableEffect onDispose { }

                    if (player.currentMediaItem == null) {
                        playVideoAsAudio = false
                        if (playerState.isVisible) {
                            showPlayer = false
                        }
                    } else {
                        if (launchedFromNotification) {
                            intent.replaceExtras(Bundle())
                            showPlayer = !Preferences.PLAYER_KEEP_MINIMIZED.value
                        } else {
                            showPlayer = false
                        }
                    }

                    fun isPlayRequested(player: Player) =
                        VideoBackgroundPlayback.isAudioPlayRequested(
                            playWhenReady = player.playWhenReady,
                            playbackState = player.playbackState,
                            suppressionReason = player.playbackSuppressionReason
                        )

                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(
                                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                                    Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED
                                )
                            ) audioPlayRequested = isPlayRequested(player)
                            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED))
                                audioAdvancing = player.isPlaying

                            if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) &&
                                VideoBackgroundPlayback.playBelongsToVideo(
                                    windowShowsVideoWithItsSound = showPlayer && !playVideoAsAudio &&
                                            videoFollower == null && handingOffVideo == null &&
                                            currentMediaItemForPip?.isVideo == true,
                                    currentIsVideo = player.currentMediaItem?.isVideo == true,
                                    playWhenReady = player.playWhenReady
                                )
                            ) {
                                logger.i { "Play command while the video sounds itself: forwarded to the video" }
                                player.playWhenReady = false
                                if (player is StatefulPlayer) player.unloadMedia() else player.stop()
                                videoPlayRequest++
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            // A silent video follows the audio of its own track only. The same
                            // track started as a video again plays with its own sound.
                            if (videoFollower?.item?.mediaId != mediaItem?.mediaId || mediaItem?.isVideo == true)
                                endVideoFollower()
                            if (minimizedVideo?.mediaId != mediaItem?.mediaId || mediaItem?.isVideo == true)
                                minimizedVideo = null
                            if (pausedVideoId != mediaItem?.mediaId) pausedVideoId = null
                            // Another track after audio that continued a video: normal volume again.
                            if (videoAudioLevel.videoId != null && mediaItem?.mediaId != videoAudioLevel.videoId) {
                                videoAudioLevel.videoId = null
                                player.restoreGlobalVolume()
                            }

                            val previousItem = currentMediaItemForPip
                            val continuesSameEmbeddedVideo =
                                previousItem != null &&
                                        previousItem.isVideo &&
                                        mediaItem != null &&
                                        mediaItem.isVideo &&
                                        previousItem.mediaId == mediaItem.mediaId

                            // End the state of a replaced embedded player before exposing the next
                            // item. Its late WebView callbacks are rejected by identity above.
                            if (!continuesSameEmbeddedVideo)
                                PlaybackNotificationSilencer.setEmbeddedVideoPlaybackActive(false)

                            val keepPlayerOpenForAudioFallback =
                                playVideoAsAudio &&
                                        mediaItem != null &&
                                        mediaItem.mediaId == previousItem?.mediaId &&
                                        mediaItem.supportsVideoPlayback &&
                                        !mediaItem.isVideo
                            currentMediaItemForPip = mediaItem
                            playVideoAsAudio = keepPlayerOpenForAudioFallback

                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if ( mediaItem.localConfiguration?.tag !== PersistentQueue.Tag )
                                    showPlayer = keepPlayerOpenForAudioFallback ||
                                            mediaItem.isVideo ||
                                            !Preferences.PLAYER_KEEP_MINIMIZED.value
                            }

                            setDynamicPalette(mediaItem?.mediaMetadata?.artworkUri.thumbnail(1200).toString())
                        }


                    }

                    player.addListener(listener)
                    currentMediaItemForPip = player.currentMediaItem
                    audioPlayRequested = isPlayRequested(player)
                    audioAdvancing = player.isPlaying

                    onDispose { player.removeListener(listener) }
                }

                AnimatedStartupSplash(
                    visible = showStartupSplash,
                    appName = BuildConfig.APP_NAME,
                    backgroundColor = appearance.colorPalette.background0,
                    secondaryBackgroundColor = appearance.colorPalette.background1,
                    isDark = appearance.colorPalette.isDark,
                    contentColor = appearance.colorPalette.text,
                    accentColor = appearance.colorPalette.accent,
                    onFinished = { showStartupSplash = false },
                )

            }

            LaunchedEffect(intentUriData) {
                val uri = intentUriData ?: return@LaunchedEffect

                Toaster.n(
                    "${BuildConfig.APP_NAME} ${this@MainActivity.resources.getString( R.string.opening_url )}",
                    duration = Toast.LENGTH_LONG
                )

                lifecycleScope.launch(Dispatchers.Main) {
                    when (val path = uri.pathSegments.firstOrNull()) {
                        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                            val browseId = "VL$playlistId"

                            if (playlistId.startsWith("OLAK5uy_")) {
                                Innertube.playlistPage(BrowseBody(browseId = browseId))
                                    ?.getOrNull()?.let {
                                        it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                                            NavRoutes.YT_ALBUM.navigateHere( navController, browseId )
                                        }
                                    }
                            } else {
                                NavRoutes.YT_PLAYLIST.navigateHere( navController, browseId )
                            }
                        }

                        "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                            NavRoutes.YT_ARTIST.navigateHere( navController, channelId )
                        }

                        "search" -> uri.getQueryParameter("q")?.let { query ->
                            NavRoutes.searchResults.navigateHere( navController, query )
                        }

                        else -> when {
                            path == "watch" -> uri.getQueryParameter("v")
                            uri.host == "youtu.be" -> path
                            else -> null
                        }?.let { videoId ->
                            Innertube.song(videoId)?.getOrNull()?.let { song ->
                                withContext(Dispatchers.Main) {
                                    if ( !song.explicit && !Preferences.PARENTAL_CONTROL.value )
                                        player.forcePlay(song.asMediaItem)
                                    else
                                        Toaster.w( "Parental control is enabled" )
                                }
                            }
                        }
                    }
                }
                intentUriData = null
            }
        }
    }


    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {

            if ( Preferences.AUDIO_SHAKE_TO_SKIP.value ) {
                // Fetching x,y,z values
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                lastAcceleration = currentAcceleration

                // Getting current accelerations
                // with the help of fetched x,y,z values
                currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta: Float = currentAcceleration - lastAcceleration
                acceleration = acceleration * 0.9f + delta

                // Display a Toast message if
                // acceleration value is over 12
                if (acceleration > 12) {
                    shakeCounter++
                    //Toast.makeText(applicationContext, "Shake event detected", Toast.LENGTH_SHORT).show()
                }
                if (shakeCounter >= 1) {
                    //Toast.makeText(applicationContext, "Shaked $shakeCounter times", Toast.LENGTH_SHORT).show()
                    shakeCounter = 0
                    inject<StatefulPlayer>(StatefulPlayer::class.java).value.playNext()
                }

            }

        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            sensorManager?.registerListener(
                sensorListener, sensorManager!!.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER
                ), SensorManager.SENSOR_DELAY_NORMAL
            )
        }.onFailure {
            logger.e( it ) { "onResume failed" }
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            sensorListener.let { sensorManager?.unregisterListener(it) }
        }.onFailure {
            logger.e( it ) { "onPause failed" }
        }
    }

    @UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.INDEPENDENT_FORK && intent.getBooleanExtra("kruxxDownloadJobs", false))
            app.kreate.android.downloads.DownloadCenter.settings()
        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()

    }

    @UnstableApi
    override fun onDestroy() =
        try {
            //<editor-fold desc="Stop player">
            // Stop music
            val player by inject<StatefulPlayer>(StatefulPlayer::class.java)
            player.run {
                stop()
                // FIXME: Android will try to recreate service if
                //  there's some MediaItems left in the queue .
                clearMediaItems()
            }
            // Unbind service (making sure there's no connection with the service)
            unbindService( serviceConnection )
            // Stop service (release resources)
            val intent = Intent(this, PlayerServiceModern::class.java)
            stopService( intent )

            logger.d { "Successfully stop player and unbind PlayerServiceModern service" }
            //</editor-fold>

            // Delete latest report
            val report = CrashReportDialog(this)
            if( report.isAvailable() ) {
                report.crashlogFile.delete()
                logger.d { "Successfully deleted latest crashlog" }
            }

            monet.removeMonetColorsChangedListener(this)
            _monet = null
            logger.d { "Successfully removed MonetColorsChangedListener" }

            Preferences.unload()
            logger.d { "Unloaded preferences" }
        } catch( err: Exception ) {
            logger.e( err ) { "onDestroy failed!" }
        } finally {
            super.onDestroy()
        }

    private fun setSystemBarAppearance(isDark: Boolean) {
        with(WindowCompat.getInsetsController(window, window.decorView.rootView)) {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        if (!isAtLeastAndroid6) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }

        if (!isAtLeastAndroid8) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val action_search = "it.fast4x.rimusic.action.search"
        const val action_songs = "it.fast4x.rimusic.action.songs"
        const val action_albums = "it.fast4x.rimusic.action.albums"
        const val actions_artists = "it.fast4x.rimusic.action.artists"
        const val action_library = "it.fast4x.rimusic.action.library"
        const val action_copy_crash_log = "it.fast4x.rimusic.action.copy_crash_log"
    }


    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        val colorPaletteName by Preferences.COLOR_PALETTE
        if (!isInitialChange && colorPaletteName == ColorPaletteName.MaterialYou) {
            /*
            monet.updateMonetColors()
            monet.invokeOnReady {
                startApp()
            }
             */
            this@MainActivity.recreate()
        }
    }


}

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { TODO() }

val LocalDownloadHelper = staticCompositionLocalOf<MyDownloadHelper> { error("No Downloader provided") }

@OptIn(ExperimentalMaterial3Api::class)
val LocalPlayerSheetState =
    staticCompositionLocalOf<SheetState> { error("No player sheet state provided") }
