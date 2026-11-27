package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.trim.TrimSamples
import io.github.solcott.activitytracker.app.trim.TrimState
import io.github.solcott.activitytracker.app.trim.TrimUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun TrimLoaded() {
    ScreenPreview { TrimUi(TrimSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun TrimStates(@PreviewParameter(TrimStates::class) state: TrimState) {
    ScreenPreview { TrimUi(state) }
}

class TrimStates : OtherSamples<TrimState>(TrimSamples.all)
