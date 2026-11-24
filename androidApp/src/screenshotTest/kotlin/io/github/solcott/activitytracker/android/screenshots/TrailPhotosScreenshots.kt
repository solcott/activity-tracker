package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.segment.TrailPhotosUi
import io.github.solcott.activitytracker.app.segment.TrailReportSamples

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun TrailPhotosLoaded() {
    ScreenPreview { TrailPhotosUi(TrailReportSamples.photos) }
}
