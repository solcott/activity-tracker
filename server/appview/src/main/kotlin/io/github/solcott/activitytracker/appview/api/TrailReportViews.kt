package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.TrailReportBlobs
import io.github.solcott.activitytracker.appview.db.TrailReportTags
import io.github.solcott.activitytracker.appview.db.TrailReports
import io.github.solcott.activitytracker.appview.index.TRAIL_REPORT_CURRENT_WINDOW
import io.github.solcott.activitytracker.appview.segments.ReportFacts
import io.github.solcott.activitytracker.appview.segments.summarizeConditions
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GetTrailPhotos
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

// Trail reports' views (segments/trail-reports): the conditions summary every segment view carries, the reports list, photos and a trail's
// roll-up. Reports are public, so what a viewer sees depends only on blocks, mutes and labels.

/** How many photos a segment view carries. */
internal const val SEGMENT_PHOTOS = 4

/** How many photos a trail's roll-up carries. */
internal const val PATH_PHOTOS = 5

/**
 * Reports [viewer] may see anywhere, summaries included: in no block with its author, served (no `!takedown` on it or its account, nor an
 * account that isn't active), and not hidden (`!hide`), the viewer's own excepted.
 */
internal fun reportsShownTo(viewer: Did?): Op<Boolean> =
    notBlocked(viewer, TrailReports.authorDid) and
        served(viewer, TrailReports.authorDid, TrailReports.uri, TrailReports.cid) and
        unhidden(viewer, TrailReports.authorDid, TrailReports.uri)

/** Reports a list or photo strip shows [viewer]: [reportsShownTo], less the authors they mute. */
internal fun reportsListedTo(viewer: Did?): Op<Boolean> = reportsShownTo(viewer) and notMuted(viewer, TrailReports.authorDid)

/**
 * The conditions of each of [segments] as [viewer] sees them at [now], with [SEGMENT_PHOTOS] photos when [withPhotos]. A segment with no
 * report the viewer may see is absent.
 */
@Suppress("LongParameterList")
internal fun JdbcTransaction.conditionsOf(
    segments: Collection<String>,
    viewer: Did?,
    now: Long,
    avatars: AvatarUrls,
    media: MediaUrls,
    withPhotos: Boolean,
): Map<String, TrailConditions> {
    val rows =
        segments.chunked(LOOKUP_CHUNK).flatMap { chunk ->
            TrailReports.selectAll().where { (TrailReports.segmentUri inList chunk) and reportsShownTo(viewer) }.toList()
        }
    if (rows.isEmpty()) return emptyMap()
    val tags = tagsOf(rows.map { it[TrailReports.uri] })
    // Which authors the viewer mutes.
    val muted =
        if (viewer == null) emptySet()
        else rows.map { it[TrailReports.authorDid] }.distinct().filter { ownMute(viewer.value, it) != null }.toSet()
    val summaries =
        rows
            .groupBy { it[TrailReports.segmentUri] }
            .mapValues { (_, reports) ->
                summarizeConditions(
                    reports.map {
                        val uri = it[TrailReports.uri]
                        ReportFacts(uri, it[TrailReports.authorDid], it[TrailReports.indexedAt], tags[uri].orEmpty(), it[TrailReports.note])
                    },
                    now,
                    muted,
                )
            }
    val notes = summaries.values.mapNotNull { it.latestNote }.toSet()
    val noteViews = trailReportViews(rows.filter { it[TrailReports.uri] in notes }, now, avatars, media).associateBy { it.uri.value }
    val photoCounts = photoCountsOf(summaries.keys, viewer)
    return summaries.mapValues { (segment, summary) ->
        TrailConditions(
            lead = summary.lead,
            leadRiders = summary.leadRiders,
            riders = summary.riders,
            latestAt = summary.latestAt?.let(Instant::fromEpochMilliseconds),
            tags = summary.tags,
            latestNote = summary.latestNote?.let(noteViews::get),
            lastReportAt = summary.lastReportAt?.let(Instant::fromEpochMilliseconds),
            lastReportTags = summary.lastReportTags,
            photoCount = photoCounts[segment] ?: 0,
            photos =
                if (withPhotos) trailPhotos(TrailReports.segmentUri eq segment, viewer, SEGMENT_PHOTOS, null, avatars, media).photos
                else emptyList(),
        )
    }
}

