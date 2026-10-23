package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.map.RouteMapSamples
import io.github.solcott.activitytracker.app.map.RouteMapState
import io.github.solcott.activitytracker.app.map.RouteMapUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun RouteMapLoaded() {
    ScreenPreview { RouteMapUi(RouteMapSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun RouteMapStates(@PreviewParameter(RouteMapStates::class) state: RouteMapState) {
    ScreenPreview { RouteMapUi(state) }
}

class RouteMapStates : OtherSamples<RouteMapState>(RouteMapSamples.all)
