package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.TrainingZonesSamples
import io.github.solcott.activitytracker.app.settings.TrainingZonesState
import io.github.solcott.activitytracker.app.settings.TrainingZonesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun TrainingZonesLoaded() {
    ScreenPreview { TrainingZonesUi(TrainingZonesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun TrainingZonesStates(@PreviewParameter(TrainingZonesStates::class) state: TrainingZonesState) {
    ScreenPreview { TrainingZonesUi(state) }
}

class TrainingZonesStates : OtherSamples<TrainingZonesState>(TrainingZonesSamples.all)
