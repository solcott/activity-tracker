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

/**
 * The segment group switcher (segments/segment-groups, segments/segment-direction): two-way, named by climb, and a lone one-way segment.
 */
@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentGroupSwitcher(@PreviewParameter(SegmentGroupStates::class) state: SegmentState) {
    ScreenPreview { SegmentUi(state) }
}

class SegmentGroupStates :
    OtherSamples<SegmentState>(
        SegmentSamples.variants.filterKeys { it in setOf(SegmentSamples.GROUP, SegmentSamples.GROUP_BY_CLIMB, SegmentSamples.ONE_WAY) },
    )

/**
 * The Segment page's whole layout (segments/segment-page): the heading with its trail, the figures, the profile, the Leaderboard and Mayor
 * cards and Trail details in cards; and the Leaderboard card alone when both boards' reads failed.
 */
@PreviewTest
@PreviewTallPhoneSize
@Composable
private fun SegmentLayout(@PreviewParameter(SegmentLayoutStates::class) state: SegmentState) {
    ScreenPreview { SegmentUi(state) }
}

class SegmentLayoutStates :
    OtherSamples<SegmentState>(
        SegmentSamples.variants.filterKeys { it in setOf(SegmentSamples.OPEN_STREET_MAP, SegmentSamples.BOARDS_FAILED) },
    )
