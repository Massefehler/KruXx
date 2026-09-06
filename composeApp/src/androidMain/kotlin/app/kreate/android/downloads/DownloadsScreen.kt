@file:androidx.media3.common.util.UnstableApi

package app.kreate.android.downloads

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkManager
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.isKruxxGlassEnabled
import it.fast4x.rimusic.ui.styling.kruxxGlassCard
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.playVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.compose.koinInject
import java.io.File

/** Public files are queried on resume and on changes, so deleted copies cannot appear as downloads. */
@Composable
fun DownloadsScreen(player: StatefulPlayer = koinInject()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val palette = colorPalette()
    val accent = if (isKruxxGlassEnabled) KruxxGlass.electricBlue else palette.accent
    val removeColor = if (isKruxxGlassEnabled) KruxxGlass.signalRed else MaterialTheme.colorScheme.error
    var category by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable(category) { mutableStateOf(false) }
    var selected by rememberSaveable(category) { mutableStateOf(emptyList<String>()) }
    val removal: DownloadRemovalViewModel = viewModel()
    val removalState by removal.state.collectAsState()
    val removing = removalState?.running == true
    var refresh by remember { mutableIntStateOf(0) }
    var files by remember { mutableStateOf<List<DownloadFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val permissionNeeded = Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val assets by DownloadCenter.saved.collectAsState()
    val downloads by MyDownloadHelper.instance.downloads.collectAsState()
    val transferPercent by produceState(emptyMap<String, Int>(), downloads) {
        do {
            value = downloads.mapValues { (_, item) -> item.percentDownloaded.toInt().coerceIn(0, 99) }
            if (downloads.values.none { it.state == Download.STATE_DOWNLOADING }) break
            kotlinx.coroutines.delay(350)
        } while (true)
    }
    val songs by remember { Database.songTable.sortAllByTitle() }.collectAsState(emptyList())
    val manager = remember(context) { WorkManager.getInstance(context) }
    val jobs by remember(manager) { manager.getWorkInfosByTagFlow(DownloadCenter.WORK_TAG) }.collectAsState(emptyList())
    val hiddenStatusJobs by DownloadCenter.hiddenStatusJobs.collectAsState()
    val statusJob = visibleDownloadJob(jobs, hiddenStatusJobs)

    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        val fileObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh++ }
        }
        lifecycle.addObserver(observer)
        if (Build.VERSION.SDK_INT >= 29)
            context.contentResolver.registerContentObserver(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), true, fileObserver)
        onDispose {
            lifecycle.removeObserver(observer)
            context.contentResolver.unregisterContentObserver(fileObserver)
        }
    }
    LaunchedEffect(refresh, removal.revision, jobs.map { it.id to it.state }) {
        loading = true
        failed = false
        try {
            files = if (permissionNeeded) emptyList() else withContext(Dispatchers.IO) { SharedDownloads.list(context) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { files = emptyList(); failed = true }
        finally { loading = false }
    }
    val visible = files.filter { if (category == 1) it.mime.startsWith("video/") else it.mime.startsWith("audio/") }
    val appSongs = songs.filter { downloads.containsKey(it.id) }
    val targets: List<DownloadRemovalTarget> = if (category == 2)
        appSongs.map { DownloadRemovalTarget.Original(DownloadCenter.trackFor(it.asMediaItem)) } +
            assets.map { DownloadRemovalTarget.Asset(it) }
    else visible.map { DownloadRemovalTarget.Public(it) }
    val selectedTargets = targets.filter { it.key in selected }
    fun toggle(target: DownloadRemovalTarget) {
        selecting = true
        selected = if (target.key in selected) selected - target.key else selected + target.key
    }
    LaunchedEffect(removal.revision) { selected = emptyList(); selecting = false }
    DownloadRemovalDialog(removal, context)
    CompositionLocalProvider(LocalContentColor provides palette.text) {
        Column(Modifier.fillMaxSize()) {
            TabHeader(R.string.kruxx_downloads_tab) {}
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (category == 2) R.string.kruxx_download_in_app else R.string.kruxx_folder_default),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { refresh++ }) {
                    Icon(painterResource(R.drawable.refresh_circle), stringResource(R.string.kruxx_downloads_refresh))
                }
                IconButton(onClick = { DownloadCenter.settings() }) {
                    Icon(painterResource(R.drawable.settings), stringResource(R.string.kruxx_download_settings))
                }
            }
            ButtonsRow(listOf(0 to stringResource(R.string.kruxx_downloads_audio),
                1 to stringResource(R.string.kruxx_downloads_video), 2 to stringResource(R.string.kruxx_download_in_app)),
                category, { category = it })
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    val selectAllLabel = stringResource(R.string.kruxx_remove_select_all)
                    TriStateCheckbox(modifier = Modifier.semantics { contentDescription = selectAllLabel },
                        colors = CheckboxDefaults.colors(checkedColor = accent), state = when {
                        selectedTargets.isEmpty() -> ToggleableState.Off
                        selectedTargets.size == targets.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }, enabled = !removing, onClick = {
                        selected = if (selectedTargets.size == targets.size) emptyList() else targets.map { it.key }
                    })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.kruxx_remove_select_all), style = MaterialTheme.typography.bodyMedium)
                        Text(pluralStringResource(R.plurals.kruxx_remove_selected, selectedTargets.size, selectedTargets.size),
                            style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    IconButton(onClick = { removal.request(selectedTargets) }, enabled = selectedTargets.isNotEmpty() && !removing) {
                        Icon(painterResource(R.drawable.trash), stringResource(R.string.kruxx_remove_selection), tint = removeColor)
                    }
                    IconButton(onClick = { selecting = false; selected = emptyList() }, enabled = !removing) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.kruxx_remove_selection_close))
                    }
                } else {
                    Text(pluralStringResource(R.plurals.kruxx_downloads_file_count, targets.size, targets.size),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selecting = true }, enabled = targets.isNotEmpty() && !removing,
                        colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
                        Text(stringResource(R.string.kruxx_remove_select))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                top = 8.dp, bottom = Dimensions.bottomSpacer), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (statusJob != null) item(key = "job/${statusJob.id}") {
                    SwipeableDownloadProgressCard(statusJob, Modifier.animateItem(),
                        onDismiss = { DownloadCenter.hideStatus(statusJob.id) })
                }
                if (category != 2) {
                    item {
                        Text(SharedDownloads.relativePath + if (category == 1) SharedDownloads.VIDEO else SharedDownloads.AUDIO,
                            style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                    when {
                        permissionNeeded -> item {
                            TextButton(onClick = { permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }) {
                                Text(stringResource(R.string.kruxx_downloads_allow_access))
                            }
                        }
                        loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        failed -> item { Text(stringResource(R.string.kruxx_downloads_read_failed)) }
                        visible.isEmpty() -> item { Text(stringResource(R.string.kruxx_downloads_empty)) }
                    }
                    items(visible, key = { it.uri.toString() }) { file ->
                        val target = DownloadRemovalTarget.Public(file)
                        DownloadFileRow(file.name.substringBeforeLast('.'), file.name.substringAfterLast('.').uppercase() +
                            " · " + Formatter.formatFileSize(context, file.size) + " · " + file.path,
                            onOpen = {
                                runCatching {
                                    val uri = if (file.uri.scheme == "file") FileProvider.getUriForFile(context,
                                        "${context.packageName}.provider", File(requireNotNull(file.uri.path))) else file.uri
                                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                }.onFailure { Toaster.e(R.string.kruxx_downloads_open_failed) }
                            }, onRemove = { removal.request(listOf(target)) },
                            selected = if (selecting) target.key in selected else null, onSelect = { toggle(target) }, interactive = !removing)
                    }
                } else {
                    if (appSongs.isEmpty() && assets.isEmpty()) item { Text(stringResource(R.string.kruxx_downloads_app_empty)) }
                    items(appSongs, key = { "audio/${it.id}" }) { song ->
                        val download = downloads[song.id]
                        val done = download?.state == Download.STATE_COMPLETED
                        val track = DownloadCenter.trackFor(song.asMediaItem)
                        val target = DownloadRemovalTarget.Original(track)
                        DownloadFileRow(track.names.label, stringResource(when {
                            done -> R.string.kruxx_format_original
                            download?.state == Download.STATE_REMOVING -> R.string.kruxx_remove_running
                            download?.state == Download.STATE_FAILED -> R.string.kruxx_files_failed
                            else -> R.string.kruxx_files_waiting
                        }), onOpen = { player.forcePlay(song.asMediaItem) }, enabled = done,
                            onCopy = if (done) ({ DownloadCenter.copy(listOf(track.mediaItem())) }) else null,
                            onRemove = { removal.request(listOf(target)) },
                            selected = if (selecting) target.key in selected else null, onSelect = { toggle(target) }, interactive = !removing)
                        if (!done && download?.state != Download.STATE_FAILED && download?.state != Download.STATE_REMOVING) {
                            val percent = transferPercent[song.id] ?: 0
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { DownloadProgressBar(percent) }
                                Text("$percent %", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(assets, key = { it.track.id + it.kind.name }) { asset ->
                        val target = DownloadRemovalTarget.Asset(asset)
                        DownloadFileRow(asset.track.names.label, stringResource(asset.kind.label),
                            onOpen = {
                                val item = asset.track.mediaItem()
                                if (asset.kind == DownloadKind.VIDEO) player.playVideo(item.buildUpon().setMediaMetadata(
                                    item.mediaMetadata.buildUpon().setExtras(Bundle().apply { putBoolean("isVideo", true) }).build()).build())
                                else player.forcePlay(item)
                            }, onCopy = { DownloadCenter.copy(listOf(asset.track.mediaItem()), asset.kind) },
                            onRemove = { removal.request(listOf(target)) },
                            selected = if (selecting) target.key in selected else null, onSelect = { toggle(target) }, interactive = !removing)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFileRow(title: String, detail: String, onOpen: () -> Unit, enabled: Boolean = true,
                            onCopy: (() -> Unit)? = null, onRemove: () -> Unit, selected: Boolean?,
                            onSelect: () -> Unit, interactive: Boolean) {
    val accent = if (isKruxxGlassEnabled) KruxxGlass.electricBlue else colorPalette().accent
    val removeColor = if (isKruxxGlassEnabled) KruxxGlass.signalRed else MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth().kruxxGlassCard().combinedClickable(enabled = interactive,
        onClick = { if (selected != null) onSelect() else if (enabled) onOpen() },
        onLongClick = onSelect).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected != null) Checkbox(checked = selected, onCheckedChange = { onSelect() }, enabled = interactive,
            modifier = Modifier.semantics { contentDescription = title },
            colors = CheckboxDefaults.colors(checkedColor = accent))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(detail, color = colorPalette().textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (selected == null) IconButton(onClick = onOpen, enabled = enabled && interactive) {
            Icon(painterResource(R.drawable.play), stringResource(R.string.kruxx_file_play))
        }
        if (selected == null && onCopy != null) IconButton(onClick = onCopy, enabled = interactive) {
            Icon(painterResource(R.drawable.export_outline), stringResource(R.string.kruxx_copy_files))
        }
        if (selected == null) IconButton(onClick = onRemove, enabled = interactive) {
            Icon(painterResource(R.drawable.trash), stringResource(R.string.kruxx_remove_file, title),
                tint = removeColor)
        }
    }
}
