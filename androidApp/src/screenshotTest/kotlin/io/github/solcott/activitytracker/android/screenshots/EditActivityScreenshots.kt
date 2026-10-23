package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.edit.EditActivitySamples
import io.github.solcott.activitytracker.app.edit.EditActivityState
import io.github.solcott.activitytracker.app.edit.EditActivityUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun EditActivityLoaded() {
    ScreenPreview { EditActivityUi(EditActivitySamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun EditActivityStates(@PreviewParameter(EditActivityStates::class) state: EditActivityState) {
    ScreenPreview { EditActivityUi(state) }
}

class EditActivityStates : OtherSamples<EditActivityState>(EditActivitySamples.all)
