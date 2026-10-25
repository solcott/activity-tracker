package io.github.solcott.activitytracker.data.recording

import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Recordings saved as they happen (record/gps-recording): kept per account, in order, until deleted. */
class RoomRecordingJournalTest {
    private val db = inMemoryDatabase()
    private val journal = RoomRecordingJournal(db)
    private val start = Instant.parse("2026-10-24T07:00:00Z")

    private fun sample(seconds: Int, altitude: Double? = null) =
        LocationSample(45.0, 7.0 + seconds * 0.0001, altitude, speed = 3.0, accuracy = 4.0, time = start + seconds.seconds)

    @Test
    fun eventsComeBackInOrder() = runTest {
        val id = journal.begin("did:plc:alice", start)
        val events =
            listOf(
                RecordingEvent.Sample(sample(0, altitude = 120.5)),
                RecordingEvent.Sample(sample(1)),
                RecordingEvent.Paused(start + 2.seconds),
                RecordingEvent.Resumed(start + 60.seconds),
                RecordingEvent.Sample(sample(61)),
            )
        events.forEach { journal.append(id, it) }

        val saved = journal.unfinished("did:plc:alice")!!
        assertEquals(id, saved.id)
        assertEquals(start, saved.startedAt)
        assertEquals(events, saved.events)
    }

    @Test
    fun anotherAccountsRecordingIsntTheirs() = runTest {
        journal.begin("did:plc:alice", start)
        assertNull(journal.unfinished("did:plc:bob"))
    }

    @Test
    fun deletingLeavesNothing() = runTest {
        val id = journal.begin("did:plc:alice", start)
        journal.append(id, RecordingEvent.Sample(sample(0)))
        journal.delete(id)

        assertNull(journal.unfinished("did:plc:alice"))
        assertEquals(emptyList(), db.recordings().events(id))
    }
}
