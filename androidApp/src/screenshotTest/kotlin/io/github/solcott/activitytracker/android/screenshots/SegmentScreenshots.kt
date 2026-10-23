package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.SegmentSamples
import io.github.solcott.activitytracker.app.segment.SegmentState
import io.github.solcott.activitytracker.app.segment.SegmentUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentLoaded() {
    ScreenPreview { SegmentUi(SegmentSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentStates(@PreviewParameter(SegmentStates::class) state: SegmentState) {
    ScreenPreview { SegmentUi(state) }
}

class SegmentStates : OtherSamples<SegmentState>(SegmentSamples.all)
