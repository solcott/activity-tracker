package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.search.AthleteSearchSamples
import io.github.solcott.activitytracker.app.search.AthleteSearchState
import io.github.solcott.activitytracker.app.search.AthleteSearchUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun AthleteSearchLoaded() {
    ScreenPreview { AthleteSearchUi(AthleteSearchSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun AthleteSearchStates(@PreviewParameter(AthleteSearchStates::class) state: AthleteSearchState) {
    ScreenPreview { AthleteSearchUi(state) }
}

class AthleteSearchStates : OtherSamples<AthleteSearchState>(AthleteSearchSamples.all)
