package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.detail.ActivityDetailMosaicSamples
import io.github.solcott.activitytracker.app.detail.ActivityDetailSamples
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.ActivityDetailUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun ActivityDetailLoaded() {
    ScreenPreview { ActivityDetailUi(ActivityDetailSamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityDetailStates(@PreviewParameter(ActivityDetailStates::class) state: ActivityDetailState) {
    ScreenPreview { ActivityDetailUi(state) }
}

class ActivityDetailStates : OtherSamples<ActivityDetailState>(ActivityDetailSamples.all)

@PreviewTest
@PreviewScreenSizes
@Composable
private fun ActivityDetailMosaic() {
    ScreenPreview { ActivityDetailUi(ActivityDetailMosaicSamples.variants.getValue("photos")) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun ActivityDetailMosaicStates(@PreviewParameter(ActivityDetailMosaicStates::class) state: ActivityDetailState) {
    ScreenPreview { ActivityDetailUi(state) }
}

class ActivityDetailMosaicStates : OtherSamples<ActivityDetailState>(ActivityDetailMosaicSamples.variants)
