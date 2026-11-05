package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.names.ProposePlaceNameSamples
import io.github.solcott.activitytracker.app.names.ProposePlaceNameState
import io.github.solcott.activitytracker.app.names.ProposePlaceNameUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ProposePlaceNameLoaded() {
    ScreenPreview { ProposePlaceNameUi(ProposePlaceNameSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ProposePlaceNameStates(@PreviewParameter(ProposePlaceNameStates::class) state: ProposePlaceNameState) {
    ScreenPreview { ProposePlaceNameUi(state) }
}

class ProposePlaceNameStates : OtherSamples<ProposePlaceNameState>(ProposePlaceNameSamples.all)
