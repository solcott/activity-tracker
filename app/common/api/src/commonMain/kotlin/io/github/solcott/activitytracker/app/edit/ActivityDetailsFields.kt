package io.github.solcott.activitytracker.app.edit

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.textfield.TextFieldState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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

    /** [description] for Swift, whose export leaves out a member named `description` (dev/ios-swift-interop). */
    val about: TextFieldState
        get() = description
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
    data class PhotosPicked(val photos: ImmutableList<PickedPhoto>) : DetailsEvent {
        /** For Swift, whose export gives no initialiser to a class that takes an immutable collection (dev/ios-swift-interop). */
        constructor(photos: List<PickedPhoto>) : this(photos.toImmutableList())
    }

    /** Removes one of [ActivityDetailsFields.media], by index. */
    data class RemoveMedia(val index: Int) : DetailsEvent
}

/** What the details form says that follows a rule (dev/shared-display-text), chosen here for the shared and the native form. */
object DetailsFormTexts {
    /** How many photos the activity holds of how many it can: "2 of 4 photos". */
    fun photosCount(fields: ActivityDetailsFields): AppText =
        AppText.Plural(
            "details_photos_count",
            fields.maxMedia,
            persistentListOf(
                AppText.written(formatWhole(fields.media.size.toLong())),
                AppText.written(formatWhole(fields.maxMedia.toLong())),
            ),
        )
}
