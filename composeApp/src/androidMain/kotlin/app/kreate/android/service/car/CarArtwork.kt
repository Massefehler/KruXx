package app.kreate.android.service.car

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android Auto and Android Automotive OS only load media artwork from local
 * `content://` or `android.resource://` URIs, so web thumbnails never appear in the car.
 * [uri] wraps a thumbnail in a `content://` URI that [CarArtworkProvider] serves.
 *
 * The provider has to be exported for the car hosts. Each URI therefore carries an HMAC made
 * with a key that never leaves this installation: the provider only serves sources that KruXx
 * handed out itself and cannot be used by other apps to fetch arbitrary addresses.
 */
object CarArtwork {

    private const val PATH = "art"
    private const val KEY_FILE = "car_artwork.key"
    private const val KEY_BYTES = 32
    private const val SIGNATURE_BYTES = 16
    private const val ALGORITHM = "HmacSHA256"
    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    /** Image hosts of YouTube Music thumbnails. */
    private val TRUSTED_IMAGE_HOSTS = listOf("googleusercontent.com", "ggpht.com", "ytimg.com")

    private class Key(val file: File, val bytes: ByteArray)

    @Volatile
    private var key: Key? = null

    fun authority(context: Context): String = "${context.packageName}.carartwork"

    /**
     * The URI under which car hosts can load [source], `null` when there is nothing they could
     * show. App resources are already local and are returned unchanged.
     */
    fun uri(context: Context, source: String?): Uri? {
        if (source.isNullOrBlank()) return null
        val parsed = source.toUri()
        if (parsed.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) return parsed
        if (!isTrustedSource(parsed)) return null

        val encoded = Base64.encodeToString(source.toByteArray(), BASE64_FLAGS)
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(authority(context))
            .appendPath(PATH)
            .appendPath(sign(context, encoded))
            .appendPath(encoded)
            .build()
    }

    /** The source behind a URI created by [uri], `null` for anything KruXx did not sign. */
    fun source(context: Context, uri: Uri): Uri? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != authority(context)) return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != PATH) return null
        val (_, signature, encoded) = segments
        if (!MessageDigest.isEqual(sign(context, encoded).toByteArray(), signature.toByteArray())) return null

        val source = runCatching { String(Base64.decode(encoded, BASE64_FLAGS)) }.getOrNull() ?: return null
        return source.toUri().takeIf(::isTrustedSource)
    }

    internal fun isTrustedSource(uri: Uri): Boolean = when (uri.scheme) {
        "https" -> uri.host?.lowercase()?.let { host ->
            TRUSTED_IMAGE_HOSTS.any { host == it || host.endsWith(".$it") }
        } == true
        // Album art of songs stored on the device
        ContentResolver.SCHEME_CONTENT -> uri.authority == MediaStore.AUTHORITY
        else -> false
    }

    private fun sign(context: Context, value: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key(context), ALGORITHM)) }
        val signature = mac.doFinal(value.toByteArray()).copyOf(SIGNATURE_BYTES)
        return Base64.encodeToString(signature, BASE64_FLAGS)
    }

    private fun key(context: Context): ByteArray {
        val file = File(context.noBackupFilesDir, KEY_FILE)
        key?.takeIf { it.file == file }?.let { return it.bytes }
        return synchronized(this) {
            key?.takeIf { it.file == file }?.bytes
                ?: loadOrCreateKey(file).also { key = Key(file, it) }
        }
    }

    private fun loadOrCreateKey(file: File): ByteArray {
        runCatching { file.readBytes() }.getOrNull()
            ?.takeIf { it.size == KEY_BYTES }
            ?.let { return it }

        val created = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val temporary = File(file.parentFile, "$KEY_FILE.tmp")
        temporary.writeBytes(created)
        if (!temporary.renameTo(file)) temporary.delete()
        return created
    }
}
