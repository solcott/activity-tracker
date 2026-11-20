package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.crypto.base32
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.PathSection
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.PathCandidate
import io.github.solcott.activitytracker.location.PathOverlap
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.Writer
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The paths file's format; bump it when what a line holds changes. */
internal const val PATHS_VERSION = 2

/** The first line of a paths file. */
@Serializable internal data class PathsHeader(val region: String, val pathsVersion: Int)

/** Each later line: one trail or road a region's segments may lie on (segments/path-import). */
@Serializable
internal data class PathEntry(
    /** The record key it's published under: stable across extracts ([PathIds]). */
    val id: String,
    /** A [PathKind] value. */
    val kind: String,
    val name: String,
    /** Its line in pieces, each encoded as a `feed.segment` line, at most [PathRecord.MAX_PIECES]. */
    val lines: List<String>,
    val lengthMeters: Long,
    val source: String,
    val license: String,
    val attribution: String? = null,
    /** The sport groups it is open to; null for any, as a road. */
    val sports: List<String>? = null,
    /**
     * The sections of its trail family that the extract's kept candidates on it are named after, by name, each with those candidates' ids
     * sorted (segments/path-import); null for none. Files written before sections read as none.
     */
    val sections: List<PathSection>? = null,
)

private val json = Json { explicitNulls = false }

/** The paths file: a header line, then one line per path, in the order given. */
internal fun writePaths(out: Writer, region: String, paths: List<PathEntry>) {
    out.appendLine(json.encodeToString(PathsHeader.serializer(), PathsHeader(region, PATHS_VERSION)))
    for (path in paths) out.appendLine(json.encodeToString(PathEntry.serializer(), path))
}

/** A paths file read back: its region and its paths. */
internal data class PathsFile(val region: String, val paths: List<PathEntry>)

internal fun readPaths(lines: Sequence<String>): PathsFile {
    val iterator = lines.filter { it.isNotBlank() }.iterator()
    require(iterator.hasNext()) { "an empty paths file" }
    val header = json.decodeFromString(PathsHeader.serializer(), iterator.next())
    require(header.pathsVersion == PATHS_VERSION) {
        "a paths file of format version ${header.pathsVersion}; this importer reads version $PATHS_VERSION"
    }
    return PathsFile(header.region, iterator.asSequence().map { json.decodeFromString(PathEntry.serializer(), it) }.toList())
}

/** Stable path ids: from the kind, the name and the source features at the two ends, whichever way the line runs. */
internal object PathIds {
    private const val ID_CHARS = 26

    fun of(kind: String, name: String, endA: String, endB: String): String {
        val (first, last) = listOf(endA, endB).sorted()
        val key = "$kind|$name|$first|$last"
        return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).base32().take(ID_CHARS)
    }
}

/** A path's line as pieces that each fit a record: split into at most [PathRecord.MAX_PIECES] stretches, each simplified. */
internal object PathPieces {
    /** The longest stretch one piece holds before the line is split further, in meters. */
    private const val PIECE_M = 40_000.0

    fun of(points: List<GeoPoint>): List<String> {
        if (points.size < 2) return emptyList()
        val length = SegmentGeometry.lengthMeters(points)
        val count = ceil(length / max(PIECE_M, length / PathRecord.MAX_PIECES)).toInt().coerceIn(1, PathRecord.MAX_PIECES)
        val target = length / count
        val pieces = mutableListOf<List<GeoPoint>>()
        var start = 0
        var along = 0.0
        for (i in 1 until points.size) {
            along += SegmentGeometry.distanceMeters(points[i - 1], points[i])
            if (along >= target && pieces.size < count - 1 && i < points.lastIndex) {
                pieces += points.subList(start, i + 1)
                start = i
                along = 0.0
            }
        }
        pieces += points.subList(start, points.size)
        return pieces.map { SegmentGeometry.encodePolyline(CandidateCutter.simplified(it)) }
    }

    fun lengthOf(lines: List<String>): Long =
        lines.sumOf { SegmentGeometry.lengthMeters(SegmentGeometry.decodePolyline(it).orEmpty()) }.roundToLong()
}

/**
 * A region's trail paths (segments/path-import): one for each trail family, the trails of one name or of one family of sections (a parent
 * trail's family, else the name before " - ") whose ways touch, across every sport group. Each is named after its family, open to its
 * trails' sport groups, and holds each of its ways once, in as few pieces as its trails allow. An agency trail that OSM also maps gives
 * only OSM's path ([folded]).
 */
