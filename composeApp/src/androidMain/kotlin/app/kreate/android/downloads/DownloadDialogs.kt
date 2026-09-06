@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.work.*
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.android.service.isDownloadPending
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.ThemedAlertDialog
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.playVideo
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.getDownloadState
import me.knighthat.utils.Toaster
import java.io.File

@Composable
fun DownloadDialogs(player: Player) {
    if (!BuildConfig.INDEPENDENT_FORK) return
    val prompts by DownloadCenter.prompts.collectAsState()
    val shownJob by DownloadCenter.shownJob.collectAsState()
    val prompt = prompts.firstOrNull()
    if (prompt == null) {
        shownJob?.let { DownloadProgressDialog(it) }
        return
    }
    key(prompt.token) {
        if (prompt.settings) DownloadSettingsDialog(prompt, player) else DownloadChoiceDialog(prompt)
    }
}

@Composable
private fun DownloadChoiceDialog(prompt: DownloadPrompt) {
    val context = LocalContext.current
    val assets by DownloadCenter.saved.collectAsState()
    val downloads by MyDownloadHelper.instance.downloads.collectAsState()
    var kindName by rememberSaveable { mutableStateOf(
        (prompt.preferredKind ?: if (prompt.video) DownloadCenter.defaultVideoKind else DownloadCenter.defaultKind).name) }
    val kind = DownloadKind.valueOf(kindName)
    var external by rememberSaveable { mutableStateOf(prompt.copy || DownloadCenter.alsoSaveFile) }
    var rememberChoice by rememberSaveable { mutableStateOf(DownloadCenter.rememberChoice) }
    var defaultFolder by rememberSaveable { mutableStateOf(DownloadCenter.folder == SharedDownloads.destination) }
    var folderString by rememberSaveable { mutableStateOf(DownloadCenter.folder.takeUnless { it == SharedDownloads.destination }?.toString()) }
    var startAfterPicker by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(prompt.tracks.map { it.id }) }
    val available = remember(prompt, kind, downloads, assets) {
        if (!prompt.copy) prompt.tracks else prompt.tracks.filter { track ->
            when (kind) {
                DownloadKind.VIDEO -> assets.any { it.track.id == track.id && it.kind == kind }
                DownloadKind.MP3 -> assets.any { it.track.id == track.id && it.kind == kind } ||
                    downloads[track.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
                DownloadKind.ORIGINAL -> downloads[track.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
            }
        }
    }
    val selected = available.filter { it.id in selectedIds }
    fun submit(uri: Uri?) {
        if (!prompt.copy) DownloadCenter.remember(external, kind, rememberChoice, prompt.video, uri)
        DownloadCenter.submit(selected, kind, external, uri, copy = prompt.copy)
        DownloadCenter.dismiss(prompt)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) submit(SharedDownloads.destination) else Toaster.e(R.string.kruxx_folder_permission)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.onSuccess {
                folderString = uri.toString()
                defaultFolder = false
                if (startAfterPicker) submit(uri)
            }.onFailure { Toaster.e(R.string.kruxx_folder_failed) }
        }
    }
    ThemedAlertDialog(
        onDismissRequest = { DownloadCenter.dismiss(prompt) },
        title = { Text(stringResource(if (prompt.copy) R.string.kruxx_copy_files else R.string.kruxx_download_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.kruxx_download_count, selected.size))
                val kinds = if (prompt.video) listOf(DownloadKind.VIDEO, DownloadKind.MP3)
                    else if (prompt.copy && assets.any { a -> a.kind == DownloadKind.VIDEO && prompt.tracks.any { it.id == a.track.id } })
                        DownloadKind.entries
                    else listOf(DownloadKind.ORIGINAL, DownloadKind.MP3)
                kinds.forEach { value ->
                    OptionRow(stringResource(value.label), kind == value) { kindName = value.name }
                }
                if (kind == DownloadKind.MP3) Text(stringResource(R.string.kruxx_mp3_note), style = MaterialTheme.typography.bodySmall)
                if (!prompt.copy) {
                    HorizontalDivider()
                    OptionRow(stringResource(R.string.kruxx_download_in_app), !external) { external = false }
                }
                OptionRow(stringResource(if (prompt.copy) R.string.kruxx_folder_default else R.string.kruxx_download_default_folder),
                    external && defaultFolder) { external = true; defaultFolder = true }
                OptionRow(stringResource(if (prompt.copy) R.string.kruxx_folder_other else R.string.kruxx_download_other_folder),
                    external && !defaultFolder) { external = true; defaultFolder = false }
                if (external && defaultFolder) Text(stringResource(R.string.kruxx_folder_default_note), style = MaterialTheme.typography.bodySmall)
                if (!prompt.copy) {
                    Row(Modifier.fillMaxWidth().clickable { rememberChoice = !rememberChoice }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                        Text(stringResource(R.string.kruxx_download_remember), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(stringResource(R.string.kruxx_copy_note), style = MaterialTheme.typography.bodySmall)
                    if (available.isEmpty()) Text(stringResource(R.string.kruxx_copy_empty))
                    else {
                        TextButton(onClick = {
                            selectedIds = if (selected.size == available.size) emptyList() else available.map { it.id }
                        }) { Text(stringResource(R.string.kruxx_select_all_downloads)) }
                        LazyColumn(Modifier.heightIn(max = 230.dp)) {
                            items(available, key = { it.id }) { track ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    selectedIds = if (track.id in selectedIds) selectedIds - track.id else selectedIds + track.id
                                }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(track.id in selectedIds, onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + track.id else selectedIds - track.id
                                    })
                                    Column {
                                        Text(track.names.label, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
                if (external && !defaultFolder && folderString != null) {
                    Text(folderString?.let(Uri::parse)?.lastPathSegment.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        startAfterPicker = false
                        picker.launch(folderString?.let(Uri::parse))
                    }) {
                        Text(stringResource(R.string.kruxx_folder_change))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                if (external && !defaultFolder && folderString == null) {
                    startAfterPicker = true
                    picker.launch(folderString?.let(Uri::parse))
                }
                else if (external && defaultFolder && Build.VERSION.SDK_INT < 29 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else submit(if (!external) null else if (defaultFolder) SharedDownloads.destination else folderString?.let(Uri::parse))
            }) { Text(stringResource(if (external && !defaultFolder && folderString == null) R.string.kruxx_folder_choose else R.string.kruxx_download_start)) }
        },
        dismissButton = { TextButton(onClick = { DownloadCenter.dismiss(prompt) }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = onClick)
        Text(label)
    }
}

val DownloadKind.label: Int get() = when (this) {
    DownloadKind.ORIGINAL -> R.string.kruxx_format_original
    DownloadKind.MP3 -> R.string.kruxx_format_mp3
    DownloadKind.VIDEO -> R.string.kruxx_format_video
}

@Composable
private fun DownloadSettingsDialog(prompt: DownloadPrompt, player: Player) {
    val context = LocalContext.current
    val manager = remember(context) { WorkManager.getInstance(context) }
    val work by remember(manager) { manager.getWorkInfosByTagFlow(DownloadCenter.WORK_TAG) }.collectAsState(emptyList())
    val assets by DownloadCenter.saved.collectAsState()
    var remembered by remember { mutableStateOf(DownloadCenter.rememberChoice) }
    var destination by remember { mutableStateOf(DownloadCenter.folder) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            DownloadCenter.remember(DownloadCenter.alsoSaveFile, DownloadCenter.defaultKind, remembered, false, uri)
            destination = uri
        }.onFailure { Toaster.e(R.string.kruxx_folder_failed) }
    }
    ThemedAlertDialog(
        onDismissRequest = { DownloadCenter.dismiss(prompt) },
        title = { Text(stringResource(R.string.kruxx_download_settings)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(if (!remembered) stringResource(R.string.kruxx_download_always_ask)
                        else stringResource(if (DownloadCenter.alsoSaveFile) R.string.kruxx_download_also_file else R.string.kruxx_download_in_app))
                    if (remembered) TextButton(onClick = {
                        DownloadCenter.remember(DownloadCenter.alsoSaveFile, DownloadCenter.defaultKind, false, false, null)
                        remembered = false
                    }) { Text(stringResource(R.string.kruxx_download_reset)) }
                    Text(stringResource(R.string.kruxx_download_settings_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(DownloadCenter.defaultKind.label) + " / " +
                        stringResource(DownloadCenter.defaultVideoKind.label), style = MaterialTheme.typography.bodySmall)
                    OptionRow(stringResource(R.string.kruxx_folder_default), destination == SharedDownloads.destination) {
                        DownloadCenter.remember(DownloadCenter.alsoSaveFile, DownloadCenter.defaultKind, remembered, false, SharedDownloads.destination)
                        destination = SharedDownloads.destination
                    }
                    OptionRow(stringResource(R.string.kruxx_folder_other), destination != SharedDownloads.destination) {
                        picker.launch(destination.takeUnless { it == SharedDownloads.destination })
                    }
                    if (destination == SharedDownloads.destination) Text(stringResource(R.string.kruxx_folder_default_note), style = MaterialTheme.typography.bodySmall)
                    else {
                        Text(destination.lastPathSegment.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { picker.launch(destination) }) { Text(stringResource(R.string.kruxx_folder_change)) }
                    }
                }
                items(sortedDownloadJobs(work).take(20), key = { it.id }) { job ->
                    DownloadProgressCard(job)
                }
                if (assets.isNotEmpty()) item { Text(stringResource(R.string.kruxx_saved_files)) }
                items(assets, key = { it.track.id + it.kind.name }) { asset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(asset.track.names.label, maxLines = 2)
                            Text(stringResource(asset.kind.label), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            val item = asset.track.mediaItem()
                            if (asset.kind == DownloadKind.VIDEO) player.playVideo(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                                .setExtras(Bundle().apply { putBoolean("isVideo", true) }).build()).build())
                            else player.forcePlay(item)
                            DownloadCenter.dismiss(prompt)
                        }) { Icon(painterResource(R.drawable.play), stringResource(R.string.kruxx_file_play)) }
                        IconButton(onClick = {
                            DownloadCenter.dismiss(prompt)
                            DownloadCenter.copy(listOf(asset.track.mediaItem()), asset.kind)
                        }) { Icon(painterResource(R.drawable.export_outline), stringResource(R.string.kruxx_copy_files)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { DownloadCenter.dismiss(prompt) }) { Text(stringResource(android.R.string.ok)) } },
    )
}

class CopyDownloadsButton(private val download: DownloadAllDialog) : MenuIcon, Descriptive {
    override val iconId = R.drawable.export_outline
    override val messageId = R.string.kruxx_copy_files
    override val menuIconTitle: String @Composable get() = stringResource(messageId)
    override fun onShortClick() = DownloadCenter.copy(download.getSongs().map { it.asMediaItem })
}

@Composable
fun AudioDownloadButton(item: androidx.media3.common.MediaItem) {
    val state = getDownloadState(item.mediaId)
    val icon = when {
        state.isDownloadPending() -> R.drawable.download_progress
        state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> R.drawable.downloaded
        else -> R.drawable.download
    }
    // A completed audio download can still be rendered as MP3 or copied to another destination.
    IconButton(onClick = { DownloadCenter.request(listOf(item), video = false) }) {
        Icon(painterResource(icon), stringResource(R.string.kruxx_audio_download), tint = colorPalette().text)
    }
}

@Composable
fun VideoDownloadButton(item: androidx.media3.common.MediaItem) {
    val saved by DownloadCenter.saved.collectAsState()
    val downloaded = saved.any { it.track.id == item.mediaId }
    IconButton(onClick = { DownloadCenter.request(listOf(item), video = true) }) {
        Icon(painterResource(if (downloaded) R.drawable.downloaded else R.drawable.download),
            stringResource(R.string.kruxx_video_download), tint = colorPalette().text)
    }
}
