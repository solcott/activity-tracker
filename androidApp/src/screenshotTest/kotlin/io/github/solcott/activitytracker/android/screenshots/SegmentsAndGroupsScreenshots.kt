package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsSamples
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsState
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentsAndGroupsLoaded() {
    ScreenPreview { SegmentsAndGroupsUi(SegmentsAndGroupsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentsAndGroupsStates(@PreviewParameter(SegmentsAndGroupsStates::class) state: SegmentsAndGroupsState) {
    ScreenPreview { SegmentsAndGroupsUi(state) }
}

class SegmentsAndGroupsStates : OtherSamples<SegmentsAndGroupsState>(SegmentsAndGroupsSamples.all)
