package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.moderation.ModerationSamples
import io.github.solcott.activitytracker.app.moderation.ModerationState
import io.github.solcott.activitytracker.app.moderation.ModerationUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ModerationLoaded() {
    ScreenPreview { ModerationUi(ModerationSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ModerationStates(@PreviewParameter(ModerationStates::class) state: ModerationState) {
    ScreenPreview { ModerationUi(state) }
}

class ModerationStates : OtherSamples<ModerationState>(ModerationSamples.all)
