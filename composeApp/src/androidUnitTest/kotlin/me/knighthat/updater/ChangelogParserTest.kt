package me.knighthat.updater

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogParserTest {

    @Test
    fun parsesKreateFormat() {
        val result = parseChangelog(
            sequenceOf(
                "Fixes:",
                "- Error 403",
                "Changes:",
                "- Updated player"
            )
        )

        assertEquals(
            listOf(
                ChangelogSection( "Fixes", listOf("- Error 403") ),
                ChangelogSection( "Changes", listOf("- Updated player") )
            ),
            result
        )
    }

    @Test
    fun parsesHistoricKruxxFormatAndJoinsWrappedItems() {
        val result = parseChangelog(
            sequenceOf(
                "KruXx 2.2.3-kruxx.2 (Basis: Kreate 2.2.3)",
                "",
                "• Charts auf der Startseite funktionieren wieder. YouTube hat die Felder der",
                "  Länderauswahl umbenannt.",
                "• Zweiter Eintrag."
            )
        )

        assertEquals(
            listOf(
                ChangelogSection(
                    "KruXx 2.2.3-kruxx.2 (Basis: Kreate 2.2.3)",
                    listOf(
                        "- Charts auf der Startseite funktionieren wieder. YouTube hat die Felder der Länderauswahl umbenannt.",
                        "- Zweiter Eintrag."
                    )
                )
            ),
            result
        )
    }

    @Test
    fun emptyInputProducesNoSections() {
        assertEquals( emptyList<ChangelogSection>(), parseChangelog(emptySequence()) )
    }
}