/** Each report's known tags, in [TrailCondition.all]'s order. */
internal fun JdbcTransaction.tagsOf(reports: Collection<String>): Map<String, List<String>> =
    reports
        .chunked(LOOKUP_CHUNK)
        .flatMap { chunk ->
            TrailReportTags.selectAll()
                .where { TrailReportTags.reportUri inList chunk }
                .map { it[TrailReportTags.reportUri] to it[TrailReportTags.tag] }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, tags) -> TrailCondition.known(tags) }

/** How many photos each of [segments] has among the reports [viewer] is listed. */
private fun JdbcTransaction.photoCountsOf(segments: Collection<String>, viewer: Did?): Map<String, Int> {
    val count = TrailReportBlobs.reportUri.count()
    return segments
        .chunked(LOOKUP_CHUNK)
        .flatMap { chunk ->
            TrailReportBlobs.join(TrailReports, JoinType.INNER, TrailReportBlobs.reportUri, TrailReports.uri)
                .select(TrailReports.segmentUri, count)
                .where { (TrailReports.segmentUri inList chunk) and reportsListedTo(viewer) }
                .groupBy(TrailReports.segmentUri)
                .map { it[TrailReports.segmentUri] to it[count].toInt() }
        }
        .toMap()
}

/** How many photos the reports on [where] that [viewer] is listed hold. */
internal fun JdbcTransaction.photoCount(where: Op<Boolean>, viewer: Did?): Int =
    TrailReportBlobs.join(TrailReports, JoinType.INNER, TrailReportBlobs.reportUri, TrailReports.uri)
        .selectAll()
        .where { where and reportsListedTo(viewer) }
        .count()
        .toInt()

/**
 * A page of photos of the reports matching [where] that [viewer] is listed, newest report first and each report's in order, and the cursor
 * after it (`"<indexedAt>::<report>::<index>"`).
 */
@Suppress("LongParameterList")
internal fun JdbcTransaction.trailPhotos(
    where: Op<Boolean>,
    viewer: Did?,
    limit: Int,
    cursor: String?,
    avatars: AvatarUrls,
    media: MediaUrls,
): GetTrailPhotos.Output {
    val rows =
        TrailReportBlobs.join(TrailReports, JoinType.INNER, TrailReportBlobs.reportUri, TrailReports.uri)
            .selectAll()
            .where { where and reportsListedTo(viewer) and photoAfter(cursor) }
            .orderBy(
                TrailReports.indexedAt to SortOrder.DESC,
                TrailReports.uri to SortOrder.DESC,
                TrailReportBlobs.sortIndex to SortOrder.ASC,
            )
            .limit(limit)
            .toList()
    if (rows.isEmpty()) return GetTrailPhotos.Output(emptyList())
    val reports = rows.map { it[TrailReports.uri] }.distinct()
    val tags = tagsOf(reports)
    val labels = labelViews(rows.associate { it[TrailReports.uri] to it[TrailReports.cid] })
    val profiles = basicProfiles(rows.map { it[TrailReports.authorDid] }, avatars)
    val photos = rows.map {
        val report = it[TrailReports.uri]
        val author = it[TrailReports.authorDid]
        TrailPhotoView(
            image = imageOf(it, media),
            report = AtUri(report),
            cid = Cid(it[TrailReports.cid]),
            position = it[TrailReportBlobs.sortIndex] + 1,
            segment = AtUri(it[TrailReports.segmentUri]),
            author = profiles[author] ?: unknownProfile(author),
            conditions = tags[report].orEmpty(),
            indexedAt = Instant.fromEpochMilliseconds(it[TrailReports.indexedAt]),
            labels = labels[report],
        )
    }
    val next = nextCursor(rows, limit) { "${it[TrailReports.indexedAt]}::${it[TrailReports.uri]}::${it[TrailReportBlobs.sortIndex]}" }
    return GetTrailPhotos.Output(photos, next)
}

