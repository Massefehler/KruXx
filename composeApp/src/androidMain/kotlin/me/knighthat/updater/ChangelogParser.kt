package me.knighthat.updater

internal data class ChangelogSection(
    val title: String,
    val changes: List<String>
)

/**
 * Parses both Kreate's `Heading:`/`- item` format and the historic KruXx
 * `Heading`/`• item` format. Indented lines continue the preceding item.
 */
internal fun parseChangelog( lines: Sequence<String> ): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    var currentTitle: String? = null
    val currentChanges = mutableListOf<String>()

    fun packSection() {
        val title = currentTitle?.trim().orEmpty()
        if( title.isNotEmpty() )
            sections.add( ChangelogSection(title, currentChanges.toList()) )

        currentChanges.clear()
    }

    lines.forEach { rawLine ->
        val line = rawLine.trim()

        when {
            line.isEmpty() -> Unit

            line.endsWith( ":" ) -> {
                currentTitle?.let { packSection() }
                currentTitle = line.removeSuffix( ":" ).trim()
            }

            line.startsWith( "-" ) || line.startsWith( "•" ) -> {
                val change = line.drop( 1 ).trim()
                if( currentTitle != null && change.isNotEmpty() )
                    currentChanges.add( "- $change" )
            }

            currentTitle == null -> currentTitle = line

            currentChanges.isNotEmpty() -> {
                val lastIndex = currentChanges.lastIndex
                currentChanges[lastIndex] = "${currentChanges[lastIndex]} $line"
            }
        }
    }

    currentTitle?.let { packSection() }
    return sections
}
