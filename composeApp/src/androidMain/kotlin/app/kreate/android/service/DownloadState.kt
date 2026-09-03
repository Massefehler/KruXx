package app.kreate.android.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

/** States in which Media3 has accepted a download but has not finished it yet. */
@androidx.annotation.OptIn(UnstableApi::class)
fun Int.isDownloadPending(): Boolean =
    this == Download.STATE_QUEUED ||
            this == Download.STATE_DOWNLOADING ||
            this == Download.STATE_RESTARTING

/** States represented by a tappable "cancel/remove" icon in the UI. */
@androidx.annotation.OptIn(UnstableApi::class)
fun Int.isDownloadRemovable(): Boolean =
    this == Download.STATE_COMPLETED || isDownloadPending()
