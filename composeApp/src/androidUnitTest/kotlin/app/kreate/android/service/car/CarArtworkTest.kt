package app.kreate.android.service.car

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CarArtworkTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun webThumbnailsBecomeLocalUrisThatResolveToTheirSource() {
        val source = "https://lh3.googleusercontent.com/abc_DEF-123=w544-h544-l90-rj"
        val uri = CarArtwork.uri(context, source)!!

        assertEquals(ContentResolver.SCHEME_CONTENT, uri.scheme)
        assertEquals("${context.packageName}.carartwork", uri.authority)
        assertEquals(Uri.parse(source), CarArtwork.source(context, uri))
        // Stable, so car hosts can cache the image
        assertEquals(uri, CarArtwork.uri(context, source))
    }

    @Test
    fun albumArtOfDeviceSongsIsServedToo() {
        val source = "content://media/external/audio/albumart/42"

        assertEquals(Uri.parse(source), CarArtwork.source(context, CarArtwork.uri(context, source)!!))
    }

    @Test
    fun appResourcesAreAlreadyLocal() {
        val resource = "android.resource://${context.packageName}/drawable/heart"

        assertEquals(Uri.parse(resource), CarArtwork.uri(context, resource))
    }

    @Test
    fun onlyKnownImageHostsAreWrapped() {
        assertNull(CarArtwork.uri(context, null))
        assertNull(CarArtwork.uri(context, " "))
        assertNull(CarArtwork.uri(context, "https://example.com/cover.jpg"))
        assertNull(CarArtwork.uri(context, "http://lh3.googleusercontent.com/cover"))
        assertNull(CarArtwork.uri(context, "https://googleusercontent.com.example.com/cover"))
        assertNull(CarArtwork.uri(context, "https://evilggpht.com/cover"))
        assertNull(CarArtwork.uri(context, "content://com.example.provider/file"))
        assertNull(CarArtwork.uri(context, "file:///data/data/${context.packageName}/files/secret"))
    }

    @Test
    fun foreignOrTamperedUrisAreRejected() {
        val uri = CarArtwork.uri(context, "https://i.ytimg.com/vi/abc/hqdefault.jpg")!!
        val (path, signature, encoded) = uri.pathSegments
        val other = CarArtwork.uri(context, "https://yt3.ggpht.com/other")!!

        fun withSegments(vararg segments: String) =
            uri.buildUpon().path(null).apply { segments.forEach(::appendPath) }.build()

        assertNull(CarArtwork.source(context, withSegments(path, other.pathSegments[1], encoded)))
        assertNull(CarArtwork.source(context, withSegments(path, signature, other.pathSegments[2])))
        assertNull(CarArtwork.source(context, withSegments(path, signature)))
        assertNull(CarArtwork.source(context, uri.buildUpon().authority("com.example.provider").build()))
        assertNull(CarArtwork.source(context, Uri.parse("https://i.ytimg.com/vi/abc/hqdefault.jpg")))
        assertNotEquals(uri, other)
    }

    @Test
    fun theSigningKeyStaysOnTheDeviceAndOutOfBackups() {
        CarArtwork.uri(context, "https://lh3.googleusercontent.com/abc")

        val key = File(context.noBackupFilesDir, "car_artwork.key")
        assertTrue(key.isFile)
        assertEquals(32L, key.length())
    }

    @Test
    fun urisStayValidAcrossRestartsWithTheStoredKey() {
        val key = ByteArray(32) { it.toByte() }
        File(context.noBackupFilesDir, "car_artwork.key").writeBytes(key)
        val uri = CarArtwork.uri(context, "https://yt3.ggpht.com/artist=s544")!!
        val encoded = uri.pathSegments[2]

        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val expected = Base64.encodeToString(
            mac.doFinal(encoded.toByteArray()).copyOf(16),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        assertEquals(expected, uri.pathSegments[1])
    }
}
