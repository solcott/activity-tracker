package io.github.solcott.activitytracker.segmentimporter

/**
 * Builds [OsmData] for tests, so tag rules and cutting run without a PBF: `osm { node(1, 47.0, 8.0); way(10, 1, 2, tags = path("Oak")) }`.
 * Every node gets its coordinate, as the reader gives every node of a kept way.
 */
internal class OsmBuilder {
    private val nodes = mutableMapOf<Long, Pair<Double, Double>>()
    private val ways = mutableListOf<OsmWay>()
    private val relations = mutableListOf<OsmRelation>()

    fun node(id: Long, lat: Double, lng: Double) {
        nodes[id] = lat to lng
    }

    fun way(id: Long, vararg nodes: Long, tags: Map<String, String>) {
        ways += OsmWay(id, tags, nodes)
    }

    fun relation(id: Long, vararg ways: Long, tags: Map<String, String>) {
        relations += OsmRelation(id, tags, ways.map { OsmMember(OsmMember.Type.WAY, it, "") })
    }

    fun build(): OsmData {
        val coords = NodeCoords(nodes.keys.sorted().toLongArray())
        nodes.forEach { (id, at) -> coords.set(id, at.first, at.second) }
        return OsmData(ways.toList(), relations.toList(), coords)
    }
}

internal fun osm(block: OsmBuilder.() -> Unit): OsmData = OsmBuilder().apply(block).build()

/** Tags for a named `highway=path`, plus [more]. */
internal fun path(name: String?, vararg more: Pair<String, String>): Map<String, String> = highway("path", name, *more)

internal fun highway(kind: String, name: String?, vararg more: Pair<String, String>): Map<String, String> = buildMap {
    put("highway", kind)
    name?.let { put("name", it) }
    putAll(more)
}

/** About [meters] north of [lat], for laying out test trails. */
internal fun north(lat: Double, meters: Double): Double = lat + meters / METERS_PER_DEGREE

private const val METERS_PER_DEGREE = 111_195.0
