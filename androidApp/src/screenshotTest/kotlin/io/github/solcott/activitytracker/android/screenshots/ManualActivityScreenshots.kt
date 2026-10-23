package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.record.ManualActivitySamples
import io.github.solcott.activitytracker.app.record.ManualActivityState
import io.github.solcott.activitytracker.app.record.ManualActivityUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ManualActivityLoaded() {
    ScreenPreview { ManualActivityUi(ManualActivitySamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ManualActivityStates(@PreviewParameter(ManualActivityStates::class) state: ManualActivityState) {
    ScreenPreview { ManualActivityUi(state) }
}

class ManualActivityStates : OtherSamples<ManualActivityState>(ManualActivitySamples.all)
