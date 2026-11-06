package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.AccountSamples
import io.github.solcott.activitytracker.app.settings.AccountState
import io.github.solcott.activitytracker.app.settings.AccountUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun AccountLoaded() {
    ScreenPreview { AccountUi(AccountSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun AccountStates(@PreviewParameter(AccountStates::class) state: AccountState) {
    ScreenPreview { AccountUi(state) }
}

// The data export's section sits below the fold of a phone-sized preview: a tall phone shows it.
@PreviewTest
@Preview(name = "tallPhone", widthDp = 400, heightDp = 1100)
@Composable
private fun AccountExportStates(@PreviewParameter(AccountExportStates::class) state: AccountState) {
    ScreenPreview { AccountUi(state) }
}

class AccountStates : OtherSamples<AccountState>(AccountSamples.all.filterKeys { !it.startsWith(EXPORT) })

class AccountExportStates : OtherSamples<AccountState>(AccountSamples.all.filterKeys { it.startsWith(EXPORT) })

private const val EXPORT = "export"
