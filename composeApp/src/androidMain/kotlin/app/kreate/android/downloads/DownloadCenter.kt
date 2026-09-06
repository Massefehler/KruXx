@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import androidx.work.*
import app.kreate.android.BuildConfig
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.EXPLICIT_BUNDLE_TAG
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class DownloadKind { ORIGINAL, MP3, VIDEO }

data class DownloadTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String = "",
    val explicit: Boolean = false,
    val durationMs: Long = -1,
    val album: String = "",
    val albumId: String = "",
    val artistNames: List<String> = emptyList(),
    val artistIds: List<String> = emptyList(),
    val videoSource: Boolean = false,
) {
    fun mediaItem(): MediaItem = MediaItem.Builder().setMediaId(id).setUri(id)
        .setCustomCacheKey(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(names.title).setArtist(names.artist)
                .setDisplayTitle(names.title).setAlbumTitle(album)
                .setDurationMs(durationMs.takeIf { it >= 0 })
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true).setIsBrowsable(false)
                .setArtworkUri(artwork.takeIf(String::isNotEmpty)?.let(Uri::parse))
                .setExtras(Bundle().apply {
                    putBoolean(EXPLICIT_BUNDLE_TAG, explicit)
                    albumId.takeIf(String::isNotEmpty)?.let { putString("albumId", it) }
                    putStringArrayList("artistNames", ArrayList(if (names.artist == artist) artistNames else listOf(names.artist).filter(String::isNotBlank)))
                    putStringArrayList("artistIds", ArrayList(if (names.artist == artist) artistIds else emptyList()))
                    putString("downloadSourceTitle", title)
                    putString("downloadSourceArtist", artist)
                    putStringArrayList("downloadSourceArtistNames", ArrayList(artistNames))
                    putStringArrayList("downloadSourceArtistIds", ArrayList(artistIds))
                    putBoolean("downloadSourceVideo", videoSource)
                }).build()
        ).build()

    fun json(): JSONObject = JSONObject().put("id", id).put("title", title)
        .put("artist", artist).put("artwork", artwork).put("explicit", explicit)
        .put("durationMs", durationMs).put("album", album).put("albumId", albumId)
        .put("artistNames", JSONArray(artistNames)).put("artistIds", JSONArray(artistIds))
        .put("videoSource", videoSource)

    companion object {
        fun from(item: MediaItem) = DownloadTrack(
            item.mediaId, item.mediaMetadata.extras?.getString("downloadSourceTitle") ?: item.mediaMetadata.title?.toString().orEmpty(),
            item.mediaMetadata.extras?.getString("downloadSourceArtist") ?: item.mediaMetadata.artist?.toString().orEmpty(),
            item.mediaMetadata.artworkUri?.toString().orEmpty(),
            item.mediaMetadata.extras?.getBoolean(EXPLICIT_BUNDLE_TAG) == true,
            item.mediaMetadata.durationMs ?: -1,
            item.mediaMetadata.albumTitle?.toString().orEmpty(),
            item.mediaMetadata.extras?.getString("albumId").orEmpty(),
            item.mediaMetadata.extras?.getStringArrayList("downloadSourceArtistNames") ?: item.mediaMetadata.extras?.getStringArrayList("artistNames").orEmpty(),
            item.mediaMetadata.extras?.getStringArrayList("downloadSourceArtistIds") ?: item.mediaMetadata.extras?.getStringArrayList("artistIds").orEmpty(),
            item.mediaMetadata.extras?.let { extras ->
                if (extras.containsKey("downloadSourceVideo")) extras.getBoolean("downloadSourceVideo")
                else !extras.getBoolean("isArtTrack") && (extras.getBoolean("downloadVideo") ||
                    extras.getBoolean("isVideo") || extras.containsKey("musicVideoType"))
            } == true,
        )
        fun from(json: JSONObject) = DownloadTrack(
            json.getString("id"), json.getString("title"), json.getString("artist"),
            json.optString("artwork"), json.optBoolean("explicit"),
            json.optLong("durationMs", -1), json.optString("album"), json.optString("albumId"),
            json.optJSONArray("artistNames").strings(), json.optJSONArray("artistIds").strings(),
            // The first local download builds did not persist the video flag. Their video
            // assets have channel mappings and no music album; keep this fallback JSON-only.
            if (json.has("videoSource")) json.optBoolean("videoSource") else
                json.optString("album").isBlank() && json.optString("albumId").isBlank() &&
                    json.optJSONArray("artistIds").strings().isNotEmpty(),
        )
        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map(::getString)
    }
}

data class DownloadPrompt(
    val tracks: List<DownloadTrack>,
    val video: Boolean = false,
    val copy: Boolean = false,
    val settings: Boolean = false,
    val preferredKind: DownloadKind? = null,
    val token: String = UUID.randomUUID().toString(),
)

data class SavedMedia(val track: DownloadTrack, val kind: DownloadKind, val extension: String, val size: Long)

