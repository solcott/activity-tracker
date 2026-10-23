package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.detail.CombinePickerSamples
import io.github.solcott.activitytracker.app.detail.CombinePickerState
import io.github.solcott.activitytracker.app.detail.CombinePickerUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun CombinePickerLoaded() {
    ScreenPreview { CombinePickerUi(CombinePickerSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun CombinePickerStates(@PreviewParameter(CombinePickerStates::class) state: CombinePickerState) {
    ScreenPreview { CombinePickerUi(state) }
}

class CombinePickerStates : OtherSamples<CombinePickerState>(CombinePickerSamples.all)
