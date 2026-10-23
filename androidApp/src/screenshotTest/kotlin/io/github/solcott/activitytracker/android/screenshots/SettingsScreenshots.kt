package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.SettingsSamples
import io.github.solcott.activitytracker.app.settings.SettingsUi

// Settings has only its loaded sample, so no states preview: an empty parameter provider fails to render.
@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SettingsLoaded() {
    ScreenPreview { SettingsUi(SettingsSamples.all.loaded()) }
}
