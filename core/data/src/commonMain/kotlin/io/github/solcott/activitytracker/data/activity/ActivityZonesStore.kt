package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.data.profile.TrainingNarrowing
import io.github.solcott.activitytracker.data.profile.ownTrainingVisibility
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityZonesRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Where an activity's zones go: the narrower of its charts' visibility and the athlete's training's. */
object ZonesPlacement {
    /**
     * The visibility for zones of charts at [charts] when training is at [training]. Without training details (null), zones can't be placed
     * wider than [current], where they are now, or Only me for new ones.
     */
    fun target(charts: String, training: String?, current: String? = null): String =
        Visibility.narrower(Visibility.known(charts), Visibility.known(training ?: current ?: Visibility.SELF))
}

/**
 * The signed-in athlete's `feed.activityZones` records. Zone bounds reveal thresholds, so each is stored at [ZonesPlacement.target] of its
 * activity's charts and the athlete's training, read fresh for every write. Every write that could leave zones too wide fails toward
 * narrower: narrowing training moves zones first ([narrowTo]), and a sweep once a session ([sweepOnce]) moves any another device left wide
 * and drops copies an interrupted move left behind. All of it runs under one lock, so two moves never race on a record.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // Placement, moves, the backfill and the sweep share the lock and the listing.
class ActivityZonesStore(
    private val db: AppDatabase,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clients: SessionClients,
    private val clock: Clock,
    logger: Logger,
) : TrainingNarrowing {
    private val log = logger.withTag("ActivityZonesStore")
    private val mutex = Mutex()
    private var swept = false

    /**
     * Queues [activity]'s zones at the place for charts at [charts] and the training as saved now. Doesn't drain the outbox. When the
     * training can't be read (offline), they go to Only me; when the session can't write zones to the space they need, they're dropped
     * rather than published wider.
     */
    suspend fun put(activity: AtUri, charts: String, heartRate: ZoneSnapshot?, power: ZoneSnapshot?) {
        if (heartRate == null && power == null) return
        val training = runSuspendCatching { clients.ownTrainingVisibility() }.getOrElse { Visibility.SELF }
        val target = ZonesPlacement.target(charts, training)
        if (!canWrite(target)) {
            log.w { "Dropping the zones of $activity: this session can't write them to the $target space" }
            return
        }
        val record = ActivityZonesRecord(activity, clock.now(), heartRate, power)
        writer.put(Nsid.FEED_ACTIVITY_ZONES, requireNotNull(activity.rkey), ActivityZonesRecord.serializer(), record, space = place(target))
    }

    /**
     * Moves [activity]'s zones, now, to the place for charts at [charts] and the training as saved now: wider only up to the training,
     * never wider than they are without training details. Copies left elsewhere by an earlier interrupted move go too.
     */
    suspend fun move(activity: AtUri, charts: String) = mutex.withLock {
        val rkey = requireNotNull(activity.rkey)
        val copies = PLACES.mapNotNull { at -> clients.readOwn(Nsid.FEED_ACTIVITY_ZONES, rkey, place(at))?.let { at to it } }
        val (narrowest, record) = copies.maxByOrNull { (at, _) -> Visibility.rank(at) } ?: return@withLock
        val target = ZonesPlacement.target(charts, clients.ownTrainingVisibility(), current = narrowest)
        if (!canWrite(target)) return@withLock
        if (copies.none { it.first == target }) clients.writeOwn(Nsid.FEED_ACTIVITY_ZONES, rkey, record, place(target))
        for ((at, _) in copies) if (at != target) clients.deleteOwn(Nsid.FEED_ACTIVITY_ZONES, rkey, place(at))
    }

    /** Queues removing [activity]'s zones from every place they could be. Doesn't drain the outbox. */
    suspend fun delete(activity: AtUri) {
        val rkey = requireNotNull(activity.rkey)
        writer.delete(Nsid.FEED_ACTIVITY_ZONES, rkey)
        if (clients.canUseSpaces) AthleteSpaces.TIERS.forEach { writer.delete(Nsid.FEED_ACTIVITY_ZONES, rkey, clients.tiers.uri(it)) }
    }

    /**
     * Before training is saved at [visibility]: every zones record stored wider moves down to it, through the outbox, and this returns only
     * once the outbox has drained. Queued publishes drain first, so none lands at the old tier after the scan. Throws if anything is left
     * pending, so the training keeps its old visibility; the queued moves still finish on a later drain. A retry scans again, so it moves
     * only what's still too wide, and skips records whose moves are already queued.
     */
    override suspend fun narrowTo(visibility: String) = mutex.withLock {
        val tier = Visibility.known(visibility)
        drainOrThrow()
        val queued = queuedRkeys()
        for (at in PLACES.filter { Visibility.rank(it) < Visibility.rank(tier) }) {
            for ((rkey, record) in list(Nsid.FEED_ACTIVITY_ZONES, at)) {
                if (rkey in queued) continue
                queueMove(rkey, record, from = at, to = tier)
            }
        }
        drainOrThrow()
    }

    /**
     * Once a session, moves any zones stored wider than their charts and the training as saved now allow (another device may have published
     * at an old training tier), and drops copies an interrupted move left in a second place. Never widens. Best effort: a failure is logged
     * and left for the next session, and the moves it queued drain with the outbox.
     */
    suspend fun sweepOnce() {
        if (swept || !clients.canUseSpaces) return
        runSuspendCatching { mutex.withLock { if (!swept) sweep().also { swept = true } } }
            .onFailure { e -> log.w(e) { "Couldn't sweep the athlete's zones" } }
    }

    private suspend fun sweep() {
        val training = clients.ownTrainingVisibility()
        val zones = PLACES.associateWith { list(Nsid.FEED_ACTIVITY_ZONES, it) }
        // Charts held in two places mid-move count at the narrower.
        val charts = mutableMapOf<String, String>()
        for (at in PLACES) list(Nsid.FEED_ACTIVITY_STREAMS, at).keys.forEach { charts[it] = at }
        val queued = queuedRkeys()
        for (rkey in zones.values.flatMap { it.keys }.toSet() - queued) {
            val copies = PLACES.filter { rkey in zones.getValue(it) }
            val narrowest = copies.last()
            // Never wider than the narrowest copy: the sweep only narrows.
            val target = Visibility.narrower(ZonesPlacement.target(charts[rkey] ?: narrowest, training), narrowest)
            if (!canWrite(target)) continue
            if (target !in copies)
                writer.put(
                    Nsid.FEED_ACTIVITY_ZONES,
                    rkey,
                    JsonObject.serializer(),
                    zones.getValue(narrowest).getValue(rkey),
                    space = place(target),
                )
            for (at in copies) if (at != target) writer.delete(Nsid.FEED_ACTIVITY_ZONES, rkey, place(at))
        }
    }

    private suspend fun queueMove(rkey: String, record: JsonObject, from: String, to: String) {
        if (!canWrite(to)) throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.SCOPE_MISSING, "Sign in again to hide your zones")
        writer.put(Nsid.FEED_ACTIVITY_ZONES, rkey, JsonObject.serializer(), record, space = place(to))
        writer.delete(Nsid.FEED_ACTIVITY_ZONES, rkey, place(from))
    }

    private suspend fun drainOrThrow() {
        val pending = outbox.drain()
        if (pending > 0) throw XrpcException(OUTBOX_STUCK_STATUS, OUTBOX_PENDING, "$pending writes are still waiting to be sent")
    }

    private suspend fun queuedRkeys(): Set<String> =
        db.outbox().pending(clients.did.value).filter { it.collection == Nsid.FEED_ACTIVITY_ZONES }.map { it.rkey }.toSet()

    /** The athlete's own [collection] records at [at], by rkey. A tier space that doesn't exist yet holds none. */
    private suspend fun list(collection: String, at: String): Map<String, JsonObject> {
        val records = mutableMapOf<String, JsonObject>()
        val space = place(at)
        var cursor: String? = null
        do {
            val page = if (space == null) repoPage(collection, cursor) else spacePage(space, collection, cursor)
            records += page.first
            cursor = page.second
        } while (cursor != null)
        return records
    }

    /** One page of the repo's [collection], and the next cursor (null at the end). */
    private suspend fun repoPage(collection: String, cursor: String?): Pair<Map<String, JsonObject>, String?> {
        val page = clients.pds.query(ListRecords.method, ListRecords.Params(clients.did.value, collection, PAGE, cursor))
        val records = page.records.mapNotNull { r -> r.uri.rkey?.let { it to r.value } }.toMap()
        return records to page.cursor?.takeIf { page.records.isNotEmpty() }
    }

    /** One page of [space]'s [collection], and the next cursor (null at the end); none when the space doesn't exist yet. */
    private suspend fun spacePage(space: SpaceUri, collection: String, cursor: String?): Pair<Map<String, JsonObject>, String?> {
        val read =
            try {
                clients.spaces.listRecords(space, clients.did, collection, PAGE, cursor)
            } catch (e: XrpcException) {
                if (e.error != SpaceErrors.SPACE_NOT_FOUND) throw e
                SpaceRead.NotFound
            }
        val page =
            when (read) {
                is SpaceRead.Found -> read.value
                SpaceRead.NotFound -> null
                SpaceRead.NoAccess -> throw XrpcException(SCOPE_MISSING_STATUS, SpaceErrors.NOT_AUTHORIZED, "Can't read $space")
            }
        val records = page?.records.orEmpty().mapNotNull { r -> r.value?.let { r.rkey to it } }.toMap()
        return records to page?.cursor?.takeIf { page.records.isNotEmpty() }
    }

    private fun place(visibility: String): SpaceUri? = clients.placeOf(visibility)

    private fun canWrite(visibility: String) = place(visibility) == null || clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_ZONES)

    private companion object {
        /** Widest first, so the last copy of a record is its narrowest. */
        val PLACES = listOf(Visibility.PUBLIC) + AthleteSpaces.TIERS
        const val PAGE = 100
        const val SCOPE_MISSING_STATUS = 403
        const val OUTBOX_STUCK_STATUS = 503
        const val OUTBOX_PENDING = "OutboxPending"
    }
}
