package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.gear.GearListSamples
import io.github.solcott.activitytracker.app.gear.GearListState
import io.github.solcott.activitytracker.app.gear.GearListUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun GearListLoaded() {
    ScreenPreview { GearListUi(GearListSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun GearListStates(@PreviewParameter(GearListStates::class) state: GearListState) {
    ScreenPreview { GearListUi(state) }
}

class GearListStates : OtherSamples<GearListState>(GearListSamples.all)