/** Photos after a [trailPhotos] cursor: an older report, or a later photo of the same one. */
private fun photoAfter(cursor: String?): Op<Boolean> {
    val parts = cursor?.let { cursorParts(it, PHOTO_CURSOR_PARTS) }
    val sort = parts?.get(0)?.toLongOrNull()
    val position = parts?.get(2)?.toIntOrNull()
    if (parts == null || sort == null || position == null) return Op.TRUE
    val report = parts[1]
    return (TrailReports.indexedAt less sort) or
        ((TrailReports.indexedAt eq sort) and (TrailReports.uri less report)) or
        ((TrailReports.indexedAt eq sort) and (TrailReports.uri eq report) and (TrailReportBlobs.sortIndex greater position))
}

/** The views of report [rows] at [now], in their order: author, tags, note, photos, labels and whether they're current. */
internal fun JdbcTransaction.trailReportViews(
    rows: List<ResultRow>,
    now: Long,
    avatars: AvatarUrls,
    media: MediaUrls,
): List<TrailReportView> {
    if (rows.isEmpty()) return emptyList()
    val uris = rows.map { it[TrailReports.uri] }
    val tags = tagsOf(uris)
    val images =
        uris
            .chunked(LOOKUP_CHUNK)
            .flatMap { chunk ->
                TrailReportBlobs.selectAll().where { TrailReportBlobs.reportUri inList chunk }.orderBy(TrailReportBlobs.sortIndex).toList()
            }
            .groupBy({ it[TrailReportBlobs.reportUri] }, { imageOf(it, media) })
    val labels = labelViews(rows.associate { it[TrailReports.uri] to it[TrailReports.cid] })
    val profiles = basicProfiles(rows.map { it[TrailReports.authorDid] }, avatars)
    val since = now - TRAIL_REPORT_CURRENT_WINDOW.inWholeMilliseconds
    return rows.map {
        val uri = it[TrailReports.uri]
        val author = it[TrailReports.authorDid]
        TrailReportView(
            uri = AtUri(uri),
            cid = Cid(it[TrailReports.cid]),
            segment = AtUri(it[TrailReports.segmentUri]),
            author = profiles[author] ?: unknownProfile(author),
            conditions = tags[uri].orEmpty(),
            note = it[TrailReports.note],
            images = images[uri].orEmpty(),
            indexedAt = Instant.fromEpochMilliseconds(it[TrailReports.indexedAt]),
            current = it[TrailReports.indexedAt] >= since,
            labels = labels[uri],
        )
    }
}

/**
 * Whether [cid] is a photo of one of [did]'s trail reports (segments/trail-reports) that carries no `!takedown`, by an account that's
 * served. Reports are public, so their photos redirect like a public activity's.
 */
internal fun JdbcTransaction.isServedTrailReportPhoto(did: String, cid: String): Boolean =
    !TrailReportBlobs.join(TrailReports, JoinType.INNER, TrailReportBlobs.reportUri, TrailReports.uri)
        .select(TrailReportBlobs.reportUri)
        .where {
            (TrailReportBlobs.authorDid eq did) and
                (TrailReportBlobs.cid eq cid) and
                served(null as Expression<String>?, TrailReports.authorDid, TrailReports.uri, TrailReports.cid)
        }
        .limit(1)
        .empty()

/** A photo row's image, linked through the media redirect. */
private fun imageOf(row: ResultRow, media: MediaUrls): ActivityMediaView.Image {
    val width = row[TrailReportBlobs.width]
    val height = row[TrailReportBlobs.height]
    return ActivityMediaView.Image(
        fullsize = media.url(row[TrailReportBlobs.authorDid], row[TrailReportBlobs.cid]),
        alt = row[TrailReportBlobs.alt],
        aspectRatio = if (width != null && height != null) AspectRatio(width, height) else null,
    )
}

private const val LOOKUP_CHUNK = 500

/** A photo cursor's parts: the report's indexed time, its URI and the photo's index. */
private const val PHOTO_CURSOR_PARTS = 3
