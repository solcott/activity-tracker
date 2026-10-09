package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/**
 * Deletes the signed-in athlete's activities and splits recordings off their groups, through the outbox like every write. The feed drops
 * what changed at once ([FeedRepository.deletes]), since the AppView serves it as it was until Jetstream catches up.
 */
@SingleIn(SessionScope::class)
@Inject
class ActivityManager(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val writer: OutboxWriter,
    private val streams: ActivityStreamsStore,
    private val feed: FeedRepository,
) {
    /**
     * Deletes [activity] and its streams. A main activity's other recordings go too [withRecordings], or else are split off first, each to
     * stand as an activity of its own, so none is left pointing at one that's gone.
     */
    suspend fun delete(activity: ActivityView, withRecordings: Boolean) {
        val recordings = activity.recordings.orEmpty().map { it.uri }
        if (withRecordings) {
            recordings.forEach { queueDelete(it) }
        } else {
            recordings.forEach { queueUngroup(it, from = activity.uri) }
        }
        queueDelete(activity.uri, recordingOf = activity.record.recordingOf)
        outbox.drain()
    }

    /** Splits [recording] off the activity it records, [from], to stand as an activity of its own. */
    suspend fun ungroup(recording: AtUri, from: AtUri) {
        queueUngroup(recording, from)
        outbox.drain()
    }

    private suspend fun queueDelete(uri: AtUri, recordingOf: AtUri? = null) {
        writer.delete(Nsid.FEED_ACTIVITY, requireNotNull(uri.rkey))
        streams.delete(uri)
        feed.deletes.delete(uri)
        db.feed().deleteByUri(uri.value)
        recordingOf?.let { feed.updateCached(it, feed.deletes::applyTo) }
    }

    // Written without a blob, so the record keeps the track it has.
    private suspend fun queueUngroup(recording: AtUri, from: AtUri) {
        val rkey = requireNotNull(recording.rkey)
        val record = clients.ownActivityRecord(rkey).copy(recordingOf = null)
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record)
        feed.rewrites.set(recording, record)
        feed.updateCached(recording) { it.copy(record = record) }
        feed.deletes.ungroup(recording)
        feed.updateCached(from, feed.deletes::applyTo)
    }
}
