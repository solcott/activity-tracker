package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.app.forTier
import io.github.solcott.activitytracker.lexicon.app.heldCategories
import io.github.solcott.activitytracker.lexicon.app.holdsCore
import io.github.solcott.activitytracker.lexicon.app.mergeStreams
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.atproto.SyncGetBlob
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * An activity split by tier, as its [DetailCategory]s' visibilities say: the activity record at [activity]'s place without the fields of
 * narrower categories, a details record in each narrower tier that holds any, and a streams record in each tier that holds any channel. All
 * share the activity's rkey, and where each is stored is its only visibility.
 */
data class ActivityLayout(
    /** The activity's visibility: where [record] goes. */
    val activity: String,
    val record: ActivityRecord,
    /** By tier, each narrower than [activity]. */
    val details: Map<String, ActivityDetailsRecord>,
    /** By tier. */
    val streams: Map<String, ActivityStreamData>,
) {
    /** The tiers this holds anything in, narrowest first: the order a new activity's records are queued in. */
    val tiersNarrowestFirst: List<String>
        get() = Visibility.all.reversed().filter { it == activity || it in details || it in streams }

    companion object {
        /**
         * [record] (the whole activity, every field exact) and [streams] at [activity], with each category at its visibility in
         * [categories] (capped at the activity, the activity's when missing). A hidden start leaves the record the start of its local day,
         * and a hidden map leaves it no polyline; core channels, lengths, sets and lap and leg structure stay at the activity's tier.
         */
        fun split(
            record: ActivityRecord,
            streams: ActivityStreamData?,
            uri: AtUri,
            activity: String,
            categories: Map<String, String>,
        ): ActivityLayout {
            val tier = Visibility.known(activity)
            return ActivityLayout(
                activity = tier,
                record = DetailCategory.recordFor(record, tier, categories),
                details = DetailCategory.detailsFor(record, uri, tier, categories),
                streams = streams?.let { DetailCategory.streamsFor(it, tier, categories) }.orEmpty(),
            )
        }

        /** [draft], as its [record] at [uri], split by its categories; its streams fitted to the blob limit first, so every part fits. */
        fun split(draft: ActivityDraft, record: ActivityRecord, uri: AtUri): ActivityLayout =
            split(record, draft.streams?.let(PdsActivityStreamsStore::fitted), uri, draft.visibility, draft.categories)
    }
}

/** What one place holds of an activity: its record, details record and streams data, each null when it holds none. */
internal data class PlaceRecords(
    val activity: ActivityRecord? = null,
    val details: ActivityDetailsRecord? = null,
    val streams: ActivityStreamData? = null,
) {
    val isEmpty: Boolean
        get() = activity == null && details == null && streams == null
}

/**
 * Which of an activity's data one place holds: the activity record ([record]), the summary fields of [summary]'s categories (on the record
 * if it's here, else on a details record), the channels and lap and leg fields of [channels]' categories, and the core channels ([core]).
 */
internal data class PlaceShare(
    val record: Boolean = false,
    val summary: Set<String> = emptySet(),
    val channels: Set<String> = emptySet(),
    val core: Boolean = false,
) {
    infix fun union(other: PlaceShare) =
        PlaceShare(record || other.record, summary + other.summary, channels + other.channels, core || other.core)

    /** This share's records, cut from the activity's [full] record and [data]. */
    fun records(full: ActivityRecord, data: ActivityStreamData?, uri: AtUri): PlaceRecords {
        val activity =
            if (record) DetailCategory.SUMMARY_FIELDS.filter { it.category !in summary }.fold(full) { r, f -> f.strip(r) } else null
        val details =
            if (record) null
            else
                DetailCategory.SUMMARY_FIELDS.filter { it.category in summary && it.isSet(full) }
                    .takeIf { it.isNotEmpty() }
                    ?.fold(ActivityDetailsRecord(activity = uri, createdAt = full.createdAt)) { d, f -> f.copy(d, full) }
        return PlaceRecords(activity, details, data?.forTier(channels, core))
    }
}

/** What every place holds of an activity, by tier ([Visibility.all]). */
internal data class Shares(val byTier: Map<String, PlaceShare>) {
    operator fun get(tier: String): PlaceShare = byTier[tier] ?: PlaceShare()

