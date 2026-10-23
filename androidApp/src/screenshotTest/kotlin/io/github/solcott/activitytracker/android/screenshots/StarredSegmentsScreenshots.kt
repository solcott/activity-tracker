package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.StarredSegmentsSamples
import io.github.solcott.activitytracker.app.segment.StarredSegmentsState
import io.github.solcott.activitytracker.app.segment.StarredSegmentsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun StarredSegmentsLoaded() {
    ScreenPreview { StarredSegmentsUi(StarredSegmentsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun StarredSegmentsStates(@PreviewParameter(StarredSegmentsStates::class) state: StarredSegmentsState) {
    ScreenPreview { StarredSegmentsUi(state) }
}

class StarredSegmentsStates : OtherSamples<StarredSegmentsState>(StarredSegmentsSamples.all)
