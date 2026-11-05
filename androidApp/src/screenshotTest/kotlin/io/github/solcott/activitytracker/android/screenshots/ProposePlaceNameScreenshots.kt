package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameSamples
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameState
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ProposeSegmentNameLoaded() {
    ScreenPreview { ProposeSegmentNameUi(ProposeSegmentNameSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ProposeSegmentNameStates(@PreviewParameter(ProposeSegmentNameStates::class) state: ProposeSegmentNameState) {
    ScreenPreview { ProposeSegmentNameUi(state) }
}

class ProposeSegmentNameStates : OtherSamples<ProposeSegmentNameState>(ProposeSegmentNameSamples.all)
