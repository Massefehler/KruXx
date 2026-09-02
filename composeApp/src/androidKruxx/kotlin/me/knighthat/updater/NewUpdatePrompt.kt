package me.knighthat.updater

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.InteractiveDialog

object NewUpdatePrompt : InteractiveDialog {
    override val dialogTitle: String
        @Composable get() = stringResource(R.string.update_available)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val releaseNotes = Updater.release.body.orEmpty().trim().take(1_200)

        Column(modifier = Modifier.fillMaxWidth(.9f)) {
            BasicText(
                text = stringResource(
                    R.string.kruxx_update_available_details,
                    Updater.releaseVersion,
                    Formatter.formatShortFileSize(context, Updater.build.size)
                ),
                style = typography().s.semiBold.copy(color = colorPalette().text)
            )
            if(releaseNotes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = releaseNotes,
                    maxLines = 8,
                    style = typography().xs.copy(color = colorPalette().textSecondary)
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                text = stringResource(R.string.kruxx_update_open_release_page),
                style = typography().xs.copy(
                    color = colorPalette().accent,
                    textDecoration = TextDecoration.Underline
                ),
                modifier = Modifier
                    .clickable { uriHandler.openUri(Updater.release.htmlUrl) }
                    .padding(vertical = 4.dp)
            )
        }
    }

    @Composable
    override fun Buttons() {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(.9f)
        ) {
            SecondaryTextButton(
                text = stringResource(R.string.kruxx_update_download_install),
                onClick = {
                    hideDialog()
                    DownloadAndInstallDialog.start()
                }
            )
            SecondaryTextButton(
                text = stringResource(R.string.kruxx_update_later),
                onClick = ::hideDialog,
                alternative = true
            )
        }
    }
}
