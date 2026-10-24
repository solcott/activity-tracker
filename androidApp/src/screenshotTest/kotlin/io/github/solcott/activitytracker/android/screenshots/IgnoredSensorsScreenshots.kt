package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsSamples
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsState
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun IgnoredSensorsLoaded() {
    ScreenPreview { IgnoredSensorsUi(IgnoredSensorsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun IgnoredSensorsStates(@PreviewParameter(IgnoredSensorsStates::class) state: IgnoredSensorsState) {
    ScreenPreview { IgnoredSensorsUi(state) }
}

class IgnoredSensorsStates : OtherSamples<IgnoredSensorsState>(IgnoredSensorsSamples.all)
