@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.ThemedAlertDialog
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.utils.removeDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.utils.Toaster

internal sealed interface DownloadRemovalTarget {
    val key: String
    val title: String
    fun location(context: Context): String

    data class Public(val file: DownloadFile) : DownloadRemovalTarget {
        override val key = file.uri.toString()
        override val title = file.name
        override fun location(context: Context) = file.path
    }
    data class Asset(val asset: SavedMedia) : DownloadRemovalTarget {
        override val key = "asset/${asset.track.id}/${asset.kind}"
        override val title = asset.track.names.label
        override fun location(context: Context) = context.getString(asset.kind.label)
    }
    data class Original(val track: DownloadTrack) : DownloadRemovalTarget {
        override val key = "original/${track.id}"
        override val title = track.names.label
        override fun location(context: Context) = context.getString(R.string.kruxx_format_original)
    }
}

internal enum class RemovalProblem { ACCESS, CHANGED, OTHER }
internal data class RemovalFailure(val target: DownloadRemovalTarget, val problem: RemovalProblem)
internal data class DownloadRemovalState(
    val targets: List<DownloadRemovalTarget>,
    val running: Boolean = false,
    val completed: Int = 0,
    val removed: Int = 0,
    val failures: List<RemovalFailure> = emptyList(),
    val finished: Boolean = false,
)

/** A confirmed snapshot survives rotation and tab changes; partial failures stay reviewable. */
internal class DownloadRemovalViewModel : ViewModel() {
    val state = MutableStateFlow<DownloadRemovalState?>(null)
    var revision by mutableIntStateOf(0)
        private set

    fun request(targets: List<DownloadRemovalTarget>) {
        if (state.value?.running != true && targets.isNotEmpty())
            state.value = DownloadRemovalState(targets.distinctBy { it.key })
    }

    fun dismiss() { if (state.value?.running != true) state.value = null }

    fun retry(context: Context) {
        val failed = state.value?.failures.orEmpty().map { it.target }
        if (failed.isNotEmpty()) { request(failed); confirm(context) }
    }

    fun confirm(context: Context) {
        val snapshot = state.value ?: return
        if (snapshot.running || snapshot.finished) return
        val app = context.applicationContext
        state.value = snapshot.copy(running = true)
        viewModelScope.launch {
            for (target in snapshot.targets) {
                val failure = try {
                    withContext(Dispatchers.IO) { remove(app, target) }
                    null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    RemovalFailure(target, when (e) {
                        is SecurityException -> RemovalProblem.ACCESS
                        is DownloadFileChangedException -> RemovalProblem.CHANGED
                        else -> RemovalProblem.OTHER
                    })
                }
                state.value = state.value?.let {
                    it.copy(completed = it.completed + 1, removed = it.removed + if (failure == null) 1 else 0,
                        failures = it.failures + listOfNotNull(failure))
                }
            }
            state.value = state.value?.copy(running = false, finished = true)
            revision++
        }
    }

    private suspend fun remove(context: Context, target: DownloadRemovalTarget) {
        when (target) {
            is DownloadRemovalTarget.Public -> SharedDownloads.remove(context, target.file)
            is DownloadRemovalTarget.Asset -> DownloadCenter.removeAsset(target.asset)
            is DownloadRemovalTarget.Original -> {
                val downloads = MyDownloadHelper.instance.downloads
                if (target.track.id !in downloads.value) return
                DownloadCenter.invalidateDownload(target.track.id)
                // The library's track-wide helper also removes converted assets. Here each
                // format is selected separately, so send only the Media3 removal command.
                context.removeDownload<MyDownloadService>(target.track.id).getOrThrow()
                check(withTimeoutOrNull(30_000) { downloads.first { target.track.id !in it } } != null) {
                    "Audio removal has not completed"
                }
            }
        }
    }
}

