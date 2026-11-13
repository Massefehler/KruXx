package app.kreate.android.service.car

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Serves the artwork URIs created by [CarArtwork] to Android Auto and Android Automotive OS.
 *
 * Read-only. Requests for anything KruXx didn't sign are answered with [FileNotFoundException].
 */
class CarArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Car artwork is read-only")
        val context = context ?: throw FileNotFoundException(uri.toString())
        val source = CarArtwork.source(context, uri)
            ?: throw FileNotFoundException("Unknown car artwork")

        if (source.scheme == ContentResolver.SCHEME_CONTENT)
            return context.contentResolver.openFileDescriptor(source, "r")
                ?: throw FileNotFoundException("Album art unavailable")

        val file = CarArtworkCache.file(context, source)
            ?: throw FileNotFoundException("Artwork unavailable")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? {
        val context = context ?: return null
        val source = CarArtwork.source(context, uri) ?: return null
        return if (source.scheme == ContentResolver.SCHEME_CONTENT) "image/*" else "image/jpeg"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
