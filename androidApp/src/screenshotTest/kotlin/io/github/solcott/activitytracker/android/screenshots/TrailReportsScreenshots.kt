package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.TrailReportSamples
import io.github.solcott.activitytracker.app.segment.TrailReportsState
import io.github.solcott.activitytracker.app.segment.TrailReportsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun TrailReportsLoaded() {
    ScreenPreview { TrailReportsUi(TrailReportSamples.reportsAll.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun TrailReportsStates(@PreviewParameter(TrailReportsStates::class) state: TrailReportsState) {
    ScreenPreview { TrailReportsUi(state) }
}

class TrailReportsStates : OtherSamples<TrailReportsState>(TrailReportSamples.reportsAll)
