package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
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

class AccountStates : OtherSamples<AccountState>(AccountSamples.all)
