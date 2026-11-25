package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.detail.ActivityDataSamples
import io.github.solcott.activitytracker.app.detail.ActivityDataState
import io.github.solcott.activitytracker.app.detail.ActivityDataUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ActivityDataLoaded() {
    ScreenPreview { ActivityDataUi(ActivityDataSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityDataAtLaps() {
    ScreenPreview { ActivityDataUi(ActivityDataSamples.atLaps) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityDataStates(@PreviewParameter(ActivityDataStates::class) state: ActivityDataState) {
    ScreenPreview { ActivityDataUi(state) }
}

class ActivityDataStates : OtherSamples<ActivityDataState>(ActivityDataSamples.all)