internal object TrailPaths {
    /** [trails]' paths, each listing the sections of the kept [candidates] on it ([sectionsOf]). */
    fun of(trails: List<Trail>, agencyKeys: Map<Long, String>, candidates: List<Candidate> = emptyList()): List<PathEntry> {
        val sectioned = candidates.filter { it.section != null && it.family != null }.groupBy { it.family to it.source }
        return trails
            .groupBy { trail -> trail.familyKey to trail.rules.source }
            .flatMap { (key, family) ->
                touching(family).mapNotNull { near ->
                    entry(key.first, near, agencyKeys)?.copy(sections = sectionsOf(near, sectioned[key].orEmpty(), agencyKeys))
                }
            }
            .let(::folded)
    }

    /**
     * The sections of [candidates] of one family and source that lie wholly on [trails]' ways, by name, each with its candidates' ids
     * sorted (segments/path-import); null when there are none. Over the record's limits, the first sections and ids by order are kept.
     */
    private fun sectionsOf(trails: List<Trail>, candidates: List<Candidate>, agencyKeys: Map<Long, String>): List<PathSection>? {
        if (candidates.isEmpty()) return null
        val ways = trails.flatMapTo(mutableSetOf()) { it.ways }
        val features = ways.mapNotNullTo(mutableSetOf(), agencyKeys::get)
        fun onPath(candidate: Candidate) =
            if (candidate.osmWays.isNotEmpty()) ways.containsAll(candidate.osmWays)
            else candidate.agencyFeatures.isNotEmpty() && features.containsAll(candidate.agencyFeatures)
        val sections =
            candidates.filter(::onPath).groupBy({ requireNotNull(it.section) }, { it.id }).toSortedMap().map { (name, ids) ->
                PathSection(name, ids.distinct().sorted())
            }
        if (sections.size > PathRecord.MAX_SECTIONS || sections.any { it.segments.size > PathSection.MAX_SEGMENTS }) {
            println("Kept only part of the sections of ${trails.first().familyKey}: more than a path record holds")
        }
        return sections
            .take(PathRecord.MAX_SECTIONS)
            .map { it.copy(segments = it.segments.take(PathSection.MAX_SEGMENTS)) }
            .ifEmpty { null }
    }

    /**
     * [paths] without each agency trail path that mostly follows one OSM trail path, at least [PathOverlap.IMPORTED_SHARE] of its length by
     * the AppView's test among the OSM paths near it; that OSM path keeps its own name, line and id and is opened to the agency path's
     * sport groups too.
     */
    fun folded(paths: List<PathEntry>): List<PathEntry> {
        val osm = paths.filter { it.kind == PathKind.TRAIL && it.source == SegmentSource.OSM }
        val agency = paths.filter { it.kind == PathKind.TRAIL && it.source == SegmentSource.AGENCY }
        if (osm.isEmpty() || agency.isEmpty()) return paths
        val lines = (osm + agency).associate { path -> path.id to path.lines.map { SegmentGeometry.decodePolyline(it).orEmpty() } }
        val boxes = lines.mapValues { (_, pieces) -> Box.of(pieces.flatten()) }
        // Each agency path that one OSM path covers, by that OSM path's id.
        val into = agency.mapNotNull { path -> coveringOsm(path, osm, lines, boxes)?.let { path to it } }
        val dropped = into.mapTo(mutableSetOf()) { it.first.id }
        val opened = into.groupBy({ it.second }, { it.first.sports.orEmpty() }).mapValues { (_, sports) -> sports.flatten() }
        val order = SegmentSportGroup.all
        return paths
            .filter { it.id !in dropped }
            .map { path ->
                val more = opened[path.id] ?: return@map path
                val sports = (path.sports.orEmpty() + more).distinct()
                path.copy(sports = sports.sortedBy { order.indexOf(it).takeIf { i -> i >= 0 } ?: order.size })
            }
    }

