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
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.RouteTrimRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
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
 * without reading the zones. Rewrites go through the outbox and change only `polyline`.
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
        if (!privacy.hasStale) return
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
                if (route == null) {
                    complete = false
                    continue
                }
                rewrite(entry.key, at, route, zones, listed = entry.value)
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

    /**
     * Re-trims activities whose activity and track are both still in the outbox: the listing can't see them, and they may have been trimmed
     * by zones read before the change. Their route comes from the queued track's GPX.
     */
    private suspend fun trimQueued(zones: RouteTrim.Known) {
        val pending = db.outbox().pending(clients.did.value).filter { it.operation == OutboxProcessor.OP_PUT }
        val activities = pending.filter { it.collection == Nsid.FEED_ACTIVITY }.associateBy { it.rkey }
        for (track in pending.filter { it.collection == Nsid.FEED_ACTIVITY_TRACK && it.rkey in activities }) {
            val gpx = track.blob ?: continue
            val at = activities.getValue(track.rkey).space?.substringAfterLast('/') ?: Visibility.PUBLIC
            runSuspendCatching { rewrite(track.rkey, at, readGpxTrack(gpx), zones, listed = null) }
                .onFailure { e -> log.w(e) { "Couldn't re-trim queued activity ${track.rkey}" } }
        }
    }

    /** Re-trims [PrivacyZones.takeStale]'s activities by [zones]; true if any rewrite was queued. */
    private suspend fun trimStale(zones: RouteTrim.Known): Boolean {
        var queued = false
        for (stale in privacy.takeStale(zones)) {
            val rkey = requireNotNull(stale.uri.rkey)
            runSuspendCatching { if (rewrite(rkey, stale.visibility, stale.route, zones, listed = null)) queued = true }
                .onFailure { e -> log.w(e) { "Couldn't re-trim ${stale.uri}" } }
        }
        return queued
    }

    /**
     * Queues [rkey] at [at] with its polyline rebuilt from [route] by [zones], starting from its queued put if there is one, else [listed],
     * else the PDS's copy. True if it changed.
     */
    private suspend fun rewrite(
        rkey: String,
        at: String,
        route: List<LocationSample>,
        zones: RouteTrim.Known,
        listed: JsonObject?,
    ): Boolean {
        val current =
            db.queuedActivityRecord(clients, rkey)
                ?: listed?.let { runCatching { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it) }.getOrNull() }
                ?: clients.ownActivityRecord(rkey, at)
        val record = retrimmed(current, route, zones)
        if (record == current) return false
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record, space = clients.placeOf(at))
        val uri = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)
        feed.rewrites.set(uri, record)
        feed.updateCached(uri) { feed.rewrites.rewritten(it, record) }
        return true
    }

    /**
     * [current] with its polyline and breaks rebuilt from [route] by [zones]: a combined activity's broken between its members' recordings,
     * a single one's where positions were recorded far apart.
     */
    private fun retrimmed(current: ActivityRecord, route: List<LocationSample>, zones: RouteTrim.Known): ActivityRecord {
        val line = if (current.combined != null) zones.segmentedPolylineOf(route) else zones.polylineOf(route)
        return current.copy(polyline = line?.polyline, polylineGaps = line?.gaps)
    }

    /** Sends the queued activity writes, skipping the rest of the outbox; returns how many are left. */
    private suspend fun drainActivities(): Int = outbox.drain { it.collection == Nsid.FEED_ACTIVITY }

    private suspend fun marker(): String? =
        clients.readOwn(Nsid.ACTOR_ROUTE_TRIM, RouteTrimRecord.RKEY, self)?.let {
            runCatching { AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), it).revision }.getOrNull()
        }

    private companion object {
        val PLACES = listOf(Visibility.PUBLIC) + AthleteSpaces.TIERS
        const val CONCURRENT_TRACKS = 4
    }
}
