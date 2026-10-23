package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.EditGearSamples
import io.github.solcott.activitytracker.app.gear.EditGearState
import io.github.solcott.activitytracker.app.gear.EditGearUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun EditGearLoaded() {
    ScreenPreview { EditGearUi(EditGearSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun EditGearStates(@PreviewParameter(EditGearStates::class) state: EditGearState) {
    ScreenPreview { EditGearUi(state) }
}

class EditGearStates : OtherSamples<EditGearState>(EditGearSamples.all)
