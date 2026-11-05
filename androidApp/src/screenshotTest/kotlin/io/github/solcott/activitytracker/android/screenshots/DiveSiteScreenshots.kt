package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.divesite.DiveSiteSamples
import io.github.solcott.activitytracker.app.divesite.DiveSiteState
import io.github.solcott.activitytracker.app.divesite.DiveSiteUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun DiveSiteLoaded() {
    ScreenPreview { DiveSiteUi(DiveSiteSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun DiveSiteStates(@PreviewParameter(DiveSiteStates::class) state: DiveSiteState) {
    ScreenPreview { DiveSiteUi(state) }
}

class DiveSiteStates : OtherSamples<DiveSiteState>(DiveSiteSamples.all)
