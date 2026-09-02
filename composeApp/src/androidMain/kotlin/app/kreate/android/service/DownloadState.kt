package app.kreate.android.service

import androidx.media3.exoplayer.offline.Download

/** States in which Media3 has accepted a download but has not finished it yet. */
fun Int.isDownloadPending(): Boolean =
    this == Download.STATE_QUEUED ||
            this == Download.STATE_DOWNLOADING ||
            this == Download.STATE_RESTARTING

/** States represented by a tappable "cancel/remove" icon in the UI. */
fun Int.isDownloadRemovable(): Boolean =
    this == Download.STATE_COMPLETED || isDownloadPending()
