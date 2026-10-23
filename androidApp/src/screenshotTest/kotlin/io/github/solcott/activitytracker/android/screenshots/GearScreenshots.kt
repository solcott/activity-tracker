package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.GearSamples
import io.github.solcott.activitytracker.app.gear.GearState
import io.github.solcott.activitytracker.app.gear.GearUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun GearLoaded() {
    ScreenPreview { GearUi(GearSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun GearStates(@PreviewParameter(GearStates::class) state: GearState) {
    ScreenPreview { GearUi(state) }
}

class GearStates : OtherSamples<GearState>(GearSamples.all)
