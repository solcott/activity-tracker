package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.SegmentBrowseSamples
import io.github.solcott.activitytracker.app.segment.SegmentBrowseState
import io.github.solcott.activitytracker.app.segment.SegmentBrowseUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentBrowseLoaded() {
    ScreenPreview { SegmentBrowseUi(SegmentBrowseSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentBrowseStates(@PreviewParameter(SegmentBrowseStates::class) state: SegmentBrowseState) {
    ScreenPreview { SegmentBrowseUi(state) }
}

class SegmentBrowseStates :
    OtherSamples<SegmentBrowseState>(
        SegmentBrowseSamples.all +
            mapOf(
                "selected" to SegmentBrowseSamples.selected,
                "tooWide" to SegmentBrowseSamples.tooWide,
                "nameResults" to SegmentBrowseSamples.nameResults,
                "nothingHere" to SegmentBrowseSamples.nothingHere,
                "nowhere" to SegmentBrowseSamples.nowhere,
                "locating" to SegmentBrowseSamples.locating,
            )
    )
