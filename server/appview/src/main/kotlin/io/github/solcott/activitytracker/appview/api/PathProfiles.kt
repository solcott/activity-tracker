package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.PathTerrain
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.roundToLong
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A returned segment's [uri] and its stored [line], whose stretch along a path [pathProfile] gives. */
internal data class ListedLine(val uri: AtUri, val line: String)

/** A covered piece of a path: its [index] in the path's lines and its decoded [line]. */
internal data class CoveredPiece(val index: Int, val line: List<GeoPoint>)

/**
 * The terrain profile `feed.getPath` serves (segments/path-profile): the path at [pathUri]'s covered pieces of [lines] (its stored lines),
 * their climb and descent, and where each of [segments] (the returned segments) lies along one. Null when no piece is covered.
 */
internal fun JdbcTransaction.pathProfile(pathUri: String, lines: String, segments: List<ListedLine>): GetPath.PathProfile? {
    val rows = PathTerrain.selectAll().where { PathTerrain.pathUri eq pathUri }.orderBy(PathTerrain.piece to SortOrder.ASC).toList()
    if (rows.isEmpty()) return null
    val pieces = rows.map {
        GetPath.PieceProfile(
            piece = it[PathTerrain.piece],
            heights = it[PathTerrain.profileDm].split(',').map(String::toInt),
            lengthMeters = it[PathTerrain.lengthM],
            maxGrade = it[PathTerrain.maxGrade],
        )
    }
    val encoded = lines.split('\n')
    val covered = pieces.mapNotNull { piece ->
        encoded.getOrNull(piece.piece)?.let(SegmentGeometry::decodePolyline)?.let { CoveredPiece(piece.piece, it) }
    }
    return GetPath.PathProfile(
        pieces = pieces,
        gain = rows.sumOf { it[PathTerrain.gainM] }.toInt(),
        loss = rows.sumOf { it[PathTerrain.lossM] }.toInt(),
        spans = segments.mapNotNull { listed -> SegmentGeometry.decodePolyline(listed.line)?.let { spanOf(listed.uri, it, covered) } },
    )
}

/**
 * Where [line] lies along the first of [pieces] (in the lines' order) that both its ends are within [SPAN_REACH_M] of, nearer end first;
 * null when it lies along none.
 */
internal fun spanOf(uri: AtUri, line: List<GeoPoint>, pieces: List<CoveredPiece>): GetPath.SegmentSpan? {
    val ends = listOfNotNull(line.firstOrNull(), line.lastOrNull()).takeIf { line.size >= 2 }.orEmpty()
    return pieces.firstNotNullOfOrNull { piece ->
        val along = ends.mapNotNull { SegmentGeometry.nearestAlong(it, piece.line) }
        along
            .takeIf { it.size == 2 && it.all { end -> end.offMeters <= SPAN_REACH_M } }
            ?.map { it.alongMeters }
            ?.sorted()
            ?.let { (near, far) -> GetPath.SegmentSpan(uri, piece.index, near.roundToLong(), far.roundToLong()) }
    }
}

/** How near a segment's ends must each be to a piece for the segment to lie along it. */
internal const val SPAN_REACH_M = 30.0
