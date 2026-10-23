package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.SportZonesSamples
import io.github.solcott.activitytracker.app.settings.SportZonesState
import io.github.solcott.activitytracker.app.settings.SportZonesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SportZonesLoaded() {
    ScreenPreview { SportZonesUi(SportZonesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SportZonesStates(@PreviewParameter(SportZonesStates::class) state: SportZonesState) {
    ScreenPreview { SportZonesUi(state) }
}

class SportZonesStates : OtherSamples<SportZonesState>(SportZonesSamples.all)
