package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.record.ImportActivitySamples
import io.github.solcott.activitytracker.app.record.ImportActivityState
import io.github.solcott.activitytracker.app.record.ImportActivityUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ImportActivityLoaded() {
    ScreenPreview { ImportActivityUi(ImportActivitySamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ImportActivityStates(@PreviewParameter(ImportActivityStates::class) state: ImportActivityState) {
    ScreenPreview { ImportActivityUi(state) }
}

class ImportActivityStates : OtherSamples<ImportActivityState>(ImportActivitySamples.all)