    infix fun union(other: Shares) = Shares(Visibility.all.associateWith { this[it] union other[it] })

    companion object {
        /**
         * The shares of an activity at [activity] whose categories' summary fields are at [summary]'s tiers and channels at [streams]',
         * with the core channels at [core]'s.
         */
        fun of(activity: String, summary: Map<String, String>, streams: Map<String, String>, core: String = activity) =
            Shares(
                Visibility.all.associateWith { tier ->
                    PlaceShare(
                        record = tier == activity,
                        summary = summary.filterValues { it == tier }.keys,
                        channels = streams.filterValues { it == tier }.keys,
                        core = tier == core,
                    )
                }
            )

        /** Every category of an activity at [activity] at its tier in [tiers], for summary fields and channels alike. */
        fun of(activity: String, tiers: Map<String, String>) = of(activity, tiers, tiers)
    }
}

/**
 * The signed-in athlete's activity as stored now, read from every place ([places], by tier), straight from their PDS rather than the
 * AppView, which may lag a write.
 */
internal class StoredActivity(val uri: AtUri, val places: Map<String, PlaceRecords>) {
    /** The whole activity: its narrowest record with every details record laid over it, widest first. Null when no place holds it. */
    val full: ActivityRecord? =
        Visibility.all
            .mapNotNull { places[it]?.activity }
            .lastOrNull()
            ?.let { record -> effectiveRecord(record, Visibility.all.mapNotNull { places[it]?.details }) }

    /** Its streams from every place, merged, a narrower place's winning. */
    val data: ActivityStreamData? = mergeStreams(Visibility.all.mapNotNull { places[it]?.streams })

    /** The tiers holding its record. */
    val recordTiers: List<String>
        get() = Visibility.all.filter { places[it]?.activity != null }

    /** What each place holds now. */
    val shares: Shares
        get() {
            val exactStart = full?.startedAt
            return Shares(
                Visibility.all.associateWith { tier ->
                    val held = places[tier] ?: PlaceRecords()
                    val summary =
                        held.activity?.let { record ->
                            DetailCategory.SUMMARY_FIELDS.filter { it.category != DetailCategory.START_TIME && it.isSet(record) }
                                .map { it.category }
                                .toSet() + listOfNotNull(DetailCategory.START_TIME.takeIf { record.startedAt == exactStart })
                        } ?: held.details?.let { d -> DetailCategory.SUMMARY_FIELDS.filter { it.inDetails(d) }.map { it.category }.toSet() }
                    PlaceShare(
                        record = held.activity != null,
                        summary = summary.orEmpty(),
                        channels = held.streams?.heldCategories().orEmpty(),
                        core = held.streams?.holdsCore() == true,
                    )
                }
            )
        }

    /**
     * Where each category is stored now, for summary fields and for channels: its narrowest place when it's in several (an interrupted
     * move), so a repair never widens it. A category with no data is absent.
     */
    fun narrowestTiers(): Pair<Map<String, String>, Map<String, String>> {
        val now = shares
        fun narrowest(of: (PlaceShare) -> Set<String>) =
            DetailCategory.all.mapNotNull { c -> Visibility.all.lastOrNull { c in of(now[it]) }?.let { c to it } }.toMap()
        return narrowest { it.summary } to narrowest { it.channels }
    }

    /**
     * What each place should hold when every category goes to the narrowest place it's in now, capped at the narrowest copy of the record:
     * what a repair after an interrupted save leaves, never wider than anything is now. Null when no place holds the record.
     */
    fun narrowestShares(): Shares? {
        val activity = recordTiers.lastOrNull() ?: return null
        val (summary, channels) = narrowestTiers()
        fun capped(tiers: Map<String, String>) = tiers.mapValues { (_, t) -> Visibility.capped(t, activity) }
        return Shares.of(activity, capped(summary), capped(channels), coreTier?.let { Visibility.capped(it, activity) } ?: activity)
    }

    /** The narrowest tier holding the core channels, or null when no streams record holds them. */
    val coreTier: String?
        get() = Visibility.all.lastOrNull { shares[it].core }

    /** Whether it's split: some details record, or streams in more than one place. */
    val isSplit: Boolean
        get() = places.values.any { it.details != null } || places.values.count { it.streams != null } > 1
}

