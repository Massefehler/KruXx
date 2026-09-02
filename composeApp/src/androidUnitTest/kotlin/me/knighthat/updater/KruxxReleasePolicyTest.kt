package me.knighthat.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KruxxReleasePolicyTest {
    @Test
    fun `parses only stable three-part releases`() {
        assertEquals(KruxxVersion(1, 2, 3), KruxxReleasePolicy.parseVersion("v1.2.3"))
        assertEquals(KruxxVersion(1, 2, 3), KruxxReleasePolicy.parseVersion("1.2.3"))
        assertNull(KruxxReleasePolicy.parseVersion("v1.2"))
        assertNull(KruxxReleasePolicy.parseVersion("v1.2.3-beta"))
        assertNull(KruxxReleasePolicy.parseVersion("v01.2.3"))
    }

    @Test
    fun `compares semantic components instead of strings`() {
        assertTrue(KruxxReleasePolicy.isNewer("v1.10.0", "1.9.9") == true)
        assertFalse(KruxxReleasePolicy.isNewer("v1.0.0", "1.0.0") == true)
        assertFalse(KruxxReleasePolicy.isNewer("v0.9.9", "1.0.0") == true)
        assertNull(KruxxReleasePolicy.isNewer("latest", "1.0.0"))
    }

    @Test
    fun `derives exact release asset name`() {
        assertEquals("KruXx-2.3.4-release.apk", KruxxReleasePolicy.expectedAssetName("v2.3.4"))
        assertNull(KruxxReleasePolicy.expectedAssetName("v2.3.4-rc1"))
    }

    @Test
    fun `accepts only complete sha256 digests`() {
        assertTrue(KruxxReleasePolicy.isValidDigest("sha256:${"ab".repeat(32)}"))
        assertTrue(KruxxReleasePolicy.isValidDigest("sha256:${"AB".repeat(32)}"))
        assertFalse(KruxxReleasePolicy.isValidDigest("${"ab".repeat(32)}"))
        assertFalse(KruxxReleasePolicy.isValidDigest("sha256:abcd"))
        assertFalse(KruxxReleasePolicy.isValidDigest(null))
    }

    @Test
    fun `accepts only the exact GitHub release asset URL`() {
        val trusted = "https://github.com/Massefehler/KruXx/releases/download/v1.2.3/KruXx-1.2.3-release.apk"
        fun check(url: String, asset: String = "KruXx-1.2.3-release.apk") =
            KruxxReleasePolicy.isTrustedDownloadUrl(
                url = url,
                owner = "Massefehler",
                repository = "KruXx",
                releaseTag = "v1.2.3",
                assetName = asset
            )

        assertTrue(check(trusted))
        assertTrue(check(trusted.replace("github.com/", "github.com:443/")))
        assertFalse(check(trusted.replace("https://", "http://")))
        assertFalse(check(trusted.replace("github.com", "example.com")))
        assertFalse(check(trusted.replace("github.com/", "attacker@github.com/")))
        assertFalse(check(trusted.replace("Massefehler/KruXx", "Massefehler/Other")))
        assertFalse(check("$trusted?download=1"))
        assertFalse(check(trusted, asset = "renamed.apk"))
    }
}
