package me.knighthat.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    val id: Long,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("assets") val builds: List<Build> = emptyList()
) {
    @Serializable
    data class Build(
        val id: Long,
        val name: String,
        val size: Long,
        val digest: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        @SerialName("browser_download_url") val downloadUrl: String
    )
}
