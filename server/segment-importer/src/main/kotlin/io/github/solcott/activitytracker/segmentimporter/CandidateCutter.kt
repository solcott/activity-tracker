package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import java.security.MessageDigest
import java.text.BreakIterator
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.time.Instant

/** A candidate segment before it's written (segments/trail-candidates). */
@Suppress("LongParameterList") // A plain holder of what the writer and dedup read.
internal class Candidate(
    val id: String,
    val group: String,
    val name: String,
    /** The simplified line, encoded as a `feed.segment` line is. */
    val line: String,
    val points: List<GeoPoint>,
    val lengthMeters: Long,
    /** The OSM ways it follows, in its direction of travel; empty for an agency's. */
    val osmWays: List<Long>,
    val fromRelation: Boolean,
    val whole: Boolean,
    val source: String = SegmentSource.OSM,
    val license: String = LICENSE_OSM,
    val attribution: String? = null,
    /** The agency features it follows, as `<kind>:<feature key>`, in its direction of travel; empty for an OSM one. */
    val agencyFeatures: List<String> = emptyList(),
)

/**
 * Cuts [Trail]s into candidates: the whole trail, and its stretches between junctions with named ways that aren't its own, joined until
 * each reaches the group's minimum; each in every direction all its edges allow. Lines are simplified and kept only within the group's
 * length bounds, measured on the simplified line as the AppView measures it.
 */
internal class CandidateCutter(data: OsmData) {
    /** The named ways through each node, for junctions and their names. */
    private val namedAt: Map<Long, List<OsmWay>> =
        buildMap<Long, MutableList<OsmWay>> {
                for (way in data.ways) {
                    if (way.name == null || !way.rules.marksJunctions(way.tags)) continue
                    for (node in way.nodes.distinct()) getOrPut(node) { mutableListOf() } += way
                }
            }
            .mapValues { (_, ways) -> ways.sortedBy { it.id } }

    /** Each agency way's feature key, for an agency candidate's id and features. */
    private val agencyKeys: Map<Long, String> = data.ways.mapNotNull { way -> way.tags[AgencyRules.KEY]?.let { way.id to it } }.toMap()

    fun cut(trail: Trail): List<Candidate> {
        val min = SegmentSportGroup.minMeters(trail.group)
        val last = trail.nodes.lastIndex
        val distance = DoubleArray(trail.nodes.size)
        for (i in 1..last) distance[i] = distance[i - 1] + SegmentGeometry.distanceMeters(trail.points[i - 1], trail.points[i])
        val junctions = (1 until last).filter { i -> others(trail, trail.nodes[i]).isNotEmpty() }

        val stretches = mutableListOf<IntRange>()
        var start = 0
        for (cut in junctions + last) {
            if (distance[cut] - distance[start] >= min) {
                stretches += start..cut
                start = cut
            }
        }
        if (start != last && stretches.isNotEmpty()) stretches[stretches.lastIndex] = stretches.last().first..last

        val pieces = buildList {
            add(Piece(0..last, part = null))
            if (stretches.size > 1) stretches.forEachIndexed { k, range -> add(Piece(range, part = k + 1)) }
        }
        return pieces.flatMap { piece -> directions(trail, piece) }
    }

    private data class Piece(val range: IntRange, val part: Int?)

    private fun directions(trail: Trail, piece: Piece): List<Candidate> {
        val edges = piece.range.first until piece.range.last
        val forward = edges.all { trail.forward[it] }
        val backward = edges.all { trail.backward[it] }
        return listOfNotNull(
            if (forward) candidate(trail, piece, reversed = false, bothWays = backward) else null,
            if (backward) candidate(trail, piece, reversed = true, bothWays = forward) else null,
        )
    }

