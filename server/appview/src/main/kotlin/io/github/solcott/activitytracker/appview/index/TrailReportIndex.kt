package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.TrailReportBlobs
import io.github.solcott.activitytracker.appview.db.TrailReportTags
import io.github.solcott.activitytracker.appview.db.TrailReports
import io.github.solcott.activitytracker.appview.segments.suggestable
import io.github.solcott.activitytracker.appview.utcDate
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.trailReport` (segments/trail-reports), from repos only.

/** How far back an effort makes its author a recent rider who may report. */
internal val TRAIL_REPORT_RIDE_WINDOW = 30.days

/** How long a report's tags count toward a segment's conditions. */
internal val TRAIL_REPORT_CURRENT_WINDOW = 14.days

/** Reports one author may have accepted on one segment in a UTC day. */
internal const val TRAIL_REPORTS_PER_DAY = 5

/**
 * A report from [author]'s repo. A new one is stored only when it names an indexed active segment that isn't a lift, holds a known tag or a
 * photo, [author] has an effort on it that began within [TRAIL_REPORT_RIDE_WINDOW] of [now], and they have fewer than
 * [TRAIL_REPORTS_PER_DAY] accepted on it today; anything else is dropped for good. A rewrite of a stored report replaces its tags, note and
 * photos and keeps its first indexing time, and a rewrite left with neither tags nor photos deletes it; one naming another segment is
 * ignored, since it would skip those checks.
 */
@Suppress("ReturnCount") // Each reason a report is dropped.
internal fun JdbcTransaction.upsertTrailReport(json: JsonObject, author: Did, uri: AtUri, cid: String, now: Long) {
    val record = AtJson.decodeFromJsonElement(TrailReportRecord.serializer(), json)
    val tags = TrailCondition.known(record.conditions.take(TrailCondition.MAX))
    val photos = boundedPhotos(record.images.take(TrailReportRecord.MAX_IMAGES)).orEmpty()
    val stored =
        TrailReports.select(TrailReports.indexedAt, TrailReports.dayUtc, TrailReports.segmentUri)
            .where { TrailReports.uri eq uri.value }
            .firstOrNull()
    val segment = record.segment.uri
    // A report stays on the segment it was accepted for: a rewrite naming another would skip the checks below, so it's ignored.
    if (stored != null && stored[TrailReports.segmentUri] != segment.value.take(MAX_URI_LENGTH)) return
    if (tags.isEmpty() && photos.isEmpty()) {
        if (stored != null) deleteTrailReports(listOf(uri.value))
        return
    }
    if (stored == null && !mayReport(segment.value, author.value, now)) return
    val indexedAt = stored?.get(TrailReports.indexedAt) ?: now
    TrailReports.upsert {
        it[TrailReports.uri] = uri.value
        it[TrailReports.cid] = cid
        it[authorDid] = author.value
        it[segmentUri] = segment.value.take(MAX_URI_LENGTH)
        it[note] = record.note?.trim()?.let { ActivityRecord.clampText(it, TrailReportRecord.MAX_NOTE_GRAPHEMES) }?.ifEmpty { null }
        it[hasConditions] = tags.isNotEmpty()
        it[TrailReports.indexedAt] = indexedAt
        it[dayUtc] = stored?.get(TrailReports.dayUtc) ?: utcDate(now).toString()
    }
    TrailReportTags.deleteWhere { reportUri eq uri.value }
    TrailReportTags.batchInsert(tags) { tag ->
        this[TrailReportTags.reportUri] = uri.value
        this[TrailReportTags.tag] = tag
    }
    TrailReportBlobs.deleteWhere { reportUri eq uri.value }
    TrailReportBlobs.batchInsert(photos.withIndex()) { (index, photo) ->
        this[TrailReportBlobs.reportUri] = uri.value
        this[TrailReportBlobs.sortIndex] = index
        this[TrailReportBlobs.authorDid] = author.value
        this[TrailReportBlobs.cid] = photo.image.ref.link
        this[TrailReportBlobs.mimeType] = photo.image.mimeType
        this[TrailReportBlobs.alt] = photo.alt
        this[TrailReportBlobs.width] = photo.aspectRatio?.width
        this[TrailReportBlobs.height] = photo.aspectRatio?.height
    }
}

/** Deleted reports, with their tags and photos. */
internal fun JdbcTransaction.deleteTrailReports(uris: Collection<String>) {
    if (uris.isEmpty()) return
    TrailReportTags.deleteWhere { reportUri inList uris }
    TrailReportBlobs.deleteWhere { reportUri inList uris }
    TrailReports.deleteWhere { TrailReports.uri inList uris }
}

/** [author]'s reports, for a deleted account. */
internal fun JdbcTransaction.deleteTrailReportsBy(author: Did) =
    deleteTrailReports(TrailReports.select(TrailReports.uri).where { TrailReports.authorDid eq author.value }.map { it[TrailReports.uri] })

/**
 * Whether [did] may report on [segment] at [now]: it's an active run segment, never a lift ([suggestable]), they have an effort on it, of
 * any visibility, that began within [TRAIL_REPORT_RIDE_WINDOW] of [now], and they have fewer than [TRAIL_REPORTS_PER_DAY] accepted on it
 * that UTC day. The indexer and `getSegment`'s flag both ask this.
 */
internal fun JdbcTransaction.mayReport(segment: String, did: String, now: Long): Boolean =
    suggestable(segment) &&
        lastEffortAt(segment, did, now) != null &&
        reportsOn(segment, did, utcDate(now).toString()) < TRAIL_REPORTS_PER_DAY

/** When [did]'s newest effort on [segment] began, only when within [TRAIL_REPORT_RIDE_WINDOW] of [now]. */
internal fun JdbcTransaction.lastEffortAt(segment: String, did: String, now: Long): Long? {
    val since = now - TRAIL_REPORT_RIDE_WINDOW.inWholeMilliseconds
    return SegmentEfforts.select(SegmentEfforts.startedAt)
        .where {
            (SegmentEfforts.segmentUri eq segment) and
                (SegmentEfforts.authorDid eq did) and
                (SegmentEfforts.startedAt greaterEq since) and
                (SegmentEfforts.startedAt neq 0L)
        }
        .maxOfOrNull { it[SegmentEfforts.startedAt] }
}

/** How many reports [did] has accepted on [segment] on the UTC [day]. */
private fun JdbcTransaction.reportsOn(segment: String, did: String, day: String): Long =
    TrailReports.selectAll()
        .where { (TrailReports.authorDid eq did) and (TrailReports.segmentUri eq segment) and (TrailReports.dayUtc eq day) }
        .count()
