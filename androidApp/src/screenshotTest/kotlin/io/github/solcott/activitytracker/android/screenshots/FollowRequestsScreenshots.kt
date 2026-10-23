package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.profile.FollowRequestsSamples
import io.github.solcott.activitytracker.app.profile.FollowRequestsState
import io.github.solcott.activitytracker.app.profile.FollowRequestsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun FollowRequestsLoaded() {
    ScreenPreview { FollowRequestsUi(FollowRequestsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun FollowRequestsStates(@PreviewParameter(FollowRequestsStates::class) state: FollowRequestsState) {
    ScreenPreview { FollowRequestsUi(state) }
}

class FollowRequestsStates : OtherSamples<FollowRequestsState>(FollowRequestsSamples.all)
