package me.knighthat.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.StringRes
import app.kreate.android.BuildConfig
import app.kreate.android.R
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal sealed interface ApkVerificationResult {
    data object Success : ApkVerificationResult
    data class Failure(@StringRes val messageId: Int) : ApkVerificationResult
}

internal object ApkVerifier {
    fun verify(
        context: Context,
        apkFile: File,
        releaseTag: String,
        expectedDigest: String?
    ): ApkVerificationResult {
        if(!apkFile.isFile) return failure(R.string.error_downloaded_file_not_found)

        val expectedHash = expectedDigest
            ?.takeIf(KruxxReleasePolicy::isValidDigest)
            ?.substringAfter("sha256:")
            ?: return failure(R.string.kruxx_update_invalid_digest)
        if(!expectedHash.equals(sha256(apkFile), ignoreCase = true))
            return failure(R.string.error_failed_to_verify_download_file)

        val packageManager = context.packageManager
        val archive = archiveInfo(packageManager, apkFile)
            ?: return failure(R.string.kruxx_update_invalid_apk)
        if(archive.packageName != context.packageName)
            return failure(R.string.kruxx_update_wrong_package)

        val expectedVersion = KruxxReleasePolicy.parseVersion(releaseTag)?.toString()
            ?: return failure(R.string.kruxx_update_invalid_release)
        if(archive.versionName != expectedVersion)
            return failure(R.string.kruxx_update_wrong_version)
        if(archive.versionCodeLong() <= BuildConfig.VERSION_CODE.toLong())
            return failure(R.string.kruxx_update_not_newer)

        val pinnedCertificate = normalizeFingerprint(BuildConfig.EXPECTED_SIGNING_CERT_SHA256)
        if(pinnedCertificate.length != 64)
            return failure(R.string.kruxx_update_wrong_signature)

        val archiveCertificates = archive.signingCertificates().map(::fingerprint).toSet()
        val installed = installedInfo(packageManager, context.packageName)
            ?: return failure(R.string.kruxx_update_wrong_signature)
        val installedCertificates = installed.signingCertificates().map(::fingerprint).toSet()

        if(pinnedCertificate !in archiveCertificates ||
            pinnedCertificate !in installedCertificates ||
            archiveCertificates.intersect(installedCertificates).isEmpty()
        ) return failure(R.string.kruxx_update_wrong_signature)

        return ApkVerificationResult.Success
    }

    private fun failure(@StringRes messageId: Int) = ApkVerificationResult.Failure(messageId)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while(true) {
                val count = input.read(buffer)
                if(count < 0) break
                if(count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(packageManager: PackageManager, apkFile: File): PackageInfo? =
        packageManager.getPackageArchiveInfo(apkFile.absolutePath, signingFlags())

    @Suppress("DEPRECATION")
    private fun installedInfo(packageManager: PackageManager, packageName: String): PackageInfo? =
        runCatching { packageManager.getPackageInfo(packageName, signingFlags()) }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingFlags(): Int =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else
            PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingCertificates(): Array<Signature> =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptyArray()
            if(info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            signatures ?: emptyArray()
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeLong(): Long =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun fingerprint(signature: Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun normalizeFingerprint(value: String): String =
        value.filter(Char::isLetterOrDigit).lowercase()
}
