package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.GearCollectionSamples
import io.github.solcott.activitytracker.app.gear.GearCollectionState
import io.github.solcott.activitytracker.app.gear.GearCollectionUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun GearCollectionLoaded() {
    ScreenPreview { GearCollectionUi(GearCollectionSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun GearCollectionStates(@PreviewParameter(GearCollectionStates::class) state: GearCollectionState) {
    ScreenPreview { GearCollectionUi(state) }
}

class GearCollectionStates : OtherSamples<GearCollectionState>(GearCollectionSamples.all)
