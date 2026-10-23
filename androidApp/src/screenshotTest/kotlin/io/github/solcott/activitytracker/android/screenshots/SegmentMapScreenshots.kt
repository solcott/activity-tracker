package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.SegmentMapSamples
import io.github.solcott.activitytracker.app.segment.SegmentMapState
import io.github.solcott.activitytracker.app.segment.SegmentMapUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentMapLoaded() {
    ScreenPreview { SegmentMapUi(SegmentMapSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentMapStates(@PreviewParameter(SegmentMapStates::class) state: SegmentMapState) {
    ScreenPreview { SegmentMapUi(state) }
}

class SegmentMapStates : OtherSamples<SegmentMapState>(SegmentMapSamples.all)
