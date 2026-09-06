package app.kreate.android.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.*
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.ThemedAlertDialog
import it.fast4x.rimusic.ui.styling.KruxxGlass
import it.fast4x.rimusic.ui.styling.kruxxGlassCard
import java.io.File
import java.util.UUID

internal val DownloadStep.label: Int get() = when (this) {
    DownloadStep.WAITING -> R.string.kruxx_files_waiting
    DownloadStep.DOWNLOADING, DownloadStep.VIDEO -> R.string.kruxx_progress_downloading
    DownloadStep.PREPARING -> R.string.kruxx_files_preparing
    DownloadStep.CONVERTING -> R.string.kruxx_progress_converting
    DownloadStep.JOINING -> R.string.kruxx_progress_joining
    DownloadStep.SAVING -> R.string.kruxx_progress_saving
    DownloadStep.VERIFYING -> R.string.kruxx_progress_verifying
    DownloadStep.FINISHED -> R.string.kruxx_progress_done
    DownloadStep.ERROR -> R.string.kruxx_progress_error
}

internal fun sortedDownloadJobs(jobs: List<WorkInfo>) = jobs.sortedWith(
    compareBy<WorkInfo> { it.state.isFinished }.thenByDescending {
        it.tags.firstOrNull { tag -> tag.startsWith("kruxx_created:") }?.substringAfter(':')?.toLongOrNull() ?: 0L
    }
)

/** Select before hiding: dismissing a result must not reveal an older finished card. */
internal fun visibleDownloadJob(jobs: List<WorkInfo>, hidden: Set<String>): WorkInfo? =
    sortedDownloadJobs(jobs).firstOrNull {
        !it.state.isFinished || it.outputData.getString("destination") != null
    }?.takeUnless { it.id.toString() in hidden }

@Composable
internal fun DownloadProgressDialog(id: UUID) {
    val manager = WorkManager.getInstance(LocalContext.current)
    val job by remember(manager, id) { manager.getWorkInfoByIdFlow(id) }.collectAsState(null)
    val palette = colorPalette()
    ThemedAlertDialog(
        onDismissRequest = { DownloadCenter.shownJob.value = null },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.kruxx_downloads_tab), Modifier.weight(1f))
                DownloadStatusCloseButton { DownloadCenter.hideStatus(id) }
            }
        },
        text = {
            Column {
                job?.let {
                    SwipeableDownloadProgressCard(it, Modifier.weight(1f, fill = false),
                        showCloseButton = false, scrollable = true,
                        onDismiss = { DownloadCenter.hideStatus(id) })
                }
                    ?: Text(stringResource(R.string.kruxx_files_waiting))
                if (job?.state?.isFinished != true) Text(stringResource(R.string.kruxx_progress_hide_hint),
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = { DownloadCenter.shownJob.value = null }) {
                Text(stringResource(if (job?.state?.isFinished == true) android.R.string.ok else R.string.kruxx_progress_continue))
            }
        }
    )
}

@Composable
@Suppress("DEPRECATION") // Use the same gesture state as the app's other swipe controls.
internal fun SwipeableDownloadProgressCard(
    job: WorkInfo,
    modifier: Modifier = Modifier,
    showCloseButton: Boolean = true,
    scrollable: Boolean = false,
    onDismiss: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { true },
        positionalThreshold = { it * 0.35f },
    )
    val palette = colorPalette()
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(KruxxGlass.cardShape).semantics { dismiss { onDismiss(); true } },
        onDismiss = { onDismiss() },
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.Settled) Row(
                Modifier.fillMaxSize().kruxxGlassCard().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                    Arrangement.Start else Arrangement.End,
            ) {
                Icon(painterResource(R.drawable.close), null, Modifier.size(20.dp), tint = palette.textSecondary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kruxx_progress_hide), style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary)
            }
        },
    ) {
        DownloadProgressCard(job, onDismiss = if (showCloseButton) onDismiss else null, scrollable = scrollable)
    }
}

/** Blur is confined to the soft highlight; the icon and its 48-dp touch target stay crisp. */
@Composable
private fun DownloadStatusCloseButton(onClick: () -> Unit) {
    val palette = colorPalette()
    val tint = lerp(KruxxGlass.signalRed, if (palette.isDark) Color.White else Color.Black,
        if (palette.isDark) 0.3f else 0.2f)
    val shape = RoundedCornerShape(12.dp)
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(34.dp).clip(shape)
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = if (palette.isDark) 0.12f else 0.7f), tint.copy(alpha = 0.06f))))
            .border(1.dp, Brush.linearGradient(listOf(tint.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.15f))), shape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(24.dp).blur(8.dp).background(Brush.radialGradient(
                listOf(tint.copy(alpha = 0.22f), Color.Transparent)), CircleShape))
            Icon(painterResource(R.drawable.close), stringResource(R.string.kruxx_progress_hide),
                Modifier.size(20.dp), tint = tint)
        }
    }
}

