package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.CreateSegmentSamples
import io.github.solcott.activitytracker.app.segment.CreateSegmentState
import io.github.solcott.activitytracker.app.segment.CreateSegmentUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun CreateSegmentLoaded() {
    ScreenPreview { CreateSegmentUi(CreateSegmentSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun CreateSegmentStates(@PreviewParameter(CreateSegmentStates::class) state: CreateSegmentState) {
    ScreenPreview { CreateSegmentUi(state) }
}

class CreateSegmentStates : OtherSamples<CreateSegmentState>(CreateSegmentSamples.all)
