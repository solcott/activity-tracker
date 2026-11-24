package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.NewPhoto
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.placeOf
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.activity.upload
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.GetPathConditions
import io.github.solcott.activitytracker.lexicon.app.GetTrailPhotos
import io.github.solcott.activitytracker.lexicon.app.GetTrailReports
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.lexicon.atproto.SyncGetBlob
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The athlete's trail reports (segments/trail-reports): what a segment is like now, as a public `feed.trailReport` in their repo with
 * condition tags, a short note and up to [TrailReportRecord.MAX_IMAGES] photos. A photo is new (picked and re-encoded by the device without
 * its metadata) or shared from one of the athlete's own activities, whose already re-encoded bytes are read from wherever that activity
 * keeps them and uploaded again as a new public blob, so nothing links the copy back to the activity. Writes go through the outbox and show
 * at once through [changes], which [applyTo] lays over the AppView's views until it catches up.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // The writes, the reads with this session's changes laid over them, and the photo uploads they need.
class TrailReports(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
    feed: FeedRepository,
) {
    private val held: HeldPhotos = feed.heldPhotos
    private val mutex = Mutex()

    /** What the athlete changed this session, by segment URI. */
    val changes: StateFlow<Map<String, TrailReportChanges>>
        field = MutableStateFlow(emptyMap())

    /** The signed-in athlete, whose own reports they delete rather than report. */
    val self: io.github.solcott.activitytracker.lexicon.Did
        get() = clients.did

    /**
     * Posts a report on [segment] (its URI and CID) with the known [tags], [note] and [photos], at most [TrailReportRecord.MAX_IMAGES], and
     * returns its URI. Needs a tag or a photo.
     */
    suspend fun post(segment: StrongRef, tags: Collection<String>, note: String?, photos: List<ReportPhoto>): AtUri {
        val known = TrailCondition.known(tags)
        require(known.isNotEmpty() || photos.isNotEmpty()) { "Nothing to report" }
        require(photos.size <= TrailReportRecord.MAX_IMAGES) { "At most ${TrailReportRecord.MAX_IMAGES} photos" }
        val images = photos.map { uploadPhoto(it) }
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_TRAIL_REPORT, rkey)
        val now = clock.now()
        val text = note?.trim()?.ifEmpty { null }
        val record = TrailReportRecord(segment, known, text, images, now)
        val view =
            TrailReportView(
                uri = uri,
                cid = Cid(PENDING_CID),
                segment = segment.uri,
                author = ProfileViewBasic(clients.did, clients.handle),
                conditions = known,
                note = text,
                images = images.map { ActivityMediaView.Image(HeldPhotos.url(it.image.ref.link), it.alt, it.aspectRatio) },
                indexedAt = now,
                current = true,
            )
        write(segment.uri, { it.copy(posted = it.posted + view) }) {
            writer.put(Nsid.FEED_TRAIL_REPORT, rkey, TrailReportRecord.serializer(), record)
        }
        return uri
    }

    /** Deletes the athlete's own [report]. */
    suspend fun delete(report: TrailReportView) = delete(report.uri, report.segment)

    private suspend fun delete(report: AtUri, segment: AtUri) {
        val rkey = requireNotNull(report.rkey)
        write(segment, { it.copy(deleted = it.deleted + report.value, posted = it.posted.filterNot { p -> p.uri == report }) }) {
            writer.delete(Nsid.FEED_TRAIL_REPORT, rkey)
        }
    }

    /** Removes the athlete's own [photo] from its report, read fresh from their PDS; its report's last photo, with no tags, deletes it. */
    suspend fun removePhoto(photo: TrailPhotoView) {
        val record = recordOf(photo.report)
        val kept = record.images.filterIndexed { index, _ -> index != photo.position - 1 }
        if (kept.isEmpty() && TrailCondition.known(record.conditions).isEmpty()) return delete(photo.report, photo.segment)
        write(photo.segment, { it.copy(removedPhotos = it.removedPhotos + photo.image.fullsize) }) {
            writer.put(
                Nsid.FEED_TRAIL_REPORT,
                requireNotNull(photo.report.rkey),
                TrailReportRecord.serializer(),
                record.copy(images = kept),
            )
        }
    }

    /**
     * Removes the photo at [position] (from 1) from the athlete's own [report], whose [record] it rewrites; the last photo of a report with
     * no tags deletes it.
     */
    suspend fun removePhoto(report: TrailReportView, record: TrailReportRecord, position: Int) {
        val kept = record.images.filterIndexed { index, _ -> index != position - 1 }
        if (kept.isEmpty() && TrailCondition.known(record.conditions).isEmpty()) return delete(report)
        val rkey = requireNotNull(report.uri.rkey)
        val removed = report.images.getOrNull(position - 1)?.fullsize
        write(report.segment, { it.copy(removedPhotos = it.removedPhotos + listOfNotNull(removed)) }) {
            writer.put(Nsid.FEED_TRAIL_REPORT, rkey, TrailReportRecord.serializer(), record.copy(images = kept))
        }
    }

    /** [report]'s record as the athlete's PDS holds it, for [removePhoto]. */
    suspend fun recordOf(report: TrailReportView): TrailReportRecord = recordOf(report.uri)

    private suspend fun recordOf(report: AtUri): TrailReportRecord =
        AtJson.decodeFromJsonElement(
            TrailReportRecord.serializer(),
            clients.pds
                .query(GetRecord.method, GetRecord.Params(clients.did.value, Nsid.FEED_TRAIL_REPORT, requireNotNull(report.rkey)))
                .value,
        )

    /** A page of [segment]'s reports, newest first, with this session's changes laid over it. */
    suspend fun reportsPage(segment: AtUri, cursor: String? = null): GetTrailReports.Output =
        clients.appView.query(GetTrailReports.method, GetTrailReports.Params(segment, cursor = cursor)).let {
            it.copy(
                reports = applyTo(segment, it.reports).let { r -> if (cursor == null) r else r.filter { v -> v.cid.value != PENDING_CID } },
            )
        }

    /** A page of photos of [segment]'s reports, or of [path]'s, with this session's deletes and removals left out. */
    suspend fun photosPage(segment: AtUri? = null, path: AtUri? = null, cursor: String? = null): GetTrailPhotos.Output =
        clients.appView.query(GetTrailPhotos.method, GetTrailPhotos.Params(segment, path, cursor = cursor)).let {
            it.copy(photos = applyTo(it.photos))
        }

    /** [path]'s conditions rolled up from its segments, with this session's deletes and removed photos left out of its strip. */
    suspend fun pathConditions(path: AtUri): GetPathConditions.Output =
        clients.appView.query(GetPathConditions.method, GetPathConditions.Params(path)).let { it.copy(photos = applyTo(it.photos)) }

    /** [view] with this session's reports laid over its conditions. */
    fun applyTo(view: SegmentView): SegmentView =
        changes.value[view.uri.value]?.let { view.copy(conditions = applyReportChanges(view.conditions, it, clock.now())) } ?: view

    /** [reports] of one segment with this session's posts first and its deletes and removed photos left out. */
    fun applyTo(segment: AtUri, reports: List<TrailReportView>): List<TrailReportView> {
        val change = changes.value[segment.value] ?: return reports
        val pending = change.posted.filter { p -> reports.none { it.uri == p.uri } }
        return (pending + reports)
            .filterNot { it.uri.value in change.deleted }
            .map { r -> r.copy(images = r.images.filterNot { it.fullsize in change.removedPhotos }) }
    }

    /** [photos] with this session's deleted reports' and removed photos left out. */
    fun applyTo(photos: List<TrailPhotoView>): List<TrailPhotoView> {
        val all = changes.value.values
        val deleted = all.flatMap { it.deleted }.toSet()
        val removed = all.flatMap { it.removedPhotos }.toSet()
        return photos.filterNot { it.report.value in deleted || it.image.fullsize in removed }
    }

    private suspend fun uploadPhoto(photo: ReportPhoto): ActivityMedia.Image =
        when (photo) {
            is ReportPhoto.New -> clients.upload(photo.photo, held).copy(alt = photo.alt?.take(ActivityMedia.Image.MAX_ALT_LENGTH))
            is ReportPhoto.Shared -> {
                val image = photo.image
                val bytes = requireNotNull(ownBlob(image.image.ref.link, photo.visibility)) { "That photo isn't there any more" }
                val ratio = image.aspectRatio
                clients
                    .upload(NewPhoto(bytes, image.image.mimeType, ratio?.width ?: 1, ratio?.height ?: 1), held)
                    .copy(alt = (photo.alt ?: image.alt)?.take(ActivityMedia.Image.MAX_ALT_LENGTH))
            }
        }

    /** The athlete's own blob [cid] from where an activity of [visibility] keeps it: their repo, or that tier's space. */
    private suspend fun ownBlob(cid: String, visibility: String): ByteArray? {
        val place = clients.placeOf(visibility)
        return if (place == null) clients.pds.queryBytes(SyncGetBlob.method, SyncGetBlob.Params(clients.did, Cid(cid))).bytes
        else (clients.spaces.getBlob(place, clients.did, Cid(cid)) as? SpaceRead.Found)?.value?.bytes
    }

    private suspend fun write(segment: AtUri, change: (TrailReportChanges) -> TrailReportChanges, queue: suspend () -> Unit) {
        mutex.withLock {
            val previous = changes.value
            changes.update { it + (segment.value to change(it[segment.value] ?: TrailReportChanges())) }
            undoingOnFailure({ changes.value = previous }) { queue() }
        }
        outbox.drain()
    }

    private companion object {
        /** A posted report's CID until the AppView lists it: only others report it, and they see the served one. */
        const val PENDING_CID = "pending"
    }
}