@Composable
internal fun DownloadRemovalDialog(model: DownloadRemovalViewModel, context: Context) {
    val state by model.state.collectAsState()
    val current = state ?: return
    val public = current.targets.all { it is DownloadRemovalTarget.Public }
    val accent = if (isKruxxGlassEnabled) KruxxGlass.electricBlue else colorPalette().accent
    val removeColor = if (isKruxxGlassEnabled) KruxxGlass.signalRed else MaterialTheme.colorScheme.error
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.confirm(context) else Toaster.e(R.string.kruxx_remove_access_failed)
    }
    val folderPermission = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                require(SharedDownloads.acceptsTree(uri))
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.onSuccess { model.retry(context) }
                .onFailure { Toaster.e(R.string.kruxx_remove_choose_folder) }
        }
    }
    ThemedAlertDialog(
        onDismissRequest = model::dismiss,
        title = { Text(stringResource(when {
            current.running -> R.string.kruxx_remove_running
            current.finished -> R.string.kruxx_remove_result
            else -> R.string.kruxx_remove_title
        })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (public) R.string.kruxx_folder_default else R.string.kruxx_download_in_app),
                    style = MaterialTheme.typography.titleSmall)
                when {
                    current.running -> {
                        LinearProgressIndicator(progress = { current.completed.toFloat() / current.targets.size },
                            modifier = Modifier.fillMaxWidth(), color = accent)
                        Text(stringResource(R.string.kruxx_remove_progress, current.completed, current.targets.size))
                    }
                    current.finished -> {
                        Text(pluralStringResource(R.plurals.kruxx_remove_success, current.removed, current.removed))
                        if (current.failures.isNotEmpty()) Text(pluralStringResource(R.plurals.kruxx_remove_failed,
                            current.failures.size, current.failures.size))
                    }
                    else -> Text(pluralStringResource(R.plurals.kruxx_remove_confirm, current.targets.size, current.targets.size))
                }
                val listed = if (current.finished) current.failures.map { it.target } else current.targets
                if (listed.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(listed, key = { it.key }) { target ->
                        Column {
                            Text(target.title, style = MaterialTheme.typography.bodyMedium)
                            Text(target.location(context), style = MaterialTheme.typography.bodySmall,
                                color = colorPalette().textSecondary)
                            current.failures.firstOrNull { it.target.key == target.key }?.let { failure ->
                                Text(stringResource(when (failure.problem) {
                                    RemovalProblem.ACCESS -> R.string.kruxx_remove_access_failed
                                    RemovalProblem.CHANGED -> R.string.kruxx_remove_changed
                                    RemovalProblem.OTHER -> R.string.kruxx_remove_error
                                }), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (!current.finished && !current.running)
                    Text(stringResource(if (public) R.string.kruxx_remove_public_note else R.string.kruxx_remove_app_note),
                        style = MaterialTheme.typography.bodySmall, color = colorPalette().textSecondary)
                if (public && current.finished && Build.VERSION.SDK_INT >= 29 && current.failures.any { it.problem == RemovalProblem.ACCESS })
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = accent), onClick = {
                        folderPermission.launch(DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                            "primary:${SharedDownloads.relativePath.removeSuffix("/")}"))
                    }) { Text(stringResource(R.string.kruxx_downloads_allow_access)) }
            }
        },
        confirmButton = {
            if (!current.running) TextButton(onClick = {
                if (current.finished) model.dismiss()
                else if (public && Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else model.confirm(context)
            }) { Text(stringResource(if (current.finished) R.string.done else R.string.kruxx_remove_action),
                color = if (current.finished) colorPalette().text else removeColor) }
        },
        dismissButton = {
            if (!current.running && (!current.finished || current.failures.isNotEmpty())) TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = accent), onClick = {
                if (current.finished && current.failures.isNotEmpty()) model.retry(context) else model.dismiss()
            }) {
                Text(stringResource(if (current.finished) R.string.kruxx_files_retry else android.R.string.cancel))
            }
        },
    )
}
