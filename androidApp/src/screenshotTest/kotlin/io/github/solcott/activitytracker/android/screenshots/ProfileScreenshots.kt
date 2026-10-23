package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.profile.ProfileSamples
import io.github.solcott.activitytracker.app.profile.ProfileState
import io.github.solcott.activitytracker.app.profile.ProfileUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ProfileLoaded() {
    ScreenPreview { ProfileUi(ProfileSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ProfileStates(@PreviewParameter(ProfileStates::class) state: ProfileState) {
    ScreenPreview { ProfileUi(state) }
}

class ProfileStates : OtherSamples<ProfileState>(ProfileSamples.all)
