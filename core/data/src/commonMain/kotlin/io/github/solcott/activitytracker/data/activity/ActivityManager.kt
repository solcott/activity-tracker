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
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * Edits, deletes and ungroups the signed-in athlete's activities, through the outbox like every write, at the place each one's visibility
 * says. Changing who may see an activity or its detail categories ([setVisibility]) is the exception: it moves the records now, as profile
 * saves do. The feed shows what changed at once ([FeedRepository.rewrites], [FeedRepository.deletes]), since the AppView serves it as it
 * was until it catches up.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList") // Injected collaborators.
class ActivityManager(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val outbox: OutboxProcessor,
    private val writer: OutboxWriter,
    private val streams: ActivityStreamsStore,
    private val tracks: ActivityTrackStore,
    private val sources: ActivitySourceStore,
    private val devices: ActivityDevicesStore,
    private val zones: ActivityZonesStore,
    private val places: ActivityPlaces,
    private val feed: FeedRepository,
) {
    /**
     * Deletes [activity], its streams, its track and its original file. A main activity's other recordings go too [withRecordings], or else
     * are split off first, each to stand as an activity of its own, so none is left pointing at one that's gone.
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
        val current = db.queuedActivityRecord(clients, rkey) ?: clients.ownActivityRecord(rkey, visibility)
        val have = current.media.orEmpty()
        val photos =
            edit.media.take(ActivityMedia.MAX).mapNotNull { change ->
                when (change) {
                    is MediaEdit.Keep -> have.firstOrNull { it.cid == change.cid }
                    is MediaEdit.Add -> clients.upload(change.photo, feed.heldPhotos)
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
     * Shows [activity] and each of its detail categories to [choice]'s viewers, each category capped at the activity, now rather than
     * through the outbox, so a failure shows. Its other recordings, which share its visibility, move with it, each keeping its categories
     * where they are unless they're now wider than the activity. Each activity is read from every place it could be ([ActivityPlaces]) and
     * rewritten narrowest place first, then cut down widest first ([ActivityPlaces.save]), so an interrupted save leaves everything stored
     * at least once and saving again finishes it. Zones move first when the heart rate or power tier changes. Pending outbox writes go
     * first, so the save reads what the athlete last saved. Throws on failure.
     */
    suspend fun setVisibility(activity: ActivityView, choice: VisibilityChoice) {
        val capped = choice.capped()
        val to = Visibility.known(capped.activity)
        clients.requireSpacesFor(to)
        if (capped.splits) clients.requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS)
        outbox.drain()
        val targets = listOf(activity.uri) + activity.recordings.orEmpty().map { it.uri }
        for (uri in targets) {
            val stored = places.read(uri)
            // Nowhere at all: it was deleted since, so saving nothing isn't success.
            val from = stored.recordTiers.lastOrNull() ?: throw XrpcException(NOT_FOUND_STATUS, "RecordNotFound", "No activity $uri")
            val (summary, channels) = stored.narrowestTiers()
            val desired =
                if (uri == activity.uri) {
                    Shares.of(to, capped.tiers)
                } else {
                    fun capped(tiers: Map<String, String>) = DetailCategory.tiers(to, tiers)
                    Shares.of(to, capped(summary), capped(channels), Visibility.narrower(stored.coreTier ?: to, to))
                }
            val before = zonesTier(DetailCategory.tiers(from, summary + channels))
            val after = if (uri == activity.uri) zonesTier(capped.tiers) else zonesTier(DetailCategory.tiers(to, summary + channels))
            // Zones first: a session that can't move them stops here, before the charts are narrower than their zones.
            if (before != after) zones.move(uri, after)
            places.save(stored, desired)
            val full = stored.full ?: continue
            val saved = Visibility.all.associateWith { desired[it].records(full, stored.data, uri) }
            val record = saved[to]?.activity
            val details = Visibility.all.mapNotNull { tier -> saved[tier]?.details?.let { DetailsView(tier, it) } }.ifEmpty { null }
            if (record != null) feed.rewrites.set(uri, record)
            feed.updateCached(uri) { view -> view.copy(visibility = to, record = record ?: view.record, details = details) }
        }
    }

    /**
     * Whether saving [activity] with [choice] would make its core charts (elevation, distance and the like) more visible than they are: it
     * was published before detail categories, with its one streams record narrower than the activity is to be, and core charts always go
     * with the activity. The athlete is asked first.
     */
    suspend fun coreChartsWiden(activity: ActivityView, choice: VisibilityChoice): Boolean {
        val stored = places.read(activity.uri)
        val core = stored.coreTier ?: return false
        return !stored.isSplit && Visibility.rank(Visibility.known(choice.capped().activity)) < Visibility.rank(core)
    }

    /** Splits [recording] off the activity it records, [from], at [visibility] (both's), to stand as an activity of its own. */
    suspend fun ungroup(recording: AtUri, from: AtUri, visibility: String = Visibility.PUBLIC) {
        queueUngroup(recording, from, visibility)
        outbox.drain()
    }

    private suspend fun queueDelete(uri: AtUri, visibility: String, recordingOf: AtUri? = null) {
        writer.delete(Nsid.FEED_ACTIVITY, requireNotNull(uri.rkey), clients.placeOf(visibility))
        streams.delete(uri)
        tracks.delete(uri)
        sources.delete(uri)
        devices.delete(uri)
        feed.deletes.delete(uri)
        db.feed().deleteByUri(uri.value)
        recordingOf?.let { feed.updateCached(it, feed.deletes::applyTo) }
    }

    private suspend fun queueUngroup(recording: AtUri, from: AtUri, visibility: String) {
        val rkey = requireNotNull(recording.rkey)
        val record = (db.queuedActivityRecord(clients, rkey) ?: clients.ownActivityRecord(rkey, visibility)).copy(recordingOf = null)
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), record, space = clients.placeOf(visibility))
        feed.rewrites.set(recording, record)
        feed.updateCached(recording) { it.copy(record = record) }
        feed.deletes.ungroup(recording)
        feed.updateCached(from, feed.deletes::applyTo)
    }
}

private const val NOT_FOUND_STATUS = 400
