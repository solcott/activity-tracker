package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.record.RecordSamples
import io.github.solcott.activitytracker.app.record.RecordState
import io.github.solcott.activitytracker.app.record.RecordUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun RecordLoaded() {
    ScreenPreview { RecordUi(RecordSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun RecordStates(@PreviewParameter(RecordStates::class) state: RecordState) {
    ScreenPreview { RecordUi(state) }
}

class RecordStates : OtherSamples<RecordState>(RecordSamples.all)
