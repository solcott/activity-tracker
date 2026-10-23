package io.github.solcott.activitytracker.data.gear

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.deleteOwn
import io.github.solcott.activitytracker.data.activity.listOwn
import io.github.solcott.activitytracker.data.activity.writeOwn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** One of the athlete's gear collections: its [record] under [rkey]. [items] are its members that still exist, in its order. */
@Immutable data class GearCollection(val rkey: String, val record: GearCollectionRecord, val items: List<AtUri> = record.items.orEmpty())

/**
 * The athlete's gear collections (gear/gear-collections): `gear.collection` records in their `self` space only, which the AppView never
 * stores. Membership lives on the collection, so an item's collections are found by scanning them. Saves are direct calls, like gear saves,
 * and refuse a session whose grant predates the record with [GearNeedsSignInException].
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // The reads and writes, and membership from the item's side.
class GearCollections(private val clients: SessionClients, private val clock: Clock) {
    private val mutex = Mutex()

    /** Counts this session's saves and deletes, so screens showing collections read again after each. */
    val changes: StateFlow<Int>
        field = MutableStateFlow(0)

    /** Whether this session can save collections: not one signed in before its `space:` grant listed `gear.collection`. */
    val canSave: Boolean
        get() = clients.canWriteToSpaces(Nsid.GEAR_COLLECTION)

    /**
     * Every collection, by name, its members limited to [items] (the athlete's gear) so a deleted item is never shown. A record that
     * doesn't decode is skipped; a session without the `space:` scope has none.
     */
    suspend fun all(items: List<GearItem>): List<GearCollection> {
        val existing = items.map { it.uri(clients.did) }.toSet()
        return stored().map { it.copy(items = it.items.filter { uri -> uri in existing }) }.sortedBy { it.record.name.lowercase() }
    }

    /** Every collection, by name, with its members as stored: for its name and whether it lists an item, never to show its members. */
    suspend fun all(): List<GearCollection> = stored().sortedBy { it.record.name.lowercase() }

    /** The collection at [rkey] ([all]), or null when there's none. */
    suspend fun collection(rkey: String, items: List<GearItem>): GearCollection? = all(items).firstOrNull { it.rkey == rkey }

    /**
     * Saves [record] as [existing] (null: a new collection under a new TID) and returns it. Its items are de-duplicated and capped at
     * [GearCollectionRecord.MAX_ITEMS].
     */
    suspend fun save(existing: GearCollection?, record: GearCollectionRecord): GearCollection = mutex.withLock {
        if (!canSave) throw GearNeedsSignInException()
        val rkey = existing?.rkey ?: Tid.next(clock)
        val saved = record.copy(items = record.items?.distinct()?.take(GearCollectionRecord.MAX_ITEMS)?.ifEmpty { null })
        write(rkey, saved)
        changes.update { it + 1 }
        GearCollection(rkey, saved)
    }

    /** Deletes the collection at [rkey]. Its items, and every activity's gear, are untouched. */
    suspend fun delete(rkey: String) {
        if (!canSave) throw GearNeedsSignInException()
        clients.deleteOwn(Nsid.GEAR_COLLECTION, rkey, self())
        changes.update { it + 1 }
    }

    /** The rkeys of the collections listing [item]. */
    suspend fun collectionsOf(item: AtUri): Set<String> = stored().filter { item in it.items }.map { it.rkey }.toSet()

    /**
     * Makes [item] a member of exactly the collections in [rkeys], read afresh so a change on another device isn't lost: it's added at the
     * end of each it joins, while that collection has room, and removed from the others. Only collections that change are written.
     */
    suspend fun setMembership(item: AtUri, rkeys: Set<String>) = mutex.withLock {
        if (!canSave) throw GearNeedsSignInException()
        var changed = false
        for (collection in stored()) {
            val listed = item in collection.items
            val wanted = collection.rkey in rkeys
            val items =
                when {
                    wanted && !listed && collection.items.size < GearCollectionRecord.MAX_ITEMS -> collection.items + item
                    !wanted && listed -> collection.items - item
                    else -> continue
                }
            write(collection.rkey, collection.record.copy(items = items.ifEmpty { null }))
            changed = true
        }
        if (changed) changes.update { it + 1 }
    }

    /** Removes [item], just deleted, from every collection listing it. */
    suspend fun removeItem(item: AtUri) = setMembership(item, emptySet())

    private suspend fun stored(): List<GearCollection> {
        if (!clients.canUseSpaces) return emptyList()
        return clients.listOwn(Nsid.GEAR_COLLECTION, self()).mapNotNull { (rkey, value) -> decode(value)?.let { GearCollection(rkey, it) } }
    }

    private suspend fun write(rkey: String, record: GearCollectionRecord) =
        clients.writeOwn(
            Nsid.GEAR_COLLECTION,
            rkey,
            AtJson.encodeToJsonElement(GearCollectionRecord.serializer(), record) as JsonObject,
            self(),
        )

    private fun self() = clients.tiers.uri(AthleteSpace.SELF)

    private fun decode(value: JsonObject): GearCollectionRecord? =
        try {
            AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), value)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            null
        }
}
