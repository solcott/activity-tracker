package io.github.solcott.activitytracker.app.media

import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.data.moderation.BlurReason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object MediaGallerySamples {
    val items: ImmutableList<MediaItem> =
        persistentListOf(
            MediaItem.Photo("https://cdn.test/photo/1.jpg", aspectRatio = 1.5f, alt = "The view from the ridge"),
            MediaItem.Photo("https://cdn.test/photo/2.jpg", aspectRatio = 0.75f, alt = null),
        )

    val all: Map<String, MediaGalleryState>
        get() = contentSamples(persistentListOf(), items) { MediaGalleryState(it) }

    /** Beyond content loading: someone else's photos with Report, the second zoomed, a labelled one covered, and the owner's taken down. */
    val variants: Map<String, MediaGalleryState>
        get() =
            mapOf(
                "report" to
                    MediaGalleryState(ContentStates.loaded(items), page = 1, canReport = true, reporting = Reporting(available = true)),
                "zoomed" to MediaGalleryState(ContentStates.loaded(items), zoomed = true),
                "blurred" to MediaGalleryState(ContentStates.loaded(items), blur = BlurReason.GraphicMedia),
                "takenDown" to MediaGalleryState(ContentStates.loaded(items), takenDown = true),
            )
}
