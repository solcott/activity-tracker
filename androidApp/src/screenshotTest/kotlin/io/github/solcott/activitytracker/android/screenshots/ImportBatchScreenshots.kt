package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.feed.ImportBatchSamples
import io.github.solcott.activitytracker.app.feed.ImportBatchState
import io.github.solcott.activitytracker.app.feed.ImportBatchUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ImportBatchLoaded() {
    ScreenPreview { ImportBatchUi(ImportBatchSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ImportBatchStates(@PreviewParameter(ImportBatchStates::class) state: ImportBatchState) {
    ScreenPreview { ImportBatchUi(state) }
}

class ImportBatchStates : OtherSamples<ImportBatchState>(ImportBatchSamples.all)
