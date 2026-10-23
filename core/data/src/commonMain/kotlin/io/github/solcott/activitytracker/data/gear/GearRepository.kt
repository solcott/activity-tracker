package io.github.solcott.activitytracker.data.gear

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.deleteOwn
import io.github.solcott.activitytracker.data.activity.listOwn
import io.github.solcott.activitytracker.data.activity.placeOf
import io.github.solcott.activitytracker.data.activity.writeOwn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.data.ownBlobUrl
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SyncGetBlob
import io.github.solcott.dataresult.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/**
 * One of the athlete's gear items: its [record] under [rkey], shown at [visibility], the narrowest of the [places] holding it (a move that
 * failed part-way leaves it in two). [photoUrl] shows its photo: the repo's for a Public one, else the bytes this session holds.
 */
@Immutable
data class GearItem(
    val rkey: String,
    val record: GearItemRecord,
    val visibility: String,
    val places: Set<String> = setOf(visibility),
    val photoUrl: String? = null,
) {
    /** Its canonical URI, wherever it's stored. */
    fun uri(did: Did): AtUri = AtUri("at://${did.value}/${Nsid.GEAR_ITEM}/$rkey")
}

/** What a save does to an item's photo. */
sealed interface GearPhotoEdit {
    data object Keep : GearPhotoEdit

    data object Remove : GearPhotoEdit

    data class Replace(val photo: NewPhoto) : GearPhotoEdit
}

/** The athlete has no gear item at the rkey asked for. */
class GearNotFoundException : NoSuchElementException("No such gear")

/** This session signed in before gear could be stored in tier spaces, so it can save Public gear only until the athlete signs in again. */
class GearNeedsSignInException : IllegalStateException("Sign in again to save private gear")

