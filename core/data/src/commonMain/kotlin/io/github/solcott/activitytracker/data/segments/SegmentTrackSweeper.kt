package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityTrackStore
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.RouteTrim
import io.github.solcott.activitytracker.data.activity.listOwn
import io.github.solcott.activitytracker.data.activity.placeOf
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings the athlete's segment tracks in line with their setting. While matching is on, [sweep] writes one for every activity with a full
 * track that has none, or one trimmed by zones other than those saved now, from that track; while it's off, it deletes every one. Nothing
 * marks a sweep done: whatever an interrupted one left still differs, so the next session, on any device, finishes it. Writes go through
 * the outbox; only segment-track writes are sent.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList") // Injected collaborators.
class SegmentTrackSweeper(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val prefs: SegmentPrefsRepository,
    private val store: SegmentTrackStore,
    private val tracks: ActivityTrackStore,
    private val privacy: PrivacyZones,
    private val outbox: OutboxProcessor,
    logger: Logger,
) {
    private val log = logger.withTag("SegmentTrackSweeper")
    private val mutex = Mutex()

    private val self
        get() = clients.tiers.uri(AthleteSpace.SELF)

    /** Writes or deletes segment tracks as the setting, read now, says. Best effort: a failure waits for the next run. */
    suspend fun sweep() {
        if (!prefs.canUse) return
        runSuspendCatching { mutex.withLock { sweepLocked() } }
            .onFailure { e -> log.w(e) { "Couldn't sweep the athlete's segment tracks" } }
    }

    /**
     * Sweeps after every save of the setting this session ([SegmentPrefsRepository.saved]), a newer save replacing a sweep still running,
     * for as long as it's collected.
     */
    suspend fun sweepOnSave() {
        prefs.saved.filterNotNull().collectLatest { sweep() }
    }

    private suspend fun sweepLocked() {
        when (prefs.refresh()) {
            SegmentMatching.ON -> privacy.readFresh()?.let { write(it) }
            SegmentMatching.OFF -> deleteAll()
            // Never asked: none was ever written. Unreadable: nothing is certain, so nothing changes.
            SegmentMatching.NEVER_ASKED,
            null -> return
        }
        outbox.drain { it.collection == Nsid.FEED_SEGMENT_TRACK }
    }

    private suspend fun write(zones: RouteTrim.Known) {
        val stored = store.list()
        val queued = queuedRkeys()
        val withTracks = clients.listOwn(Nsid.FEED_ACTIVITY_TRACK, self).keys
        val due = withTracks.filter { rkey -> rkey !in queued && (rkey !in stored || stored[rkey] != zones.revision) }
        if (due.isEmpty()) return
        val combined = combinedRkeys()
        for (chunk in due.chunked(CONCURRENT_TRACKS)) {
            val routes = coroutineScope { chunk.map { async { tracks.read(activityUri(it)) } }.awaitAll() }
            for ((rkey, route) in chunk.zip(routes)) {
                if (route.isNullOrEmpty()) continue
                val pieces = zones.settings.pieces(route, combined = rkey in combined)
                if (pieces.isNotEmpty()) store.put(activityUri(rkey), segmentTrackData(pieces, route.first().time), zones.revision)
                else if (rkey in stored) store.delete(activityUri(rkey))
            }
        }
    }

    private suspend fun deleteAll() {
        val queuedPuts =
            db.outbox()
                .pending(clients.did.value)
                .filter { it.collection == Nsid.FEED_SEGMENT_TRACK }
                .groupBy { it.rkey }
                .filterValues { it.last().operation == OutboxProcessor.OP_PUT }
                .keys
        (store.list().keys + queuedPuts).forEach { store.delete(activityUri(it)) }
    }

    /** Rkeys with a segment-track write still queued: the publish that queued it already chose. */
    private suspend fun queuedRkeys(): Set<String> =
        db.outbox().pending(clients.did.value).filter { it.collection == Nsid.FEED_SEGMENT_TRACK }.mapTo(mutableSetOf()) { it.rkey }

    /** The athlete's combined activities, wherever each is stored: their segment tracks break per member, as their polylines do. */
    private suspend fun combinedRkeys(): Set<String> =
        PLACES.flatMapTo(mutableSetOf()) { at ->
            clients
                .listOwn(Nsid.FEED_ACTIVITY, clients.placeOf(at))
                .filterValues { value ->
                    runCatching { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), value).combined != null }.getOrDefault(false)
                }
                .keys
        }

    private fun activityUri(rkey: String) = AtUri.of(clients.did, Nsid.FEED_ACTIVITY, rkey)

    private companion object {
        val PLACES = listOf(Visibility.PUBLIC) + AthleteSpaces.TIERS
        const val CONCURRENT_TRACKS = 4
    }
}
