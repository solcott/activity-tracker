package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.crypto.base32
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentClimb
import io.github.solcott.activitytracker.location.SegmentDirection
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentHeading
import io.github.solcott.activitytracker.location.TrackMath
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.IdentityHashMap
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
    val kind: TrailKind,
    val whole: Boolean,
    val source: String = SegmentSource.OSM,
    val license: String = LICENSE_OSM,
    val attribution: String? = null,
    /** The agency features it follows, as `<kind>:<feature key>`, in its direction of travel; empty for an OSM one. */
    val agencyFeatures: List<String> = emptyList(),
    /** A [SegmentKind] value: a trail's candidates are runs, a ski lift's a [SegmentKind.LIFT]. */
    val segmentKind: String = SegmentKind.RUN,
    /** Given in this direction only, by a one-way tag or because it is alpine (segments/segment-direction). */
    val oneWay: Boolean = false,
    /**
     * Whether it climbs or descends in its direction of travel (segments/trail-candidates): its own line's clear climb, else its trail's;
     * null without terrain, when neither clearly climbs, and for alpine.
     */
    val climb: SegmentClimb? = null,
)

/**
 * Cuts [Trail]s into candidates: the whole trail, and, unless it's a parent trail, its stretches between junctions with named ways that
 * aren't its own, joined until each reaches the largest minimum of the groups its ways are open to; each in every direction all its edges
 * allow. A trail whose permitted directions change is cut there first, each run giving its own candidate and stretches, and gives no
 * whole-trail candidate. Lines are simplified and kept only within the group's length bounds, measured on the simplified line as the
 * AppView measures it.
 */
