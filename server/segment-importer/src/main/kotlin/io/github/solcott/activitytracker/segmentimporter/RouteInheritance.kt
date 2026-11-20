package io.github.solcott.activitytracker.segmentimporter

/**
 * The trail attribute tags an OSM way takes from the named route relations it belongs to (segments/trail-attributes): `dog`, `horse`,
 * `wheelchair`, `operator` and `website`, each only where the way has none and every such relation that has it agrees. Worked out once per
 * extract, before trails are built.
 */
internal object RouteInheritance {
    private const val WEBSITE = "website"
    private const val CONTACT_WEBSITE = "contact:website"
    private val KEYS = listOf("dog", "horse", "wheelchair", "operator", WEBSITE)

    /** [data] with each OSM way given the tags it inherits; the same [data] when none inherits anything. */
    fun applied(data: OsmData): OsmData {
        val inherited = inheritedTags(data.relations)
        if (inherited.isEmpty()) return data
        val ways =
            data.ways.map { way ->
                val more = inherited[way.id]?.filterKeys { key -> !has(way.tags, key) }
                if (more.isNullOrEmpty() || way.rules !== TrailTags) way else OsmWay(way.id, way.tags + more, way.nodes, way.rules)
            }
        return OsmData(ways, data.relations, data.coords, data.timestamp, data.agencyLines, data.nodes)
    }

    /** Each member way's tags its named route relations agree on, by way id. */
    fun inheritedTags(relations: List<OsmRelation>): Map<Long, Map<String, String>> {
        val values = mutableMapOf<Long, MutableMap<String, MutableSet<String>>>()
        for (relation in relations.filter { it.tags["type"] == "route" && it.name != null }) {
            val given = KEYS.mapNotNull { key -> valueOf(relation.tags, key)?.let { key to it } }
            for (member in relation.members.filter { it.type == OsmMember.Type.WAY && given.isNotEmpty() }) {
                val byKey = values.getOrPut(member.ref) { mutableMapOf() }
                for ((key, value) in given) byKey.getOrPut(key) { mutableSetOf() } += value
            }
        }
        return values
            .mapValues { (_, byKey) -> byKey.filterValues { it.size == 1 }.mapValues { (_, agreed) -> agreed.single() } }
            .filterValues { it.isNotEmpty() }
    }

    private fun valueOf(tags: Map<String, String>, key: String): String? =
        (if (key == WEBSITE) tags[WEBSITE] ?: tags[CONTACT_WEBSITE] else tags[key])?.trim()?.takeIf { it.isNotEmpty() }

    private fun has(tags: Map<String, String>, key: String): Boolean = valueOf(tags, key) != null
}
