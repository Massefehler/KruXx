package me.knighthat.updater

import android.content.Context
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.ConnectivityUtils
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import me.knighthat.utils.Repository
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

object Updater : KoinComponent {
    private const val LOG_TAG = "KruXxUpdater"
    private const val PREFS_NAME = "kruxx_updater"
    private const val LAST_SUCCESSFUL_CHECK = "last_successful_check"
    private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
    private val NETWORK_RECOVERY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2)
    private const val NETWORK_RECHECK_DELAY_MS = 500L

    private val client by inject<HttpClient>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var checkJob: Job? = null
    @Volatile
    private var forcedCheckRunning = false

    lateinit var release: GithubRelease
        private set

    lateinit var build: GithubRelease.Build
        private set

    val releaseVersion: String
        get() = KruxxReleasePolicy.parseVersion(release.tagName)?.toString().orEmpty()

    private suspend fun getLatestRelease(): GithubRelease {
        val url = "${Repository.GITHUB_API}/repos/${Repository.REPO}/releases/latest"
        return client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header(HttpHeaders.UserAgent, "KruXx/${BuildConfig.VERSION_NAME}")
            header("X-GitHub-Api-Version", "2022-11-28")
        }.body()
    }

    private fun selectUpdate(release: GithubRelease): GithubRelease.Build? {
        check(!release.draft && !release.prerelease) { "Latest GitHub release is not stable" }

        val isNewer = KruxxReleasePolicy.isNewer(release.tagName, BuildConfig.VERSION_NAME)
            ?: error("Invalid release or installed version")
        if(!isNewer) return null

        val expectedName = KruxxReleasePolicy.expectedAssetName(release.tagName)
            ?: error("Invalid release tag")
        val candidates = release.builds.filter { it.name == expectedName }
        check(candidates.size == 1) { "Expected exactly one release APK" }

        return candidates.single().also { asset ->
            check(asset.size > 0L) { "Release APK is empty" }
            check(KruxxReleasePolicy.isValidDigest(asset.digest)) { "Release APK has no valid SHA-256 digest" }
            check(
                KruxxReleasePolicy.isTrustedDownloadUrl(
                    url = asset.downloadUrl,
                    owner = BuildConfig.REPO_OWNER,
                    repository = BuildConfig.REPO_NAME,
                    releaseTag = release.tagName,
                    assetName = asset.name
                )
            ) { "Release APK URL is not trusted" }
        }
    }

    private suspend fun awaitUsableNetwork(context: Context): Boolean {
        if(isNetworkConnected(context)) return true

        return withTimeoutOrNull(NETWORK_RECOVERY_TIMEOUT_MS) {
            while(!isNetworkConnected(context)) {
                // The application-wide callback reacts immediately to a newly available network.
                // If Android has not validated it yet, poll briefly until capabilities catch up.
                ConnectivityUtils.isAvailable.first { it }
                if(!isNetworkConnected(context)) delay(NETWORK_RECHECK_DELAY_MS)
            }
            true
        } ?: false
    }

    private fun isRetryable(error: Exception): Boolean = when(error) {
        is ResponseException -> UpdateCheckPolicy.isRetryableHttpStatus(error.response.status.value)
        is UnresolvedAddressException -> true
        is SerializationException,
        is IllegalArgumentException,
        is IllegalStateException -> false
        else -> true
    }

    private fun forcedErrorMessage(error: Exception): Int = when {
        error is ResponseException && error.response.status == HttpStatusCode.NotFound ->
            R.string.kruxx_update_no_release
        error is UnresolvedAddressException || error is SerializationException ->
            R.string.error_check_for_updates_failed
        error is IllegalArgumentException || error is IllegalStateException ->
            R.string.kruxx_update_invalid_release
        else -> R.string.error_check_for_updates_failed
    }

    @Synchronized
    private fun clearCompletedJob(job: Job) {
        if(checkJob === job) {
            checkJob = null
            forcedCheckRunning = false
        }
    }

    @Synchronized
    fun cancelAutomaticCheck() {
        if(!forcedCheckRunning) checkJob?.cancel()
    }

    @Synchronized
    fun checkForUpdate(context: Context, isForced: Boolean = false): Job? {
        if(!BuildConfig.SELF_UPDATE_ENABLED || BuildConfig.DEBUG) return null
        checkJob?.takeIf(Job::isActive)?.let { activeJob ->
            if(!isForced || forcedCheckRunning) return activeJob
            activeJob.cancel()
        }

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(LAST_SUCCESSFUL_CHECK, 0L)
        if(!isForced && !UpdateCheckPolicy.isAutomaticCheckDue(
                nowMillis = System.currentTimeMillis(),
                lastSuccessfulCheckMillis = lastCheck,
                intervalMillis = CHECK_INTERVAL_MS
            )
        ) return null

        val newJob = scope.launch {
            var attempt = 1
            while(true) {
                if(isForced && !isNetworkConnected(appContext)) {
                    withContext(Dispatchers.Main) { Toaster.noInternet() }
                    return@launch
                }
                if(!isForced && !awaitUsableNetwork(appContext)) {
                    Logger.w(tag = LOG_TAG) { "Automatic update check deferred: no usable network" }
                    return@launch
                }

                try {
                    val latestRelease = getLatestRelease()
                    val latestBuild = selectUpdate(latestRelease)
                    prefs.edit().putLong(LAST_SUCCESSFUL_CHECK, System.currentTimeMillis()).apply()

                    withContext(Dispatchers.Main) {
                        if(latestBuild == null) {
                            if(isForced) Toaster.i(R.string.info_no_update_available)
                            return@withContext
                        }

                        release = latestRelease
                        build = latestBuild
                        if(NewUpdatePrompt.isActive || DownloadAndInstallDialog.isActive)
                            return@withContext

                        when(Preferences.CHECK_UPDATE.value) {
                            CheckUpdateState.DOWNLOAD_INSTALL -> DownloadAndInstallDialog.start()
                            CheckUpdateState.ASK -> NewUpdatePrompt.showDialog()
                            CheckUpdateState.DISABLED -> if(isForced) NewUpdatePrompt.showDialog()
                        }
                    }
                    return@launch
                } catch(cancelled: CancellationException) {
                    throw cancelled
                } catch(error: Exception) {
                    val retryDelay = if(!isForced && isRetryable(error))
                        UpdateCheckPolicy.retryDelayMillis(attempt)
                    else
                        null

                    if(retryDelay != null) {
                        Logger.w(error, LOG_TAG) {
                            "Automatic update check attempt $attempt failed; retrying"
                        }
                        attempt++
                        delay(retryDelay)
                        continue
                    }

                    Logger.e(error, LOG_TAG) { "Update check failed" }
                    if(isForced) withContext(Dispatchers.Main) {
                        Toaster.e(forcedErrorMessage(error))
                    }
                    return@launch
                }
            }
        }

        checkJob = newJob
        forcedCheckRunning = isForced
        newJob.invokeOnCompletion { clearCompletedJob(newJob) }
        return newJob
    }
}
