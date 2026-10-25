package io.github.solcott.activitytracker.data.recording

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.RecordingEntity
import io.github.solcott.activitytracker.data.db.RecordingEventEntity
import io.github.solcott.activitytracker.location.JournaledRecording
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingEvent
import io.github.solcott.activitytracker.location.RecordingJournal
import kotlin.time.Instant

/**
 * Saves recordings in Room (`recording`, `recording_event`), one row per event, keyed by the account that started each. Every platform
 * binds it; only phones record.
 */
@ContributesBinding(AppScope::class)
@Inject
class RoomRecordingJournal(private val db: AppDatabase) : RecordingJournal {
    override suspend fun begin(did: String, startedAt: Instant): Long =
        db.recordings().begin(RecordingEntity(did = did, startedAt = startedAt.toEpochMilliseconds()))

    override suspend fun append(id: Long, event: RecordingEvent) {
        val sample = (event as? RecordingEvent.Sample)?.sample
        db.recordings()
            .append(
                recordingId = id,
                kind =
                    when (event) {
                        is RecordingEvent.Sample -> RecordingEventEntity.KIND_SAMPLE
                        is RecordingEvent.Paused -> RecordingEventEntity.KIND_PAUSED
                        is RecordingEvent.Resumed -> RecordingEventEntity.KIND_RESUMED
                    },
                at = event.at.toEpochMilliseconds(),
                latitude = sample?.latitude,
                longitude = sample?.longitude,
                altitude = sample?.altitude,
                speed = sample?.speed,
                accuracy = sample?.accuracy,
            )
    }

    override suspend fun unfinished(did: String): JournaledRecording? {
        val recording = db.recordings().latest(did) ?: return null
        val events = db.recordings().events(recording.id).mapNotNull(::eventOf)
        return JournaledRecording(recording.id, recording.did, Instant.fromEpochMilliseconds(recording.startedAt), events)
    }

    override suspend fun delete(id: Long) = db.recordings().delete(id)

    /** The event a row holds; null for a kind this version doesn't know or a sample row without a position. */
    private fun eventOf(row: RecordingEventEntity): RecordingEvent? {
        val at = Instant.fromEpochMilliseconds(row.at)
        return when (row.kind) {
            RecordingEventEntity.KIND_SAMPLE ->
                if (row.latitude == null || row.longitude == null) null
                else RecordingEvent.Sample(LocationSample(row.latitude, row.longitude, row.altitude, row.speed, row.accuracy, at))
            RecordingEventEntity.KIND_PAUSED -> RecordingEvent.Paused(at)
            RecordingEventEntity.KIND_RESUMED -> RecordingEvent.Resumed(at)
            else -> null
        }
    }
}