/** UI requests converge here. WorkManager owns durable transfers; Media3 owns offline audio. */
@android.annotation.SuppressLint("StaticFieldLeak") // Only applicationContext is retained.
object DownloadCenter {
    lateinit var context: Context
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prompts = MutableStateFlow<List<DownloadPrompt>>(emptyList())
    val shownJob = MutableStateFlow<UUID?>(null)
    val hiddenStatusJobs = MutableStateFlow<Set<String>>(emptySet())
    val saved = MutableStateFlow<List<SavedMedia>>(emptyList())
    val removals = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val prefs get() = context.getSharedPreferences("kruxx_download_options", Context.MODE_PRIVATE)
    val mediaDirectory get() = File(context.filesDir, "offline_media").apply { mkdirs() }
    val jobDirectory get() = File(context.noBackupFilesDir, "file_download_jobs").apply { mkdirs() }
    val rememberChoice get() = prefs.getBoolean("remember", false)
    val alsoSaveFile get() = prefs.getBoolean("external", false)
    val defaultKind get() = runCatching { DownloadKind.valueOf(prefs.getString("kind", "ORIGINAL")!!) }
        .getOrDefault(DownloadKind.ORIGINAL)
    val defaultVideoKind get() = runCatching { DownloadKind.valueOf(prefs.getString("video_kind", "VIDEO")!!) }
        .getOrDefault(DownloadKind.VIDEO)
    val folder get() = prefs.getString("folder", null)?.let(Uri::parse) ?: SharedDownloads.destination

