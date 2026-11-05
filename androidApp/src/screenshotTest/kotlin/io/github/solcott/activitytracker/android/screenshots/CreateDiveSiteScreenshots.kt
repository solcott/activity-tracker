package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteSamples
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteState
import io.github.solcott.activitytracker.app.divesite.CreateDiveSiteUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun CreateDiveSiteLoaded() {
    ScreenPreview { CreateDiveSiteUi(CreateDiveSiteSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun CreateDiveSiteStates(@PreviewParameter(CreateDiveSiteStates::class) state: CreateDiveSiteState) {
    ScreenPreview { CreateDiveSiteUi(state) }
}

class CreateDiveSiteStates : OtherSamples<CreateDiveSiteState>(CreateDiveSiteSamples.all)