    /** The id of the one of [osm]'s paths that at least [PathOverlap.IMPORTED_SHARE] of [path] follows, if any. */
    private fun coveringOsm(
        path: PathEntry,
        osm: List<PathEntry>,
        lines: Map<String, List<List<GeoPoint>>>,
        boxes: Map<String, Box>,
    ): String? {
        val pieces = lines.getValue(path.id).filter { it.size >= 2 }
        val length = pieces.sumOf(SegmentGeometry::lengthMeters)
        val box = boxes.getValue(path.id)
        val candidates =
            osm.filter { boxes.getValue(it.id).near(box, SegmentGeometry.CORRIDOR_M) }.map { PathCandidate(it.id, lines.getValue(it.id)) }
        if (length <= 0.0 || candidates.isEmpty()) return null
        val followed = mutableMapOf<String, Double>()
        for (piece in pieces) PathOverlap.overlaps(piece, candidates).forEach { (id, m) -> followed.merge(id, m, Double::plus) }
        return followed.maxByOrNull { it.value }?.takeIf { it.value >= length * PathOverlap.IMPORTED_SHARE }?.key
    }

    /** How near two trails of one family may come and still be one trail: pieces split by short unnamed connectors. */
    private const val JOIN_M = 250.0

    /** [trails] split into groups that touch or come within [JOIN_M] of each other, linked through any member. */
    private fun touching(trails: List<Trail>): List<List<Trail>> {
        val parent = IntArray(trails.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        fun join(a: Int, b: Int) {
            parent[root(a)] = root(b)
        }
        val byNode = mutableMapOf<Long, Int>()
        trails.forEachIndexed { i, trail -> for (node in trail.nodes) byNode.putIfAbsent(node, i)?.let { join(it, i) } }
        val boxes = trails.map { Box.of(it.points) }
        for (i in trails.indices) {
            for (j in i + 1 until trails.size) {
                if (root(i) == root(j) || !boxes[i].near(boxes[j], JOIN_M)) continue
                if (closest(trails[i].points, trails[j].points) <= JOIN_M) join(i, j)
            }
        }
        return trails.indices.groupBy(::root).values.map { group -> group.map { trails[it] } }
    }

    /** How close [a] and [b] come, measured from points every [SegmentGeometry.REFERENCE_SPACING_M] along [a] to [b]'s line. */
    private fun closest(a: List<GeoPoint>, b: List<GeoPoint>): Double =
        SegmentGeometry.resample(a).minOf { SegmentGeometry.distanceToLineMeters(it, b) }

    /** A line's bounding box, for skipping pairs of trails that can't be near. */
    private data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
        fun near(other: Box, meters: Double): Boolean {
            val lat = meters / METERS_PER_DEGREE
            val lng = lat / max(cos(Math.toRadians(south)), MIN_COS)
            return other.south <= north + lat && other.north >= south - lat && other.west <= east + lng && other.east >= west - lng
        }

        companion object {
            fun of(points: List<GeoPoint>) =
                Box(points.minOf { it.lat }, points.minOf { it.lng }, points.maxOf { it.lat }, points.maxOf { it.lng })
        }
    }

    private const val METERS_PER_DEGREE = 111_320.0
    private const val MIN_COS = 0.01

    private fun entry(name: String, trails: List<Trail>, agencyKeys: Map<Long, String>): PathEntry? {
        val lines = piecesOf(trails).take(PathRecord.MAX_PIECES)
        if (lines.isEmpty()) return null
        val ways = trails.flatMap { it.edgeWays.toList() }
        fun end(way: Long) = agencyKeys[way] ?: "w$way"
        val order = SegmentSportGroup.all
        val first = trails.first()
        return PathEntry(
            id = PathIds.of(PathKind.TRAIL, name, end(ways.min()), end(ways.max())),
            kind = PathKind.TRAIL,
            name = CandidateCutter.truncate(name),
            lines = lines,
            lengthMeters = PathPieces.lengthOf(lines),
            source = first.rules.source,
            license = first.rules.license,
            attribution = first.rules.attribution,
            sports = trails.map { it.group }.distinct().sortedBy { order.indexOf(it).takeIf { i -> i >= 0 } ?: order.size },
        )
    }

