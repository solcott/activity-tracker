package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.time.Instant

/** What the importer keeps of an OSM extract: trail ways and relations, named ways for junctions, and the coordinates trails need. */
internal class OsmData(
    /** Ways that may be trails or route-relation members, and every named highway, whose nodes mark junctions. */
    val ways: List<OsmWay>,
    /** Route relations that may be trails. */
    val relations: List<OsmRelation>,
    /** Coordinates of the nodes of ways that may be trails or members; roads kept only for junctions have none. */
    val coords: NodeCoords,
    /** When the extract's data was current, from its header; null when it doesn't say. */
    val timestamp: Instant? = null,
    /** The agency lines whose closures leave out OSM candidates along them. */
    val agencyLines: List<AgencyLine> = emptyList(),
    /** Tagged nodes with their positions; only a dive-site read keeps any. */
    val nodes: List<OsmNode> = emptyList(),
)

/**
 * [this] and [agency]'s ways and points as one network, with its lines for closures; agency way and point ids are negative, so they never
 * meet OSM's.
 */
internal operator fun OsmData.plus(agency: AgencyData): OsmData =
    OsmData(ways + agency.ways, relations, coords + agency.points, timestamp, agencyLines + agency.lines, nodes)

/** A tagged OSM node at [lat], [lng] in degrees. */
internal class OsmNode(val id: Long, val tags: Map<String, String>, val lat: Double, val lng: Double)

/** An OSM element that is a dive site: its [type] and [id] key it, placed at [at] (a way or relation's centroid). */
internal data class OsmDiveSite(val type: OsmMember.Type, val id: Long, val name: String, val at: GeoPoint)

/**
 * What makes an OSM element a dive site: a name and `sport=scuba_diving` or `sport=free_diving`, on something that isn't a shop, amenity,
 * club or building, since dive shops and clubs carry the same sport tag.
 */
internal object DiveSiteTags {
    private val SPORTS = setOf("scuba_diving", "free_diving")
    private val EXCLUDED = setOf("shop", "amenity", "club", "building")

    /** Whether [tags] may make a site, before its name is checked: the reader keeps these elements. */
    fun isDiveSite(tags: Map<String, String>): Boolean =
        tags["sport"]?.split(';')?.any { it.trim() in SPORTS } == true && EXCLUDED.none { it in tags }