    fun initialize(context: Context) {
        this.context = context.applicationContext
        hiddenStatusJobs.value = prefs.getStringSet("hidden_status_jobs", emptySet()).orEmpty().toSet()
        val removed = runCatching { JSONObject(prefs.getString("removals", "{}")) }.getOrDefault(JSONObject())
        removals.value = removed.keys().asSequence().associateWith { removed.optInt(it) }
        val array = runCatching { JSONArray(prefs.getString("assets", "[]")) }.getOrDefault(JSONArray())
        saved.value = (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                SavedMedia(DownloadTrack.from(json), DownloadKind.valueOf(json.getString("kind")),
                    json.getString("extension"), json.getLong("size"))
            }.getOrNull()
        }.filter { file(it).let { f -> f.isFile && f.length() == it.size } }
    }

    fun remember(external: Boolean, kind: DownloadKind, enabled: Boolean, video: Boolean, folder: Uri?) {
        prefs.edit {
            putBoolean("remember", enabled)
            putBoolean("external", external)
            putString(if (video) "video_kind" else "kind", kind.name)
            if (folder != null) putString("folder", folder.toString())
        }
    }

    fun request(items: List<MediaItem>, video: Boolean = items.any {
        it.mediaMetadata.extras?.getBoolean("downloadVideo") == true ||
            it.mediaMetadata.extras?.getBoolean("isVideo") == true
    }, automatic: Boolean = false) {
        val tracks = items.filterNot { it.isLocal }.distinctBy { it.mediaId }.map(::trackFor)
        if (tracks.isEmpty()) return
        // Background actions cannot open a folder picker. Until a default is chosen they save
        // offline audio, just as before; a remembered destination applies to them too.
        if (automatic && !rememberChoice) {
            MyDownloadHelper.instance.addDownloadsInternal(tracks.map(DownloadTrack::mediaItem))
        } else if (rememberChoice) {
            submit(tracks, if (video) defaultVideoKind else defaultKind, alsoSaveFile, folder, showProgress = !automatic)
        } else {
            prompts.update { it + DownloadPrompt(tracks, video = video) }
        }
    }

    fun copy(items: List<MediaItem>, kind: DownloadKind? = null) {
        prompts.update { it + DownloadPrompt(items.distinctBy { it.mediaId }.map(::trackFor), copy = true, preferredKind = kind) }
    }

    /** Library Song records from earlier builds have no video-source extras. */
    internal fun trackFor(item: MediaItem): DownloadTrack {
        val track = DownloadTrack.from(item)
        val known = saved.value.firstOrNull { it.track.id == track.id }?.track
        return if (known != null && known.title == track.title && known.artist == track.artist)
            track.copy(videoSource = known.videoSource || track.videoSource) else track
    }

    fun settings() { prompts.update { it + DownloadPrompt(emptyList(), settings = true) } }
    fun dismiss(prompt: DownloadPrompt) { prompts.update { list -> list.filterNot { it.token == prompt.token } } }

    /** Hiding presentation never cancels work or removes its history or files. */
    @Synchronized
    fun hideStatus(id: UUID) {
        val hidden = hiddenStatusJobs.value + id.toString()
        prefs.edit { putStringSet("hidden_status_jobs", hidden) }
        hiddenStatusJobs.value = hidden
        if (shownJob.value == id) shownJob.value = null
    }

    fun isDownloaded(id: String): Boolean =
        MyDownloadHelper.instance.downloads.value[id]?.state == Download.STATE_COMPLETED ||
            saved.value.any { it.track.id == id }

    fun file(asset: SavedMedia): File = File(mediaDirectory, "${key(asset.track.id)}-${asset.kind.name.lowercase()}.${asset.extension}")
    fun asset(id: String, kind: DownloadKind): SavedMedia? =
        saved.value.firstOrNull { it.track.id == id && it.kind == kind && file(it).let { f -> f.isFile && f.length() == it.size } }

    @Synchronized
    @android.annotation.SuppressLint("UseKtx") // Must check commit success before publishing the asset.
    fun register(asset: SavedMedia) {
        val next = saved.value.filterNot { it.track.id == asset.track.id && it.kind == asset.kind } + asset
        check(prefs.edit().putString("assets", JSONArray(next.map {
            it.track.json().put("kind", it.kind.name).put("extension", it.extension).put("size", it.size)
        }).toString()).commit())
        saved.value = next
    }

    @Synchronized
    internal fun invalidateDownload(id: String) {
        removals.update { it + (id to (generation(id) + 1)) }
        prefs.edit { putString("removals", JSONObject(removals.value).toString()) }
    }

    /** Remove exactly this conversion; other formats and Media3 audio have separate entries. */
    @Synchronized
    internal fun removeAsset(asset: SavedMedia) {
        val current = saved.value.firstOrNull { it.track.id == asset.track.id && it.kind == asset.kind } ?: return
        if (current != asset) throw DownloadFileChangedException()
        val target = file(asset)
        require(target.canonicalFile.parentFile == mediaDirectory.canonicalFile)
        if (target.exists()) {
            if (!target.isFile || target.length() != asset.size) throw DownloadFileChangedException()
            check(target.delete()) { "Cannot delete app download" }
        }
        invalidateDownload(asset.track.id)
        saved.value = saved.value - asset
        prefs.edit(commit = true) {
            putString("assets", JSONArray(saved.value.map {
                it.track.json().put("kind", it.kind.name).put("extension", it.extension).put("size", it.size)
            }).toString())
        }
    }

    @Synchronized
    fun removeFiles(id: String) {
        invalidateDownload(id)
        scope.launch(Dispatchers.IO) {
            synchronized(this@DownloadCenter) {
                saved.value.filter { it.track.id == id }.forEach { file(it).delete() }
                saved.value = saved.value.filterNot { it.track.id == id }
                prefs.edit(commit = true) {
                    putString("assets", JSONArray(saved.value.map {
                        it.track.json().put("kind", it.kind.name).put("extension", it.extension).put("size", it.size)
                    }).toString())
                }
            }
        }
    }

    fun generation(id: String): Int = removals.value[id] ?: 0

    fun submit(tracks: List<DownloadTrack>, kind: DownloadKind, external: Boolean, destination: Uri?, copy: Boolean = false, showProgress: Boolean = true) {
        if (tracks.isEmpty()) return
        scope.launch {
          try {
            require(!(external || copy) || destination != null) { "Missing destination" }
            val token = UUID.randomUUID().toString()
            val payload = JSONObject().put("tracks", JSONArray(tracks.map {
                it.json().put("generation", generation(it.id))
            }))
                .put("kind", kind.name).put("folder", if (external || copy) destination?.toString().orEmpty() else "")
                .put("copy", copy)
            withContext(Dispatchers.IO) {
                val atomic = AtomicFile(File(jobDirectory, "$token.json"))
                val stream = atomic.startWrite()
                try {
                    stream.write(payload.toString().toByteArray())
                    atomic.finishWrite(stream)
                } catch (e: Exception) {
                    atomic.failWrite(stream)
                    throw e
                }
            }
            val request = OneTimeWorkRequestBuilder<FileDownloadWorker>()
                .setInputData(workDataOf("payload" to token, "title" to tracks.first().names.label.take(300),
                    "total" to tracks.size, "kind" to kind.name,
                    "destination" to destinationLabel(kind, if (external || copy) destination else null)))
                .addTag(WORK_TAG)
                .addTag("kruxx_payload:$token").addTag("kruxx_created:${System.currentTimeMillis()}")
                .apply {
                    if (!copy && tracks.any { track ->
                        asset(track.id, kind) == null && (kind == DownloadKind.VIDEO ||
                            MyDownloadHelper.instance.downloads.value[track.id]?.state != Download.STATE_COMPLETED)
                    }) setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$WORK_TAG/$token", ExistingWorkPolicy.KEEP, request)
            if (showProgress) shownJob.value = request.id
          } catch (e: CancellationException) { throw e }
          catch (_: Exception) { me.knighthat.utils.Toaster.e(app.kreate.android.R.string.kruxx_files_failed) }
        }
    }

    internal fun destinationLabel(kind: DownloadKind, folder: Uri?): String = when (folder) {
        null -> context.getString(app.kreate.android.R.string.kruxx_download_in_app)
        SharedDownloads.destination -> SharedDownloads.relativePath +
            if (kind == DownloadKind.VIDEO) SharedDownloads.VIDEO else SharedDownloads.AUDIO
        else -> folder.lastPathSegment.orEmpty()
    }

    const val WORK_TAG = "kruxx_file_downloads"
    fun key(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

/** Explicit video-download intent survives menus without changing normal audio queue behavior. */
fun MediaItem.forVideoDownload(): MediaItem = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putBoolean("downloadVideo", true)
    }).build()
).build()
