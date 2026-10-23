package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.PrivacyZonesSamples
import io.github.solcott.activitytracker.app.settings.PrivacyZonesState
import io.github.solcott.activitytracker.app.settings.PrivacyZonesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun PrivacyZonesLoaded() {
    ScreenPreview { PrivacyZonesUi(PrivacyZonesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun PrivacyZonesStates(@PreviewParameter(PrivacyZonesStates::class) state: PrivacyZonesState) {
    ScreenPreview { PrivacyZonesUi(state) }
}

class PrivacyZonesStates : OtherSamples<PrivacyZonesState>(PrivacyZonesSamples.all)
