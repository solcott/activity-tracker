package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.uploads.UploadsSamples
import io.github.solcott.activitytracker.app.uploads.UploadsState
import io.github.solcott.activitytracker.app.uploads.UploadsUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun UploadsLoaded() {
    ScreenPreview { UploadsUi(UploadsSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun UploadsStates(@PreviewParameter(UploadsStates::class) state: UploadsState) {
    ScreenPreview { UploadsUi(state) }
}

class UploadsStates : OtherSamples<UploadsState>(UploadsSamples.all)
