package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.util.PriorityQueue

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
    /**
     * What candidates call it: [name] without its generic words, or for a parent trail that branches, `<Parent> › <section>` or `<Parent>
     * from <section>` from its shortened parts.
     */
    val label: String,
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
    /** A parent trail's family name, the part of its sections' names before `" - "`; null for any other trail. */
    val family: String? = null,
    /** The ways on no other parent trail of a parent trail's family, which tell its spans apart from theirs. */
    val ownWays: Set<Long> = emptySet(),
    /** A parent trail's ways' names, by way id, for finding where its sections meet. */
    val wayNames: Map<Long, String> = emptyMap(),
    /** The ways it follows only as short shared tread, a road or another trail it runs along between two of its own ways. */
    val bridged: Set<Long> = emptySet(),
) {
    /** The ways it follows, so a crossing by one of its own ways isn't a junction. */
    val ways: Set<Long> = edgeWays.toSet()

    /** This trail labelled [label] instead, for candidates' names. */
    fun withLabel(label: String): Trail =
        Trail(
            name,
            label,
            rules,
            group,
            kind,
            nodes,
            points,
            edgeWays,
            edgeReversed,
            forward,
            backward,
            family,
            ownWays,
            wayNames,
            bridged,
        )
}

/** Joins [OsmData]'s ways into [Trail]s per sport group (segments/trail-candidates). */
@Suppress("TooManyFunctions") // Each kind of trail, how its ways join, and the shared tread and relation ends between them.
internal object TrailBuilder {
    /**
     * [data]'s trails, labelled without [words]. A trail may follow ways that carry its own ref between two of its parts for up to
     * [refBridgeM] meters.
     */
    fun build(data: OsmData, words: GenericWords = GenericWords.ENGLISH, refBridgeM: Double = DEFAULT_REF_BRIDGE_M): List<Trail> {
        val usable = data.ways.filter { way -> way.nodes.size >= 2 && way.nodes.all { data.coords[it] != null } }.associateBy { it.id }
        val relations = relationTrails(data.relations, usable, data, words)
        // A named trail that follows exactly a route relation's ways goes by the relation's name, so its candidates read alike.
        val relationLabels = relations.groupBy { it.ways }.mapValues { (_, trails) -> trails.minBy { it.name }.label }
        val named =
            namedTrails(usable.values, data, words, refBridgeM).map { trail ->
                relationLabels[trail.ways]?.takeIf { it != trail.label }?.let(trail::withLabel) ?: trail
            }
        return named + relations + parentTrails(usable.values, data, words)
    }

    /** How far along roads or other trails a trail may follow them as shared tread, in meters. */
    const val BRIDGE_M = 200.0

    /** How far a trail may follow ways that carry its own ref between two of its parts unless the extract says, in meters. */
    const val DEFAULT_REF_BRIDGE_M = 1_000.0

    /** How many parent trails a family keeps per group; with more paths, it keeps only its longest. */
    private const val MAX_PARENT_PATHS = 4

    /** How a branching parent trail's label joins its parent and section, by the word its name uses. */
    private val LABEL_JOINERS = mapOf("via" to "›", "from" to "from")

    /** How many steps a family's path search may take per group before it settles for the paths found so far. */
    private const val PARENT_SEARCH_STEPS = 1_000

    /** The family a way named [name] belongs to: the part before the first `" - "`, or the whole name. */
    fun familyOf(name: String): String = name.substringBefore(" - ").trim().ifEmpty { name }

    /**
     * A way placed in a trail: its nodes [from] to [to], all of them unless it's a [bridge], [reversed] when the trail runs against the
     * way's node order. A [bridge] is shared tread the trail follows between two of its own ways.
     */
    private data class Placed(
        val way: OsmWay,
        val reversed: Boolean,
        val from: Int = 0,
        val to: Int = way.nodes.lastIndex,
        val bridge: Boolean = false,
    ) {
        val nodes: List<Long>
            get() = way.nodes.slice(from..to).let { if (reversed) it.reversed() else it }

        fun turned(): Placed = copy(reversed = !reversed)
    }

    private fun List<Placed>.turned(): List<Placed> = reversed().map { it.turned() }

    private val List<Placed>.start: Long
        get() = first().nodes.first()

    private val List<Placed>.end: Long
        get() = last().nodes.last()