@Suppress("TooManyFunctions") // Cutting by direction and junction, then naming and identifying each piece.
internal class CandidateCutter(
    data: OsmData,
    private val words: GenericWords = GenericWords.ENGLISH,
    /**
     * Whether a line, in its points' order, clearly climbs or descends by terrain (segments/segment-direction); null when it doesn't or
     * terrain doesn't cover it. Without it, two-way whole trails are named by compass.
     */
    private val climbOf: ((List<GeoPoint>) -> SegmentClimb?)? = null,
) {
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

    /** Each trail's whole line's climb in its own order, judged once per trail (segments/trail-candidates). */
    private val trailClimbs = IdentityHashMap<Trail, SegmentClimb?>()

    /** Each way by id, for the sport groups a trail's own ways are open to. */
    private val waysById: Map<Long, OsmWay> = data.ways.associateBy { it.id }

    fun cut(trail: Trail): List<Candidate> {
        val pieces = buildList {
            if (trail.kind == TrailKind.PARENT) {
                // A parent trail's stretches are its sections' own candidates.
                add(Piece(0..trail.nodes.lastIndex, part = null))
                addAll(junctionSpans(trail))
                return@buildList
            }
            val runs = directionRuns(trail)
            if (runs.size == 1) {
                add(Piece(runs.single(), part = null))
                stretchesOf(trail, runs.single()).takeIf { it.size > 1 }?.forEachIndexed { k, range -> add(Piece(range, part = k + 1)) }
                return@buildList
            }
            // Where the trail's permitted directions change, each run is cut as a trail of its own, and the trail gives no whole candidate.
            var part = 0
            for (run in runs) {
                add(Piece(run, part = ++part))
                stretchesOf(trail, run).takeIf { it.size > 1 }?.forEach { range -> add(Piece(range, part = ++part)) }
            }
        }
        return pieces.flatMap { piece -> directions(trail, piece) }
    }

    /** [trail]'s node ranges over which every edge allows the same directions, in order. */
    private fun directionRuns(trail: Trail): List<IntRange> {
        val last = trail.nodes.lastIndex
        val runs = mutableListOf<IntRange>()
        var start = 0
        for (edge in 1 until last) {
            val changes = trail.forward[edge] != trail.forward[edge - 1] || trail.backward[edge] != trail.backward[edge - 1]
            if (changes) {
                runs += start..edge
                start = edge
            }
        }
        runs += start..last
        return runs
    }

    /**
     * [range] of [trail] cut at its junctions into stretches, each joined to the next until it reaches the largest minimum length of the
     * sport groups all the trail's own ways are open to, so each group cuts the same ways at the same nodes; a short tail joins the last.
     */
    private fun stretchesOf(trail: Trail, range: IntRange): List<IntRange> {
        val min = stretchMinimum(trail)
        val distance = DoubleArray(range.last - range.first + 1)
        for (i in range.first + 1..range.last) {
            val k = i - range.first
            distance[k] = distance[k - 1] + SegmentGeometry.distanceMeters(trail.points[i - 1], trail.points[i])
        }
        val junctions = (range.first + 1 until range.last).filter { i -> others(trail, trail.nodes[i]).isNotEmpty() }
        val stretches = mutableListOf<IntRange>()
        var start = range.first
        for (cut in junctions + range.last) {
            if (distance[cut - range.first] - distance[start - range.first] >= min) {
                stretches += start..cut
                start = cut
            }
        }
        if (start != range.last && stretches.isNotEmpty()) stretches[stretches.lastIndex] = stretches.last().first..range.last
        return stretches
    }

    /** The minimum stretch length for [trail]: the largest of its own group's and of the groups all its own ways are open to. */
    private fun stretchMinimum(trail: Trail): Double {
        val own = (trail.ways - trail.bridged).mapNotNull(waysById::get)
        val shared = own.map { it.rules.wayGroups(it.tags) }.reduceOrNull { a, b -> a intersect b }.orEmpty()
        return (shared + trail.group).maxOf { SegmentSportGroup.minMeters(it) }.toDouble()
    }

    /** A piece of a trail to cut: the whole trail, its stretch number [part], or a parent trail's span to or from the node [junction]. */
    private data class Piece(val range: IntRange, val part: Int?, val junction: Long? = null) {
        val whole: Boolean
            get() = part == null && junction == null
    }

    /**
     * A parent trail's spans from its start to each junction and from each junction to its end, where a junction is a node between two
     * differently named sections that a named way outside its family meets. A span following ways of one name is its section's own.
     */
    private fun junctionSpans(trail: Trail): List<Piece> {
        val last = trail.nodes.lastIndex
        fun section(edge: Int) = trail.wayNames[trail.edgeWays[edge]]
        return (1 until last)
            .filter { i -> section(i - 1) != section(i) && junctionName(trail, trail.nodes[i]) != null }
            .flatMap { i ->
                listOf(Piece(0..i, part = null, junction = trail.nodes[i]), Piece(i..last, part = null, junction = trail.nodes[i]))
            }
            .filter { piece -> (piece.range.first until piece.range.last).mapTo(mutableSetOf(), ::section).size >= 2 }
    }

    /**
     * What to call a parent trail's junction at [node]: the family name of the first, by name, of the named ways outside its family there.
     */
    private fun junctionName(trail: Trail, node: Long): String? =
        others(trail, node)
            .mapNotNull { it.name }
            .filter { TrailBuilder.familyOf(it) != trail.family }
            .minOrNull()
            ?.let(TrailBuilder::familyOf)

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
        val ownClimb by lazy { climbAlong(trail, piece, reversed) }
        val name = nameOf(trail, piece, nodes, bothWays) { SegmentDirection.of(SegmentHeading.of(points), ownClimb) }
        val line = simplified(points)
        val length = SegmentGeometry.lengthMeters(line)
        if (length < SegmentSportGroup.minMeters(trail.group) || length > SegmentSportGroup.MAX_METERS) return null
        val visits =
            Visits(
                nodes.subList(1, nodes.lastIndex).count { it == nodes.first() },
                nodes.subList(1, nodes.lastIndex).count { it == nodes.last() },
            )
        val agency = trail.rules is AgencyRules
        val features = if (agency) ways.map(agencyKeys::getValue) else emptyList()
        return Candidate(
            id =
                if (agency) {
                    agencyIdOf(trail.group, features, alongFirstWay, points.first(), points.last(), visits)
                } else {
                    idOf(trail.group, ways, alongFirstWay, nodes.first(), nodes.last(), visits)
                },
            group = trail.group,
            name = truncate(name),
            line = SegmentGeometry.encodePolyline(line),
            points = line,
            lengthMeters = length.roundToLong(),
            osmWays = if (agency) emptyList() else ways,
            kind = trail.kind,
            whole = piece.whole,
            source = trail.rules.source,
            license = trail.rules.license,
            attribution = trail.rules.attribution,
            agencyFeatures = features,
            oneWay = !bothWays,
            climb = climbOf(trail, reversed) { ownClimb },
        )
    }

    /**
     * A candidate's name: a whole trail's, with its direction when it goes both ways; a junction span's, `<trail> → <junction>` or `<trail>
     * from <junction>` in its direction of travel, after its own parent trail when it follows a way no other one does and else its family;
     * or a stretch's, named after the places at its ends or else numbered. Every name in it is without its generic [words].
     */
    private fun nameOf(
        trail: Trail,
        piece: Piece,
        nodes: List<Long>,
        bothWays: Boolean,
        direction: () -> SegmentDirection,
    ): String {
        val from = nameAt(trail, nodes.first())
        val to = nameAt(trail, nodes.last())
        return when {
            piece.junction != null -> spanName(trail, piece, piece.junction, nodes)
            piece.part == null -> if (bothWays) "${trail.label} (${direction().word})" else trail.label
            from != null && to != null -> "${trail.label}: ${words.shorten(from)} → ${words.shorten(to)}"
            else -> "${trail.label} (part ${piece.part})"
        }
    }

    /**
     * Whether [piece] of [trail] clearly climbs or descends travelled [reversed] or not, judged once along the trail's own order and
     * reversed for the other way, so a trail's two directions never disagree; null without terrain.
     */
    private fun climbAlong(trail: Trail, piece: Piece, reversed: Boolean): SegmentClimb? {
        val judge = climbOf ?: return null
        val climb = judge(trail.points.subList(piece.range.first, piece.range.last + 1))
        return if (reversed) climb?.reversed() else climb
    }

    /** A candidate's climb (segments/trail-candidates): its [own], else its trail's; none for alpine. */
    private fun climbOf(trail: Trail, reversed: Boolean, own: () -> SegmentClimb?): SegmentClimb? =
        if (trail.group == SegmentSportGroup.ALPINE) null else own() ?: trailClimb(trail, reversed)

    /**
     * Whether [trail]'s whole line clearly climbs or descends travelled [reversed] or not, so a flat piece of a trail that climbs takes its
     * trail's climb; null without terrain.
     */
    private fun trailClimb(trail: Trail, reversed: Boolean): SegmentClimb? {
        val judge = climbOf ?: return null
        val climb = trailClimbs.getOrPut(trail) { judge(trail.points) }
        return if (reversed) climb?.reversed() else climb
    }

    /** A junction span's name, travelling along [nodes]: to [junction] when it ends there, else from it. */
    private fun spanName(trail: Trail, piece: Piece, junction: Long, nodes: List<Long>): String {
        val place = junctionName(trail, junction)?.let(words::shorten)
        val own = (piece.range.first until piece.range.last).any { trail.edgeWays[it] in trail.ownWays }
        val base = if (own) trail.label else words.shorten(trail.family ?: trail.name)
        return if (nodes.last() == junction) "$base → $place" else "$base from $place"
    }

    /**
     * The named ways through [node] that aren't [trail]'s own, nor of its name without generic [words]. For an agency trail, the agency's
     * other lines of the same name don't count either: the Forest Service publishes one trail as several features, a summer and a winter
     * one split at different mileposts.
     */
    private fun others(trail: Trail, node: Long): List<OsmWay> {
        val own = words.shorten(trail.name)
        return namedAt[node].orEmpty().filter { way ->
            way.id !in trail.ways &&
                !(trail.rules is AgencyRules && way.rules is AgencyRules && way.name == trail.name) &&
                // Another line of the trail's own name, such as an informal alternate, is the same trail, not a place on it.
                way.name?.let(words::shorten) != own
        }
    }

    /** What to call the place [node] is on [trail]: the name of another way there, preferring one that isn't the trail's own name. */
    private fun nameAt(trail: Trail, node: Long): String? =
        others(trail, node).mapNotNull { it.name }.distinct().sortedWith(compareBy({ it == trail.name }, { it })).firstOrNull()

    companion object {
        /**
         * How far a simplified line may stray from the trail, in meters: close enough to keep switchbacks' turns on a segment's map, and
         * well inside the matcher's 30 m corridor.
         */
        const val TOLERANCE_M = 1.0

        /** The longest a `feed.segment` line may be, in characters. */
        const val MAX_LINE_CHARS = 100_000

        const val MAX_NAME_GRAPHEMES = 100

        private const val ID_CHARS = 26
        private const val E5 = 1e5

        /**
         * [points] simplified at [TOLERANCE_M], coarser only as far as its encoding needs to fit a record (`TrackMath.simplifyToFit`), and
         * at the polyline's precision, so its length is the one the AppView checks.
         */
        fun simplified(points: List<GeoPoint>): List<GeoPoint> {
            val samples = points.map { LocationSample(it.lat, it.lng, null, null, null, Instant.DISTANT_PAST) }
            val line =
                TrackMath.simplifyToFit(listOf(samples), TOLERANCE_M, MAX_LINE_CHARS) {
                    SegmentGeometry.encodePolyline(it.single().map(::geoPoint))
                }
            // As the AppView decodes it: encoding rounds each position to the polyline's precision, about a metre, which can take a line
            // measured just over its group's minimum length under it, and the AppView measures the decoded line.
            return checkNotNull(SegmentGeometry.decodePolyline(SegmentGeometry.encodePolyline(line.single().map(::geoPoint))))
        }

        private fun geoPoint(sample: LocationSample) = GeoPoint(sample.latitude, sample.longitude)

        /**
         * A stable id from the group, the direction of travel ([alongFirstWay]: whether it follows its first way's node order), the ways
         * followed in order and the end nodes: the same OSM data always gives the same id, and moving an interior node (a geometry fix)
         * keeps it. [visits] tells apart lines along a way that crosses itself at one of their ends. 26 base32 characters of a SHA-256.
         */
        @Suppress("LongParameterList") // Each input the spec names, kept apart so none can be left out.
        fun idOf(group: String, ways: List<Long>, alongFirstWay: Boolean, first: Long, last: Long, visits: Visits = Visits.NONE): String {
            val sense = if (alongFirstWay) "+" else "-"
            return hashed("$group|$sense|${ways.joinToString(",")}|$first|$last${visits.key}")
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
            visits: Visits = Visits.NONE,
        ): String {
            val sense = if (alongFirstWay) "+" else "-"
            return hashed("$group|$sense|${features.joinToString(",")}|${e5(first)}|${e5(last)}${visits.key}")
        }

        /**
         * How often a line passed its first end again ([start]) and its last end before ending there ([end]), for a way that crosses
         * itself: the whole of a lollipop and its stem can follow the one way between the same ends. Each is left out of an id when zero,
         * as it nearly always is, so ids from before [start] was counted keep their value.
         */
        data class Visits(val start: Int, val end: Int) {
            val key: String
                get() = (if (end > 0) "#$end" else "") + (if (start > 0) "^$start" else "")

            companion object {
                val NONE = Visits(0, 0)
            }
        }

        private fun e5(point: GeoPoint): String = "${(point.lat * E5).roundToLong()},${(point.lng * E5).roundToLong()}"

        /** 26 base32 characters of [key]'s SHA-256. */
        private fun hashed(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).base32().take(ID_CHARS)

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
