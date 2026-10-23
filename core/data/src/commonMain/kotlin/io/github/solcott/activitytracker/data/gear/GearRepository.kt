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
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.data.ownBlobUrl
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActorGearItem
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.GetActorGear
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.GetGearActivities
import io.github.solcott.activitytracker.lexicon.app.MountGraph
import io.github.solcott.activitytracker.lexicon.app.MountProblem
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SyncGetBlob
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import kotlin.time.Clock
import kotlin.time.Instant
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
    fun uri(did: Did): AtUri = uriOf(did, rkey)

    companion object {
        /** The canonical URI of [did]'s item at [rkey]. */
        fun uriOf(did: Did, rkey: String): AtUri = AtUri("at://${did.value}/${Nsid.GEAR_ITEM}/$rkey")
    }
}

/** The athlete's [items] and their [collections], whose members are limited to those items. */
@Immutable data class GearAndCollections(val items: List<GearItem>, val collections: List<GearCollection>)

/** A [collection] with its [members] in order, and every one of the athlete's [items]. */
@Immutable data class GearCollectionItems(val collection: GearCollection, val members: List<GearItem>, val items: List<GearItem>)

/** What a save does to an item's photo. */
sealed interface GearPhotoEdit {
    data object Keep : GearPhotoEdit

    data object Remove : GearPhotoEdit

    data class Replace(val photo: NewPhoto) : GearPhotoEdit
}

/** The athlete has no gear item at the rkey asked for. */
class GearNotFoundException : NoSuchElementException("No such gear")

/** The athlete has no gear collection at the rkey asked for. */
class GearCollectionNotFoundException : NoSuchElementException("No such gear collection")

