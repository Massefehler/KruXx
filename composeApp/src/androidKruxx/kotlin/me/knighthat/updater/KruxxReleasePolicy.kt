package me.knighthat.updater

import java.net.URI

internal data class KruxxVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<KruxxVersion> {
    override fun compareTo(other: KruxxVersion): Int =
        compareValuesBy(this, other, KruxxVersion::major, KruxxVersion::minor, KruxxVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

internal object KruxxReleasePolicy {
    private val stableVersion = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")
    private val sha256Digest = Regex("^sha256:[0-9a-fA-F]{64}$")

    fun parseVersion(value: String): KruxxVersion? {
        val match = stableVersion.matchEntire(value.trim()) ?: return null
        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
        return KruxxVersion(parts[0], parts[1], parts[2])
    }

    fun isNewer(releaseTag: String, currentVersion: String): Boolean? {
        val release = parseVersion(releaseTag) ?: return null
        val current = parseVersion(currentVersion) ?: return null
        return release > current
    }

    fun expectedAssetName(releaseTag: String): String? =
        parseVersion(releaseTag)?.let { "KruXx-$it-release.apk" }

    fun isValidDigest(digest: String?): Boolean =
        digest != null && sha256Digest.matches(digest)

    fun isTrustedDownloadUrl(
        url: String,
        owner: String,
        repository: String,
        releaseTag: String,
        assetName: String
    ): Boolean = runCatching {
        if(expectedAssetName(releaseTag) != assetName) return@runCatching false

        val uri = URI(url)
        val expectedPath = "/$owner/$repository/releases/download/$releaseTag/$assetName"
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawPath == expectedPath &&
            uri.rawQuery == null &&
            uri.rawFragment == null
    }.getOrDefault(false)
}
