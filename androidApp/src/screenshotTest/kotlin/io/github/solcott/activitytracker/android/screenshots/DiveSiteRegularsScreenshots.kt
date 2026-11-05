package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsSamples
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsState
import io.github.solcott.activitytracker.app.divesite.DiveSiteRegularsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun DiveSiteRegularsLoaded() {
    ScreenPreview { DiveSiteRegularsUi(DiveSiteRegularsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun DiveSiteRegularsStates(@PreviewParameter(DiveSiteRegularsStates::class) state: DiveSiteRegularsState) {
    ScreenPreview { DiveSiteRegularsUi(state) }
}

class DiveSiteRegularsStates : OtherSamples<DiveSiteRegularsState>(DiveSiteRegularsSamples.all)