    fun nameOf(tags: Map<String, String>): String? = tags["name"]?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The dive-site pass: every named node, way and relation [DiveSiteTags] accepts, a way or relation at the centroid of its nodes' known
 * coordinates (a relation's through its member ways and nodes, one level deep), sorted by type and id. One with no known coordinate is left
 * out.
 */
internal fun OsmData.diveSites(): List<OsmDiveSite> {
    val waysById = ways.associateBy { it.id }
    val nodesById = nodes.associateBy { it.id }
    fun pointOf(id: Long): GeoPoint? = coords[id] ?: nodesById[id]?.let { GeoPoint(it.lat, it.lng) }
    fun wayPoints(way: OsmWay): List<GeoPoint> = way.nodes.distinct().mapNotNull(::pointOf)
    fun memberPoints(member: OsmMember): List<GeoPoint> =
        when (member.type) {
            OsmMember.Type.WAY -> waysById[member.ref]?.let(::wayPoints).orEmpty()
            OsmMember.Type.NODE -> listOfNotNull(pointOf(member.ref))
            OsmMember.Type.RELATION -> emptyList()
        }
    val sites =
        nodes.mapNotNull { siteOf(OsmMember.Type.NODE, it.id, it.tags) { GeoPoint(it.lat, it.lng) } } +
            ways.mapNotNull { way -> siteOf(OsmMember.Type.WAY, way.id, way.tags) { centroid(wayPoints(way)) } } +
            relations.mapNotNull { rel ->
                siteOf(OsmMember.Type.RELATION, rel.id, rel.tags) { centroid(rel.members.flatMap(::memberPoints).distinct()) }
            }
    return sites.sortedWith(compareBy({ it.type }, { it.id }))
}

/** A site of the element [type] [id] with [tags], at [at], or null when it's no named dive site or has no position. */
private inline fun siteOf(type: OsmMember.Type, id: Long, tags: Map<String, String>, at: () -> GeoPoint?): OsmDiveSite? {
    val name = DiveSiteTags.nameOf(tags)?.takeIf { DiveSiteTags.isDiveSite(tags) } ?: return null
    return at()?.let { OsmDiveSite(type, id, name, it) }
}

/** The mean of [points], or null when there are none. Dive-site features are small, so plain degrees average well. */
private fun centroid(points: List<GeoPoint>): GeoPoint? =
    if (points.isEmpty()) null else GeoPoint(points.sumOf { it.lat } / points.size, points.sumOf { it.lng } / points.size)

/** A way of the trail network: an OSM way, or an agency line made into one, whose [tags] its source's [rules] read. */
internal class OsmWay(val id: Long, val tags: Map<String, String>, val nodes: LongArray, val rules: TrailRules = TrailTags) {
    val name: String?
        get() = tags["name"]?.trim()?.takeIf { it.isNotEmpty() }
}

internal class OsmRelation(val id: Long, val tags: Map<String, String>, val members: List<OsmMember>) {
    val name: String?
        get() = tags["name"]?.trim()?.takeIf { it.isNotEmpty() }
}

internal data class OsmMember(val type: Type, val ref: Long, val role: String) {
    enum class Type {
        NODE,
        WAY,
        RELATION,
    }
}

/**
 * Node coordinates for sorted, distinct [ids], at 1e-7 degrees in parallel arrays: a few bytes per node, so a whole region's trail nodes
 * fit in memory. [set] fills in a coordinate for an id that's present.
 */
internal class NodeCoords(private val ids: LongArray) {
    private val lat = IntArray(ids.size)
    private val lng = IntArray(ids.size)
    private val known = BooleanArray(ids.size)

    operator fun contains(id: Long): Boolean = ids.binarySearch(id) >= 0

    fun set(id: Long, latitude: Double, longitude: Double) {
        val i = ids.binarySearch(id)
        if (i < 0) return
        lat[i] = (latitude * E7).toInt()
        lng[i] = (longitude * E7).toInt()
        known[i] = true
    }

    operator fun get(id: Long): GeoPoint? {
        val i = ids.binarySearch(id)
        return if (i >= 0 && known[i]) GeoPoint(lat[i] / E7, lng[i] / E7) else null
    }

    /** These coordinates and [more]'s together, in new arrays: [more]'s ids, agency points', are negative and never OSM node ids. */
    operator fun plus(more: Map<Long, GeoPoint>): NodeCoords {
        val merged =
            NodeCoords(
                LongSetBuilder()
                    .apply {
                        addAll(ids)
                        more.keys.forEach(::add)
                    }
                    .build()
            )
        for (i in ids.indices) if (known[i]) merged.set(ids[i], lat[i] / E7, lng[i] / E7)
        for ((id, point) in more) merged.set(id, point.lat, point.lng)
        return merged
    }

    companion object {
        private const val E7 = 1e7
    }
}

/** A growable set of node or way ids, frozen into a sorted, distinct [LongArray] for binary search. */
internal class LongSetBuilder {
    private var values = LongArray(INITIAL)
    private var size = 0

    fun add(value: Long) {
        if (size == values.size) values = values.copyOf(size * 2)
        values[size++] = value
    }

    fun addAll(more: LongArray) = more.forEach(::add)

    fun build(): LongArray {
        val sorted = values.copyOf(size).also { it.sort() }
        var distinct = 0
        for (i in sorted.indices) if (i == 0 || sorted[i] != sorted[i - 1]) sorted[distinct++] = sorted[i]
        return sorted.copyOf(distinct)
    }

    companion object {
        private const val INITIAL = 1024
    }
}
