package io.github.solcott.activitytracker.app.media

import io.github.solcott.activitytracker.app.fixtures.contentSamples
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
}
