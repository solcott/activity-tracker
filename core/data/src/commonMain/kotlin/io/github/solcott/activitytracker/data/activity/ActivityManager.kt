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
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import kotlin.time.Clock

/**
 * Edits, deletes and ungroups the signed-in athlete's activities, through the outbox like every write, at the place each one's visibility
 * says. Changing who may see an activity or its detail categories ([setVisibility]) is the exception: it moves the records now, as profile
 * saves do. The feed shows what changed at once ([FeedRepository.rewrites], [FeedRepository.deletes]), since the AppView serves it as it
 * was until it catches up.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions") // Injected collaborators; every change to an activity already published.
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
    private val privacy: PrivacyZones,
    private val clock: Clock,
) {
    /**
     * Deletes [activity], its streams, its track and its original file. A main activity's other recordings go too [withRecordings], or else
     * are split off first, each to stand as an activity of its own, so none is left pointing at one that's gone.
     */
    suspend fun delete(activity: ActivityView, withRecordings: Boolean) {
        val recordings = activity.recordings.orEmpty().map { it.uri }
        // A primary's recordings, and a combined activity's members, share its visibility, and so its place.
        val visibility = activity.visibilityOrPublic
        val members = activity.record.combined?.members.orEmpty()
        if (withRecordings) {
            recordings.forEach { queueDelete(it, visibility) }
            members.forEach { queueDelete(it, visibility) }
        } else {
            recordings.forEach { queueUngroup(it, from = activity.uri, visibility) }
            feed.deletes.release(members)
        }
        queueDelete(activity.uri, visibility, recordingOf = activity.record.recordingOf)
        outbox.drain()
        // A member deleted on its own leaves its combined activity rebuilt without it, or split when one member would be left.
        activity.combinedInto?.let { combined -> without(feed.activity(combined), activity.uri) }
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
        val targets = listOf(activity.uri) + activity.recordings.orEmpty().map { it.uri } + activity.record.combined?.members.orEmpty()
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
     * Combines [other] with [into], two of the athlete's own activities (`feed/combined-activities`). When [into] is already a combined
     * activity, [other] becomes another of its members and it's rebuilt; otherwise a new combined activity of the two starts from [into]'s
     * title, description, photos, sport and visibility, each detail category at the narrowest either has. [other] moves to [into]'s
     * visibility first. Members are never rewritten otherwise. Returns the combined activity's URI.
     *
     * Refuses [other] when it's a combined activity or another one's member, and an eleventh member.
     */
    suspend fun combine(into: ActivityView, other: ActivityView): AtUri {
        require(other.record.combined == null) { "A combined activity can't be a member" }
        require(other.combinedInto == null || other.combinedInto == into.uri) { "Already part of a combined activity" }
        require(into.combinedInto == null) { "Combine with the combined activity, not its member" }
        require(other.uri != into.uri) { "An activity can't be combined with itself" }
        val existing = into.record.combined
        val members = existing?.members.orEmpty().ifEmpty { listOf(into.uri) } + other.uri
        require(members.size <= CombinedActivity.MAX_MEMBERS) { "At most ${CombinedActivity.MAX_MEMBERS} members" }
        val visibility = into.visibilityOrPublic
        outbox.drain()
        if (other.visibilityOrPublic != visibility) follow(other.uri, visibility)
        val uri = if (existing != null) into.uri else AtUri.of(clients.did, Nsid.FEED_ACTIVITY, Tid.next(clock))
        build(uri, base = if (existing != null) into.record.combined?.members?.firstOrNull() ?: into.uri else into.uri, members, visibility)
        feed.deletes.combine(members)
        members.forEach { db.feed().deleteByUri(it.value) }
        outbox.drain()
        return uri
    }

    /** Takes [member] out of [combined], which is rebuilt without it, or split when only one member would be left. */
    suspend fun removeMember(combined: ActivityView, member: AtUri) {
        outbox.drain()
        without(combined, member)
    }

    /** Splits [combined] back into its members: it's deleted, with its streams and track, and every member stands on its own again. */
    suspend fun split(combined: ActivityView) {
        val members = requireNotNull(combined.record.combined) { "Not a combined activity" }.members
        queueDelete(combined.uri, combined.visibilityOrPublic)
        feed.deletes.release(members)
        outbox.drain()
    }

    private suspend fun without(combined: ActivityView, member: AtUri) {
        val members = combined.record.combined?.members.orEmpty() - member
        feed.deletes.release(listOf(member))
        if (members.size < CombinedActivity.MIN_MEMBERS) {
            split(combined)
        } else {
            build(combined.uri, base = members.first(), members, combined.visibilityOrPublic)
            outbox.drain()
        }
    }

    /**
     * Queues the combined activity at [uri] built from [members] ([ActivityCombiner]): its record and details at [visibility], its streams
     * and zones, and its track. A rebuild keeps the ranking, title, description, photos and categories it has; a new one starts from
     * [base].
     */
    private suspend fun build(uri: AtUri, base: AtUri, members: List<AtUri>, visibility: String) {
        val stored = members.map { places.read(it) }
        val read = stored.map { member ->
            val record = member.full ?: throw XrpcException(NOT_FOUND_STATUS, "RecordNotFound", "No activity ${member.uri}")
            CombineMember(member.uri, record, member.data, tracks.read(member.uri))
        }
        val current = places.read(uri).takeIf { it.full != null }
        val built = ActivityCombiner.combine(base, read, clock.now(), current?.full?.combined?.sources.orEmpty())
        val trim = privacy.forPublish()
        val record = keptFrom(current?.full, built.record, trim.trim.segmentedPolylineOf(built.route))
        val categories =
            current?.narrowestTiers()?.let { (summary, channels) -> DetailCategory.tiers(visibility, summary + channels) }
                ?: ActivityCombiner.narrowestTiers(
                    visibility,
                    stored.map { it.narrowestTiers().let { (summary, channels) -> summary + channels } },
                )
        val layout = ActivityLayout.split(record, null, uri, visibility, categories)
        queueLayout(layout, uri, visibility)
        privacy.published(uri, visibility, built.route, trim)
        if (built.route.isNotEmpty()) {
            val segments = CombinedRoute.segments(built.route)
            tracks.put(uri, Gpx.writeSegments(record.title ?: record.sport, record.sport, segments).encodeToByteArray())
        } else if (current != null) {
            tracks.delete(uri)
        }
        built.streams?.let { data ->
            // Zones from the member whose heart rate the combined activity takes.
            val served = built.record.combined?.sourcesOf(StreamChannel.HEART_RATE)?.firstOrNull()?.let { feed.streams(it) }
            streams.put(
                uri,
                ActivityStreams(data, visibility, categories, served?.heartRateZones, served?.powerZones, served?.trainingLoad),
            )
        }
        feed.rewrites.set(uri, layout.record)
        feed.updateCached(uri) { feed.rewrites.rewritten(it, layout.record) }
    }

    /** [layout]'s details records, narrowest first, then its activity record at [visibility]. */
    private suspend fun queueLayout(layout: ActivityLayout, uri: AtUri, visibility: String) {
        val rkey = requireNotNull(uri.rkey)
        if (layout.details.isNotEmpty()) clients.requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS)
        for (tier in layout.tiersNarrowestFirst) {
            val details = layout.details[tier] ?: continue
            writer.put(Nsid.FEED_ACTIVITY_DETAILS, rkey, ActivityDetailsRecord.serializer(), details, space = clients.placeOf(tier))
        }
        writer.put(Nsid.FEED_ACTIVITY, rkey, ActivityRecord.serializer(), layout.record, space = clients.placeOf(visibility))
    }

    /** Moves [uri] to [to], as a combined activity's member or a primary's recording moves with it: keeping its categories, capped. */
    private suspend fun follow(uri: AtUri, to: String) {
        clients.requireSpacesFor(to)
        val stored = places.read(uri)
        val from = stored.recordTiers.lastOrNull() ?: throw XrpcException(NOT_FOUND_STATUS, "RecordNotFound", "No activity $uri")
        val (summary, channels) = stored.narrowestTiers()
        fun capped(tiers: Map<String, String>) = DetailCategory.tiers(to, tiers)
        val desired = Shares.of(to, capped(summary), capped(channels), Visibility.narrower(stored.coreTier ?: to, to))
        val before = zonesTier(DetailCategory.tiers(from, summary + channels))
        val after = zonesTier(DetailCategory.tiers(to, summary + channels))
        if (before != after) zones.move(uri, after)
        places.save(stored, desired)
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

/**
 * [built] with [line] as its polyline, and what the athlete gave the combined activity so far ([kept], when it's rebuilt): its title,
 * description, photos and type.
 */
private fun keptFrom(kept: ActivityRecord?, built: ActivityRecord, line: SegmentedPolyline?): ActivityRecord =
    built.copy(
        polyline = line?.polyline,
        polylineGaps = line?.gaps,
        title = kept?.title ?: built.title,
        description = kept?.description ?: built.description,
        media = kept?.media ?: built.media,
        sport = kept?.sport ?: built.sport,
        subSport = kept?.subSport ?: built.subSport,
    )

private const val NOT_FOUND_STATUS = 400
