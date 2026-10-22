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
)

/** [this] and [agency]'s ways and points as one network; agency way and point ids are negative, so they never meet OSM's. */
internal operator fun OsmData.plus(agency: AgencyData): OsmData = OsmData(ways + agency.ways, relations, coords + agency.points, timestamp)

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
