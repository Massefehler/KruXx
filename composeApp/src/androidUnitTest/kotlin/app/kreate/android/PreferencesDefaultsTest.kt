package app.kreate.android

import it.fast4x.rimusic.enums.HomeScreenTabs
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesDefaultsTest {

    @Test
    fun missingAndFormerSongsDefaultMigrateToQuickPicks() {
        assertEquals(
            HomeScreenTabs.QuickPics,
            kruxxStartupScreenAfterDefaultMigration(null)
        )
        assertEquals(
            HomeScreenTabs.QuickPics,
            kruxxStartupScreenAfterDefaultMigration(HomeScreenTabs.Songs.name)
        )
    }

    @Test
    fun anotherExplicitStartupScreenIsPreserved() {
        assertEquals(
            HomeScreenTabs.Albums,
            kruxxStartupScreenAfterDefaultMigration(HomeScreenTabs.Albums.name)
        )
    }
}