    private fun candidate(trail: Trail, piece: Piece, reversed: Boolean, bothWays: Boolean): Candidate? {
        val (a, b) = piece.range.first to piece.range.last
        val nodes = trail.nodes.slice(a..b).let { if (reversed) it.reversed() else it }
        val points = trail.points.subList(a, b + 1).let { if (reversed) it.reversed() else it }
        val ways = trail.edgeWays.slice(a until b).let { if (reversed) it.reversed() else it }.dedupeRuns()
        // Whether travel follows the first way's own node order: the two directions of a loop share their ways and end nodes.
        val alongFirstWay = if (reversed) trail.edgeReversed[b - 1] else !trail.edgeReversed[a]
        val name = nameOf(trail, piece, nodes, points, bothWays)
        val line = simplified(points)
        val length = SegmentGeometry.lengthMeters(line)
        if (length < SegmentSportGroup.minMeters(trail.group) || length > SegmentSportGroup.MAX_METERS) return null
        val endVisit = nodes.subList(1, nodes.lastIndex).count { it == nodes.last() }
        val agency = trail.rules is AgencyRules
        val features = if (agency) ways.map(agencyKeys::getValue) else emptyList()
        return Candidate(
            id =
                if (agency) {
                    agencyIdOf(trail.group, features, alongFirstWay, points.first(), points.last(), endVisit)
                } else {
                    idOf(trail.group, ways, alongFirstWay, nodes.first(), nodes.last(), endVisit)
                },
            group = trail.group,
            name = truncate(name),
            line = SegmentGeometry.encodePolyline(line),
            points = line,
            lengthMeters = length.roundToLong(),
            osmWays = if (agency) emptyList() else ways,
            fromRelation = trail.fromRelation,
            whole = piece.part == null,
            source = trail.rules.source,
            license = trail.rules.license,
            attribution = trail.rules.attribution,
            agencyFeatures = features,
        )
    }

    /**
     * A candidate's name: a whole trail's, with its direction when it goes both ways, or a stretch's, named after the places at its ends or
     * else numbered.
     */
    private fun nameOf(trail: Trail, piece: Piece, nodes: List<Long>, points: List<GeoPoint>, bothWays: Boolean): String {
        if (piece.part == null) return if (bothWays) "${trail.name} (${heading(points)})" else trail.name
        val from = nameAt(trail, nodes.first())
        val to = nameAt(trail, nodes.last())
        return if (from != null && to != null) "${trail.name}: $from to $to" else "${trail.name} (part ${piece.part})"
    }

    /**
     * The named ways through [node] that aren't [trail]'s own. For an agency trail, the agency's other lines of the same name don't count
     * either: the Forest Service publishes one trail as several features, a summer and a winter one split at different mileposts.
     */
    private fun others(trail: Trail, node: Long): List<OsmWay> =
        namedAt[node].orEmpty().filter { way ->
            way.id !in trail.ways && !(trail.rules is AgencyRules && way.rules is AgencyRules && way.name == trail.name)
        }

    /** What to call the place [node] is on [trail]: the name of another way there, preferring one that isn't the trail's own name. */
    private fun nameAt(trail: Trail, node: Long): String? =
        others(trail, node).mapNotNull { it.name }.distinct().sortedWith(compareBy({ it == trail.name }, { it })).firstOrNull()

