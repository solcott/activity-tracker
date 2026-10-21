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
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.text.BreakIterator
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.segment` records (segments/segment-records), from the repo only.

/** Shortest and longest a segment may be along its line, in meters. */
internal const val MIN_SEGMENT_M = 200.0
internal const val MAX_SEGMENT_M = 50_000.0

private const val MAX_NAME_LENGTH = 1000
private const val MAX_NAME_GRAPHEMES = 100
private const val MAX_TOKEN_LENGTH = 64
private const val MAX_LAT = 90.0
private const val MAX_LNG = 180.0
private const val E7 = 1e7

/**
 * A segment from [author]'s repo. Indexed only with a name, a known sport group and a line of at least two valid positions whose length,
 * computed here, is 200 m to 50 km; anything else is dropped like an undecodable record. A new segment, or one whose line or sport changed,
 * loses its efforts and is queued to be matched against past tracks. Its cells are rewritten either way.
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertSegment(json: JsonObject, author: Did, uri: AtUri, cid: String, jobs: SegmentJobs, now: Long) {
    val record = AtJson.decodeFromJsonElement(SegmentRecord.serializer(), json)
    val name = record.name.trim()
    require(name.isNotEmpty()) { "an empty name" }
    require(graphemes(name) <= MAX_NAME_GRAPHEMES) { "a name over $MAX_NAME_GRAPHEMES graphemes" }
    require(record.sport in SegmentSportGroup.all) { "unknown sport group ${record.sport}" }
    val line = requireNotNull(SegmentGeometry.decodePolyline(record.line)) { "a malformed line" }
    require(line.size >= 2) { "a line of ${line.size} positions" }
    require(line.all { it.lat in -MAX_LAT..MAX_LAT && it.lng in -MAX_LNG..MAX_LNG }) { "a position out of range" }
    val length = SegmentGeometry.lengthMeters(line)
    require(length in MIN_SEGMENT_M..MAX_SEGMENT_M) { "a line of $length m" }

    val old = Segments.select(Segments.line, Segments.sport).where { Segments.uri eq uri.value }.firstOrNull()
    val changed = old == null || old[Segments.line] != record.line || old[Segments.sport] != record.sport
    Segments.upsert {
        it[Segments.uri] = uri.value
        it[Segments.cid] = cid
        it[authorDid] = author.value
        it[Segments.name] = name.take(MAX_NAME_LENGTH)
        it[sport] = record.sport
        it[Segments.line] = record.line
        it[Segments.record] = AtJson.encodeToString(SegmentRecord.serializer(), record.copy(name = name))
        it[distanceM] = length.roundToLong()
        it[minLatE7] = line.minOf { p -> p.lat }.e7()
        it[maxLatE7] = line.maxOf { p -> p.lat }.e7()
        it[minLngE7] = line.minOf { p -> p.lng }.e7()
        it[maxLngE7] = line.maxOf { p -> p.lng }.e7()
        it[dataSource] = record.source.take(MAX_TOKEN_LENGTH)
        it[license] = record.license.take(MAX_TOKEN_LENGTH)
        it[indexedAt] = now
    }
    if (!changed) return
    SegmentEfforts.deleteWhere { segmentUri eq uri.value }
    replaceSegmentCells(uri.value, line)
    jobs.enqueueSegment(uri.value, now)
}

/** A deleted segment: it, its cells and every effort on it go. */
internal fun JdbcTransaction.deleteSegment(uri: AtUri) = deleteSegments(listOf(uri.value))

/** [author]'s segments, for a deleted account. */
internal fun JdbcTransaction.deleteSegmentsBy(author: Did) =
    deleteSegments(Segments.select(Segments.uri).where { Segments.authorDid eq author.value }.map { it[Segments.uri] })

private fun JdbcTransaction.deleteSegments(uris: List<String>) {
    if (uris.isEmpty()) return
    SegmentEfforts.deleteWhere { segmentUri inList uris }
    SegmentCellIndex.deleteWhere { segmentUri inList uris }
    Segments.deleteWhere { Segments.uri inList uris }
}

private fun JdbcTransaction.replaceSegmentCells(uri: String, line: List<GeoPoint>) {
    SegmentCellIndex.deleteWhere { segmentUri eq uri }
    SegmentCellIndex.batchInsert(SegmentCells.cellsOf(line)) {
        this[SegmentCellIndex.cell] = it
        this[SegmentCellIndex.segmentUri] = uri
    }
}

private fun Double.e7(): Int = (this * E7).roundToInt()

private fun graphemes(text: String): Int {
    val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
    var count = 0
    while (breaks.next() != BreakIterator.DONE) count++
    return count
}
