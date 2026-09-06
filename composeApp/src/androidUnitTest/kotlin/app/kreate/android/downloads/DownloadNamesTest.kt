package app.kreate.android.downloads

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class DownloadNamesTest {
    private val noma = DownloadTrack("pITRfcVDyGQ", "Noma - Sleepwalker", "dejanprogtrens",
        artistNames = listOf("dejanprogtrens"), artistIds = listOf("channel-id"), videoSource = true)

    @Test fun videoUsesArtistAndTitleInsteadOfUploaderAcrossAllFormats() {
        assertEquals(TrackNames("Noma", "Sleepwalker"), noma.names)
        for (extension in listOf("mp3", "mp4", "webm", "m4a"))
            assertEquals("Noma - Sleepwalker.$extension", OfflineFiles.fileName(noma, extension))
    }

    @Test fun audioMetadataKeepsTitleHyphensAndDoesNotRepeatTheArtist() {
        assertEquals("Noma - Sleepwalker", noma.copy(title = "Sleepwalker", artist = "Noma", videoSource = false).names.label)
        assertEquals("Noma - Sleepwalker", noma.copy(artist = "Noma", videoSource = false).names.label)
        assertEquals(TrackNames("Artist", "Part One - Part Two"),
            DownloadTrack("id", "Part One - Part Two", "Artist").names)
    }

    @Test fun videoPresentationLabelsAreRemovedWhileVersionsAndArtistHyphensSurvive() {
        assertEquals("AC-DC - Song (Live Remix)", noma.copy(title = "AC-DC — Song (Live Remix) [Official Video] [4K]").names.label)
        assertEquals("Noma - Sleepwalker - Extended Mix", noma.copy(title = "Noma – Sleepwalker - Extended Mix").names.label)
        assertEquals("Noma - Sleepwalker (feat. Guest)", noma.copy(title = "Noma - Sleepwalker (feat. Guest)").names.label)
    }

    @Test fun videoWithoutArtistInTitleDoesNotPretendTheUploaderIsTheArtist() {
        assertEquals(TrackNames("", "Sleepwalker"), noma.copy(title = "Sleepwalker (Official Audio)").names)
        assertEquals("Sleepwalker.mp3", OfflineFiles.fileName(noma.copy(title = "Sleepwalker"), "mp3"))
    }

    @Test fun persistedVideoMetadataAndCopyRoundTripsKeepTheResolvedIdentity() {
        val track = DownloadTrack.from(noma.json())
        val item = track.mediaItem()
        assertEquals("Sleepwalker", item.mediaMetadata.title)
        assertEquals("Noma", item.mediaMetadata.artist)
        assertTrue(item.mediaMetadata.extras!!.getStringArrayList("artistIds")!!.isEmpty())
        assertEquals(noma, DownloadTrack.from(item))
        assertFalse(item.mediaMetadata.extras!!.getBoolean("isVideo"))
        assertEquals(noma.names, DownloadTrack.from(noma.json().apply { remove("videoSource") }).names)
    }

    @Test fun videoMenuMetadataAndOfficialArtTracksRemainDistinct() {
        fun item(art: Boolean) = MediaItem.Builder().setMediaId("id").setMediaMetadata(MediaMetadata.Builder()
            .setTitle("Sleepwalker").setArtist("Noma").setExtras(Bundle().apply {
                putString("musicVideoType", if (art) "ATV" else "UGC")
                putBoolean("isArtTrack", art)
                putBoolean("downloadVideo", true)
            }).build()).build()
        assertEquals(TrackNames("Noma", "Sleepwalker"), DownloadTrack.from(item(true)).names)
        assertEquals(TrackNames("", "Sleepwalker"), DownloadTrack.from(item(false)).names)
    }

    @Test fun migrationRecognizesOnlyAnExactKnownLegacyNameAndKeepsCollisionNumbers() {
        val name = "dejanprogtrens - Noma - Sleepwalker [c587dba6992e] - MP3 320.mp3"
        assertEquals("Noma - Sleepwalker.mp3", DownloadNames.renamedLegacyFile(name, listOf(noma)))
        assertEquals("Noma - Sleepwalker (2).mp3", DownloadNames.renamedLegacyFile(name.replace(".mp3", " (2).mp3"), listOf(noma)))
        assertNull(DownloadNames.renamedLegacyFile(name.replace("dejanprogtrens", "Someone else"), listOf(noma)))
        assertNull(DownloadNames.renamedLegacyFile(name, emptyList()))
        assertEquals("Noma - Sleepwalker (3).mp3", DownloadNames.availableName("Noma - Sleepwalker.mp3",
            listOf("noma - sleepwalker.mp3", "Noma - Sleepwalker (2).mp3")))
    }
}
