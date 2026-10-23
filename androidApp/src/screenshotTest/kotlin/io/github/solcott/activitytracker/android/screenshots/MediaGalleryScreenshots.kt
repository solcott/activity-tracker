package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.media.MediaGallerySamples
import io.github.solcott.activitytracker.app.media.MediaGalleryState
import io.github.solcott.activitytracker.app.media.MediaGalleryUi

@PreviewTest
@PreviewScreenSizes
@PreviewScreenVariants
@Composable
private fun MediaGalleryLoaded() {
    ScreenPreview { MediaGalleryUi(MediaGallerySamples.all.loaded()) }
}

@PreviewTest
@PreviewPhoneSize
@Composable
private fun MediaGalleryStates(@PreviewParameter(MediaGalleryStates::class) state: MediaGalleryState) {
    ScreenPreview { MediaGalleryUi(state) }
}

class MediaGalleryStates : OtherSamples<MediaGalleryState>(MediaGallerySamples.all)