/**
 * The athlete's gear (`gear/gear-items`): `gear.item` records in their repo (Public) and in each tier space (Followers, Mutuals, Only me),
 * read and written with their own session. An item keeps its rkey wherever it moves, and moves write the new place before deleting the old,
 * so a failure leaves it in both and [items] shows it once, at the narrower. Saves are direct calls, like profile details: a failed save
 * shows, and saving again finishes a move.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // The reads and writes, and the photo moves they need.
class GearRepository(private val clients: SessionClients, private val clock: Clock, feed: FeedRepository, logger: Logger) {
    private val log = logger.withTag("GearRepository")
    private val held: HeldPhotos = feed.heldPhotos

    /** Counts this session's saves and deletes, so the gear list reads again after each. */
    val changes: StateFlow<Int>
        field = MutableStateFlow(0)

    /** Whether this session can save non-Public gear: not one signed in before the space declaration listed `gear.item`. */
    val canSavePrivate: Boolean
        get() = clients.canWriteToSpaces(Nsid.GEAR_ITEM)

    /**
     * Every item, from the repo and the three tier spaces read in parallel. A record another client wrote that doesn't decode is skipped. A
     * session without the `space:` scope reads the repo only.
     */
    suspend fun items(): List<GearItem> = coroutineScope {
        val places = if (clients.canUseSpaces) Visibility.all else listOf(Visibility.PUBLIC)
        val found = places.map { visibility -> async { visibility to listPlace(visibility) } }.awaitAll()
        val byRkey = mutableMapOf<String, GearItem>()
        // Widest first, so a narrower copy replaces a wider one.
        for ((visibility, records) in found) {
            for ((rkey, record) in records) {
                val earlier = byRkey[rkey]
                byRkey[rkey] = GearItem(rkey, record, visibility, earlier?.places.orEmpty() + visibility)
            }
        }
        byRkey.values.map { item -> async { item.copy(photoUrl = photoUrl(item)) } }.awaitAll()
    }

    /** [items] as a stream: loading, then the items or why they couldn't be read. */
    fun itemsOutcome(): Flow<Outcome<List<GearItem>>> = outcomeFlow { items() }

    /** The item at [rkey], or null when the athlete has none there. */
    suspend fun item(rkey: String): GearItem? = items().firstOrNull { it.rkey == rkey }

    /** [item] as a stream: loading, then the item, or [GearNotFoundException] when there's none. */
    fun itemOutcome(rkey: String): Flow<Outcome<GearItem>> = outcomeFlow { item(rkey) ?: throw GearNotFoundException() }

    /**
     * Saves [record] at [visibility] as the item [existing] (null: a new item under a new TID), with [photo] changed as asked, and returns
     * it. It's written to its new place first, creating the tier space if need be, then deleted from every other place holding it. A photo
     * is uploaded where the item goes, so a moved item's photo is uploaded again from where it was. Throws [GearNeedsSignInException] for a
     * non-Public save this session can't make, before writing anything.
     */
    suspend fun save(existing: GearItem?, record: GearItemRecord, visibility: String, photo: GearPhotoEdit = GearPhotoEdit.Keep): GearItem {
        val to = Visibility.known(visibility)
        if (to != Visibility.PUBLIC && !canSavePrivate) throw GearNeedsSignInException()
        val rkey = existing?.rkey ?: Tid.next(clock)
        val photoRef =
            when (photo) {
                GearPhotoEdit.Remove -> null
                is GearPhotoEdit.Replace -> upload(photo.photo)
                GearPhotoEdit.Keep ->
                    existing?.record?.photo?.let { kept ->
                        if (existing.visibility == to) kept
                        else moved(kept, existing.visibility) ?: kept.also { log.w { "Photo not moved" } }
                    }
            }
        val saved = record.copy(photo = photoRef)
        clients.writeOwn(Nsid.GEAR_ITEM, rkey, encode(saved), clients.placeOf(to))
        for (old in existing?.places.orEmpty() - to) clients.deleteOwn(Nsid.GEAR_ITEM, rkey, clients.placeOf(old))
        changes.update { it + 1 }
        val item = GearItem(rkey, saved, to)
        return item.copy(photoUrl = photoUrl(item))
    }

    /** Deletes [item] from every place holding it. */
    suspend fun delete(item: GearItem) {
        for (place in item.places) clients.deleteOwn(Nsid.GEAR_ITEM, item.rkey, clients.placeOf(place))
        changes.update { it + 1 }
    }

    private suspend fun listPlace(visibility: String): List<Pair<String, GearItemRecord>> =
        try {
            clients.listOwn(Nsid.GEAR_ITEM, clients.placeOf(visibility)).mapNotNull { (rkey, value) -> decode(value)?.let { rkey to it } }
        } catch (e: XrpcException) {
            // A session whose grant predates gear may not list a tier: it has no gear there it could have written.
            if (visibility == Visibility.PUBLIC || e.error !in SpaceErrors.denials + SpaceErrors.SCOPE_MISSING) throw e
            emptyList()
        }

    private suspend fun upload(photo: NewPhoto): ActivityMedia.Image =
        ActivityMedia.Image(
                image = clients.pds.uploadBlob(photo.bytes, photo.mimeType),
                aspectRatio = AspectRatio(photo.width.coerceAtLeast(1), photo.height.coerceAtLeast(1)),
            )
            .also { held.hold(it.image.ref.link, photo) }

    /** [photo] uploaded again from [from], keeping its alt text and shape; null when its bytes can't be read there. */
    private suspend fun moved(photo: ActivityMedia.Image, from: String): ActivityMedia.Image? {
        val cid = photo.image.ref.link
        val bytes = held[cid]?.bytes ?: blob(cid, from) ?: return null
        return photo.copy(image = clients.pds.uploadBlob(bytes, photo.image.mimeType))
    }

    /** Where [item]'s photo shows from: the repo for a Public one, else bytes held this session (read from its space when not yet held). */
    private suspend fun photoUrl(item: GearItem): String? {
        val photo = item.record.photo ?: return null
        val cid = photo.image.ref.link
        if (item.visibility != Visibility.PUBLIC && cid !in held) {
            runSuspendCatching { blob(cid, item.visibility) }
                .onFailure { e -> log.w(e) { "Couldn't read a gear photo" } }
                .getOrNull()
                ?.let { held.hold(cid, NewPhoto(it, photo.image.mimeType, photo.aspectRatio?.width ?: 1, photo.aspectRatio?.height ?: 1)) }
        }
        return if (item.visibility == Visibility.PUBLIC) ownBlobUrl(clients.pdsUrl, clients.did, cid) else HeldPhotos.url(cid)
    }

    /** The blob [cid]'s bytes at [visibility]'s place; null when it isn't there. */
    private suspend fun blob(cid: String, visibility: String): ByteArray? {
        val place = clients.placeOf(visibility)
        return if (place == null) clients.pds.queryBytes(SyncGetBlob.method, SyncGetBlob.Params(clients.did, Cid(cid))).bytes
        else (clients.spaces.getBlob(place, clients.did, Cid(cid)) as? SpaceRead.Found)?.value?.bytes
    }

    private fun encode(record: GearItemRecord): JsonObject = AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject

    private fun decode(value: JsonObject): GearItemRecord? =
        try {
            AtJson.decodeFromJsonElement(GearItemRecord.serializer(), value)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
}
