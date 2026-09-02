package me.knighthat.updater

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.kreate.android.R
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.knighthat.component.dialog.InteractiveDialog
import java.io.File

internal fun updateApkDirectory(context: Context): File? =
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

internal fun updateApkFile(context: Context, name: String): File? =
    updateApkDirectory(context)?.resolve(name)

object DownloadAndInstallDialog : InteractiveDialog {
    private enum class Stage { INITIALIZING, DOWNLOADING, VERIFYING, READY, PERMISSION, INSTALLING, ERROR }

    override val dialogTitle: String
        @Composable get() = stringResource(R.string.word_updating)

    override var isActive: Boolean by mutableStateOf(false)
    private var stage by mutableStateOf(Stage.INITIALIZING)
    private var progress by mutableFloatStateOf(0f)
    private var runId by mutableIntStateOf(0)
    private var errorMessageId by mutableStateOf<Int?>(null)

    @Volatile private var activeDownloadId: Long? = null
    @Volatile private var activeDownloadManager: DownloadManager? = null
    @Volatile private var activeFile: File? = null

    fun start() {
        cancelDownload(deleteFile = true)
        stage = Stage.INITIALIZING
        progress = 0f
        errorMessageId = null
        runId++
        showDialog()
    }

    override fun hideDialog() {
        cancelDownload(deleteFile = stage == Stage.DOWNLOADING || stage == Stage.VERIFYING)
        super.hideDialog()
    }

    private fun cancelDownload(deleteFile: Boolean) {
        activeDownloadId?.let { activeDownloadManager?.remove(it) }
        activeDownloadId = null
        activeDownloadManager = null
        if(deleteFile) activeFile?.delete()
        activeFile = null
    }

    private fun fail(@StringRes messageId: Int) {
        errorMessageId = messageId
        stage = Stage.ERROR
    }

    private suspend fun download(context: Context, apkFile: File, onProgress: (Float) -> Unit) {
        val manager = context.getSystemService<DownloadManager>()
            ?: throw UpdateDownloadException(R.string.error_download_manager_init_failed)
        apkFile.parentFile?.mkdirs()
        if(apkFile.exists() && !apkFile.delete())
            throw UpdateDownloadException(R.string.kruxx_update_file_cleanup_failed)

        val request = DownloadManager.Request(Updater.build.downloadUrl.toUri())
            .setDestinationUri(Uri.fromFile(apkFile))
            .setMimeType(APK_MIME_TYPE)
            .setTitle(Updater.build.name)
            .setDescription(context.getString(R.string.kruxx_update_download_description))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val downloadId = manager.enqueue(request)
        activeDownloadManager = manager
        activeDownloadId = downloadId
        activeFile = apkFile

        val query = DownloadManager.Query().setFilterById(downloadId)
        while(true) {
            val cursor = manager.query(query)
            if(cursor == null) throw UpdateDownloadException(R.string.kruxx_update_download_failed)
            cursor.use {
                if(!it.moveToFirst()) throw UpdateDownloadException(R.string.kruxx_update_download_failed)
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if(total > 0L) onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))

                when(it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        onProgress(1f)
                        activeDownloadId = null
                        activeDownloadManager = null
                        return
                    }
                    DownloadManager.STATUS_FAILED ->
                        throw UpdateDownloadException(R.string.kruxx_update_download_failed)
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> Unit
                    else -> throw UpdateDownloadException(R.string.kruxx_update_download_failed)
                }
            }
            delay(250)
        }
    }

    private fun openInstaller(context: Context, apkFile: File) {
        if(!apkFile.isFile) {
            fail(R.string.error_downloaded_file_not_found)
            return
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            stage = Stage.PERMISSION
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { fail(R.string.kruxx_update_permission_settings_failed) }
            return
        }

        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.startActivity(installIntent) }
            .onSuccess { stage = Stage.INSTALLING }
            .onFailure { fail(R.string.kruxx_update_installer_failed) }
    }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val apkFile = updateApkFile(context, Updater.build.name)

        LaunchedEffect(runId, isActive, apkFile) {
            if(!isActive) return@LaunchedEffect
            if(apkFile == null) {
                fail(R.string.kruxx_update_storage_unavailable)
                return@LaunchedEffect
            }
            try {
                stage = Stage.DOWNLOADING
                withContext(Dispatchers.IO) { download(context, apkFile) { progress = it } }
                stage = Stage.VERIFYING
                val verification = withContext(Dispatchers.IO) {
                    ApkVerifier.verify(context, apkFile, Updater.release.tagName, Updater.build.digest)
                }
                when(verification) {
                    ApkVerificationResult.Success -> {
                        stage = Stage.READY
                        openInstaller(context, apkFile)
                    }
                    is ApkVerificationResult.Failure -> fail(verification.messageId)
                }
            } catch(error: UpdateDownloadException) {
                fail(error.messageId)
            } catch(cancelled: CancellationException) {
                throw cancelled
            } catch(error: Exception) {
                Logger.e(error, "KruXxUpdater") { "Update download or verification failed" }
                fail(R.string.kruxx_update_download_failed)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(.9f)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                color = if(stage == Stage.ERROR) colorPalette().red else colorPalette().accent
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                text = stringResource(
                    when(stage) {
                        Stage.INITIALIZING -> R.string.update_dialog_init_status
                        Stage.DOWNLOADING -> R.string.update_dialog_downloading_status
                        Stage.VERIFYING -> R.string.update_dialog_verifying_status
                        Stage.READY -> R.string.kruxx_update_ready
                        Stage.PERMISSION -> R.string.kruxx_update_permission_required
                        Stage.INSTALLING -> R.string.kruxx_update_installer_opened
                        Stage.ERROR -> errorMessageId ?: R.string.update_dialog_error_status
                    }
                ),
                style = typography().s.copy(color = colorPalette().text),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }

    @Composable
    override fun Buttons() {
        val context = LocalContext.current
        val file = activeFile ?: updateApkFile(context, Updater.build.name)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(.9f)
        ) {
            when(stage) {
                Stage.PERMISSION, Stage.READY -> SecondaryTextButton(
                    text = stringResource(R.string.kruxx_update_install),
                    onClick = {
                        if(file == null) fail(R.string.kruxx_update_storage_unavailable)
                        else openInstaller(context, file)
                    }
                )
                Stage.ERROR -> SecondaryTextButton(
                    text = stringResource(R.string.kruxx_update_retry),
                    onClick = ::start
                )
                else -> Unit
            }
            SecondaryTextButton(
                text = stringResource(if(stage == Stage.DOWNLOADING || stage == Stage.VERIFYING) android.R.string.cancel else R.string.kruxx_update_close),
                onClick = ::hideDialog,
                alternative = true
            )
        }
    }

    private class UpdateDownloadException(@StringRes val messageId: Int) : Exception()

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
