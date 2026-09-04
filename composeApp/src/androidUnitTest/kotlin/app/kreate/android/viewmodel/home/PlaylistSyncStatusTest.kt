package app.kreate.android.viewmodel.home

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSyncStatusTest {
    @Test
    fun `login requires both enabled account and sync id`() {
        assertEquals(
            PlaylistSyncStatus.LOGGED_OUT,
            resolvePlaylistSyncStatus(
                loginEnabled = false,
                syncId = "sync-id",
                playlistSyncEnabled = true
            )
        )
        assertEquals(
            PlaylistSyncStatus.LOGGED_OUT,
            resolvePlaylistSyncStatus(
                loginEnabled = true,
                syncId = " ",
                playlistSyncEnabled = true
            )
        )
    }

    @Test
    fun `logged in account still honors playlist sync switch`() {
        assertEquals(
            PlaylistSyncStatus.DISABLED,
            resolvePlaylistSyncStatus(
                loginEnabled = true,
                syncId = "sync-id",
                playlistSyncEnabled = false
            )
        )
        assertEquals(
            PlaylistSyncStatus.ENABLED,
            resolvePlaylistSyncStatus(
                loginEnabled = true,
                syncId = "sync-id",
                playlistSyncEnabled = true
            )
        )
    }
}
