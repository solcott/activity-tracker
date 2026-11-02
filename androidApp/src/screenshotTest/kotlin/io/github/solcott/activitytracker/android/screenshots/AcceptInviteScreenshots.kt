package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.invites.AcceptInviteSamples
import io.github.solcott.activitytracker.app.invites.AcceptInviteState
import io.github.solcott.activitytracker.app.invites.AcceptInviteUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun AcceptInviteLoaded() {
    ScreenPreview { AcceptInviteUi(AcceptInviteSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun AcceptInviteStates(@PreviewParameter(AcceptInviteStates::class) state: AcceptInviteState) {
    ScreenPreview { AcceptInviteUi(state) }
}

class AcceptInviteStates : OtherSamples<AcceptInviteState>(AcceptInviteSamples.all)
