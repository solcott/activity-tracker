package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.invites.ActivityInvitesSamples
import io.github.solcott.activitytracker.app.invites.ActivityInvitesState
import io.github.solcott.activitytracker.app.invites.ActivityInvitesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ActivityInvitesLoaded() {
    ScreenPreview { ActivityInvitesUi(ActivityInvitesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityInvitesStates(@PreviewParameter(ActivityInvitesStates::class) state: ActivityInvitesState) {
    ScreenPreview { ActivityInvitesUi(state) }
}

class ActivityInvitesStates : OtherSamples<ActivityInvitesState>(ActivityInvitesSamples.all)
