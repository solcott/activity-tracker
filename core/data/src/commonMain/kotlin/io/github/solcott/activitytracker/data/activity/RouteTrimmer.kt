package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.readGpxTrack
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.RouteTrimRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Trims the signed-in athlete's existing polylines again when their privacy zones change. [sweep] rebuilds every activity that has a track
 * from that track, at every visibility, once the zones' revision differs from the `actor.routeTrim` marker, and writes the marker only once
 * every rewrite is sent, so an interrupted sweep finishes in a later session on any device. [trimStale] fixes what this session published
 * without reading the zones. Rewrites go through the outbox and change only `polyline` and `polylineGaps`, where the Map category keeps
 * them: on the activity record when Map is at the activity's tier, else on the details record in Map's narrower tier, never wider.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList") // Injected collaborators.
class RouteTrimmer(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val privacy: PrivacyZones,
    private val tracks: ActivityTrackStore,
    private val feed: FeedRepository,
    private val clock: Clock,
    logger: Logger,
) {
    private val log = logger.withTag("RouteTrimmer")
    private val mutex = Mutex()

    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    /** Re-trims every activity once the zones have changed since the last finished sweep. Best effort: a failure waits for the next run. */
    suspend fun sweep() {
        if (!clients.canUseSpaces) return
        runSuspendCatching { mutex.withLock { sweepLocked() } }.onFailure { e -> log.w(e) { "Couldn't re-trim the athlete's routes" } }
    }

    /** Re-trims the activities this session published without a fresh read of the zones, once they can be read. Best effort. */
    suspend fun trimStale() {
        if (!privacy.hasStale()) return
        runSuspendCatching {
            mutex.withLock {
                val zones = privacy.readFresh() ?: return@withLock
                if (trimStale(zones)) drainActivities()
            }
        }
            .onFailure { e -> log.w(e) { "Couldn't re-trim this session's routes" } }
    }

    private suspend fun sweepLocked() {
        val zones = privacy.readFresh() ?: return
        trimStale(zones)
        val revision = zones.revision
        // Never saved means nothing was ever trimmed, so nothing needs trimming again.
        if (revision == null || marker() == revision) {
            drainActivities()
            return
        }
        trimQueued(zones)
        val withTracks = clients.listOwn(Nsid.FEED_ACTIVITY_TRACK, self).keys
        val details = AthleteSpaces.TIERS.associateWith { clients.listOwn(Nsid.FEED_ACTIVITY_DETAILS, clients.placeOf(it)) }
        var complete = true
        val candidates = PLACES.flatMap { at ->
            clients.listOwn(Nsid.FEED_ACTIVITY, clients.placeOf(at)).filterKeys { it in withTracks }.map { at to it }
        }
        for (chunk in candidates.chunked(CONCURRENT_TRACKS)) {
            val routes = coroutineScope {
                chunk.map { (_, entry) -> async { tracks.read(AtUri.of(clients.did, Nsid.FEED_ACTIVITY, entry.key)) } }.awaitAll()
            }
            for ((candidate, route) in chunk.zip(routes)) {
                val (at, entry) = candidate
                if (route == null) complete = false else rewriteListed(entry.key, at, entry.value, details, route, zones)
            }
        }
        if (drainActivities() == 0 && complete) {
            val record = RouteTrimRecord(revision, clock.now())
            clients.spaces.putRecord(
                clients.tiers.ensure(AthleteSpace.SELF),
                Nsid.ACTOR_ROUTE_TRIM,
                RouteTrimRecord.RKEY,
                AtJson.encodeToJsonElement(RouteTrimRecord.serializer(), record),
            )
        }
    }

    /** Re-trims [rkey], listed at [at] as [listed] with the details records [details] lists (by tier), where its map is. */
    @Suppress("LongParameterList") // The listings, and the route and zones to trim it by.
    private suspend fun rewriteListed(
        rkey: String,
        at: String,
        listed: JsonObject,
        details: Map<String, Map<String, JsonObject>>,
        route: List<LocationSample>,
        zones: RouteTrim.Known,
    ) {
        val listedDetails = details.mapNotNull { (tier, records) -> records[rkey]?.let { tier to it } }.toMap()
        val mapTier = mapTierOf(at, decodeRecord(listed), listedDetails.mapValues { (_, it) -> decodeDetails(it) })
        if (mapTier == null) log.i { "Not re-trimming $rkey: no place holds its map, so its Map tier is unknown" }
        else rewrite(rkey, at, mapTier, route, zones, listed = listed, listedDetails = listedDetails[mapTier])
    }

    /**
     * Re-trims activities whose activity and track are both still in the outbox: the listing can't see them, and they may have been trimmed
     * by zones read before the change. Their route comes from the queued track's GPX.
     */
    private suspend fun trimQueued(zones: RouteTrim.Known) {
        val pending = db.outbox().pending(clients.did.value).filter { it.operation == OutboxProcessor.OP_PUT }
        val activities = pending.filter { it.collection == Nsid.FEED_ACTIVITY }.associateBy { it.rkey }
        for (track in pending.filter { it.collection == Nsid.FEED_ACTIVITY_TRACK && it.rkey in activities }) {
            val gpx = track.blob
            val queued = activities.getValue(track.rkey)
            val at = queued.space?.substringAfterLast('/') ?: Visibility.PUBLIC
            val details =
                pending
                    .filter { it.collection == Nsid.FEED_ACTIVITY_DETAILS && it.rkey == track.rkey }
                    .associate { (it.space?.substringAfterLast('/') ?: Visibility.PUBLIC) to it.recordJson?.let(::decodeDetailsJson) }
            val mapTier = mapTierOf(at, queued.recordJson?.let(::decodeRecordJson), details)
            if (gpx == null || mapTier == null) continue
            runSuspendCatching { rewrite(track.rkey, at, mapTier, readGpxTrack(gpx), zones, listed = null, listedDetails = null) }
                .onFailure { e -> log.w(e) { "Couldn't re-trim queued activity ${track.rkey}" } }
        }
    }

    /** Re-trims [PrivacyZones.takeStale]'s activities by [zones]; true if any rewrite was queued. */
    private suspend fun trimStale(zones: RouteTrim.Known): Boolean {
        var queued = false
        for (stale in privacy.takeStale(zones)) {
            val rkey = requireNotNull(stale.uri.rkey)
            runSuspendCatching {
                if (rewrite(rkey, stale.visibility, stale.mapTier, stale.route, zones, listed = null, listedDetails = null)) queued = true
            }
                .onFailure { e -> log.w(e) { "Couldn't re-trim ${stale.uri}" } }
        }
        return queued
    }

    /**
     * Rebuilds [rkey]'s polyline from [route] by [zones] where its Map category keeps it: on the activity record at [at] when [mapTier] is
     * [at], else on the details record at [mapTier], taking it off the activity record if it's there. Each starts from its queued put if
     * there is one, else [listed] or [listedDetails], else the PDS's copy. True if anything changed.
     */
    @Suppress("LongParameterList")
    private suspend fun rewrite(
        rkey: String,
        at: String,
        mapTier: String,
        route: List<LocationSample>,
        zones: RouteTrim.Known,
        listed: JsonObject?,
        listedDetails: JsonObject?,
    ): Boolean {
        val current = db.queuedActivityRecord(clients, rkey) ?: listed?.let(::decodeRecord) ?: clients.ownActivityRecord(rkey, at)
        val line = if (current.combined != null) zones.segmentedPolylineOf(route) else zones.polylineOf(route)
        // With Map narrower than the activity, its record holds no map (a copy left there by an earlier sweep is taken off).
        val onRecord = line.takeIf { mapTier == at }
        val recordChanged = putRecord(rkey, at, current, current.copy(polyline = onRecord?.polyline, polylineGaps = onRecord?.gaps))
        if (mapTier == at) return recordChanged
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        val place = clients.placeOf(mapTier)
        val details =
            queuedDetails(rkey, place)
                ?: listedDetails?.let(::decodeDetails)
                ?: clients.readOwn(Nsid.FEED_ACTIVITY_DETAILS, rkey, place)?.let(::decodeDetails)
                ?: ActivityDetailsRecord(activity = uri, createdAt = current.createdAt)
        val rewritten = details.copy(polyline = line?.polyline, polylineGaps = line?.gaps)
        if (rewritten != details) writer.put(Nsid.FEED_ACTIVITY_DETAILS, rkey, ActivityDetailsRecord.serializer(), rewritten, space = place)
        return recordChanged || rewritten != details
    }

    /** Queues [record] for [rkey] at [at] when it differs from [current], showing it at once; true if it did. */
    private suspend fun putRecord(rkey: String, at: String, current: ActivityRecord, record: ActivityRecord): Boolean {
        if (record == current) return false
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record, space = clients.placeOf(at))
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        feed.rewrites.set(uri, record)
        feed.updateCached(uri) { feed.rewrites.rewritten(it, record) }
        return true
    }

    /** The last queued put of [rkey]'s details record at [place], if one is queued. */
    private suspend fun queuedDetails(rkey: String, place: SpaceUri?): ActivityDetailsRecord? =
        db.outbox()
            .pending(clients.did.value)
            .lastOrNull { it.collection == Nsid.FEED_ACTIVITY_DETAILS && it.rkey == rkey && it.space == place?.value }
            ?.takeIf { it.operation == OutboxProcessor.OP_PUT }
            ?.recordJson
            ?.let(::decodeDetailsJson)

    /** Sends the queued activity and details writes (where a polyline is), skipping the rest of the outbox; returns how many are left. */
    private suspend fun drainActivities(): Int = outbox.drain {
        it.collection == Nsid.FEED_ACTIVITY || it.collection == Nsid.FEED_ACTIVITY_DETAILS
    }

    private suspend fun marker(): String? =
        clients.readOwn(Nsid.ACTOR_ROUTE_TRIM, RouteTrimRecord.RKEY, self)?.let {
            runCatching { AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), it).revision }.getOrNull()
        }

    private companion object {
        val PLACES = listOf(Visibility.PUBLIC) + AthleteSpaces.TIERS
        const val CONCURRENT_TRACKS = 4
    }
}

/**
 * The tier an activity at [at] keeps its map in: the narrowest of [details] (by tier) holding a polyline, else [at] when [record] holds
 * one, else null: nothing says, so a rewrite can't know where the map may go.
 */
private fun mapTierOf(at: String, record: ActivityRecord?, details: Map<String, ActivityDetailsRecord?>): String? =
    Visibility.all.lastOrNull { details[it]?.polyline != null } ?: at.takeIf { record?.polyline != null }

private fun decodeRecord(value: JsonObject): ActivityRecord? = runCatching {
    AtJson.decodeFromJsonElement(ActivityRecord.serializer(), value)
}
    .getOrNull()

private fun decodeRecordJson(value: String): ActivityRecord? = runCatching {
    AtJson.decodeFromString(ActivityRecord.serializer(), value)
}
    .getOrNull()

private fun decodeDetails(value: JsonObject): ActivityDetailsRecord? = runCatching {
    AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), value)
}
    .getOrNull()

private fun decodeDetailsJson(value: String): ActivityDetailsRecord? = runCatching {
    AtJson.decodeFromString(ActivityDetailsRecord.serializer(), value)
}
    .getOrNull()
