package io.github.solcott.activitytracker.data.activity

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * Edits, deletes and ungroups the signed-in athlete's activities, through the outbox like every write, at the place each one's visibility
 * says. Changing who may see an activity ([setVisibility]) is the exception: it moves the records now, as profile saves do. The feed shows
 * what changed at once ([FeedRepository.rewrites], [FeedRepository.deletes]), since the AppView serves it as it was until it catches up.
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
        // A primary's recordings share its visibility, and so its place.
        val visibility = activity.visibilityOrPublic
        if (withRecordings) {
            recordings.forEach { queueDelete(it, visibility) }
        } else {
            recordings.forEach { queueUngroup(it, from = activity.uri, visibility) }
        }
        queueDelete(activity.uri, visibility, recordingOf = activity.record.recordingOf)
        outbox.drain()
    }

    /**
     * Rewrites [activity]'s title, description, dive site and photos ([edit]), leaving the rest of its record as the PDS has it. New photos
     * upload first, so this needs a connection; media kinds this client doesn't know stay, after the photos.
     */
    suspend fun edit(activity: ActivityView, edit: ActivityEdit) {
        val rkey = requireNotNull(activity.uri.rkey)
        val visibility = activity.visibilityOrPublic
        val current = clients.ownActivityRecord(rkey, visibility)
        val have = current.media.orEmpty()
        val photos =
            edit.media.take(ActivityMedia.MAX).mapNotNull { change ->
                when (change) {
                    is MediaEdit.Keep -> have.firstOrNull { it.cid == change.cid }
                    is MediaEdit.Add -> clients.upload(change.photo)
                }
            }
        val unknown = have.filterIsInstance<ActivityMedia.Unknown>()
        val record =
            current.copy(
                title = edit.title.trim().ifEmpty { null },
                description = edit.description.trim().ifEmpty { null },
                dive =
                    edit.diveSite?.let { site ->
                        (current.dive ?: ActivityDive()).copy(site = site.trim().take(ActivityDive.MAX_SITE_LENGTH).ifEmpty { null })
                    } ?: current.dive,
                media = (photos + unknown).take(ActivityMedia.MAX).ifEmpty { null },
            )
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record, space = clients.placeOf(visibility))
        feed.rewrites.set(activity.uri, record)
        feed.updateCached(activity.uri) { feed.rewrites.rewritten(it, record) }
        outbox.drain()
    }

    /**
     * Shows [activity] and its charts to [choice]'s viewers, the charts capped at the activity: moves the activity, and its other
     * recordings, which share its visibility, to the new place (writing it before deleting the old, so an interrupted move leaves it stored
     * and a retry finishes it), then moves each one's charts that are now wider than the activity, or [activity]'s to the chosen ones.
     * Pending outbox writes go first, so the move reads what the athlete last saved. Throws on failure.
     */
    suspend fun setVisibility(activity: ActivityView, choice: VisibilityChoice) {
        val capped = choice.capped()
        val from = activity.visibilityOrPublic
        val to = Visibility.known(capped.activity)
        clients.requireSpacesFor(to)
        clients.requireSpacesFor(capped.charts)
        outbox.drain()
        val targets = listOf(activity.uri) + activity.recordings.orEmpty().map { it.uri }
        if (from != to) {
            for (uri in targets) {
                val moved = clients.moveOwn(Nsid.FEED_ACTIVITY, requireNotNull(uri.rkey), clients.placeOf(from), clients.placeOf(to))
                // Neither where this view says it is nor where it's going: it was moved elsewhere since, so moving nothing isn't success.
                if (!moved) throw XrpcException(NOT_FOUND_STATUS, "RecordNotFound", "Activity $uri isn't at $from or $to")
            }
        }
        for (uri in targets) {
            val have = feed.streams(uri)?.visibility ?: continue
            val want = if (uri == activity.uri) capped.charts else Visibility.capped(have, to)
            if (have != want) streams.move(uri, have, want)
        }
        targets.forEach { uri -> feed.updateCached(uri) { it.withVisibility(to) } }
    }

    /** Splits [recording] off the activity it records, [from], at [visibility] (both's), to stand as an activity of its own. */
    suspend fun ungroup(recording: AtUri, from: AtUri, visibility: String = Visibility.PUBLIC) {
        queueUngroup(recording, from, visibility)
        outbox.drain()
    }

    private suspend fun queueDelete(uri: AtUri, visibility: String, recordingOf: AtUri? = null) {
        writer.delete(Nsid.FEED_ACTIVITY, requireNotNull(uri.rkey), clients.placeOf(visibility))
        streams.delete(uri)
        feed.deletes.delete(uri)
        db.feed().deleteByUri(uri.value)
        recordingOf?.let { feed.updateCached(it, feed.deletes::applyTo) }
    }

    // Written without a blob, so the record keeps the track it has.
    private suspend fun queueUngroup(recording: AtUri, from: AtUri, visibility: String) {
        val rkey = requireNotNull(recording.rkey)
        val record = clients.ownActivityRecord(rkey, visibility).copy(recordingOf = null)
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record, space = clients.placeOf(visibility))
        feed.rewrites.set(recording, record)
        feed.updateCached(recording) { it.copy(record = record) }
        feed.deletes.ungroup(recording)
        feed.updateCached(from, feed.deletes::applyTo)
    }
}

/**
 * This view at [visibility], as the AppView will serve it: a non-public activity shows no comments. Its likes stay until the AppView
 * recounts them for the new audience.
 */
private fun ActivityView.withVisibility(visibility: String): ActivityView =
    if (visibility == Visibility.PUBLIC) copy(visibility = visibility) else copy(visibility = visibility, commentCount = 0)

private const val NOT_FOUND_STATUS = 400
