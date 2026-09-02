package it.fast4x.rimusic.ui.screens.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.utils.hasPermission
import me.knighthat.utils.Toaster
import java.util.Locale

private enum class VoiceSearchAvailability {
    AVAILABLE,
    REQUIRES_ANDROID_12,
    ON_DEVICE_RECOGNIZER_UNAVAILABLE
}

internal enum class VoiceSearchFailure {
    PERMISSION_DENIED,
    NO_MATCH,
    LANGUAGE_UNAVAILABLE,
    GENERIC
}

private data class VoiceSearchSetup(
    val availability: VoiceSearchAvailability,
    val controller: OnDeviceSpeechRecognizerController? = null
)

/**
 * A voice-input button that deliberately uses only Android's on-device recognizer.
 *
 * There is no fallback to [SpeechRecognizer.createSpeechRecognizer] or to a recognizer
 * activity because either may hand audio to a network-backed service. Recognition results
 * are returned to the caller for editing; this component never submits the search itself.
 */
@Composable
internal fun OnDeviceVoiceSearchButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    var isListening by remember { mutableStateOf(false) }

    val setup = remember(context.applicationContext) {
        createVoiceSearchSetup(
            context = context.applicationContext,
            onListeningChanged = { isListening = it },
            onResult = { currentOnResult.value(it) },
            onFailure = ::showVoiceSearchFailure
        )
    }

    DisposableEffect(setup.controller) {
        onDispose { setup.controller?.destroy() }
    }

    fun startRecognition() {
        if (setup.availability != VoiceSearchAvailability.AVAILABLE) {
            showVoiceSearchAvailability(setup.availability)
            return
        }
        val controller = setup.controller
        if (controller == null) {
            Toaster.w(R.string.voice_search_unavailable)
            return
        }
        controller.start(Locale.getDefault())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecognition()
        else Toaster.w(R.string.voice_search_permission_denied)
    }

    IconButton(
        onClick = {
            if (isListening) {
                setup.controller?.cancel()
                return@IconButton
            }

            when (setup.availability) {
                VoiceSearchAvailability.REQUIRES_ANDROID_12 ->
                    Toaster.w(R.string.voice_search_requires_android_12)

                VoiceSearchAvailability.ON_DEVICE_RECOGNIZER_UNAVAILABLE ->
                    Toaster.w(R.string.voice_search_unavailable)

                VoiceSearchAvailability.AVAILABLE -> {
                    if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) startRecognition()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        icon = R.drawable.microphone,
        color = if (isListening) colorPalette().accent else colorPalette().favoritesIcon,
        contentDescription = stringResource(
            if (isListening) R.string.voice_search_stop else R.string.voice_search
        ),
        modifier = modifier
    )
}

private fun createVoiceSearchSetup(
    context: Context,
    onListeningChanged: (Boolean) -> Unit,
    onResult: (String) -> Unit,
    onFailure: (VoiceSearchFailure) -> Unit
): VoiceSearchSetup {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return VoiceSearchSetup(VoiceSearchAvailability.REQUIRES_ANDROID_12)
    }

    return createVoiceSearchSetupForAndroid12(
        context = context,
        onListeningChanged = onListeningChanged,
        onResult = onResult,
        onFailure = onFailure
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun createVoiceSearchSetupForAndroid12(
    context: Context,
    onListeningChanged: (Boolean) -> Unit,
    onResult: (String) -> Unit,
    onFailure: (VoiceSearchFailure) -> Unit
): VoiceSearchSetup {
    val isAvailable = runCatching {
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }.getOrDefault(false)

    if (!isAvailable) {
        return VoiceSearchSetup(VoiceSearchAvailability.ON_DEVICE_RECOGNIZER_UNAVAILABLE)
    }

    val recognizer = runCatching {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }.getOrNull() ?: return VoiceSearchSetup(
        VoiceSearchAvailability.ON_DEVICE_RECOGNIZER_UNAVAILABLE
    )

    return VoiceSearchSetup(
        availability = VoiceSearchAvailability.AVAILABLE,
        controller = OnDeviceSpeechRecognizerController(
            recognizer = recognizer,
            onListeningChanged = onListeningChanged,
            onResult = onResult,
            onFailure = onFailure
        )
    )
}

private class OnDeviceSpeechRecognizerController(
    private val recognizer: SpeechRecognizer,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFailure: (VoiceSearchFailure) -> Unit
) : RecognitionListener {

    private var isListening = false
    private var userCancelled = false
    private var isDestroyed = false

    init {
        recognizer.setRecognitionListener(this)
    }

    fun start(locale: Locale) {
        if (isDestroyed || isListening) return

        userCancelled = false
        try {
            setListening(true)
            recognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    // Defense in depth. createOnDeviceSpeechRecognizer() is the actual guarantee.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            )
        } catch (_: SecurityException) {
            setListening(false)
            onFailure(VoiceSearchFailure.PERMISSION_DENIED)
        } catch (_: RuntimeException) {
            setListening(false)
            onFailure(VoiceSearchFailure.GENERIC)
        }
    }

    fun cancel() {
        if (isDestroyed) return

        userCancelled = true
        runCatching { recognizer.cancel() }
        setListening(false)
    }

    fun destroy() {
        if (isDestroyed) return

        isDestroyed = true
        if (isListening) runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
        setListening(false)
    }

    override fun onReadyForSpeech(params: Bundle?) = setListening(true)

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        if (isDestroyed) return

        setListening(false)
        if (userCancelled) {
            userCancelled = false
            return
        }
        onFailure(voiceSearchFailure(error))
    }

    override fun onResults(results: Bundle?) {
        if (isDestroyed) return

        setListening(false)
        if (userCancelled) {
            userCancelled = false
            return
        }

        val result = firstUsableVoiceResult(
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        )
        if (result == null) onFailure(VoiceSearchFailure.NO_MATCH)
        else onResult(result)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun setListening(value: Boolean) {
        if (isListening == value) return
        isListening = value
        onListeningChanged(value)
    }
}

internal fun firstUsableVoiceResult(results: List<String>?): String? =
    results?.firstOrNull { it.isNotBlank() }?.trim()

internal fun voiceSearchFailure(error: Int): VoiceSearchFailure = when (error) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceSearchFailure.PERMISSION_DENIED
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceSearchFailure.NO_MATCH
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceSearchFailure.LANGUAGE_UNAVAILABLE
    else -> VoiceSearchFailure.GENERIC
}

private fun showVoiceSearchAvailability(availability: VoiceSearchAvailability) {
    when (availability) {
        VoiceSearchAvailability.REQUIRES_ANDROID_12 ->
            Toaster.w(R.string.voice_search_requires_android_12)

        VoiceSearchAvailability.ON_DEVICE_RECOGNIZER_UNAVAILABLE ->
            Toaster.w(R.string.voice_search_unavailable)

        VoiceSearchAvailability.AVAILABLE -> Unit
    }
}

private fun showVoiceSearchFailure(failure: VoiceSearchFailure) {
    when (failure) {
        VoiceSearchFailure.PERMISSION_DENIED ->
            Toaster.w(R.string.voice_search_permission_denied)

        VoiceSearchFailure.NO_MATCH ->
            Toaster.w(R.string.voice_search_no_match)

        VoiceSearchFailure.LANGUAGE_UNAVAILABLE ->
            Toaster.w(R.string.voice_search_language_unavailable)

        VoiceSearchFailure.GENERIC ->
            Toaster.e(R.string.voice_search_error)
    }
}