/** The same persisted result is shown immediately, in Downloads, and in download settings. */
@Composable
internal fun DownloadProgressCard(job: WorkInfo, onDismiss: (() -> Unit)? = null, scrollable: Boolean = false) {
    val manager = WorkManager.getInstance(LocalContext.current)
    val palette = colorPalette()
    val data = if (job.state.isFinished) job.outputData else job.progress
    val cancelled = job.state == WorkInfo.State.CANCELLED
    val failed = job.state == WorkInfo.State.FAILED || data.getInt("failed", 0) > 0
    val done = job.state == WorkInfo.State.SUCCEEDED && !failed
    val percent = if (done) 100 else data.getInt("percent", 0).coerceIn(0, 99)
    val phase = when {
        cancelled -> R.string.kruxx_progress_cancelled
        failed -> R.string.kruxx_progress_error
        done -> R.string.kruxx_progress_done
        else -> runCatching { DownloadStep.valueOf(data.getString("phase").orEmpty()).label }.getOrDefault(R.string.kruxx_files_waiting)
    }
    val accent = if (failed) palette.red else palette.accent
    CompositionLocalProvider(LocalContentColor provides palette.text) {
        // The dialog's close button remains fixed while long status details scroll.
        Column(Modifier.fillMaxWidth().kruxxGlassCard()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), Alignment.Center) {
                    Icon(painterResource(if (done) R.drawable.checkmark else R.drawable.download), null,
                        Modifier.size(20.dp), tint = accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(data.getString("title")?.takeIf(String::isNotBlank) ?: stringResource(R.string.kruxx_downloads_tab),
                        style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (data.getInt("total", 0) > 1) Text(stringResource(R.string.kruxx_progress_count,
                        data.getInt("current", 1), data.getInt("total", 0)),
                        style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                if (onDismiss != null) DownloadStatusCloseButton(onDismiss)
            }
            if (!cancelled) DownloadProgressBar(percent, accent)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(phase), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = if (failed) palette.red else palette.text)
                if (!cancelled) Text("$percent %", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = accent)
            }
            data.getString("kind")?.let { name ->
                val kind = DownloadKind.entries.firstOrNull { it.name == name }
                if (kind != null) Text(stringResource(kind.label), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            data.getString("destination")?.takeIf(String::isNotBlank)?.let {
                Text(stringResource(R.string.kruxx_progress_destination, it), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            when {
                cancelled -> Text(stringResource(R.string.kruxx_files_cancelled), style = MaterialTheme.typography.bodySmall)
                failed -> Text(data.getString("errors").orEmpty(), style = MaterialTheme.typography.bodySmall)
                done -> Text(stringResource(R.string.kruxx_files_result, data.getInt("completed", 0),
                    data.getInt("skipped", 0)), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            if (!job.state.isFinished) TextButton(onClick = { manager.cancelWorkById(job.id) }, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(android.R.string.cancel))
            }
            val token = data.getString("payload") ?: job.tags.firstOrNull { it.startsWith("kruxx_payload:") }?.substringAfter(':')
            if (job.state.isFinished && !done && token != null && File(DownloadCenter.jobDirectory, "$token.json").isFile) {
                TextButton(onClick = {
                    val request = OneTimeWorkRequestBuilder<FileDownloadWorker>()
                        .setInputData(workDataOf("payload" to token)).addTag(DownloadCenter.WORK_TAG)
                        .addTag("kruxx_payload:$token").addTag("kruxx_created:${System.currentTimeMillis()}").build()
                    manager.enqueueUniqueWork("${DownloadCenter.WORK_TAG}/$token", ExistingWorkPolicy.KEEP, request)
                    DownloadCenter.shownJob.value = request.id
                }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.kruxx_files_retry)) }
            }
        }
    }
}

@Composable
internal fun DownloadProgressBar(percent: Int, color: Color = colorPalette().accent) {
    val fraction by animateFloatAsState(percent.coerceIn(0, 100) / 100f, tween(280), label = "downloadProgress")
    Box(Modifier.fillMaxWidth().height(10.dp).progressSemantics(percent.coerceIn(0, 100) / 100f)
        .clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color, lerp(color, Color.White, 0.3f)))))
    }
}
