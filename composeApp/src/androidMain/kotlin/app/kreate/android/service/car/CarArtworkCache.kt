package app.kreate.android.service.car

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.kreate.android.coil3.ImageFactory
import coil3.imageLoader
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest

/**
 * Car-sized JPEG copies of web thumbnails for [CarArtworkProvider].
 *
 * Images come through the app's Coil loader, so thumbnails the phone already shows are served
 * from its disk cache without network access, and the user's proxy and DNS settings apply.
 */
internal object CarArtworkCache {

    private const val DIRECTORY = "car_artwork"
    private const val SIZE_PX = 512
    private const val JPEG_QUALITY = 90
    private const val LOAD_TIMEOUT_MS = 8_000L
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val PREFETCH_LIMIT = 30

    private val locks = Array(16) { Any() }
    private val prefetchPermits = Semaphore(3)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The cached image of [source], loading it first if needed. `null` if it can't be loaded. */
    fun file(context: Context, source: Uri): File? {
        val name = sha256(source.toString())
        val target = File(directory(context), "$name.jpg")
        if (target.isReady()) return target

        return synchronized(locks[Math.floorMod(name.hashCode(), locks.size)]) {
            if (target.isReady()) return@synchronized target
            val bitmap = load(context, source) ?: return@synchronized null
            write(bitmap, target)
            trim(target.parentFile!!)
            target.takeIf { it.isReady() }
        }
    }

    /**
     * Loads the artwork of browse results in the background, so the car host's requests don't
     * have to wait for the network.
     */
    fun prefetch(context: Context, uris: List<Uri>) {
        val appContext = context.applicationContext
        uris.asSequence()
            .mapNotNull { CarArtwork.source(appContext, it) }
            .filter { it.scheme == "https" }
            .distinct()
            .take(PREFETCH_LIMIT)
            .forEach { source ->
                scope.launch { prefetchPermits.withPermit { file(appContext, source) } }
            }
    }

    private fun load(context: Context, source: Uri): Bitmap? = runBlocking {
        withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            // A car host may start the process through the provider, before KruXx is set up.
            while (GlobalContext.getOrNull() == null) delay(50)

            val request = ImageFactory.requestBuilder(source.toString()) {
                size(SIZE_PX)
                allowHardware(false)
                bitmapConfig(Bitmap.Config.ARGB_8888)
            }
            (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        }
    }

    private fun write(bitmap: Bitmap, target: File) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val written = runCatching {
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }.getOrDefault(false)
        if (!written || !temporary.renameTo(target)) temporary.delete()
    }

    private fun trim(directory: File) {
        val files = directory.listFiles { file -> file.extension == "jpg" } ?: return
        var total = files.sumOf(File::length)
        if (total <= MAX_BYTES) return

        for (file in files.sortedBy(File::lastModified)) {
            if (total <= MAX_BYTES * 3 / 4) break
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun directory(context: Context): File =
        File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    private fun File.isReady(): Boolean = length() > 0

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