    companion object {
        /** How far a simplified line may stray from the trail, in meters: well inside the matcher's 30 m corridor. */
        const val TOLERANCE_M = 5.0

        /** The longest a `feed.segment` line may be, in characters. */
        const val MAX_LINE_CHARS = 100_000

        const val MAX_NAME_GRAPHEMES = 100

        private const val ID_CHARS = 26
        private const val E5 = 1e5
        private const val BASE32 = "abcdefghijklmnopqrstuvwxyz234567"
        private const val BITS_PER_CHAR = 5
        private const val BYTE_MASK = 0xff
        private const val CHAR_MASK = 0x1f
        private const val QUARTER_TURN = 90.0
        private const val HALF_TURN = 180.0
        private const val FULL_TURN = 360.0
        private const val EIGHTH_TURN = 45.0
        private val COMPASS = listOf("northbound", "eastbound", "southbound", "westbound")

        /** [points] simplified at [TOLERANCE_M], doubling the tolerance until its encoding fits a record. */
        fun simplified(points: List<GeoPoint>): List<GeoPoint> {
            val samples = points.map { LocationSample(it.lat, it.lng, null, null, null, Instant.DISTANT_PAST) }
            var tolerance = TOLERANCE_M
            while (true) {
                val line = TrackMath.simplify(samples, tolerance).map { GeoPoint(it.latitude, it.longitude) }
                if (SegmentGeometry.encodePolyline(line).length <= MAX_LINE_CHARS) return line
                tolerance *= 2
            }
        }

        /**
         * A stable id from the group, the direction of travel ([alongFirstWay]: whether it follows its first way's node order), the ways
         * followed in order and the end nodes: the same OSM data always gives the same id, and moving an interior node (a geometry fix)
         * keeps it. [endVisit] counts the times the line passed its last node before ending there, for a way that crosses itself; it's left
         * out when zero, as it nearly always is. 26 base32 characters of a SHA-256.
         */
        @Suppress("LongParameterList") // Each input the spec names, kept apart so none can be left out.
        fun idOf(group: String, ways: List<Long>, alongFirstWay: Boolean, first: Long, last: Long, endVisit: Int = 0): String {
            val sense = if (alongFirstWay) "+" else "-"
            val visit = if (endVisit > 0) "#$endVisit" else ""
            return hashed("$group|$sense|${ways.joinToString(",")}|$first|$last$visit")
        }

        /**
         * An agency candidate's id, as [idOf] but from the agency [features] it follows (`<kind>:<feature key>`) and its end positions to
         * 1e-5 degrees (about a metre), since agency lines have no node ids: a re-published dataset that only moves interior vertices keeps
         * every id.
         */
        @Suppress("LongParameterList") // As idOf.
        fun agencyIdOf(
            group: String,
            features: List<String>,
            alongFirstWay: Boolean,
            first: GeoPoint,
            last: GeoPoint,
            endVisit: Int = 0,
        ): String {
            val sense = if (alongFirstWay) "+" else "-"
            val visit = if (endVisit > 0) "#$endVisit" else ""
            return hashed("$group|$sense|${features.joinToString(",")}|${e5(first)}|${e5(last)}$visit")
        }

        private fun e5(point: GeoPoint): String = "${(point.lat * E5).roundToLong()},${(point.lng * E5).roundToLong()}"

        /** 26 base32 characters of [key]'s SHA-256. */
        private fun hashed(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            val out = StringBuilder()
            var buffer = 0
            var bits = 0
            for (byte in digest) {
                buffer = (buffer shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK)
                bits += Byte.SIZE_BITS
                while (bits >= BITS_PER_CHAR && out.length < ID_CHARS) {
                    out.append(BASE32[(buffer shr (bits - BITS_PER_CHAR)) and CHAR_MASK])
                    bits -= BITS_PER_CHAR
                }
            }
            return out.toString()
        }

        /** How far apart a whole trail's ends may be for it to be a loop, in meters: the AppView's duplicate distance. */
        private val LOOP_M = SegmentGeometry.DUPLICATE_DISTANCE_M

        /**
         * Which way [points] go, for a two-way whole trail's name: clockwise or counterclockwise for a loop (its ends within [LOOP_M]), by
         * the sign of its area, otherwise the cardinal direction from start to end.
         */
        fun heading(points: List<GeoPoint>): String {
            val (first, last) = points.first() to points.last()
            if (SegmentGeometry.distanceMeters(first, last) > LOOP_M) return compass(first, last)
            val scale = cos(first.lat * PI / HALF_TURN)
            // The shoelace sum, with x east and y north: positive for a counterclockwise ring.
            val area = (points + first).zipWithNext().sumOf { (p, q) -> (p.lng * scale) * q.lat - (q.lng * scale) * p.lat }
            return if (area > 0) "counterclockwise" else "clockwise"
        }

        /** The cardinal direction of travel from [from] to [to], as a word for a name. */
        fun compass(from: GeoPoint, to: GeoPoint): String {
            val dx = (to.lng - from.lng) * cos(from.lat * PI / HALF_TURN)
            val dy = to.lat - from.lat
            val bearing = (atan2(dx, dy) * HALF_TURN / PI + FULL_TURN) % FULL_TURN
            return COMPASS[(((bearing + EIGHTH_TURN) % FULL_TURN) / QUARTER_TURN).toInt()]
        }

        /** [name] cut to [MAX_NAME_GRAPHEMES] graphemes, at a word boundary when there is one. */
        fun truncate(name: String): String {
            val breaks = BreakIterator.getCharacterInstance().apply { setText(name) }
            var end = 0
            var graphemes = 0
            while (graphemes <= MAX_NAME_GRAPHEMES) {
                val next = breaks.next()
                if (next == BreakIterator.DONE) break
                if (graphemes < MAX_NAME_GRAPHEMES) end = next
                graphemes++
            }
            if (graphemes <= MAX_NAME_GRAPHEMES) return name
            val cut = name.substring(0, end)
            return cut.substringBeforeLast(' ', cut).trimEnd().ifEmpty { cut }
        }

        private fun List<Long>.dedupeRuns(): List<Long> = filterIndexed { i, id -> i == 0 || id != this[i - 1] }
    }
}