/** A mount [GearMounts] refuses, for [problem]; nothing was written. */
class GearMountException(val problem: MountProblem) : IllegalArgumentException("Mount refused: $problem")

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
class GearRepository(
    private val clients: SessionClients,
    private val clock: Clock,
    private val feed: FeedRepository,
    private val collections: GearCollections,
    logger: Logger,
) {
    private val log = logger.withTag("GearRepository")
    private val held: HeldPhotos = feed.heldPhotos

    /** Counts this session's saves and deletes, so the gear list reads again after each. */
    val changes: StateFlow<Int>
        field = MutableStateFlow(0)

    /** The athlete's DID, which every item's canonical URI ([GearItem.uri]) names. */
    val did: Did
        get() = clients.did

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

    /**
     * The item at [uri] and its usage as this viewer may see it (`gear/gear-usage`), from the AppView, so it lags the athlete's edits until
     * it catches up. The AppView answers `NotFound` when the viewer may not see it.
     */
    suspend fun usage(uri: AtUri): GetGear.Output = clients.appView.query(GetGear.method, GetGear.Params(uri))

    /** [usage] as a stream: loading, then the item's usage or why it couldn't be read (`DataError.Api` `NotFound` for a hidden item). */
    fun usageOutcome(uri: AtUri): Flow<Outcome<GetGear.Output>> = outcomeFlow { usage(uri) }

    /** Every item of [actor]'s this viewer may see, each with its totals for them, in use first, then by name, a page at a time. */
    suspend fun actorGear(actor: String): List<ActorGearItem> {
        val items = mutableListOf<ActorGearItem>()
        var cursor: String? = null
        repeat(MAX_GEAR_PAGES) {
            val page = clients.appView.query(GetActorGear.method, GetActorGear.Params(actor, Paging.MAX_LIMIT, cursor))
            items += page.items
            cursor = page.cursor ?: return items
        }
        return items
    }

    /**
     * A page of the activities counted in [uri]'s usage for this viewer, newest first, with the athlete's own edits and deletes this
     * session laid over them as the feed does.
     */
    suspend fun activitiesPage(uri: AtUri, cursor: String?): Outcome<Page<ActivityView, Unit>> = outcomeOf {
        val page = clients.appView.query(GetGearActivities.method, GetGearActivities.Params(uri, cursor = cursor))
        Page(feed.deletes.filter(page.activities).map(feed.rewrites::applyTo), Unit, page.cursor)
    }

    /** [items] as a stream: loading, then the items or why they couldn't be read. */
    fun itemsOutcome(): Flow<Outcome<List<GearItem>>> = outcomeFlow { items() }

    /** [items] and the athlete's collections of them ([GearCollections.all]), read together, as a stream. */
    fun gearAndCollectionsOutcome(): Flow<Outcome<GearAndCollections>> = outcomeFlow {
        val items = items()
        GearAndCollections(items, collections.all(items))
    }

    /**
     * The collection at [rkey] with its member items and all of [items], as a stream; [GearCollectionNotFoundException] when there's none.
     */
    fun collectionOutcome(rkey: String): Flow<Outcome<GearCollectionItems>> = outcomeFlow {
        val items = items()
        val collection = collections.collection(rkey, items) ?: throw GearCollectionNotFoundException()
        val byUri = items.associateBy { it.uri(clients.did) }
        GearCollectionItems(collection, collection.items.mapNotNull { byUri[it] }, items)
    }

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
        val saved = record.copy(photo = photoRef, mounts = endedOnRetiring(existing?.record, record))
        clients.writeOwn(Nsid.GEAR_ITEM, rkey, encode(saved), clients.placeOf(to))
        for (old in existing?.places.orEmpty() - to) clients.deleteOwn(Nsid.GEAR_ITEM, rkey, clients.placeOf(old))
        changes.update { it + 1 }
        val item = GearItem(rkey, saved, to)
        return item.copy(photoUrl = photoUrl(item))
    }

    /**
     * Mounts [component] on [parent], another of the athlete's items, from [from] (`gear/gear-components`). Throws [GearMountException]
     * when [GearMounts] refuses it against the athlete's items, or the parent isn't one of them, before writing anything.
     */
    suspend fun mount(component: GearItem, parent: AtUri, from: Instant): GearItem {
        val items = items()
        val current = items.firstOrNull { it.rkey == component.rkey } ?: throw GearNotFoundException()
        val mounts = GearMounts.sorted(current.record.mounts.orEmpty() + GearMount(parent, from))
        checkMounts(items, mapOf(current to mounts), parent)
        return save(current, current.record.copy(mounts = mounts), current.visibility)
    }

    /** Ends [component]'s open mount at [at]; it's returned unchanged when it isn't mounted. */
    suspend fun unmount(component: GearItem, at: Instant): GearItem {
        val current = item(component.rkey) ?: throw GearNotFoundException()
        val mounts = ended(current.record.mounts, at) ?: return current
        if (mounts == current.record.mounts) throw GearMountException(MountProblem.EMPTY)
        return save(current, current.record.copy(mounts = mounts), current.visibility)
    }

    /**
     * Ends [old]'s open mount at [at] and mounts [new] on the same parent from [at], written in that order. Both are checked first, so a
     * refused replacement writes nothing. Throws [GearMountException] with [MountProblem.EMPTY] when [old] isn't mounted.
     */
    suspend fun replace(old: GearItem, new: GearItem, at: Instant): GearItem {
        val items = items()
        val (oldItem, newItem) =
            listOf(old, new).map { wanted -> items.firstOrNull { it.rkey == wanted.rkey } ?: throw GearNotFoundException() }
        val parent = GearMounts.open(oldItem.record.mounts)?.parent
        val oldMounts = ended(oldItem.record.mounts, at)
        if (parent == null || oldMounts == null || oldMounts == oldItem.record.mounts) throw GearMountException(MountProblem.EMPTY)
        val newMounts = GearMounts.sorted(newItem.record.mounts.orEmpty() + GearMount(parent, at))
        checkMounts(items, mapOf(oldItem to oldMounts, newItem to newMounts), parent)
        save(oldItem, oldItem.record.copy(mounts = oldMounts), oldItem.visibility)
        return save(newItem, newItem.record.copy(mounts = newMounts), newItem.visibility)
    }

    /**
     * Deletes [item] from every place holding it, then from every collection listing it. That second pass is best effort: a failure is
     * logged, and readers skip the deleted item anyway ([GearCollections.all]).
     */
    suspend fun delete(item: GearItem) {
        for (place in item.places) clients.deleteOwn(Nsid.GEAR_ITEM, item.rkey, clients.placeOf(place))
        changes.update { it + 1 }
        runSuspendCatching { collections.removeItem(item.uri(clients.did)) }
            .onFailure { e -> log.w(e) { "Couldn't remove a deleted item from its collections" } }
    }

    /** Throws [GearMountException] when [GearMounts] refuses [changed]'s new mounts among [items], or [parent] isn't one of them. */
    private fun checkMounts(items: List<GearItem>, changed: Map<GearItem, List<GearMount>>, parent: AtUri) {
        val did = clients.did
        if (items.none { it.uri(did) == parent }) throw GearMountException(MountProblem.FOREIGN_PARENT)
        val byRkey = changed.mapKeys { it.key.rkey }
        val graph = MountGraph.of(items.associate { it.uri(did) to (byRkey[it.rkey] ?: it.record.mounts.orEmpty()) })
        for ((item, mounts) in changed) GearMounts.problem(item.uri(did), mounts, graph)?.let { throw GearMountException(it) }
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

/**
 * [mounts] with the open one ended at [at], or dropped when it starts at or after [at]; null when none is open. A mount ending before it
 * starts would be refused, so it's left unchanged: the caller tells that from a change.
 */
private fun ended(mounts: List<GearMount>?, at: Instant): List<GearMount>? =
    GearMounts.open(mounts)?.let { open ->
        if (at <= open.from) mounts else mounts.orEmpty().map { if (it == open) it.copy(to = at) else it }
    }

/** [record]'s mounts, with the open one ended on its retirement date when [record] retires an item [before] was in use. */
private fun endedOnRetiring(before: GearItemRecord?, record: GearItemRecord): List<GearMount>? {
    val retiredAt = record.retiredAt
    val open = GearMounts.open(record.mounts)
    return when {
        before == null || before.retiredAt != null || retiredAt == null || open == null -> record.mounts
        // Retired before the mount began: it never really started.
        retiredAt <= open.from -> record.mounts.orEmpty() - open
        else -> ended(record.mounts, retiredAt)
    }
}

/** 2,000 items: more than any athlete keeps, and a bound on a misbehaving cursor. */
private const val MAX_GEAR_PAGES = 20
