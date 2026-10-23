package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.EditGearCollectionSamples
import io.github.solcott.activitytracker.app.gear.EditGearCollectionState
import io.github.solcott.activitytracker.app.gear.EditGearCollectionUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun EditGearCollectionLoaded() {
    ScreenPreview { EditGearCollectionUi(EditGearCollectionSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun EditGearCollectionStates(@PreviewParameter(EditGearCollectionStates::class) state: EditGearCollectionState) {
    ScreenPreview { EditGearCollectionUi(state) }
}

class EditGearCollectionStates : OtherSamples<EditGearCollectionState>(EditGearCollectionSamples.all)
