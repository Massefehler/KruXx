@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.work.*
import app.kreate.android.R
import it.fast4x.rimusic.MainActivity
import it.fast4x.rimusic.service.MyDownloadHelper
import kotlinx.coroutines.*
import android.os.SystemClock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File

/** Persisted batch input stays outside WorkManager's 10 KiB Data limit, even for large playlists. */
class FileDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private var latest = workDataOf("percent" to 0, "phase" to DownloadStep.WAITING.name)
    private var lastPublishedAt = 0L
    private val notificationId get() = NOTIFICATION + (id.hashCode() and 0x3fffffff)

    override suspend fun doWork(): Result = slots.withPermit { runBatch() }

    private suspend fun report(data: Data, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val percent = maxOf(latest.getInt("percent", 0), data.getInt("percent", 0)).coerceAtMost(99)
        val phaseChanged = data.getString("phase") != latest.getString("phase") ||
            data.getInt("current", 0) != latest.getInt("current", 0)
        if (!force && !phaseChanged && (percent == latest.getInt("percent", 0) || now - lastPublishedAt < 250)) return
        latest = Data.Builder().putAll(data).putInt("percent", percent).build()
        lastPublishedAt = now
        setProgress(latest)
        val phase = DownloadStep.valueOf(latest.getString("phase") ?: DownloadStep.WAITING.name)
        setForeground(foreground("$percent % · ${applicationContext.getString(phase.label)} · ${latest.getString("title").orEmpty()}", percent))
    }

    private suspend fun awaitAudio(track: DownloadTrack, generation: Int, onProgress: FileProgress) {
        withTimeout(2 * 60 * 60 * 1000L) {
            while (true) {
                check(DownloadCenter.generation(track.id) == generation) { "Download removed" }
                val download = withContext(Dispatchers.Main) {
                    MyDownloadHelper.instance.downloads.value[track.id]
                }
                if (download != null) when (download.state) {
                    Download.STATE_COMPLETED -> { onProgress(DownloadStep.DOWNLOADING, 1.0); return@withTimeout }
                    Download.STATE_FAILED, Download.STATE_REMOVING -> error("Audio download failed")
                    Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING,
                    Download.STATE_STOPPED -> Unit
                }
                onProgress(DownloadStep.DOWNLOADING, download?.percentDownloaded?.takeIf { it >= 0 }?.div(100.0) ?: 0.0)
                delay(350)
            }
        }
    }

    private suspend fun runBatch(): Result = withContext(Dispatchers.IO) {
        val token = inputData.getString("payload")?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
            ?: return@withContext Result.failure()
        val payloadFile = File(DownloadCenter.jobDirectory, "$token.json")
        val scratch = File(DownloadCenter.jobDirectory, "$token-work").apply { mkdirs() }
        val marker = File(DownloadCenter.jobDirectory, "$token-target")
        var completed = 0
        var skipped = 0
        val failures = mutableListOf<String>()
        try {
            report(Data.Builder().putAll(inputData).putString("phase", DownloadStep.WAITING.name).build(), true)
            OfflineFiles.cleanInterruptedExport(applicationContext, marker)
            val payload = JSONObject(payloadFile.readText())
            val array = payload.getJSONArray("tracks")
            val tracks = (0 until array.length()).map { DownloadTrack.from(array.getJSONObject(it)) }
            val generations = (0 until array.length()).associate {
                array.getJSONObject(it).getString("id") to array.getJSONObject(it).optInt("generation")
            }
            val kind = DownloadKind.valueOf(payload.getString("kind"))
            val copy = payload.getBoolean("copy")
            val folder = payload.optString("folder").takeIf(String::isNotBlank)?.let(Uri::parse)
            val destination = DownloadCenter.destinationLabel(kind, folder)
            if (!copy) withContext(Dispatchers.Main) {
                MyDownloadHelper.instance.addDownloadsInternal(tracks.filter {
                    DownloadCenter.generation(it.id) == generations[it.id]
                }.map(DownloadTrack::mediaItem))
            }
            for ((index, track) in tracks.withIndex()) {
                currentCoroutineContext().ensureActive()
                val existing = DownloadCenter.asset(track.id, kind)
                val plan = FileProgressPlan(kind, existing != null, copy, folder != null)
                var phase = DownloadStep.WAITING
                val onProgress: FileProgress = { step, fraction ->
                    phase = step
                    report(workDataOf("title" to track.names.label.take(300), "current" to index + 1, "total" to tracks.size,
                        "kind" to kind.name, "destination" to destination, "phase" to step.name,
                        "percent" to batchPercent(index, tracks.size, plan.fraction(step, fraction))))
                }
                try {
                    check(DownloadCenter.generation(track.id) == generations[track.id]) { "Download removed" }
                    onProgress(plan.steps.firstOrNull() ?: DownloadStep.PREPARING, 0.0)
                    if (existing == null && !copy) awaitAudio(track, generations.getValue(track.id), onProgress)
                    if (kind == DownloadKind.ORIGINAL && folder == null) {
                        // Internal originals are complete in Media3: no redundant file snapshot.
                        completed++
                        continue
                    }
                    val (file, extension) = if (existing != null) {
                        val source = DownloadCenter.file(existing)
                        val updated = File(scratch, "retagged.mp3")
                        if (kind == DownloadKind.MP3 && Mp3Tags.copyWithUpdatedNames(source, updated, track.names) {
                                onProgress(DownloadStep.PREPARING, it)
                            }) retain(track, kind, updated, "mp3", generations.getValue(track.id))
                        else source to existing.extension
                    } else {
                        check(!copy || kind != DownloadKind.VIDEO) { "Video is not downloaded" }
                        onProgress(DownloadStep.PREPARING, 0.0)
                        val audio = File(scratch, "audio-source")
                        val sourceExtension = OfflineFiles.snapshot(track.id, audio) { onProgress(DownloadStep.PREPARING, it) }
                        when (kind) {
                            DownloadKind.ORIGINAL -> audio to sourceExtension
                            DownloadKind.MP3 -> {
                                onProgress(DownloadStep.CONVERTING, 0.0)
                                val rendered = File(scratch, "rendered.mp3")
                                Mp3Encoder.convert(audio, rendered, track) { onProgress(DownloadStep.CONVERTING, it) }
                                retain(track, kind, rendered, "mp3", generations.getValue(track.id))
                            }
                            DownloadKind.VIDEO -> {
                                val (rendered, ext) = OfflineFiles.video(track, audio, scratch, onProgress)
                                retain(track, kind, rendered, ext, generations.getValue(track.id))
                            }
                        }
                    }
                    if (folder != null) {
                        check(DownloadCenter.generation(track.id) == generations[track.id]) { "Download removed" }
                        val mime = when (extension) {
                            "mp3" -> "audio/mpeg"
                            "m4a" -> "audio/mp4"
                            "mp4" -> "video/mp4"
                            "ogg" -> "audio/ogg"
                            else -> if (kind == DownloadKind.VIDEO) "video/webm" else "audio/webm"
                        }
                        if (OfflineFiles.export(applicationContext, folder, file,
                                OfflineFiles.fileName(track, extension), mime, marker, onProgress)) completed++ else skipped++
                    } else completed++
                } catch (_: TimeoutCancellationException) {
                    failures += "${track.names.label.take(100)} · ${applicationContext.getString(phase.label)}"
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Persist the failed step, never CDN URLs, cookies or provider diagnostics.
                    failures += "${track.names.label.take(100)} · ${applicationContext.getString(phase.label)}"
                } finally {
                    scratch.listFiles()?.forEach { it.delete() }
                }
            }
            val result = Data.Builder().putAll(latest).putInt("completed", completed).putInt("skipped", skipped)
                .putInt("failed", failures.size).putString("errors", failures.take(10).joinToString("\n"))
                .putString("payload", token).putString("phase", if (failures.isEmpty()) DownloadStep.FINISHED.name else DownloadStep.ERROR.name)
                .putInt("percent", if (failures.isEmpty()) 100 else latest.getInt("percent", 0)).build()
            if (failures.isEmpty()) payloadFile.delete()
            notifyFinished(completed, skipped, failures.size)
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notifyFinished(completed, skipped, 1)
            Result.success(Data.Builder().putAll(latest).putInt("failed", 1)
                .putString("errors", applicationContext.getString(R.string.kruxx_files_failed))
                .putInt("completed", completed).putInt("skipped", skipped).putString("payload", token).build())
        } finally {
            scratch.deleteRecursively()
            OfflineFiles.cleanInterruptedExport(applicationContext, marker)
        }
    }

    private fun notifyFinished(completed: Int, skipped: Int, failed: Int) {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).putExtra("kruxxDownloadJobs", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.download).setContentTitle(applicationContext.getString(R.string.kruxx_files_title))
            .setContentText(applicationContext.getString(if (failed > 0) R.string.kruxx_files_failed
                else R.string.kruxx_files_result, completed, skipped))
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                applicationContext.getString(if (failed > 0) R.string.kruxx_files_failed else R.string.kruxx_progress_done) +
                    " · " + latest.getString("destination").orEmpty()))
            .setAutoCancel(true).setContentIntent(open).build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notificationId xor 0x40000000, notification)
    }

    private fun retain(track: DownloadTrack, kind: DownloadKind, rendered: File, extension: String, generation: Int): Pair<File, String> = synchronized(DownloadCenter) {
        check(DownloadCenter.generation(track.id) == generation) { "Download removed" }
        check(rendered.length() > 0)
        val asset = SavedMedia(track, kind, extension, rendered.length())
        val target = DownloadCenter.file(asset)
        check(rendered.renameTo(target)) { "Cannot store rendered media" }
        DownloadCenter.register(asset)
        target to extension
    }

    private fun foreground(text: String, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.kruxx_files_title), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).putExtra("kruxxDownloadJobs", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.download).setContentTitle(applicationContext.getString(R.string.kruxx_files_title))
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100, percent, false)
            .addAction(0, applicationContext.getString(android.R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    companion object {
        private val slots = Semaphore(1)
        private const val CHANNEL = "kruxx_file_downloads"
        private const val NOTIFICATION = 7204
    }
}