/**
 * Reads the signed-in athlete's activities from every place, and rewrites them by tier now, as direct calls rather than through the outbox,
 * so a failure shows ([save]).
 */
@SingleIn(SessionScope::class)
@Inject
class ActivityPlaces(private val clients: SessionClients, private val clock: Clock, logger: Logger) {
    private val log = logger.withTag("ActivityPlaces")
    private val mutex = Mutex()
    private var repaired = false

    /**
     * Once a session, finishes the athlete's activities that an interrupted save (here or on another device) left in two places, split or
     * with the record itself in two: each category goes to the narrowest place it's in, capped at the activity's narrowest record, so this
     * never widens. Best effort: a failure is logged and left for the next session.
     */
    suspend fun repairOnce() {
        if (repaired || !clients.canUseSpaces) return
        runSuspendCatching { mutex.withLock { if (!repaired) repair().also { repaired = true } } }
            .onFailure { e -> log.w(e) { "Couldn't repair the athlete's split activities" } }
    }

    private suspend fun repair() {
        val streams = Visibility.all.map { clients.listOwn(Nsid.FEED_ACTIVITY_STREAMS, clients.placeOf(it)).keys }
        val details = AthleteSpaces.TIERS.flatMap { clients.listOwn(Nsid.FEED_ACTIVITY_DETAILS, clients.placeOf(it)).keys }.toSet()
        val inTwo = streams.flatten().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        // A move cut short between writing the new place and deleting the old leaves the record in both, split or not.
        val records = Visibility.all.flatMap { clients.listOwn(Nsid.FEED_ACTIVITY, clients.placeOf(it)).keys }
        val recordsInTwo = records.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        for (rkey in details + inTwo + recordsInTwo) {
            val stored = read(AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey))
            val desired = stored.narrowestShares() ?: continue
            if ((stored.isSplit || stored.recordTiers.size > 1) && desired != stored.shares) save(stored, desired)
        }
    }

    /** [uri] as every place holds it now. A session without access to its spaces reads only the repo. */
    internal suspend fun read(uri: AtUri): StoredActivity = coroutineScope {
        val rkey = requireNotNull(uri.rkey)
        val tiers = if (clients.canUseSpaces) Visibility.all else listOf(Visibility.PUBLIC)
        val places =
            tiers
                .map { tier ->
                    async {
                        val place = clients.placeOf(tier)
                        val activity = clients.readOwn(Nsid.FEED_ACTIVITY, rkey, place)
                        val details = if (place == null) null else clients.readOwn(Nsid.FEED_ACTIVITY_DETAILS, rkey, place)
                        val streams = clients.readOwn(Nsid.FEED_ACTIVITY_STREAMS, rkey, place)
                        tier to
                            PlaceRecords(
                                activity = activity?.let { decodeOrNull { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it) } },
                                details =
                                    details?.let { decodeOrNull { AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), it) } },
                                streams = streams?.let { streamData(it, tier) },
                            )
                    }
                }
                .awaitAll()
                .toMap()
        StoredActivity(uri, places)
    }

    /**
     * Rewrites [stored] so each place holds what [desired] says. First, narrowest place first, every place takes the union of what it holds
     * and what it will hold; then, widest first, each place is cut down to what it will hold and the records it won't hold are deleted. So
     * a failure part way leaves every field and channel stored in at least one place, never wider than [desired] or where it was, and
     * saving again (read afresh) finishes it. [full] replaces the activity's whole record when given (an edit of its gear). Throws on
     * failure.
     */
    internal suspend fun save(stored: StoredActivity, desired: Shares, full: ActivityRecord? = stored.full) {
        full ?: return
        val data = stored.data
        val state = stored.places.toMutableMap()
        val union = stored.shares union desired
        for (tier in Visibility.all.reversed()) write(stored.uri, tier, union[tier].records(full, data, stored.uri), state, prune = false)
        for (tier in Visibility.all) write(stored.uri, tier, desired[tier].records(full, data, stored.uri), state, prune = true)
    }

    /** [stored] with the activity at [activity] and each category at its tier in [tiers]; the core channels move to the activity. */
    internal suspend fun save(stored: StoredActivity, activity: String, tiers: Map<String, String>) =
        save(stored, Shares.of(activity, tiers))

    /**
     * Writes each of [want]'s records [tier]'s place lacks or holds otherwise, and with [prune] deletes the ones [want] doesn't have,
     * keeping [state] to what the place holds after.
     */
    private suspend fun write(uri: AtUri, tier: String, want: PlaceRecords, state: MutableMap<String, PlaceRecords>, prune: Boolean) {
        val have = state[tier] ?: PlaceRecords()
        val place = clients.placeOf(tier)
        val rkey = requireNotNull(uri.rkey)
        var now = have
        if (want.activity != null && want.activity != have.activity) {
            clients.writeOwn(
                Nsid.FEED_ACTIVITY,
                rkey,
                AtJson.encodeToJsonElement(ActivityRecord.serializer(), want.activity).asObject(),
                place,
            )
            now = now.copy(activity = want.activity)
        }
        if (want.details != null && want.details != have.details) {
            val record = AtJson.encodeToJsonElement(ActivityDetailsRecord.serializer(), want.details).asObject()
            clients.writeOwn(Nsid.FEED_ACTIVITY_DETAILS, rkey, record, place)
            now = now.copy(details = want.details)
        }
        if (want.streams != null && want.streams != have.streams) {
            writeStreams(uri, want.streams, place)
            now = now.copy(streams = want.streams)
        }
        state[tier] = if (prune) prune(rkey, place, want, now) else now
    }

    /** Deletes the records [place] holds ([have]) that [want] doesn't, and returns what it holds after. */
    private suspend fun prune(rkey: String, place: SpaceUri?, want: PlaceRecords, have: PlaceRecords): PlaceRecords {
        val gone =
            listOfNotNull(
                Nsid.FEED_ACTIVITY.takeIf { want.activity == null && have.activity != null },
                Nsid.FEED_ACTIVITY_DETAILS.takeIf { want.details == null && have.details != null },
                Nsid.FEED_ACTIVITY_STREAMS.takeIf { want.streams == null && have.streams != null },
            )
        gone.forEach { clients.deleteOwn(it, rkey, place) }
        return PlaceRecords(
            activity = have.activity.takeIf { Nsid.FEED_ACTIVITY !in gone },
            details = have.details.takeIf { Nsid.FEED_ACTIVITY_DETAILS !in gone },
            streams = have.streams.takeIf { Nsid.FEED_ACTIVITY_STREAMS !in gone },
        )
    }

    private suspend fun writeStreams(uri: AtUri, data: ActivityStreamData, place: SpaceUri?) {
        val ref = clients.pds.uploadBlob(PdsActivityStreamsStore.encode(data), ActivityStreamsRecord.MIME_TYPE)
        val record = ActivityStreamsRecord(activity = uri, channels = data.channels, createdAt = clock.now(), data = ref)
        clients.writeOwn(
            Nsid.FEED_ACTIVITY_STREAMS,
            requireNotNull(uri.rkey),
            AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).asObject(),
            place,
        )
    }

    /** The data a stored streams record names, from the repo's blobs or the tier space's; null when it can't be read. */
    private suspend fun streamData(value: JsonObject, tier: String): ActivityStreamData? {
        val cid = decodeOrNull { AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), value) }?.data?.ref?.link?.let(::Cid)
        val bytes = cid?.let { blob(it, clients.placeOf(tier)) }
        return bytes?.let { decodeOrNull { AtJson.decodeFromString(ActivityStreamData.serializer(), it.decodeToString()) } }
    }

    /** The blob [cid]'s bytes at [place] (null: the repo); null when the space doesn't have it. */
    private suspend fun blob(cid: Cid, place: SpaceUri?): ByteArray? =
        if (place == null) {
            clients.pds.queryBytes(SyncGetBlob.method, SyncGetBlob.Params(clients.did, cid)).bytes
        } else {
            (clients.spaces.getBlob(place, clients.did, cid) as? SpaceRead.Found)?.value?.bytes
        }
}

/** [block]'s record, or null when it doesn't decode: any client can write the athlete's records. */
private inline fun <T> decodeOrNull(block: () -> T): T? = runCatching(block).getOrNull()

private fun JsonElement.asObject() = this as JsonObject
