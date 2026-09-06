package app.kreate.android.downloads

/** Musical identity for labels, portable filenames and MP3 tags; uploader metadata stays separate. */
internal data class TrackNames(val artist: String, val title: String) {
    val label: String get() = listOf(artist, title).filter(String::isNotBlank).joinToString(" - ")
}

internal object DownloadNames {
    private val separator = Regex("\\s+[-–—]\\s+")
    // Remove presentation labels only. Versions such as (Live), (Remix) and feat. are meaningful.
    private val presentation = Regex(
        "\\s*[\\[(](?:official (?:music )?(?:video|audio|visuali[sz]er)|(?:official )?lyrics?(?: video)?|music video|HD|HQ|4K|1080p)[\\])]\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val legacyName = Regex("^(.+) \\[([a-f0-9]{12})]( - MP3 320| - Video)?( \\([0-9]+\\))?\\.([a-z0-9]+)$")

    fun resolve(track: DownloadTrack): TrackNames {
        var title = track.title.trim()
        val artist = track.artist.trim()
        if (track.videoSource) {
            do {
                val before = title
                title = title.replace(presentation, "").trim()
            } while (title != before)
        }
        val split = separator.find(title)
        if (split != null) {
            val left = title.substring(0, split.range.first).trim()
            val right = title.substring(split.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty() &&
                (track.videoSource || left.equals(artist, ignoreCase = true)))
                return TrackNames(if (track.videoSource) left else artist, right)
        }
        // A channel is not evidence of authorship. Keep an unsplit video title without guessing.
        return TrackNames(if (track.videoSource) "" else artist, title)
    }

    fun fileName(track: DownloadTrack, extension: String): String = "${portableStem(resolve(track).label)}.$extension"

    private fun portableStem(value: String): String {
        val clean = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.')
            .ifBlank { "Download" }
        // Leave space for the extension and a collision number on FAT/SAF providers.
        val stem = StringBuilder()
        var index = 0
        var bytes = 0
        while (index < clean.length) {
            val point = clean.codePointAt(index)
            val chars = String(Character.toChars(point))
            bytes += chars.toByteArray(Charsets.UTF_8).size
            if (bytes > 160) break
            stem.append(chars)
            index += Character.charCount(point)
        }
        return stem.toString().trimEnd(' ', '.')
    }

    /** Recognize only the exact previous exporter name of a known asset, including retry numbers. */
    fun renamedLegacyFile(name: String, tracks: List<DownloadTrack>): String? {
        val match = legacyName.matchEntire(name) ?: return null
        val (oldStem, hash, suffix, number, extension) = match.destructured
        val track = tracks.firstOrNull { DownloadCenter.key(it.id).take(12) == hash } ?: return null
        val oldLabel = listOf(track.artist, track.title).filter(String::isNotBlank).joinToString(" - ")
        if (portableStem(oldLabel) != oldStem) return null
        if (suffix == " - MP3 320" && extension != "mp3") return null
        val next = fileName(track, extension)
        return "${next.substringBeforeLast('.')}$number.$extension"
    }

    fun availableName(wanted: String, occupied: Collection<String>): String {
        var name = wanted
        var number = 2
        while (occupied.any { it.equals(name, ignoreCase = true) })
            name = "${wanted.substringBeforeLast('.')} (${number++}).${wanted.substringAfterLast('.')}"
        return name
    }
}

internal val DownloadTrack.names: TrackNames get() = DownloadNames.resolve(this)
