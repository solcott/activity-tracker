package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.profile.EditProfileSamples
import io.github.solcott.activitytracker.app.profile.EditProfileState
import io.github.solcott.activitytracker.app.profile.EditProfileUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun EditProfileLoaded() {
    ScreenPreview { EditProfileUi(EditProfileSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun EditProfileStates(@PreviewParameter(EditProfileStates::class) state: EditProfileState) {
    ScreenPreview { EditProfileUi(state) }
}

class EditProfileStates : OtherSamples<EditProfileState>(EditProfileSamples.all)
