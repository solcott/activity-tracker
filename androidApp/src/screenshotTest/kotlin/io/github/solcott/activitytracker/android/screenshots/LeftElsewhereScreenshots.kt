package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.settings.LeftElsewhereSamples
import io.github.solcott.activitytracker.app.settings.LeftElsewhereState
import io.github.solcott.activitytracker.app.settings.LeftElsewhereUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun LeftElsewhereLoaded() {
    ScreenPreview { LeftElsewhereUi(LeftElsewhereSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun LeftElsewhereStates(@PreviewParameter(LeftElsewhereStates::class) state: LeftElsewhereState) {
    ScreenPreview { LeftElsewhereUi(state) }
}

class LeftElsewhereStates : OtherSamples<LeftElsewhereState>(LeftElsewhereSamples.all)
