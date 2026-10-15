package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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

/**
 * The bytes of photos this device uploaded this session, by blob CID, until the AppView serves them. A non-public activity's photo lives in
 * a tier space, which the PDS's public `sync.getBlob` won't serve, so the writing device shows it from these ([url]) instead.
 */
class HeldPhotos {
    private val photos = MutableStateFlow(emptyMap<String, NewPhoto>())

    fun hold(cid: String, photo: NewPhoto) = photos.update { it + (cid to photo) }

    operator fun get(cid: String): NewPhoto? = photos.value[cid]

    operator fun contains(cid: String): Boolean = cid in photos.value

    fun drop(cids: Collection<String>) {
        if (cids.any { it in photos.value }) photos.update { it - cids.toSet() }
    }

    /** Lets go of the photos [view] has the AppView serve. */
    fun dropServed(view: ActivityView) {
        if (photos.value.isEmpty()) return
        drop(
            view.media.orEmpty().filterIsInstance<ActivityMediaView.Image>().filterNot { isHeld(it.fullsize) }.map { photoCid(it.fullsize) }
        )
    }

    companion object {
        /** The URL scheme a held photo shows under; the app's image loader answers it from [get]. */
        const val SCHEME = "heldphoto"

        fun url(cid: String): String = "$SCHEME://blob/$cid"

        fun isHeld(url: String): Boolean = url.startsWith("$SCHEME:")
    }
}

/**
 * The blob CID a photo URL names: its last path segment, whatever its query. That holds for the AppView's URLs, public or signed for one
 * viewer, and for a held photo's ([HeldPhotos.url]).
 */
fun photoCid(url: String): String = url.substringBefore('?').substringBefore('#').substringAfterLast('/')

/**
 * Uploads [photo] to the athlete's PDS, as a member of [ActivityRecord.media], and [held] keeps its bytes. The record must be written soon
 * or the PDS drops it.
 */
internal suspend fun SessionClients.upload(photo: NewPhoto, held: HeldPhotos): ActivityMedia.Image =
    ActivityMedia.Image(
            image = pds.uploadBlob(photo.bytes, photo.mimeType),
            aspectRatio = AspectRatio(photo.width.coerceAtLeast(1), photo.height.coerceAtLeast(1)),
        )
        .also { held.hold(it.image.ref.link, photo) }

/**
 * [record]'s photos as views: the AppView's own ([served]) where it has the photo, letting [held] drop its bytes; else, for a photo it
 * hasn't indexed yet, the bytes this device uploaded. Without those, a [Visibility.PUBLIC] photo loads from the athlete's PDS
 * ([ownBlobUrl]), but a non-public one can't (the PDS's public endpoint refuses a blob only a space holds), so it keeps its held URL and
 * shows as a placeholder.
 */
internal fun mediaViews(
    record: ActivityRecord,
    served: List<ActivityMediaView>?,
    visibility: String,
    held: HeldPhotos,
    ownBlobUrl: (cid: String) -> String,
): List<ActivityMediaView>? {
    // A view rewritten before may show held photos already: those aren't the AppView's.
    val servedByCid =
        served
            .orEmpty()
            .filterIsInstance<ActivityMediaView.Image>()
            .filterNot { HeldPhotos.isHeld(it.fullsize) }
            .associateBy { photoCid(it.fullsize) }
    held.drop(servedByCid.keys)
    return record.media
        ?.mapNotNull { media ->
            val image = media as? ActivityMedia.Image ?: return@mapNotNull null
            val cid = image.image.ref.link
            servedByCid[cid]
                ?: ActivityMediaView.Image(
                    fullsize = if (cid !in held && visibility == Visibility.PUBLIC) ownBlobUrl(cid) else HeldPhotos.url(cid),
                    alt = image.alt,
                    aspectRatio = image.aspectRatio,
                )
        }
        ?.ifEmpty { null }
}
