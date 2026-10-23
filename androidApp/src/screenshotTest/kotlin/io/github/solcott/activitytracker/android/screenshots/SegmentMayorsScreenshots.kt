package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.SegmentMayorsSamples
import io.github.solcott.activitytracker.app.segment.SegmentMayorsState
import io.github.solcott.activitytracker.app.segment.SegmentMayorsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentMayorsLoaded() {
    ScreenPreview { SegmentMayorsUi(SegmentMayorsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentMayorsStates(@PreviewParameter(SegmentMayorsStates::class) state: SegmentMayorsState) {
    ScreenPreview { SegmentMayorsUi(state) }
}

class SegmentMayorsStates : OtherSamples<SegmentMayorsState>(SegmentMayorsSamples.all)
