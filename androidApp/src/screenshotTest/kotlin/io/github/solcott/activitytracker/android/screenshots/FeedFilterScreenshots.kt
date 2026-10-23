package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterSamples
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterState
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun FeedFilterLoaded() {
    ScreenPreview { FeedFilterUi(FeedFilterSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun FeedFilterStates(@PreviewParameter(FeedFilterStates::class) state: FeedFilterState) {
    ScreenPreview { FeedFilterUi(state) }
}

class FeedFilterStates : OtherSamples<FeedFilterState>(FeedFilterSamples.all)
