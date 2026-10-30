package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One of an activity's media, as the UI shows it. Photos only for now; a `Video` joins this when activities can carry them (see
 * BACKLOG.md), and every `when` over it then says where it needs handling.
 */
@Immutable
sealed interface MediaItem {
    /** Width over height, when known, to lay the item out before it loads. */
    val aspectRatio: Float?

    @Immutable data class Photo(val url: String, override val aspectRatio: Float?, val alt: String?) : MediaItem
}

/** [ActivityView.media] this client can show, in order. */
fun ActivityView.mediaItems(): ImmutableList<MediaItem> =
    media
        .orEmpty()
        .mapNotNull { view ->
            when (view) {
                is ActivityMediaView.Image ->
                    MediaItem.Photo(
                        url = view.fullsize,
                        aspectRatio = view.aspectRatio?.let { it.width.toFloat() / it.height },
                        alt = view.alt,
                    )
                is ActivityMediaView.Unknown -> null
            }
        }
        .toImmutableList()