    /**
     * The lines of [trails], each way once: the longest trail whole, then each other's stretches of ways not drawn yet, longest pieces
     * first.
     */
    private fun piecesOf(trails: List<Trail>): List<String> {
        val drawn = mutableSetOf<Long>()
        val runs = mutableListOf<List<GeoPoint>>()
        for (trail in trails.sortedByDescending { SegmentGeometry.lengthMeters(it.points) }) {
            var start = -1
            for (edge in 0 until trail.nodes.lastIndex) {
                val fresh = trail.edgeWays[edge] !in drawn
                if (fresh && start < 0) start = edge
                if (!fresh && start >= 0) {
                    runs += trail.points.subList(start, edge + 1)
                    start = -1
                }
            }
            if (start >= 0) runs += trail.points.subList(start, trail.points.size)
            drawn += trail.ways
        }
        return runs.filter { it.size >= 2 }.sortedByDescending { SegmentGeometry.lengthMeters(it) }.flatMap(PathPieces::of)
    }
}

/**
 * A region's road paths (segments/path-import): runs of same-named OSM road ways joined end to end, for roads open to bikes or to people on
 * foot. No candidate is ever cut from them; they only group athletes' segments.
 */
internal object RoadPaths {
    /** The `highway` values that are roads. */
    private val ROADS =
        setOf(
            "trunk",
            "trunk_link",
            "primary",
            "primary_link",
            "secondary",
            "secondary_link",
            "tertiary",
            "tertiary_link",
            "unclassified",
            "residential",
            "service",
            "living_street",
            "road",
        )
    private val CLOSED_ACCESS = setOf("private", "no")

    /** Whether a way is a named road a path may follow: not a motorway, not closed, and open to bikes or to people on foot. */
    fun isRoad(tags: Map<String, String>): Boolean =
        tags["highway"] in ROADS &&
            !tags["name"].isNullOrBlank() &&
            tags["access"] !in CLOSED_ACCESS &&
            !(tags["bicycle"] == "no" && tags["foot"] == "no")

    fun of(data: OsmData): List<PathEntry> {
        val roads = data.ways.filter { way -> isRoad(way.tags) && way.nodes.size >= 2 && way.nodes.all { data.coords[it] != null } }
        return roads
            .groupBy { requireNotNull(it.name) }
            .flatMap { (name, named) -> chains(named).map { chain -> entry(name, chain, data) } }
            .filterNotNull()
    }

    /** A road way placed in a chain: [reversed] when the chain runs against its node order. */
    private data class Placed(val way: OsmWay, val reversed: Boolean) {
        val first: Long
            get() = if (reversed) way.nodes.last() else way.nodes.first()

        val last: Long
            get() = if (reversed) way.nodes.first() else way.nodes.last()

        val nodes: List<Long>
            get() = if (reversed) way.nodes.reversed() else way.nodes.toList()
    }

    /** [ways] of one name joined where exactly two meet at an end node, so a junction of three never picks a branch. */
    private fun chains(ways: List<OsmWay>): List<List<Placed>> {
        val atEnd = ways.flatMap { w -> listOf(w.nodes.first() to w, w.nodes.last() to w) }.groupBy({ it.first }, { it.second })
        val used = mutableSetOf<Long>()
        fun next(node: Long, current: OsmWay): OsmWay? =
            atEnd[node].orEmpty().distinct().takeIf { it.size == 2 }?.firstOrNull { it != current && it.id !in used }
        return ways
            .sortedBy { it.id }
            .mapNotNull { start ->
                if (!used.add(start.id)) return@mapNotNull null
                val chain = ArrayDeque(listOf(Placed(start, reversed = false)))
                while (true) {
                    val end = chain.last()
                    val way = next(end.last, end.way) ?: break
                    used += way.id
                    chain.addLast(Placed(way, reversed = way.nodes.last() == end.last))
                }
                while (true) {
                    val first = chain.first()
                    val way = next(first.first, first.way) ?: break
                    used += way.id
                    chain.addFirst(Placed(way, reversed = way.nodes.first() == first.first))
                }
                chain.toList()
            }
    }

    private fun entry(name: String, chain: List<Placed>, data: OsmData): PathEntry? {
        val nodes = chain.flatMapIndexed { i, placed -> if (i == 0) placed.nodes else placed.nodes.drop(1) }
        val points = nodes.mapNotNull { data.coords[it] }
        val lines = PathPieces.of(points)
        if (lines.isEmpty()) return null
        return PathEntry(
            id = PathIds.of(PathKind.ROAD, name, "w${chain.first().way.id}", "w${chain.last().way.id}"),
            kind = PathKind.ROAD,
            name = CandidateCutter.truncate(name),
            lines = lines,
            lengthMeters = PathPieces.lengthOf(lines),
            source = TrailTags.source,
            license = TrailTags.license,
        )
    }
}
