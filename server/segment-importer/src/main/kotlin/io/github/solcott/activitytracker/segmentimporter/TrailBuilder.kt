package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint

/**
 * A trail for one sport group: a named line through [nodes], from named ways joined end to end or a route relation's members joined in
 * order. Edge `i` runs from `nodes[i]` to `nodes[i + 1]` along way `edgeWays[i]`, against the way's node order when `edgeReversed[i]`, and
 * may be travelled that way when `forward[i]`, the other way when `backward[i]`.
 */
@Suppress("LongParameterList") // The trail's line and what each of its edges allows.
internal class Trail(
    val name: String,
    val group: String,
    val fromRelation: Boolean,
    val nodes: LongArray,
    val points: List<GeoPoint>,
    val edgeWays: LongArray,
    val edgeReversed: BooleanArray,
    val forward: BooleanArray,
    val backward: BooleanArray,
) {
    /** The ways it follows, so a crossing by one of its own ways isn't a junction. */
    val ways: Set<Long> = edgeWays.toSet()
}

/** Joins [OsmData]'s ways into [Trail]s per sport group (segments/trail-candidates). */
internal object TrailBuilder {
    fun build(data: OsmData): List<Trail> {
        val usable = data.ways.filter { way -> way.nodes.size >= 2 && way.nodes.all { data.coords[it] != null } }.associateBy { it.id }
        return namedTrails(usable.values, data) + relationTrails(data.relations, usable, data)
    }

    /** A way placed in a trail: [reversed] when the trail runs against the way's node order. */
    private data class Placed(val way: OsmWay, val reversed: Boolean) {
        val nodes: List<Long>
            get() = if (reversed) way.nodes.reversed() else way.nodes.toList()
    }

    /**
     * Named ways of each group joined where they share the name and an end node. A trail ends where more than two such ways meet, so a fork
     * never picks a branch.
     */
    private fun namedTrails(ways: Collection<OsmWay>, data: OsmData): List<Trail> =
        ways
            .flatMap { way -> way.name?.let { name -> TrailTags.wayGroups(way.tags).map { group -> Triple(group, name, way) } }.orEmpty() }
            .groupBy({ (group, name, _) -> group to name }, { it.third })
            .flatMap { (key, named) ->
                val (group, name) = key
                val atEnd = named.flatMap { w -> listOf(w.nodes.first() to w, w.nodes.last() to w) }.groupBy({ it.first }, { it.second })
                val used = mutableSetOf<Long>()
                named
                    .sortedBy { it.id }
                    .mapNotNull { start ->
                        if (!used.add(start.id)) return@mapNotNull null
                        val chain = ArrayDeque(listOf(Placed(start, reversed = false)))
                        fun next(node: Long, current: OsmWay): OsmWay? =
                            atEnd[node].orEmpty().distinct().takeIf { it.size == 2 }?.firstOrNull { it != current && it.id !in used }
                        while (true) {
                            val end = chain.last()
                            val way = next(end.nodes.last(), end.way) ?: break
                            used += way.id
                            chain.addLast(Placed(way, reversed = way.nodes.last() == end.nodes.last()))
                        }
                        while (true) {
                            val first = chain.first()
                            val way = next(first.nodes.first(), first.way) ?: break
                            used += way.id
                            chain.addFirst(Placed(way, reversed = way.nodes.first() == first.nodes.first()))
                        }
                        trail(name, group, fromRelation = false, chain, data)
                    }
            }

    /**
     * Each named route relation's way members, in order, joined end to end and reversed as needed. Ways that are [TrailTags.excluded] or
     * missing are left out, and a member that doesn't join starts a new trail, so a relation with gaps gives one trail per joined stretch.
     */
    private fun relationTrails(relations: List<OsmRelation>, ways: Map<Long, OsmWay>, data: OsmData): List<Trail> =
        relations.flatMap { relation ->
            val name = relation.name ?: return@flatMap emptyList()
            val groups = TrailTags.relationGroups(relation.tags)
            val members =
                relation.members
                    .filter { it.type == OsmMember.Type.WAY }
                    .mapNotNull { ways[it.ref] }
                    .filter { !TrailTags.excluded(it.tags) }
            val stretches = mutableListOf<MutableList<Placed>>()
            for (way in members) {
                val current = stretches.lastOrNull()
                val joined = current?.let { join(it, way) } ?: false
                if (!joined) stretches += mutableListOf(Placed(way, reversed = false))
            }
            stretches.flatMap { chain -> groups.map { group -> trail(name, group, fromRelation = true, chain, data) } }
        }

    /**
     * Adds [way] to the end of [chain] if it meets the chain's end, turning the chain's only way around first if that's what joins them.
     */
    private fun join(chain: MutableList<Placed>, way: OsmWay): Boolean {
        if (chain.size == 1) {
            val only = chain.single()
            val touchesStart = way.nodes.first() == only.nodes.first() || way.nodes.last() == only.nodes.first()
            val touchesEnd = way.nodes.first() == only.nodes.last() || way.nodes.last() == only.nodes.last()
            if (touchesStart && !touchesEnd) chain[0] = only.copy(reversed = !only.reversed)
        }
        val end = chain.last().nodes.last()
        val placed =
            when (end) {
                way.nodes.first() -> Placed(way, reversed = false)
                way.nodes.last() -> Placed(way, reversed = true)
                else -> return false
            }
        chain += placed
        return true
    }

    private fun trail(name: String, group: String, fromRelation: Boolean, chain: Collection<Placed>, data: OsmData): Trail {
        val nodes = mutableListOf<Long>()
        val edgeWays = mutableListOf<Long>()
        val edgeReversed = mutableListOf<Boolean>()
        val forward = mutableListOf<Boolean>()
        val backward = mutableListOf<Boolean>()
        for (placed in chain) {
            val wayNodes = placed.nodes
            if (nodes.isEmpty()) nodes += wayNodes.first()
            val (along, against) = TrailTags.directions(placed.way.tags, group)
            repeat(wayNodes.size - 1) {
                edgeWays += placed.way.id
                edgeReversed += placed.reversed
                forward += if (placed.reversed) against else along
                backward += if (placed.reversed) along else against
            }
            nodes += wayNodes.drop(1)
        }
        return Trail(
            name = name,
            group = group,
            fromRelation = fromRelation,
            nodes = nodes.toLongArray(),
            points = nodes.map { requireNotNull(data.coords[it]) },
            edgeWays = edgeWays.toLongArray(),
            edgeReversed = edgeReversed.toBooleanArray(),
            forward = forward.toBooleanArray(),
            backward = backward.toBooleanArray(),
        )
    }
}
