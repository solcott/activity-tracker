package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.invites.AddPeopleSamples
import io.github.solcott.activitytracker.app.invites.AddPeopleState
import io.github.solcott.activitytracker.app.invites.AddPeopleUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun AddPeopleLoaded() {
    ScreenPreview { AddPeopleUi(AddPeopleSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun AddPeopleStates(@PreviewParameter(AddPeopleStates::class) state: AddPeopleState) {
    ScreenPreview { AddPeopleUi(state) }
}

class AddPeopleStates : OtherSamples<AddPeopleState>(AddPeopleSamples.all)
