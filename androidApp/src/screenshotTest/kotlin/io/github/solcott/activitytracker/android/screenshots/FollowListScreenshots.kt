package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.profile.FollowListSamples
import io.github.solcott.activitytracker.app.profile.FollowListState
import io.github.solcott.activitytracker.app.profile.FollowListUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun FollowListLoaded() {
    ScreenPreview { FollowListUi(FollowListSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun FollowListStates(@PreviewParameter(FollowListStates::class) state: FollowListState) {
    ScreenPreview { FollowListUi(state) }
}

class FollowListStates : OtherSamples<FollowListState>(FollowListSamples.all)
