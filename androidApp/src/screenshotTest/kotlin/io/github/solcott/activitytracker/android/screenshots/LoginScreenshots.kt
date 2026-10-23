package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.login.LoginSamples
import io.github.solcott.activitytracker.app.login.LoginState
import io.github.solcott.activitytracker.app.login.LoginUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun LoginLoaded() {
    ScreenPreview { LoginUi(LoginSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun LoginStates(@PreviewParameter(LoginStates::class) state: LoginState) {
    ScreenPreview { LoginUi(state) }
}

class LoginStates : OtherSamples<LoginState>(LoginSamples.all)
