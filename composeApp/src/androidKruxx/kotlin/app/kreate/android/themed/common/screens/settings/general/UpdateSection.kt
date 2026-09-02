package app.kreate.android.themed.common.screens.settings.general

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import me.knighthat.updater.ChangelogsDialog
import me.knighthat.updater.Updater
import androidx.compose.runtime.remember

fun LazyListScope.updateSection(search: SettingEntrySearch) {
    header(R.string.update)

    entry(search, R.string.setting_entry_update_checker) {
        SettingComponents.EnumEntry(
            preference = Preferences.CHECK_UPDATE,
            titleId = R.string.setting_entry_update_checker,
            subtitle = stringResource(Preferences.CHECK_UPDATE.value.subtitleId, BuildConfig.APP_NAME)
        )
    }
    entry(search, R.string.info_check_update_now) {
        val context = LocalContext.current
        SettingComponents.Text(
            title = stringResource(R.string.info_check_update_now),
            subtitle = stringResource(R.string.kruxx_update_source),
            onClick = { Updater.checkForUpdate(context, isForced = true) }
        )
    }
    entry(search, R.string.setting_entry_view_changelogs) {
        val context = LocalContext.current
        val changelogs = remember { ChangelogsDialog(context) }
        changelogs.Render()
        SettingComponents.Text(
            title = stringResource(R.string.setting_entry_view_changelogs),
            subtitle = "v${BuildConfig.VERSION_NAME}",
            onClick = changelogs::showDialog
        )
    }
}
