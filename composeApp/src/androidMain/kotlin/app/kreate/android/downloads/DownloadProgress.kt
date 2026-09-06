package app.kreate.android.downloads

/** Work shares, not time estimates. Fractions come from bytes or decoded media timestamps. */
internal enum class DownloadStep(val weight: Int) {
    WAITING(0), DOWNLOADING(40), PREPARING(5), CONVERTING(40), VIDEO(35), JOINING(10),
    SAVING(10), VERIFYING(5), FINISHED(0), ERROR(0)
}

internal class FileProgressPlan(kind: DownloadKind, existing: Boolean, copy: Boolean, external: Boolean) {
    val steps = buildList {
        if (!existing) {
            if (!copy) add(DownloadStep.DOWNLOADING)
            if (kind != DownloadKind.ORIGINAL || external) add(DownloadStep.PREPARING)
            when (kind) {
                DownloadKind.MP3 -> add(DownloadStep.CONVERTING)
                DownloadKind.VIDEO -> { add(DownloadStep.VIDEO); add(DownloadStep.JOINING) }
                DownloadKind.ORIGINAL -> Unit
            }
        }
        if (external) { add(DownloadStep.SAVING); add(DownloadStep.VERIFYING) }
    }

    fun fraction(step: DownloadStep, progress: Double): Double {
        if (step !in steps) return 0.0
        val bounded = if (progress.isFinite()) progress.coerceIn(0.0, 1.0) else 0.0
        return (steps.takeWhile { it != step }.sumOf { it.weight } + step.weight * bounded) /
            steps.sumOf { it.weight }
    }
}

internal fun batchPercent(index: Int, total: Int, fraction: Double): Int =
    if (total <= 0) 0 else ((index + fraction.coerceIn(0.0, 1.0)) * 100 / total).toInt().coerceIn(0, 99)

internal fun byteFraction(bytes: Long, length: Long): Double =
    if (length > 0) (bytes.toDouble() / length).coerceIn(0.0, 1.0) else 0.0

internal typealias FileProgress = suspend (DownloadStep, Double) -> Unit
