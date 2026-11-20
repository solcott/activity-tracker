package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.api.TrailAttributesSanitizer
import io.github.solcott.activitytracker.appview.db.PathCellIndex
import io.github.solcott.activitytracker.appview.db.PathSectionSegments
import io.github.solcott.activitytracker.appview.db.PathSegments
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PathCandidate
import io.github.solcott.activitytracker.location.PathOverlap
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.path` records (segments/segment-paths), from the segment importer's repo only, and which segments lie on each.

private const val MAX_LAT = 90.0
private const val MAX_LNG = 180.0

/** How many cells one `IN` list carries, so a long path's thousands of cells don't make one huge query. */
private const val CELL_CHUNK = 1_000

/**
 * A path from [author]'s repo, indexed only when [author] is the segment [importer], with a name, a known kind and 1 to
 * [PathRecord.MAX_PIECES] valid line pieces; anything else is dropped like an undecodable record. Indexing it stores its cells and works
 * out again which run segments lie on it.
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertPath(json: JsonObject, author: Did, uri: AtUri, cid: String, now: Long, importer: Did?) {
    if (importer == null || author != importer) return
    val record = AtJson.decodeFromJsonElement(PathRecord.serializer(), json)
    val name = validName(record.name)
    require(record.kind in PathKind.all) { "unknown kind ${record.kind}" }
    val pieces = validPieces(record.lines)
    val attribution = runCatching { validAttribution(record.attribution) }.getOrNull()
    val all = pieces.flatten()
    val sports = record.sports?.filter { sport -> sport in SegmentSportGroup.all }?.distinct()?.joinToString(",")?.ifEmpty { null }
    val old = Paths.select(Paths.lines, Paths.sports).where { Paths.uri eq uri.value }.firstOrNull()
    // Membership depends only on the line and the sports: a rename or a rewrite of the same line leaves it as it is.
    val moved = old == null || old[Paths.lines] != record.lines.joinToString("\n") || old[Paths.sports] != sports
    Paths.upsert {
        it[Paths.uri] = uri.value
        it[Paths.cid] = cid
        it[authorDid] = author.value
        it[kind] = record.kind
        it[Paths.name] = name.take(MAX_NAME_LENGTH)
        it[lines] = record.lines.joinToString("\n")
        it[lengthM] = pieces.sumOf { piece -> SegmentGeometry.lengthMeters(piece) }.roundToLong()
        it.setBox(all, Paths)
        it[dataSource] = record.source.take(MAX_TOKEN_LENGTH)
        it[license] = record.license.take(MAX_TOKEN_LENGTH)
        it[Paths.attribution] = attribution
        it[Paths.sports] = sports
        it[Paths.trail] = TrailAttributesSanitizer.encoded(record.trail)
        it[indexedAt] = now
    }
    replaceSections(uri, author, record.sections.orEmpty())
    if (!moved) return
    val cells = pieces.flatMapTo(mutableSetOf()) { SegmentCells.cellsOf(it) }
    // The segments it held too, since a path that moved may no longer be nearest to them.
    val before =
        PathSegments.select(PathSegments.segmentUri)
            .where { PathSegments.pathUri eq uri.value }
            .mapTo(mutableSetOf()) { it[PathSegments.segmentUri] }
    PathCellIndex.deleteWhere { pathUri eq uri.value }
    PathCellIndex.batchInsert(cells) {
        this[PathCellIndex.cell] = it
        this[PathCellIndex.pathUri] = uri.value
    }
    PathSegments.deleteWhere { pathUri eq uri.value }
    // A new or moved path can take segments from a neighbour, or give them up, so each segment near it is worked out again.
    repathSegments(before + segmentsIn(cells))
}

/** [lines] decoded, if they're a valid path's pieces: 1 to [PathRecord.MAX_PIECES], each bounded, of two or more positions in range. */
internal fun validPieces(lines: List<String>): List<List<GeoPoint>> {
    require(lines.size in 1..PathRecord.MAX_PIECES) { "${lines.size} line pieces" }
    return lines.map { encoded ->
        require(encoded.length <= PathRecord.MAX_PIECE_CHARS) { "a line piece over ${PathRecord.MAX_PIECE_CHARS} characters" }
        val piece = requireNotNull(SegmentGeometry.decodePolyline(encoded)) { "a malformed line piece" }
        require(piece.size >= 2) { "a line piece of ${piece.size} positions" }
        require(piece.all { it.lat in -MAX_LAT..MAX_LAT && it.lng in -MAX_LNG..MAX_LNG }) { "a position out of range" }
        piece
    }
}

/** A deleted path: it, its cells and which segments lay on it go. */
internal fun JdbcTransaction.deletePath(uri: AtUri) = deletePaths(listOf(uri.value))

/** [author]'s paths, for a deleted account. */
internal fun JdbcTransaction.deletePathsBy(author: Did) =
    deletePaths(Paths.select(Paths.uri).where { Paths.authorDid eq author.value }.map { it[Paths.uri] })

private fun JdbcTransaction.deletePaths(uris: List<String>) {
    if (uris.isEmpty()) return
    // The segments on the deleted paths may now lie on a neighbour; others weren't won by them, so their paths stand.
    val held =
        PathSegments.select(PathSegments.segmentUri)
            .where { PathSegments.pathUri inList uris }
            .mapTo(mutableSetOf()) { it[PathSegments.segmentUri] }
    PathSegments.deleteWhere { pathUri inList uris }
    PathSectionSegments.deleteWhere { pathUri inList uris }
    PathCellIndex.deleteWhere { pathUri inList uris }
    Paths.deleteWhere { Paths.uri inList uris }
    repathSegments(held)
}

/** The segments with a cell among [cells]. */
private fun JdbcTransaction.segmentsIn(cells: Collection<Long>): Set<String> =
    cells.toSet().chunked(CELL_CHUNK).flatMapTo(mutableSetOf()) { chunk ->
        SegmentCellIndex.select(SegmentCellIndex.segmentUri)
            .where { SegmentCellIndex.cell inList chunk }
            .map { it[SegmentCellIndex.segmentUri] }
    }

/**
 * Works out again which paths each of [uris] lies on, a batch at a time: the paths near a batch are read and decoded once, then each run
 * segment in it lies on those [PathOverlap] picks among them (a lift on none).
 */
private fun JdbcTransaction.repathSegments(uris: Set<String>) {
    for (chunk in uris.chunked(CELL_CHUNK)) {
        PathSegments.deleteWhere { segmentUri inList chunk }
        val segments =
            Segments.select(Segments.uri, Segments.authorDid, Segments.kind, Segments.sport, Segments.line)
                .where { (Segments.uri inList chunk) and (Segments.kind eq SegmentKind.RUN) }
                .toList()
        val near =
            if (segments.isEmpty()) PathsNear(emptyList(), emptySet())
            else
                candidatesIn(
                    SegmentCellIndex.select(SegmentCellIndex.cell)
                        .where { SegmentCellIndex.segmentUri inList segments.map { it[Segments.uri] } }
                        .mapTo(mutableSetOf()) { it[SegmentCellIndex.cell] },
                )
        val on = segments.flatMap { row ->
            val line = SegmentGeometry.decodePolyline(row[Segments.line]) ?: return@flatMap emptyList()
            val imported = row[Segments.authorDid] in near.authors
            PathOverlap.pathsUnder(line, row[Segments.sport], near.candidates, imported).map { it to row[Segments.uri] }
        }
        PathSegments.batchInsert(on) { (path, segment) ->
            this[PathSegments.pathUri] = path
            this[PathSegments.segmentUri] = segment
        }
    }
}

/**
 * Paths near some segments, decoded for [PathOverlap], and who published them: only the segment importer's paths are indexed, so a segment
 * by one of [authors] is the importer's own, held to its stricter test (segments/segment-paths).
 */
private class PathsNear(val candidates: List<PathCandidate<String>>, val authors: Set<String>)

/** The paths with a cell among [cells]. */
private fun JdbcTransaction.candidatesIn(cells: Set<Long>): PathsNear {
    val near =
        cells.chunked(CELL_CHUNK).flatMapTo(mutableSetOf()) { chunk ->
            PathCellIndex.select(PathCellIndex.pathUri).where { PathCellIndex.cell inList chunk }.map { it[PathCellIndex.pathUri] }
        }
    val rows =
        near.chunked(CELL_CHUNK).flatMap { chunk ->
            Paths.select(Paths.uri, Paths.authorDid, Paths.lines, Paths.sports).where { Paths.uri inList chunk }.toList()
        }
    return PathsNear(
        rows.map { row -> PathCandidate(row[Paths.uri], piecesOf(row[Paths.lines]), row[Paths.sports]?.split(',')?.toSet().orEmpty()) },
        rows.mapTo(mutableSetOf()) { it[Paths.authorDid] },
    )
}

/**
 * Works out again which paths the segment at [uri], by [author] and of [sport], lies on, after its line, kind or sport changed: a run on
 * the paths sharing a cell with [line] that [PathOverlap] picks (nearest wins each stretch, only paths open to [sport], the importer's own
 * segments held to its stricter test); a lift on none.
 */
@Suppress("LongParameterList") // The segment as indexed.
internal fun JdbcTransaction.repathSegment(uri: String, author: String, kind: String, sport: String, line: List<GeoPoint>) {
    PathSegments.deleteWhere { segmentUri eq uri }
    if (kind != SegmentKind.RUN) return
    val near = candidatesIn(SegmentCells.cellsOf(line))
    if (near.candidates.isEmpty()) return
    PathSegments.batchInsert(PathOverlap.pathsUnder(line, sport, near.candidates, imported = author in near.authors)) {
        this[PathSegments.pathUri] = it
        this[PathSegments.segmentUri] = uri
    }
}

/** A deleted segment no longer lies on any path. */
internal fun JdbcTransaction.unpathSegments(uris: List<String>) {
    if (uris.isNotEmpty()) PathSegments.deleteWhere { segmentUri inList uris }
}

/** A [Paths.lines] value as its pieces; a piece that no longer decodes is left out. */
internal fun piecesOf(lines: String): List<List<GeoPoint>> =
    lines.split('\n').mapNotNull { SegmentGeometry.decodePolyline(it)?.takeIf { p -> p.size >= 2 } }
