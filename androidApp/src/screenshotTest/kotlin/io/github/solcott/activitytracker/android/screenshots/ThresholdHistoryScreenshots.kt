package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.ThresholdHistorySamples
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryState
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ThresholdHistoryLoaded() {
    ScreenPreview { ThresholdHistoryUi(ThresholdHistorySamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ThresholdHistoryStates(@PreviewParameter(ThresholdHistoryStates::class) state: ThresholdHistoryState) {
    ScreenPreview { ThresholdHistoryUi(state) }
}

class ThresholdHistoryStates : OtherSamples<ThresholdHistoryState>(ThresholdHistorySamples.all)
