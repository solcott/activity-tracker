package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.feed.UnsentChangesSamples
import io.github.solcott.activitytracker.app.feed.UnsentChangesState
import io.github.solcott.activitytracker.app.feed.UnsentChangesUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun UnsentChangesLoaded() {
    ScreenPreview { UnsentChangesUi(UnsentChangesSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun UnsentChangesStates(@PreviewParameter(UnsentChangesStates::class) state: UnsentChangesState) {
    ScreenPreview { UnsentChangesUi(state) }
}

class UnsentChangesStates : OtherSamples<UnsentChangesState>(UnsentChangesSamples.all)