/** A photo for a report: a new one picked on the device, or one of the athlete's own activity photos and that activity's visibility. */
sealed interface ReportPhoto {
    val alt: String?

    data class New(val photo: NewPhoto, override val alt: String? = null) : ReportPhoto

    data class Shared(val image: ActivityMedia.Image, val visibility: String, override val alt: String? = null) : ReportPhoto {
        /** Sharing makes a public copy of a photo from a ride that isn't public, so the athlete is asked first. */
        val fromNonPublic: Boolean
            get() = visibility != Visibility.PUBLIC
    }
}

/** The athlete's changes to one segment's reports this session: what they posted, deleted, and the photo URLs they removed. */
data class TrailReportChanges(
    val posted: List<TrailReportView> = emptyList(),
    val deleted: Set<String> = emptySet(),
    val removedPhotos: Set<String> = emptySet(),
)

/**
 * [served] conditions with [changes] laid over them at [now]. A report posted within [POSTED_GRACE] that the AppView hasn't counted yet
 * (its newest counted report is older) shows at once: its tags lead as the AppView would lead with them when they're the newest (closed,
 * else its first surface tag when nothing leads), it adds a rider, its note becomes the latest, and its photos go first. Deleted reports'
 * and removed photos leave the strip; their counts catch up when the AppView does.
 */
