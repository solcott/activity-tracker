package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.feed.FeedState
import io.github.solcott.activitytracker.app.feed.FeedUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun FeedLoaded() {
    ScreenPreview { FeedUi(FeedSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun FeedStates(@PreviewParameter(FeedStates::class) state: FeedState) {
    ScreenPreview { FeedUi(state) }
}

class FeedStates : OtherSamples<FeedState>(FeedSamples.all)
