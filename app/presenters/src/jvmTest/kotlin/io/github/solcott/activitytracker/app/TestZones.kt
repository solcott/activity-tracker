package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.ActivityZonesStore
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivitySourceStore
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.PdsActivityTrackStore
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import kotlin.time.Clock

/** The outbox, and the streams, zones, track and source stores over it, shared by a test's repositories. */
internal class TestZones(db: AppDatabase, clients: SessionClients) {
    val outbox = OutboxProcessor(db, clients, Logger)
    val writer = OutboxWriter(db, clients, Clock.System)
    val store = ActivityZonesStore(db, writer, outbox, clients, Clock.System, Logger)
    val streams = PdsActivityStreamsStore(writer, clients, Clock.System, store)
    val tracks = PdsActivityTrackStore(db, writer, clients, Clock.System)
    val sources = PdsActivitySourceStore(db, writer, clients, Clock.System)
}