internal fun applyReportChanges(served: TrailConditions?, changes: TrailReportChanges, now: Instant): TrailConditions? {
    val pending = changes.posted.filter { it.isUncounted(served, now) }
    if (served == null && pending.isEmpty()) return null
    val start = served ?: TrailConditions(riders = 0, tags = emptyList(), photoCount = 0)
    val conditions = pending.sortedBy { it.indexedAt }.fold(start) { shown, report -> shown.withPosted(report) }
    val photos = conditions.photos.filterNot { it.report.value in changes.deleted || it.image.fullsize in changes.removedPhotos }
    return conditions.copy(
        photos = photos,
        latestNote = conditions.latestNote?.takeUnless { it.uri.value in changes.deleted },
    )
}

/** Whether this report, posted within [POSTED_GRACE] of [now], is newer than [served]'s newest counted one. */
private fun TrailReportView.isUncounted(served: TrailConditions?, now: Instant): Boolean =
    now - indexedAt < POSTED_GRACE && (served?.latestAt?.let { it < indexedAt - SKEW } ?: true)

/** These conditions with the posted [report] counted: its tags lead as [leadAfter] says, it adds a rider, a note and its photos. */
private fun TrailConditions.withPosted(report: TrailReportView): TrailConditions {
    val tags = report.conditions
    val tagged = tags.isNotEmpty()
    val posted =
        report.images.mapIndexed { index, image ->
            TrailPhotoView(image, report.uri, report.cid, index + 1, report.segment, report.author, tags, report.indexedAt)
        }
    val newLead = leadAfter(tags)
    return copy(
        lead = newLead ?: lead,
        leadRiders =
            when {
                !tagged -> leadRiders
                newLead != lead -> 1
                else -> (leadRiders ?: 0) + 1
            },
        riders = riders + if (tagged) 1 else 0,
        latestAt = if (tagged) report.indexedAt else latestAt,
        latestNote = if (report.note != null) report else latestNote,
        photoCount = photoCount + posted.size,
        photos = (posted + photos).take(PHOTOS_SHOWN),
    )
}

/** What leads once a report with [tags] is the newest: closed, else its first surface tag when nothing (or closed) leads. */
private fun TrailConditions.leadAfter(tags: List<String>): String? =
    when {
        TrailCondition.CLOSED in tags -> TrailCondition.CLOSED
        lead == null || lead == TrailCondition.CLOSED -> tags.firstOrNull { it in TrailCondition.surface }
        else -> lead
    }

/** How long a posted report shows before the AppView counts it: Jetstream takes seconds, so one still uncounted was dropped. */
internal val POSTED_GRACE = 5.minutes

/** How far the device's clock may run ahead of the AppView's when judging whether it counted a report. */
private val SKEW = 1.minutes

/** How many photos a segment's conditions show. */
private const val PHOTOS_SHOWN = 4
