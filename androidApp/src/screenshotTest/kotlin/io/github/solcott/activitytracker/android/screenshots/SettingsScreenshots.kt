package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.SettingsSamples
import io.github.solcott.activitytracker.app.settings.SettingsState
import io.github.solcott.activitytracker.app.settings.SettingsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SettingsLoaded() {
    ScreenPreview { SettingsUi(SettingsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SettingsStates(@PreviewParameter(SettingsStates::class) state: SettingsState) {
    ScreenPreview { SettingsUi(state) }
}

class SettingsStates : OtherSamples<SettingsState>(SettingsSamples.all)
