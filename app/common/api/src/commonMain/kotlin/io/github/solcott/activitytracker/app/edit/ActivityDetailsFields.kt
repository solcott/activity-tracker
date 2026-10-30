package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.media.MediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.io.bytestring.ByteString

/**
 * The details an athlete writes about an activity, on the import and edit screens alike: its description, a dive's site, and its photos.
 * The title stays with each screen, which fills it its own way.
 */
@Immutable
data class ActivityDetailsFields(
    val description: TextFieldState,
    /** Null unless the activity is a dive. */
    val diveSite: TextFieldState?,
    val media: ImmutableList<EditMedia>,
    /** How many media the activity can hold. */
    val maxMedia: Int,
    val eventSink: (DetailsEvent) -> Unit,
) {
    val canAddMedia: Boolean
        get() = media.size < maxMedia
}

/** A photo picked and compressed on the device, and how big it came out. */
@Immutable data class PickedPhoto(val bytes: ByteString, val mimeType: String, val width: Int, val height: Int)

/** One of the media on the form, in order. */
@Immutable
sealed interface EditMedia {
    /** Already on the activity: [cid] names its blob. */
    @Immutable data class Existing(val cid: String, val item: MediaItem) : EditMedia

    /** Picked on this form, not yet uploaded. */
    @Immutable data class Picked(val photo: PickedPhoto) : EditMedia
}

sealed interface DetailsEvent {
    /** Photos the athlete picked, compressed; any past [ActivityDetailsFields.maxMedia] are left out. */
    data class PhotosPicked(val photos: ImmutableList<PickedPhoto>) : DetailsEvent

    /** Removes one of [ActivityDetailsFields.media], by index. */
    data class RemoveMedia(val index: Int) : DetailsEvent
}
