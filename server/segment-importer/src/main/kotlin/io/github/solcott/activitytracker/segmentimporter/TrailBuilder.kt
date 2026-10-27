package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry

/** Where a trail comes from: same-named ways joined end to end, a route relation, or a family of sectioned ways (a parent trail). */
internal enum class TrailKind {
    NAMED,
    RELATION,
    PARENT,
}

/**
 * A trail for one sport group: a named line through [nodes], from named ways joined end to end or a route relation's members joined in
 * order. Edge `i` runs from `nodes[i]` to `nodes[i + 1]` along way `edgeWays[i]`, against the way's node order when `edgeReversed[i]`, and
 * may be travelled that way when `forward[i]`, the other way when `backward[i]`.
 */
@Suppress("LongParameterList") // The trail's line and what each of its edges allows.
internal class Trail(
    val name: String,
    /** The rules of the source its ways came from: a trail never mixes sources, since they share no nodes. */
    val rules: TrailRules,
    val group: String,
    val kind: TrailKind,
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
        return namedTrails(usable.values, data) + relationTrails(data.relations, usable, data) + parentTrails(usable.values, data)
    }

    /** How many parent trails a family keeps per group; with more paths, it keeps only its longest. */
    private const val MAX_PARENT_PATHS = 4

    /** How many steps a family's path search may take per group before it settles for the paths found so far. */
    private const val PARENT_SEARCH_STEPS = 1_000

    /** The family a way named [name] belongs to: the part before the first `" - "`, or the whole name. */
    fun familyOf(name: String): String = name.substringBefore(" - ").trim().ifEmpty { name }

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
            .flatMap { way -> way.name?.let { name -> way.rules.wayGroups(way.tags).map { group -> Triple(group, name, way) } }.orEmpty() }
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
                        trail(name, group, TrailKind.NAMED, chain, data)
                    }
            }

    /**
     * Each named route relation's way members, in order, joined end to end and reversed as needed. Ways that are [TrailRules.excluded] or
     * missing are left out, and a member that doesn't join starts a new trail, so a relation with gaps gives one trail per joined stretch.
     */
    private fun relationTrails(relations: List<OsmRelation>, ways: Map<Long, OsmWay>, data: OsmData): List<Trail> =
        relations.flatMap { relation ->
            val name = relation.name ?: return@flatMap emptyList()
            val groups = TrailTags.relationGroups(relation.tags)
            val members =
                relation.members.filter { it.type == OsmMember.Type.WAY }.mapNotNull { ways[it.ref] }.filter { !it.rules.excluded(it.tags) }
            val stretches = mutableListOf<MutableList<Placed>>()
            for (way in members) {
                val current = stretches.lastOrNull()
                val joined = current?.let { join(it, way) } ?: false
                if (!joined) stretches += mutableListOf(Placed(way, reversed = false))
            }
            stretches.flatMap { chain -> groups.map { group -> trail(name, group, TrailKind.RELATION, chain, data) } }
        }

    /**
     * Parent trails: OSM ways named `<Parent>` or `<Parent> - <Section>` joined at their ends along each path through them that their ways
     * allow, from a way nothing joins into to one that joins into nothing further, crossing at least two names. Only ends join, so a way
     * meeting the family mid-way is on no path. A path and its reverse are one trail. A family keeps up to [MAX_PARENT_PATHS], or only its
     * longest when it has more, each named after its first section that is on none of the others, or else its first section.
     */
    private fun parentTrails(ways: Collection<OsmWay>, data: OsmData): List<Trail> =
        ways
            .filter { it.rules == TrailTags }
            .flatMap { way ->
                way.name?.let { name -> way.rules.wayGroups(way.tags).map { group -> Triple(group, familyOf(name), way) } }.orEmpty()
            }
            .groupBy({ (group, family, _) -> group to family }, { it.third })
            .filter { (key, family) -> family.any { it.name != key.second } }
            .flatMap { (key, family) ->
                val (group, name) = key
                val paths =
                    familyPaths(name, group, family)
                        .filter { path -> path.mapTo(mutableSetOf()) { it.way.name }.size >= 2 }
                        .distinctBy { path -> path.mapTo(mutableSetOf()) { it.way.id } }
                val kept = if (paths.size > MAX_PARENT_PATHS) listOf(paths.maxBy { lengthOf(it, data) }) else paths
                kept.map { path ->
                    val trailName = if (kept.size == 1) name else viaName(name, path, kept)
                    trail(trailName, group, TrailKind.PARENT, path, data)
                }
            }

    /**
     * Every path through [family]'s ways joined at their ends, each way in a direction it allows for [group]: from a way nothing else joins
     * into, along ways not yet on the path, to where none continues. Stops after [PARENT_SEARCH_STEPS] steps, keeping what it found.
     */
    private fun familyPaths(name: String, group: String, family: List<OsmWay>): List<List<Placed>> {
        val steps = family.flatMap { way ->
            val (along, against) = way.rules.directions(way.tags, group)
            listOfNotNull(Placed(way, reversed = false).takeIf { along }, Placed(way, reversed = true).takeIf { against })
        }
        val from = steps.groupBy { it.nodes.first() }
        val into = steps.groupBy { it.nodes.last() }
        val paths = mutableListOf<List<Placed>>()
        var budget = PARENT_SEARCH_STEPS
        fun walk(path: List<Placed>) {
            val next = from[path.last().nodes.last()].orEmpty().filter { step -> path.none { it.way.id == step.way.id } }
            if (next.isEmpty()) paths += path
            for (step in next) {
                if (budget-- <= 0) return
                walk(path + step)
            }
        }
        for (start in steps) {
            if (budget <= 0) break
            if (into[start.nodes.first()].orEmpty().none { it.way.id != start.way.id }) walk(listOf(start))
        }
        if (budget <= 0) println("Searched only part of the $group ways of $name for parent trails; kept the paths found")
        return paths
    }

    /**
     * [path]'s name among [paths]: `<name> via <section>` after its first section that no other path follows, or, when the others follow
     * all its sections, `<name> from <section>` after its first, so an entry that joins partway down doesn't read as the whole trail.
     */
    private fun viaName(name: String, path: List<Placed>, paths: List<List<Placed>>): String {
        val elsewhere = paths.filter { it !== path }.flatMapTo(mutableSetOf()) { other -> other.map { it.way.name } }
        val sections = path.mapNotNull { it.way.name }.filter { it != name }
        sections
            .firstOrNull { it !in elsewhere }
            ?.let {
                return "$name via ${it.substringAfter(" - ")}"
            }
        return sections.firstOrNull()?.let { "$name from ${it.substringAfter(" - ")}" } ?: name
    }

    private fun lengthOf(path: List<Placed>, data: OsmData): Double =
        path.flatMap { it.nodes }.mapNotNull { data.coords[it] }.zipWithNext().sumOf { (a, b) -> SegmentGeometry.distanceMeters(a, b) }

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

    private fun trail(name: String, group: String, kind: TrailKind, chain: Collection<Placed>, data: OsmData): Trail {
        val nodes = mutableListOf<Long>()
        val edgeWays = mutableListOf<Long>()
        val edgeReversed = mutableListOf<Boolean>()
        val forward = mutableListOf<Boolean>()
        val backward = mutableListOf<Boolean>()
        for (placed in chain) {
            val wayNodes = placed.nodes
            if (nodes.isEmpty()) nodes += wayNodes.first()
            val (along, against) = placed.way.rules.directions(placed.way.tags, group)
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
            rules = chain.first().way.rules,
            group = group,
            kind = kind,
            nodes = nodes.toLongArray(),
            points = nodes.map { requireNotNull(data.coords[it]) },
            edgeWays = edgeWays.toLongArray(),
            edgeReversed = edgeReversed.toBooleanArray(),
            forward = forward.toBooleanArray(),
            backward = backward.toBooleanArray(),
        )
    }
}
