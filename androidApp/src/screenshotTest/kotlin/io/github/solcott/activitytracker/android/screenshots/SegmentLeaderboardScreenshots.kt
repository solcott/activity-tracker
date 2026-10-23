package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardSamples
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardState
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun SegmentLeaderboardLoaded() {
    ScreenPreview { SegmentLeaderboardUi(SegmentLeaderboardSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun SegmentLeaderboardStates(@PreviewParameter(SegmentLeaderboardStates::class) state: SegmentLeaderboardState) {
    ScreenPreview { SegmentLeaderboardUi(state) }
}

class SegmentLeaderboardStates : OtherSamples<SegmentLeaderboardState>(SegmentLeaderboardSamples.all)
