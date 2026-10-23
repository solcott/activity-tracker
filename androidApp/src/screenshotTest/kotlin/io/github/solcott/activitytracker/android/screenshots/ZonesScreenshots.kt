package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.ZonesSamples
import io.github.solcott.activitytracker.app.settings.ZonesState
import io.github.solcott.activitytracker.app.settings.ZonesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ZonesLoaded() {
    ScreenPreview { ZonesUi(ZonesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ZonesStates(@PreviewParameter(ZonesStates::class) state: ZonesState) {
    ScreenPreview { ZonesUi(state) }
}

class ZonesStates : OtherSamples<ZonesState>(ZonesSamples.all)
