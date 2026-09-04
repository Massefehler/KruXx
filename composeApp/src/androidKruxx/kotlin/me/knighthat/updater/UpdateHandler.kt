package me.knighthat.updater

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.utils.ConnectivityUtils
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.utils.isNetworkConnected
import java.io.File

@Composable
fun UpdateHandler() {
    if(!BuildConfig.SELF_UPDATE_ENABLED || BuildConfig.DEBUG) return

    val context = LocalContext.current
    val updateMode by Preferences.CHECK_UPDATE
    val networkAvailable by ConnectivityUtils.isAvailable.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUpdateMode = rememberUpdatedState(updateMode)
    val currentNetworkAvailable = rememberUpdatedState(networkAvailable)

    DownloadAndInstallDialog.Render()
    NewUpdatePrompt.Render()

    // Builds before KruXx 1.0.0 had a deliberately disabled, no-op updater. Migrate that
    // transitional default once; a later choice made by the user remains untouched.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("kruxx_updater", Context.MODE_PRIVATE)
        if(!prefs.getBoolean("update_mode_initialized", false)) {
            if(Preferences.CHECK_UPDATE.value == CheckUpdateState.DISABLED)
                Preferences.CHECK_UPDATE.value = CheckUpdateState.ASK
            prefs.edit().putBoolean("update_mode_initialized", true).apply()
        }

        // An APK used to install the currently running version is no longer needed.
        updateApkDirectory(context)
            ?.listFiles { file -> file.isFile && file.name.matches(Regex("KruXx-.*-release\\.apk")) }
            ?.forEach(File::delete)
    }

    // Start only while the UI is foreground-ready. A network callback retriggers a skipped offline
    // launch, and every later foreground entry gets another chance if no successful check was saved.
    DisposableEffect(lifecycleOwner, context) {
        fun checkIfReady() {
            if(currentUpdateMode.value != CheckUpdateState.DISABLED &&
                (currentNetworkAvailable.value || isNetworkConnected(context))
            ) Updater.checkForUpdate(context)
        }

        val observer = LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_START -> checkIfReady()
                Lifecycle.Event.ON_STOP -> Updater.cancelAutomaticCheck()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            checkIfReady()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Updater.cancelAutomaticCheck()
        }
    }

    LaunchedEffect(updateMode, networkAvailable, lifecycleOwner) {
        if(updateMode == CheckUpdateState.DISABLED) {
            Updater.cancelAutomaticCheck()
        } else if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            (networkAvailable || isNetworkConnected(context))
        ) {
            Updater.checkForUpdate(context)
        }
    }
}
