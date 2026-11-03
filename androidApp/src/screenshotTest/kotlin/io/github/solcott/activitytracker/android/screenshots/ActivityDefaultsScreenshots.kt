package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsSamples
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsState
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ActivityDefaultsLoaded() {
    ScreenPreview { ActivityDefaultsUi(ActivityDefaultsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityDefaultsStates(@PreviewParameter(ActivityDefaultsStates::class) state: ActivityDefaultsState) {
    ScreenPreview { ActivityDefaultsUi(state) }
}

class ActivityDefaultsStates : OtherSamples<ActivityDefaultsState>(ActivityDefaultsSamples.all)
