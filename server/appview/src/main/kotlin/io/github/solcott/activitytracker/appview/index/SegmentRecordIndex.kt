@file:Suppress("TooManyFunctions") // Indexing segments: validation, the creation limit, statuses and duplicates, and deletes.

package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.text.BreakIterator
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.segment` records (segments/segment-records), from the repo only.

/** How many new segments one creator may have indexed in any [CREATION_WINDOW_MS]. */
internal const val DAILY_SEGMENT_LIMIT = 10L
internal const val CREATION_WINDOW_MS = 24L * 60 * 60 * 1000

/** The statuses whose segments are matched, and that a new segment can duplicate. */
internal val LIVE_STATUSES = listOf(SegmentStatus.PROPOSED, SegmentStatus.ACTIVE)

internal const val MAX_NAME_LENGTH = 1000
private const val MAX_NAME_GRAPHEMES = 100
internal const val MAX_TOKEN_LENGTH = 64
private const val MAX_LAT = 90.0
private const val MAX_LNG = 180.0
private const val E7 = 1e7

/**
 * A segment from [author]'s repo. Indexed only with a name, a known sport group and a line of at least two valid positions whose length,
 * computed here, is its group's minimum to 50 km; anything else is dropped like an undecodable record, as is a new segment past its
 * creator's [DAILY_SEGMENT_LIMIT]. A new segment, or one whose line or sport changed, loses its efforts, gets its status again (duplicate
 * of the earliest live segment it duplicates, else proposed) and, unless a duplicate, is queued to be matched against past tracks; the
 * segments that duplicated its old version are checked again. A rename keeps its status.
 */
@Suppress("LongParameterList", "LongMethod") // The record, and where it's from; then each part of indexing it.
internal fun JdbcTransaction.upsertSegment(json: JsonObject, author: Did, uri: AtUri, cid: String, jobs: SegmentJobs, now: Long) {
    val record = AtJson.decodeFromJsonElement(SegmentRecord.serializer(), json)
    val name = validName(record.name)
    val line = validLine(record.sport, record.line)

    val old =
        Segments.select(Segments.line, Segments.sport, Segments.status, Segments.duplicateOf, Segments.firstIndexedAt)
            .where { Segments.uri eq uri.value }
            .firstOrNull()
    if (old == null) require(segmentsCreatedSince(author, now) < DAILY_SEGMENT_LIMIT) { "over the daily limit of new segments" }
    val changed = old == null || old[Segments.line] != record.line || old[Segments.sport] != record.sport
    val firstIndexed = old?.get(Segments.firstIndexedAt) ?: now
    val original = if (changed) duplicatesOf(record.sport, line, before = firstIndexed to uri.value).firstOrNull() else null
    Segments.upsert {
        it[Segments.uri] = uri.value
        it[Segments.cid] = cid
        it[authorDid] = author.value
        it[Segments.name] = name.take(MAX_NAME_LENGTH)
        it[sport] = record.sport
        it[Segments.line] = record.line
        it[Segments.record] = AtJson.encodeToString(SegmentRecord.serializer(), record.copy(name = name))
        it[distanceM] = SegmentGeometry.lengthMeters(line).roundToLong()
        it[minLatE7] = line.minOf { p -> p.lat }.e7()
        it[maxLatE7] = line.maxOf { p -> p.lat }.e7()
        it[minLngE7] = line.minOf { p -> p.lng }.e7()
        it[maxLngE7] = line.maxOf { p -> p.lng }.e7()
        it[dataSource] = record.source.take(MAX_TOKEN_LENGTH)
        it[license] = record.license.take(MAX_TOKEN_LENGTH)
        it[indexedAt] = now
        it[firstIndexedAt] = firstIndexed
        it[status] = if (changed) (if (original != null) SegmentStatus.DUPLICATE else SegmentStatus.PROPOSED) else old[Segments.status]
        it[duplicateOf] = if (changed) original else old[Segments.duplicateOf]
    }
    if (!changed) return
    SegmentEfforts.deleteWhere { segmentUri eq uri.value }
    replaceSegmentCells(uri.value, line)
    if (original == null) jobs.enqueueSegment(uri.value, now)
    if (old != null) recheckDuplicatesOf(listOf(uri.value), jobs, now)
}

/** [name] trimmed, if it's a valid segment name: not empty, and at most [MAX_NAME_GRAPHEMES] graphemes. Throws otherwise. */
internal fun validName(name: String): String {
    val trimmed = name.trim()
    require(trimmed.isNotEmpty()) { "an empty name" }
    require(graphemes(trimmed) <= MAX_NAME_GRAPHEMES) { "a name over $MAX_NAME_GRAPHEMES graphemes" }
    return trimmed
}

/**
 * [encoded] decoded, if it's a valid line for a segment of [sport]: a known group, at least two positions in range, and a length from the
 * group's minimum to [SegmentSportGroup.MAX_METERS]. Throws [IllegalArgumentException] naming what's wrong otherwise.
 */
internal fun validLine(sport: String, encoded: String): List<GeoPoint> {
    require(sport in SegmentSportGroup.all) { "unknown sport group $sport" }
    val line = requireNotNull(SegmentGeometry.decodePolyline(encoded)) { "a malformed line" }
    require(line.size >= 2) { "a line of ${line.size} positions" }
    require(line.all { it.lat in -MAX_LAT..MAX_LAT && it.lng in -MAX_LNG..MAX_LNG }) { "a position out of range" }
    val length = SegmentGeometry.lengthMeters(line)
    val min = SegmentSportGroup.minMeters(sport)
    require(length >= min && length <= SegmentSportGroup.MAX_METERS) {
        "a line of ${length.roundToLong()} m, outside $min to ${SegmentSportGroup.MAX_METERS} m"
    }
    return line
}

/** How many of [author]'s segments were first indexed in the [CREATION_WINDOW_MS] before [now]. */
internal fun JdbcTransaction.segmentsCreatedSince(author: Did, now: Long): Long =
    Segments.select(Segments.uri)
        .where { (Segments.authorDid eq author.value) and (Segments.firstIndexedAt greater now - CREATION_WINDOW_MS) }
        .count()

/**
 * The live ([LIVE_STATUSES]) segments of [sport] that [line] duplicates ([SegmentGeometry.duplicates]), earliest first indexed first. With
 * [before], a segment's own first-indexed time and URI, only those that came before it, so a segment never duplicates itself or a later
 * one. Candidates must have cells at both of the line's ends, since a duplicate's ends lie within its distance of them.
 */
internal fun JdbcTransaction.duplicatesOf(sport: String, line: List<GeoPoint>, before: Pair<Long, String>? = null): List<String> {
    fun near(point: GeoPoint) =
        SegmentCellIndex.select(SegmentCellIndex.segmentUri)
            .where { SegmentCellIndex.cell eq SegmentCells.cellOf(point.lat, point.lng) }
            .map { it[SegmentCellIndex.segmentUri] }
            .toSet()
    val nearBoth = near(line.first()) intersect near(line.last())
    if (nearBoth.isEmpty()) return emptyList()
    return Segments.select(Segments.uri, Segments.line, Segments.firstIndexedAt)
        .where { (Segments.uri inList nearBoth) and (Segments.sport eq sport) and (Segments.status inList LIVE_STATUSES) }
        .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
        .filter { row -> before == null || (row[Segments.firstIndexedAt] to row[Segments.uri]).let { earlier(it, before) } }
        .filter { row -> SegmentGeometry.decodePolyline(row[Segments.line])?.let { SegmentGeometry.duplicates(line, it) } == true }
        .map { it[Segments.uri] }
}

private fun earlier(a: Pair<Long, String>, b: Pair<Long, String>) = a.first < b.first || (a.first == b.first && a.second < b.second)

/**
 * Checks again each segment marked duplicate of one of [originals], which was deleted, archived or changed, earliest first indexed first,
 * so each in turn sees the ones before it settled. One that duplicates no other live segment becomes proposed and is queued to be matched.
 */
internal fun JdbcTransaction.recheckDuplicatesOf(originals: List<String>, jobs: SegmentJobs, now: Long) {
    if (originals.isEmpty()) return
    val duplicates =
        Segments.selectAll()
            .where { (Segments.status eq SegmentStatus.DUPLICATE) and (Segments.duplicateOf inList originals) }
            .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
            .toList()
    for (row in duplicates) {
        val uri = row[Segments.uri]
        val line = SegmentGeometry.decodePolyline(row[Segments.line]) ?: continue
        val original = duplicatesOf(row[Segments.sport], line, before = row[Segments.firstIndexedAt] to uri).firstOrNull()
        Segments.update({ Segments.uri eq uri }) {
            it[status] = if (original != null) SegmentStatus.DUPLICATE else SegmentStatus.PROPOSED
            it[duplicateOf] = original
        }
        if (original == null) jobs.enqueueSegment(uri, now)
    }
}

/** A deleted segment: it, its cells and every effort on it go, and the segments that duplicated it are checked again. */
internal fun JdbcTransaction.deleteSegment(uri: AtUri, jobs: SegmentJobs, now: Long) = deleteSegments(listOf(uri.value), jobs, now)

/** [author]'s segments, for a deleted account. */
internal fun JdbcTransaction.deleteSegmentsBy(author: Did, jobs: SegmentJobs, now: Long) =
    deleteSegments(Segments.select(Segments.uri).where { Segments.authorDid eq author.value }.map { it[Segments.uri] }, jobs, now)

private fun JdbcTransaction.deleteSegments(uris: List<String>, jobs: SegmentJobs, now: Long) {
    if (uris.isEmpty()) return
    SegmentEfforts.deleteWhere { segmentUri inList uris }
    SegmentCellIndex.deleteWhere { segmentUri inList uris }
    Segments.deleteWhere { Segments.uri inList uris }
    recheckDuplicatesOf(uris, jobs, now)
}

private fun JdbcTransaction.replaceSegmentCells(uri: String, line: List<GeoPoint>) {
    SegmentCellIndex.deleteWhere { segmentUri eq uri }
    SegmentCellIndex.batchInsert(SegmentCells.cellsOf(line)) {
        this[SegmentCellIndex.cell] = it
        this[SegmentCellIndex.segmentUri] = uri
    }
}

internal fun Double.e7(): Int = (this * E7).roundToInt()

private fun graphemes(text: String): Int {
    val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
    var count = 0
    while (breaks.next() != BreakIterator.DONE) count++
    return count
}
