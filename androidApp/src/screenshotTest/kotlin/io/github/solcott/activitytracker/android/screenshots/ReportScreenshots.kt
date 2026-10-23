package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.report.ReportSamples
import io.github.solcott.activitytracker.app.report.ReportState
import io.github.solcott.activitytracker.app.report.ReportUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ReportLoaded() {
    ScreenPreview { ReportUi(ReportSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ReportStates(@PreviewParameter(ReportStates::class) state: ReportState) {
    ScreenPreview { ReportUi(state) }
}

class ReportStates : OtherSamples<ReportState>(ReportSamples.all)
