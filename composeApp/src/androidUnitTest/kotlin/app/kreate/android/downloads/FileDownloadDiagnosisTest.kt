package app.kreate.android.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

/** A failed download logs what went wrong, but never a signed address. */
class FileDownloadDiagnosisTest {

    @Test
    fun addressesAreRemovedFromTheMessageAndItsCauses() {
        val signed = "https://rr3---sn-4g5e6nsz.googlevideo.com/videoplayback?expire=1&sig=SECRET&pot=TOKEN"
        val error = IllegalStateException(
            "Stream rejected",
            IOException( "Video download HTTP 403 for $signed" )
        )

        val diagnosis = error.diagnosis()

        assertEquals(
            "IllegalStateException: Stream rejected ← IOException: Video download HTTP 403 for <address>",
            diagnosis
        )
        assertFalse( diagnosis.contains( "SECRET" ) || diagnosis.contains( "googlevideo" ) )
    }

    @Test
    fun messagesWithoutAddressesStayReadable() {
        assertEquals(
            "IllegalStateException: Server did not honor the video byte range",
            IllegalStateException( "Server did not honor the video byte range" ).diagnosis()
        )
        assertEquals( "NullPointerException: ", NullPointerException().diagnosis() )
    }
}
