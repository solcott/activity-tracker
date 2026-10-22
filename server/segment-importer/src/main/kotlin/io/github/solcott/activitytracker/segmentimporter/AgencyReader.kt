package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * What the importer keeps of a run's agency datasets: their trail lines as ways, the points those join at, every line's open and closed
 * groups for the [ClosureFilter], and what it skipped.
 */
internal class AgencyData(
    /** One way per qualifying LineString or MultiLineString part, with negative ids. */
    val ways: List<OsmWay>,
    /** Each point's coordinates, by its negative id. */
    val points: Map<Long, GeoPoint>,
    /** Features with no stable feature id, or no line, per dataset. */
    val skipped: Map<AgencyRules, Int>,
    /** Each keyed LineString or part that is open or closed to some group, named or not, in feature-key order. */
    val lines: List<AgencyLine> = emptyList(),
)

/** One agency line as published, unsnapped: the groups its feature is open to ([AgencyRules.groupsOf]) and closed to. */
internal class AgencyLine(val open: Set<String>, val closed: Set<String>, val points: List<GeoPoint>)

/**
 * Reads agency datasets, GeoJSON text sequences of features in WGS 84 (segments/trail-candidates), into [AgencyData]: one line at a time,
 * so a large file never sits in memory as a tree. Features that qualify as trails are snapped together in feature-key order, so the same
 * data always gives the same ids.
 */
internal object AgencyReader {
    private class Feature(
        val rules: AgencyRules,
        val key: String,
        val name: String,
        val groups: Set<String>,
        val closed: Set<String>,
        val lines: List<List<GeoPoint>>,
    )

    fun read(datasets: List<Pair<AgencyRules, File>>): AgencyData {
        val features = mutableListOf<Feature>()
        val skipped = mutableMapOf<AgencyRules, Int>()
        for ((rules, file) in datasets) {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val text = line.trim { it.isWhitespace() || it == RECORD_SEPARATOR }
                    if (text.isEmpty()) continue
                    when (val read = feature(rules, Json.parseToJsonElement(text) as? JsonObject)) {
                        null -> skipped.merge(rules, 1, Int::plus)
                        else -> features += read
                    }
                }
            }
        }

        val sorted = features.sortedBy { "${it.rules.kind}:${it.key}" }
        val snapper = PointSnapper()
        val ways = waysOf(sorted.filter { it.name.isNotEmpty() && it.groups.isNotEmpty() }, snapper)
        val lines =
            sorted
                .filter { it.groups.isNotEmpty() || it.closed.isNotEmpty() }
                .flatMap { feature -> feature.lines.map { AgencyLine(feature.groups, feature.closed, it) } }
        return AgencyData(ways, snapper.points, skipped, lines)
    }

    /** One way per line of the trail [features], in order, their points snapped together by [snapper]. */
    private fun waysOf(features: List<Feature>, snapper: PointSnapper): List<OsmWay> {
        val ways = mutableListOf<OsmWay>()
        var nextWay = -1L
        for (feature in features) {
            val tags = mapOf(AgencyRules.NAME to feature.name, AgencyRules.GROUPS to feature.groups.sorted().joinToString(","))
            for ((part, points) in feature.lines.withIndex()) {
                val ids = points.map(snapper::idOf)
                val nodes = ids.filterIndexed { i, id -> i == 0 || id != ids[i - 1] }
                if (nodes.size < 2) continue
                val key = "${feature.rules.kind}:${feature.key}" + if (part > 0) "#$part" else ""
                ways += OsmWay(nextWay--, tags + (AgencyRules.KEY to key), nodes.toLongArray(), feature.rules)
            }
        }
        return ways
    }

    /**
     * A feature's key, name, open and closed groups and lines; null when it has no stable feature id or no line. A feature that isn't a
     * trail is returned with an empty name or no groups, so it isn't counted as skipped and its lines can still close the ground.
     */
    @Suppress("ReturnCount") // Each way a feature can lack an id or a line.
    private fun feature(rules: AgencyRules, json: JsonObject?): Feature? {
        if (json == null) return null
        val properties =
            (json["properties"] as? JsonObject).orEmpty().entries.associate { (k, v) ->
                k.lowercase() to (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            }
        val key = rules.featureKey(properties) ?: return null
        val geometry = json["geometry"] as? JsonObject ?: return null
        val coordinates = geometry["coordinates"] as? JsonArray ?: return null
        val parts =
            when ((geometry["type"] as? JsonPrimitive)?.content) {
                "LineString" -> listOf(coordinates)
                "MultiLineString" -> coordinates.map { it as? JsonArray ?: return null }
                else -> return null
            }
        val lines = parts.map { part -> part.mapNotNull(::position) }.filter { it.size >= 2 }
        if (lines.isEmpty()) return null
        return Feature(rules, key, rules.nameFrom(properties).orEmpty(), rules.groupsOf(properties), rules.closedTo(properties), lines)
    }

    /** A GeoJSON position, `[longitude, latitude]` with an optional height, or null when it isn't one in range. */
    @Suppress("ReturnCount") // Each way a position can be malformed.
    private fun position(json: JsonElement): GeoPoint? {
        val values = (json as? JsonArray)?.takeIf { it.size >= 2 } ?: return null
        val lng = (values[0] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lat = (values[1] as? JsonPrimitive)?.doubleOrNull ?: return null
        return GeoPoint(lat, lng).takeIf { lat in -MAX_LAT..MAX_LAT && lng in -MAX_LNG..MAX_LNG }
    }

    /** RFC 8142's record separator, which GDAL writes before each feature of a `.geojsons` file. */
    private const val RECORD_SEPARATOR = '\u001e'
    private const val MAX_LAT = 90.0
    private const val MAX_LNG = 180.0
}

/**
 * Gives every position within [TOLERANCE_M] of an earlier one that one's id, else a new negative id, so agency lines that meet share a
 * point as OSM ways share a node. Points are hashed into cells about [TOLERANCE_M] wide, and a position is compared with the points in its
 * cell and the eight around it, taking the nearest.
 */
internal class PointSnapper {
    /** Each point's coordinates, by id, in the order they were made. */
    val points: Map<Long, GeoPoint>
        field = LinkedHashMap<Long, GeoPoint>()

    private val cells = HashMap<Pair<Long, Long>, MutableList<Long>>()
    private var next = -1L

    fun idOf(point: GeoPoint): Long {
        val (row, col) = cellOf(point)
        var nearest: Long? = null
        var nearestM = TOLERANCE_M
        for (dr in -1L..1L) {
            for (dc in -1L..1L) {
                for (id in cells[(row + dr) to (col + dc)].orEmpty()) {
                    val meters = SegmentGeometry.distanceMeters(points.getValue(id), point)
                    if (meters <= nearestM) {
                        nearest = id
                        nearestM = meters
                    }
                }
            }
        }
        if (nearest != null) return nearest
        val id = next--
        points[id] = point
        cells.getOrPut(row to col) { mutableListOf() } += id
        return id
    }

    private fun cellOf(point: GeoPoint): Pair<Long, Long> {
        val size = TOLERANCE_M / METERS_PER_DEGREE
        return floor(point.lat / size).toLong() to floor(point.lng * cos(point.lat * PI / HALF_TURN) / size).toLong()
    }

    companion object {
        /** How close two positions must be to be one point: well under GPS error, above typical GIS snapping tolerance. */
        const val TOLERANCE_M = 1.0

        private const val METERS_PER_DEGREE = 111_195.0
        private const val HALF_TURN = 180.0
    }
}
