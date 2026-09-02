package it.fast4x.rimusic.ui.screens.search

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnDeviceVoiceSearchTest {

    @Test
    fun choosesFirstNonBlankRecognitionResultAndTrimsIt() {
        assertEquals(
            "Depeche Mode",
            firstUsableVoiceResult(listOf("", "  Depeche Mode  ", "Enjoy the Silence"))
        )
    }

    @Test
    fun emptyRecognitionResultsProduceNoText() {
        assertNull(firstUsableVoiceResult(null))
        assertNull(firstUsableVoiceResult(emptyList()))
        assertNull(firstUsableVoiceResult(listOf("", "   ")))
    }

    @Test
    fun mapsExpectedRecognizerErrorsToUserFacingCategories() {
        assertEquals(
            VoiceSearchFailure.PERMISSION_DENIED,
            voiceSearchFailure(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
        assertEquals(
            VoiceSearchFailure.NO_MATCH,
            voiceSearchFailure(SpeechRecognizer.ERROR_NO_MATCH)
        )
        assertEquals(
            VoiceSearchFailure.NO_MATCH,
            voiceSearchFailure(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        )
        assertEquals(
            VoiceSearchFailure.LANGUAGE_UNAVAILABLE,
            voiceSearchFailure(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        )
        assertEquals(
            VoiceSearchFailure.GENERIC,
            voiceSearchFailure(SpeechRecognizer.ERROR_AUDIO)
        )
    }
}
