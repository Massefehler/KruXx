package me.knighthat.updater

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import it.fast4x.rimusic.enums.CheckUpdateState
import java.io.File

@Composable
fun UpdateHandler() {
    if(!BuildConfig.SELF_UPDATE_ENABLED || BuildConfig.DEBUG) return

    val context = LocalContext.current
    val updateMode by Preferences.CHECK_UPDATE

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

    LaunchedEffect(updateMode) {
        if(updateMode != CheckUpdateState.DISABLED)
            Updater.checkForUpdate(context)
    }
}
