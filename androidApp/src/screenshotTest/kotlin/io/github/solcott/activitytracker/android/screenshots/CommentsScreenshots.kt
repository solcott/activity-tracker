package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.comments.CommentsSamples
import io.github.solcott.activitytracker.app.comments.CommentsScreenState
import io.github.solcott.activitytracker.app.comments.CommentsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun CommentsLoaded() {
    ScreenPreview { CommentsUi(CommentsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun CommentsStates(@PreviewParameter(CommentsStates::class) state: CommentsScreenState) {
    ScreenPreview { CommentsUi(state) }
}

class CommentsStates : OtherSamples<CommentsScreenState>(CommentsSamples.all + CommentsSamples.variants)
