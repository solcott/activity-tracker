package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.post.PostQuotesSamples
import io.github.solcott.activitytracker.app.post.PostQuotesState
import io.github.solcott.activitytracker.app.post.PostQuotesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun PostQuotesLoaded() {
    ScreenPreview { PostQuotesUi(PostQuotesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun PostQuotesStates(@PreviewParameter(PostQuotesStates::class) state: PostQuotesState) {
    ScreenPreview { PostQuotesUi(state) }
}

class PostQuotesStates : OtherSamples<PostQuotesState>(PostQuotesSamples.all)
