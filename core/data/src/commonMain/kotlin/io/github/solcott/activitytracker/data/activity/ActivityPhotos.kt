package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AspectRatio

/** A photo picked and compressed on the device ([mimeType] WebP or JPEG), not yet uploaded. */
class NewPhoto(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is NewPhoto &&
                bytes.contentEquals(other.bytes) &&
                mimeType == other.mimeType &&
                width == other.width &&
                height == other.height

    override fun hashCode(): Int = 31 * (31 * (31 * bytes.contentHashCode() + mimeType.hashCode()) + width) + height
}

/** One entry of an activity's edited media list, in order. */
sealed interface MediaEdit {
    /** A photo the record already has, named by its blob's CID. */
    data class Keep(val cid: String) : MediaEdit

    data class Add(val photo: NewPhoto) : MediaEdit
}

/**
 * The athlete's changes to their activity's details. Blank text clears a field; a null [diveSite] leaves the site as it is (the activity
 * isn't a dive).
 */
data class ActivityEdit(val title: String, val description: String, val diveSite: String?, val media: List<MediaEdit>)

/** The blob CID of a photo the record holds, or null for a kind this client doesn't know. */
val ActivityMedia.cid: String?
    get() = (this as? ActivityMedia.Image)?.image?.ref?.link

/** Uploads [photo] to the athlete's PDS, as a member of [ActivityRecord.media]. The record must be written soon or the PDS drops it. */
internal suspend fun SessionClients.upload(photo: NewPhoto): ActivityMedia.Image =
    ActivityMedia.Image(
        image = pds.uploadBlob(photo.bytes, photo.mimeType),
        aspectRatio = AspectRatio(photo.width.coerceAtLeast(1), photo.height.coerceAtLeast(1)),
    )

/**
 * [record]'s photos as views: the AppView's own ([served]) where it has the photo, else straight from the athlete's PDS ([ownBlobUrl]), for
 * a photo the AppView hasn't indexed yet.
 */
internal fun mediaViews(
    record: ActivityRecord,
    served: List<ActivityMediaView>?,
    ownBlobUrl: (cid: String) -> String,
): List<ActivityMediaView>? =
    record.media
        ?.mapNotNull { media ->
            val image = media as? ActivityMedia.Image ?: return@mapNotNull null
            val cid = image.image.ref.link
            served?.firstOrNull { (it as? ActivityMediaView.Image)?.fullsize?.endsWith("/$cid") == true }
                ?: ActivityMediaView.Image(fullsize = ownBlobUrl(cid), alt = image.alt, aspectRatio = image.aspectRatio)
        }
        ?.ifEmpty { null }