    /**
     * Named ways of each group joined where they share the name without its generic [words] and an end node. A trail ends where more than
     * two such ways meet, so a fork never picks a branch. A trail that ends where none of its ways continues joins another of its name over
     * shared tread ([SharedTread]): at most [BRIDGE_M] along roads or other trails, or [refBridgeM] along ways that carry its ref. It is
     * named after its longest way.
     */
    private fun namedTrails(ways: Collection<OsmWay>, data: OsmData, words: GenericWords, refBridgeM: Double): List<Trail> {
        val byName =
            ways
                .flatMap { way ->
                    way.name
                        ?.let { name -> way.rules.wayGroups(way.tags).map { group -> Triple(group, words.shorten(name), way) } }
                        .orEmpty()
                }
                .groupBy({ (group, key, _) -> group to key }, { it.third })
        val chains = byName.mapValues { (_, named) -> chainsOf(named) }
        // Where a chain ends with no other way of its name ending there: the ends that may join another chain over shared tread.
        val dangling = byName.mapValues { (_, named) ->
            named
                .flatMap { w -> listOf(w.nodes.first() to w.id, w.nodes.last() to w.id) }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.distinct().size == 1 }
                .keys
        }
        val waysAt = lazy { WaysAt(data.ways.filter { way -> way.rules == TrailTags && "highway" in way.tags }.sortedBy { it.id }) }
        return chains.flatMap { (key, list) ->
            val (group, label) = key
            val joined =
                if (list.size < 2) list
                else bridged(list, dangling.getValue(key), SharedTread(waysAt, data, group, label, words, refBridgeM))
            joined.map { chain ->
                val name = chain.filter { !it.bridge }.maxBy { lengthOf(listOf(it), data) }.way.name.orEmpty()
                trail(name, label, group, TrailKind.NAMED, chain, data)
            }
        }
    }

    /** [named]'s ways, all of one group and name, joined at ends where exactly two of them meet. */
    private fun chainsOf(named: List<OsmWay>): List<List<Placed>> {
        val atEnd = named.flatMap { w -> listOf(w.nodes.first() to w, w.nodes.last() to w) }.groupBy({ it.first }, { it.second })
        val used = mutableSetOf<Long>()
        return named
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
                chain.toList()
            }
    }

    /**
     * [chains] of one group and name joined over [tread]: a chain end at a [dangling] node, where no other way of the name ends, joins the
     * nearest other chain's dangling end it reaches, following the ways between them. Repeats until no more join; a chain never joins
     * itself.
     */
    private fun bridged(chains: List<List<Placed>>, dangling: Set<Long>, tread: SharedTread): List<List<Placed>> {
        val joined = chains.toMutableList()
        while (true) {
            val (i, bridge) = joined.indices.firstNotNullOfOrNull { i -> bridgeFrom(i, joined, dangling, tread)?.let { i to it } } ?: break
            val from = joined[i].let { if (it.end == bridge.placed.start) it else it.turned() }
            val to = joined[bridge.target].let { if (it.start == bridge.placed.end) it else it.turned() }
            joined[i] = from + bridge.placed + to
            joined.removeAt(bridge.target)
        }
        return joined
    }

    /** The nearest join over [tread] from either dangling end of chain [i] of [chains] to another chain's dangling end; null when none. */
    private fun bridgeFrom(i: Int, chains: List<List<Placed>>, dangling: Set<Long>, tread: SharedTread): SharedTread.Bridge? {
        val chain = chains[i]
        if (chain.start == chain.end) return null
        val ends =
            chains.indices
                .filter { it != i }
                .flatMap { j -> listOf(chains[j].start to (j to chains[j].first().way), chains[j].end to (j to chains[j].last().way)) }
                .filter { (node, _) -> node in dangling }
                .toMap()
        val starts = listOf(chain.start to chain.first().way, chain.end to chain.last().way).filter { (node, _) -> node in dangling }
        return starts.mapNotNull { (node, way) -> tread.from(node, way, ends) }.minByOrNull { it.meters }
    }

    /**
     * Every walkable highway way through each node, for the shared-tread search: each (node, way index) pair packed into one long, the node
     * in the high bits, in one sorted array, so a whole state's roads cost a few bytes a node.
     */
    private class WaysAt(val ways: List<OsmWay>) {
        private val keys: LongArray

        init {
            require(ways.size < 1 shl WAY_BITS) { "too many ways to index" }
            val packed = LongArray(ways.sumOf { it.nodes.size })
            var n = 0
            ways.forEachIndexed { i, way ->
                for (node in way.nodes) if (node in 0 until MAX_NODE) packed[n++] = (node shl WAY_BITS) or i.toLong()
            }
            keys = packed.copyOf(n).apply { sort() }
        }

        /** The ways through [node], each once, in id order. */
        fun at(node: Long): List<OsmWay> {
            if (node !in 0 until MAX_NODE) return emptyList()
            val first = node shl WAY_BITS
            var k = keys.binarySearch(first).let { if (it < 0) -it - 1 else it }
            return buildList {
                while (k < keys.size && keys[k] ushr WAY_BITS == node) {
                    val way = ways[(keys[k] and WAY_MASK).toInt()]
                    if (lastOrNull() !== way) add(way)
                    k++
                }
            }
        }

        private companion object {
            const val WAY_BITS = 29
            const val WAY_MASK = (1L shl WAY_BITS) - 1
            const val MAX_NODE = 1L shl (Long.SIZE_BITS - 1 - WAY_BITS)
        }
    }

    /**
     * Shared tread for trails of [group] named [label]: the shortest run of connected ways open to [group], of no way of the trail's own
     * name, from a trail's dangling end to another's. A run of at most [BRIDGE_M] always joins; a longer one of at most [refBridgeM] joins
     * only when each of its ways, and the trail's ways at both ends, carry one ref.
     */
    @Suppress("LongParameterList") // The index, the coordinates, and whose tread it is.
    private class SharedTread(
        private val waysAt: Lazy<WaysAt>,
        private val data: OsmData,
        private val group: String,
        private val label: String,
        private val words: GenericWords,
        private val refBridgeM: Double,
    ) {
        /** A join: the meters followed, the other chain's index, and the ways followed, placed from the start to the other chain. */
        class Bridge(val meters: Double, val target: Int, val placed: List<Placed>)

        private fun walkable(way: OsmWay) = TrailTags.crossable(way.tags, group) && way.name?.let(words::shorten) != label

        /**
         * The shortest join from [start], the end of a chain whose end way is [own], to one of [ends] (each another chain's index and end
         * way): along any walkable ways within [BRIDGE_M], or ways that carry a ref of [own] within [refBridgeM]; the plain one on a tie.
         */
        fun from(start: Long, own: OsmWay, ends: Map<Long, Pair<Int, OsmWay>>): Bridge? {
            val plain = search(start, BRIDGE_M, ::walkable) { it in ends }
            val byRef =
                TrailTags.refs(own.tags)
                    .filter { refBridgeM > BRIDGE_M }
                    .mapNotNull { ref ->
                        search(start, refBridgeM, { walkable(it) && ref in TrailTags.refs(it.tags) }) { node ->
                            ends[node]?.let { (_, way) -> ref in TrailTags.refs(way.tags) } == true
                        }
                    }
                    .minByOrNull { it.first }
            val best = listOfNotNull(plain, byRef).minByOrNull { it.first } ?: return null
            return Bridge(best.first, ends.getValue(best.second).first, best.third)
        }

        /** One step along [way] from its node index [from] at [node] to [to], the next or previous one, at [target], [meters] long. */
        private class Step(val node: Long, val way: OsmWay, val from: Int, val to: Int, val target: Long, val meters: Double)

        /**
         * The shortest run within [maxM] from [start] along ways [open] to a node [isEnd] accepts: its meters, that node, and its ways
         * placed in order. Dijkstra over the ways' nodes, each step one edge of a way.
         */
        private fun search(
            start: Long,
            maxM: Double,
            open: (OsmWay) -> Boolean,
            isEnd: (Long) -> Boolean,
        ): Triple<Double, Long, List<Placed>>? {
            val meters = hashMapOf(start to 0.0)
            val steps = hashMapOf<Long, Step>()
            val queue = PriorityQueue<Pair<Double, Long>>(compareBy { it.first })
            queue += 0.0 to start
            var found: Triple<Double, Long, List<Placed>>? = null
            while (found == null && queue.isNotEmpty()) {
                val (walked, node) = queue.poll()
                when {
                    walked > meters.getValue(node) -> Unit
                    node != start && isEnd(node) -> found = Triple(walked, node, placed(node, steps))
                    else ->
                        for (step in stepsFrom(node, open)) {
                            val total = walked + step.meters
                            if (total <= maxM && total < (meters[step.target] ?: Double.MAX_VALUE)) {
                                meters[step.target] = total
                                steps[step.target] = step
                                queue += total to step.target
                            }
                        }
                }
            }
            return found
        }

        /** Every step from [node] along a way [open] to the next or previous node, where both have coordinates. */
        private fun stepsFrom(node: Long, open: (OsmWay) -> Boolean): List<Step> {
            val here = data.coords[node] ?: return emptyList()
            return waysAt.value.at(node).filter(open).flatMap { way ->
                way.nodes.indices
                    .filter { way.nodes[it] == node }
                    .flatMap { at ->
                        listOf(at - 1, at + 1).mapNotNull { next ->
                            val target = way.nodes.getOrNull(next)
                            target?.let(data.coords::get)?.let {
                                Step(node, way, at, next, target, SegmentGeometry.distanceMeters(here, it))
                            }
                        }
                    }
            }
        }

        /** The steps back from [end] as placed ways, consecutive steps along one way in one direction merged into one slice. */
        private fun placed(end: Long, steps: Map<Long, Step>): List<Placed> {
            val path = generateSequence(steps[end]) { steps[it.node] }.toList().asReversed()
            val runs = mutableListOf<MutableList<Step>>()
            for (step in path) {
                val run =
                    runs.lastOrNull()?.takeIf { run ->
                        val last = run.last()
                        last.way === step.way && step.from == last.to && (step.to - step.from) == (last.to - last.from)
                    }
                if (run != null) run += step else runs += mutableListOf(step)
            }
            return runs.map { run ->
                val (first, last) = run.first().from to run.last().to
                if (first < last) Placed(run[0].way, reversed = false, from = first, to = last, bridge = true)
                else Placed(run[0].way, reversed = true, from = last, to = first, bridge = true)
            }
        }
    }

    /**
     * Each named route relation's way members, in order, joined end to end and reversed as needed. Ways that are [TrailRules.excluded] or
     * missing are left out, except a road of at most [BRIDGE_M] open to the relation's groups, kept where it joins the members on both
     * sides. A member that doesn't join starts a new trail, so a relation with gaps gives one trail per joined stretch. From each end of a
     * stretch, ways of another named trail are dropped while a way of the relation's own name or family is left.
     */
    private fun relationTrails(relations: List<OsmRelation>, ways: Map<Long, OsmWay>, data: OsmData, words: GenericWords): List<Trail> =
        relations.flatMap { relation ->
            val name = relation.name ?: return@flatMap emptyList()
            val groups = TrailTags.relationGroups(relation.tags)
            val members =
                relation.members
                    .filter { it.type == OsmMember.Type.WAY }
                    .mapNotNull { ways[it.ref] }
                    .mapNotNull { way ->
                        when {
                            !way.rules.excluded(way.tags) -> Placed(way, reversed = false)
                            sharedRoad(way, groups, data) -> Placed(way, reversed = false, bridge = true)
                            else -> null
                        }
                    }
            val stretches = mutableListOf<MutableList<Placed>>()
            for (member in members) {
                val current = stretches.lastOrNull()
                val joined = current?.let { join(it, member) } ?: false
                if (!joined) stretches += mutableListOf(member)
            }
            val family = familyOf(words.shorten(name))
            stretches
                .map { trimmed(it, family, words) }
                .filter { it.isNotEmpty() }
                .flatMap { chain -> groups.map { group -> trail(name, words.shorten(name), group, TrailKind.RELATION, chain, data) } }
        }

    /** Whether a relation of [groups] may keep [way] as shared tread: a road at most [BRIDGE_M] long, open to all of them. */
    private fun sharedRoad(way: OsmWay, groups: Set<String>, data: OsmData): Boolean =
        way.rules == TrailTags &&
            TrailTags.isRoad(way.tags) &&
            groups.all { TrailTags.crossable(way.tags, it) } &&
            lengthOf(listOf(Placed(way, reversed = false)), data) <= BRIDGE_M

    /**
     * [stretch] without shared roads at its ends, which join nothing there, and without ways of another named trail at its ends while a way
     * of the relation's [family] is left: a route that runs onto a neighbouring trail for its last few hundred metres.
     */
    private fun trimmed(stretch: MutableList<Placed>, family: String, words: GenericWords): List<Placed> {
        fun own(placed: Placed) = !placed.bridge && placed.way.name?.let { familyOf(words.shorten(it)) } == family
        fun foreign(placed: Placed) =
            !placed.bridge && !own(placed) && placed.way.name != null && placed.way.rules.wayGroups(placed.way.tags).isNotEmpty()
        val chain = ArrayDeque(stretch)
        while (chain.isNotEmpty()) {
            when {
                chain.first().bridge -> chain.removeFirst()
                chain.last().bridge -> chain.removeLast()
                foreign(chain.first()) && chain.drop(1).any(::own) -> chain.removeFirst()
                foreign(chain.last()) && chain.dropLast(1).any(::own) -> chain.removeLast()
                else -> break
            }
        }
        return chain.toList()
    }

    /**
     * Parent trails: OSM ways named `<Parent>` or `<Parent> - <Section>` joined at their ends along each path through them that their ways
     * allow, from a way nothing joins into to one that joins into nothing further, crossing at least two names. Only ends join, so a way
     * meeting the family mid-way is on no path. A path and its reverse are one trail. A family keeps up to [MAX_PARENT_PATHS], or only its
     * longest when it has more, each named after its first section that is on none of the others, or else its first section.
     */
    private fun parentTrails(ways: Collection<OsmWay>, data: OsmData, words: GenericWords): List<Trail> =
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
                    val branch = if (kept.size == 1) null else branchOf(name, path, kept)
                    val trailName = branch?.let { (joiner, section) -> "$name $joiner $section" } ?: name
                    val label =
                        branch?.let { (joiner, section) -> "${words.shorten(name)} ${LABEL_JOINERS[joiner]} ${words.shorten(section)}" }
                            ?: words.shorten(name)
                    val elsewhere = kept.filter { it !== path }.flatMapTo(mutableSetOf()) { other -> other.map { it.way.id } }
                    val own = path.map { it.way.id }.filterTo(mutableSetOf()) { it !in elsewhere }
                    trail(trailName, label, group, TrailKind.PARENT, path, data, family = name, ownWays = own)
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
     * What tells [path] apart among [paths]: `via` its first section that no other path follows, or, when the others follow all its
     * sections, `from` its first, so an entry that joins partway down doesn't read as the whole trail. Null with no sections.
     */
    private fun branchOf(name: String, path: List<Placed>, paths: List<List<Placed>>): Pair<String, String>? {
        val elsewhere = paths.filter { it !== path }.flatMapTo(mutableSetOf()) { other -> other.map { it.way.name } }
        val sections = path.mapNotNull { it.way.name }.filter { it != name }
        val via = sections.firstOrNull { it !in elsewhere }
        return if (via != null) "via" to via.substringAfter(" - ") else sections.firstOrNull()?.let { "from" to it.substringAfter(" - ") }
    }

    private fun lengthOf(path: List<Placed>, data: OsmData): Double =
        path.flatMap { it.nodes }.mapNotNull { data.coords[it] }.zipWithNext().sumOf { (a, b) -> SegmentGeometry.distanceMeters(a, b) }

    /**
     * Adds [member] to the end of [chain] if its way meets the chain's end, turning the chain's only way around first if that's what joins
     * them.
     */
    private fun join(chain: MutableList<Placed>, member: Placed): Boolean {
        val way = member.way
        if (chain.size == 1) {
            val only = chain.single()
            val touchesStart = way.nodes.first() == only.nodes.first() || way.nodes.last() == only.nodes.first()
            val touchesEnd = way.nodes.first() == only.nodes.last() || way.nodes.last() == only.nodes.last()
            if (touchesStart && !touchesEnd) chain[0] = only.copy(reversed = !only.reversed)
        }
        val end = chain.last().nodes.last()
        val placed =
            when (end) {
                way.nodes.first() -> member.copy(reversed = false)
                way.nodes.last() -> member.copy(reversed = true)
                else -> return false
            }
        chain += placed
        return true
    }

    @Suppress("LongParameterList") // A parent trail's family and own ways come along with the line.
    private fun trail(
        name: String,
        label: String,
        group: String,
        kind: TrailKind,
        chain: Collection<Placed>,
        data: OsmData,
        family: String? = null,
        ownWays: Set<Long> = emptySet(),
    ): Trail {
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
            label = label,
            rules = chain.first().way.rules,
            group = group,
            kind = kind,
            nodes = nodes.toLongArray(),
            points = nodes.map { requireNotNull(data.coords[it]) },
            edgeWays = edgeWays.toLongArray(),
            edgeReversed = edgeReversed.toBooleanArray(),
            forward = forward.toBooleanArray(),
            backward = backward.toBooleanArray(),
            family = family,
            ownWays = ownWays,
            wayNames = if (family == null) emptyMap() else chain.associate { it.way.id to it.way.name.orEmpty() },
            bridged = chain.filter { it.bridge }.mapTo(mutableSetOf()) { it.way.id },
        )
    }
}
