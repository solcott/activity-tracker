package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityTrackStore
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.segments.PdsSegmentTrackStore
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.segments.SegmentTrackSweeper
import io.github.solcott.activitytracker.data.segments.SegmentTracks
import kotlin.time.Clock

/** A session's segment-track collaborators, built once per [SessionClients] so every store a test wires shares them. */
class TestSegments(
    val prefs: SegmentPrefsRepository,
    val store: PdsSegmentTrackStore,
    val tracks: SegmentTracks,
    val sweeper: SegmentTrackSweeper,
)

private val built = mutableMapOf<SessionClients, TestSegments>()

/** [clients]' [TestSegments], built on first use from the session's other stores. */
@Suppress("LongParameterList") // The stores the segment collaborators are built from.
fun testSegments(
    db: AppDatabase,
    clients: SessionClients,
    writer: OutboxWriter,
    outbox: OutboxProcessor,
    privacy: PrivacyZones,
    tracks: ActivityTrackStore,
): TestSegments =
    synchronized(built) {
        built.getOrPut(clients) {
            val prefs = SegmentPrefsRepository(clients, Clock.System)
            val store = PdsSegmentTrackStore(writer, clients, Clock.System, db)
            TestSegments(
                prefs,
                store,
                SegmentTracks(prefs, store),
                SegmentTrackSweeper(db, clients, prefs, store, tracks, privacy, outbox, Logger),
            )
        }
    }

/** The [TestSegments] a session's stores were wired with ([testSegments]). */
fun segmentsOf(clients: SessionClients): TestSegments = synchronized(built) { built.getValue(clients) }
