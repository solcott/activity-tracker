package io.github.solcott.activitytracker.location

import kotlin.time.Instant

/**
 * Where a recording is saved as it happens (record/gps-recording), so a killed app, a refused publish or a sign-out never loses it: its
 * start, then every [RecordingEvent] in order, for the account that started it. A recording stays until it's [delete]d, once its activity
 * is queued or the athlete discards it.
 */
interface RecordingJournal {
    /** Starts a recording for [did] at [startedAt] and returns its id. */
    suspend fun begin(did: String, startedAt: Instant): Long

    /** Saves [event] after the recording's earlier ones. Returns only once it's saved. */
    suspend fun append(id: Long, event: RecordingEvent)

    /** [did]'s unfinished recording with its events in order, or null when it has none. */
    suspend fun unfinished(did: String): JournaledRecording?

    suspend fun delete(id: Long)

    /** Saves nothing: for recorders with no account, and tests that don't look. */
    object None : RecordingJournal {
        override suspend fun begin(did: String, startedAt: Instant): Long = 0

        override suspend fun append(id: Long, event: RecordingEvent) = Unit

        override suspend fun unfinished(did: String): JournaledRecording? = null

        override suspend fun delete(id: Long) = Unit
    }
}

/** A recording as [RecordingJournal] saved it. */
data class JournaledRecording(val id: Long, val did: String, val startedAt: Instant, val events: List<RecordingEvent>) {
    /** The accepted samples, in order. */
    val samples: List<LocationSample>
        get() = events.filterIsInstance<RecordingEvent.Sample>().map { it.sample }
}

/** A [RecordingJournal] in memory, for tests: a new recorder over the same journal is the app started again after it was killed. */
class InMemoryRecordingJournal : RecordingJournal {
    private val recordings = mutableMapOf<Long, JournaledRecording>()
    private var nextId = 1L

    /** Fails every save while set, as a full disk would. */
    var failing = false

    override suspend fun begin(did: String, startedAt: Instant): Long {
        check(!failing) { "disk full" }
        val id = nextId++
        recordings[id] = JournaledRecording(id, did, startedAt, emptyList())
        return id
    }

    override suspend fun append(id: Long, event: RecordingEvent) {
        check(!failing) { "disk full" }
        recordings[id]?.let { recordings[id] = it.copy(events = it.events + event) }
    }

    override suspend fun unfinished(did: String): JournaledRecording? = recordings.values.filter { it.did == did }.maxByOrNull { it.id }

    override suspend fun delete(id: Long) {
        recordings.remove(id)
    }

    /** Every recording still saved. */
    val saved: List<JournaledRecording>
        get() = recordings.values.toList()
}
