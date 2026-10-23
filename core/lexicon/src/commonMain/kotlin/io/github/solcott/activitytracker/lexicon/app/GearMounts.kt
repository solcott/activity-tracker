package io.github.solcott.activitytracker.lexicon.app

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.time.Instant

/** Why [GearMounts] refuses a mount. */
enum class MountProblem {
    /** The parent isn't another item of the same athlete. */
    FOREIGN_PARENT,

    /** It ends before it starts. */
    EMPTY,

    /** Its window overlaps an earlier mount of the same item. */
    OVERLAP,

    /** Following parents comes back to the item. */
    LOOP,

    /** The item, or one mounted on it, would sit more than [GearMounts.MAX_DEPTH] levels below a top-level item. */
    TOO_DEEP,
}

/**
 * Who is mounted on whom, at any time: [parentsOf] an item and [childrenOf] it. Loops and depth are checked over every window, so a chain
 * moved between bikes counts once for each.
 */
class MountGraph(val parentsOf: (AtUri) -> Collection<AtUri>, val childrenOf: (AtUri) -> Collection<AtUri>) {
    companion object {
        /** The graph of [mounts], each item's own mounts by its URI. */
        fun of(mounts: Map<AtUri, List<GearMount>>): MountGraph {
            val parents = mounts.mapValues { (_, list) -> list.map { it.parent }.toSet() }
            val children =
                buildMap<AtUri, MutableSet<AtUri>> {
                    parents.forEach { (child, ps) -> ps.forEach { getOrPut(it) { mutableSetOf() } += child } }
                }
            return MountGraph({ parents[it].orEmpty() }, { children[it].orEmpty() })
        }
    }
}

/**
 * The rules for [GearItemRecord.mounts]: a parent of the same athlete, windows that don't overlap, no loops, and at most [MAX_DEPTH] levels
 * below a top-level item. The client checks a whole list before saving ([problem]); the AppView drops each invalid mount ([kept]).
 */
object GearMounts {
    /** Mount levels below a top-level item: tires on a wheelset on a bike are two. */
    const val MAX_DEPTH = 3

    /** [mounts] oldest first. */
    fun sorted(mounts: List<GearMount>): List<GearMount> = mounts.sortedBy { it.from }

    /** The mount covering [at], if any. */
    fun at(mounts: List<GearMount>?, at: Instant): GearMount? = mounts?.firstOrNull { it.from <= at && (it.to == null || at < it.to) }

    /** The mount with no end, if any. */
    fun open(mounts: List<GearMount>?): GearMount? = mounts?.firstOrNull { it.to == null }

    /**
     * Each of [component]'s [mounts], oldest first, with the reason it's refused or null; a mount is checked against the earlier ones kept.
     */
    fun review(component: AtUri, mounts: List<GearMount>, graph: MountGraph): List<Pair<GearMount, MountProblem?>> {
        val kept = mutableListOf<GearMount>()
        // The most mount levels below the component: 0 when nothing is mounted on it.
        val height = longest(component, graph.childrenOf)
        return sorted(mounts).map { mount ->
            val problem =
                when {
                    !isSibling(component, mount.parent) -> MountProblem.FOREIGN_PARENT
                    mount.to != null && mount.to <= mount.from -> MountProblem.EMPTY
                    kept.any { overlaps(it, mount) } -> MountProblem.OVERLAP
                    loops(component, mount.parent, graph) -> MountProblem.LOOP
                    longest(mount.parent, graph.parentsOf) + 1 + height > MAX_DEPTH -> MountProblem.TOO_DEEP
                    else -> null
                }
            if (problem == null) kept += mount
            mount to problem
        }
    }

    /** The first reason [component]'s [mounts] are refused, or null when they're all valid. */
    fun problem(component: AtUri, mounts: List<GearMount>, graph: MountGraph): MountProblem? =
        review(component, mounts, graph).firstNotNullOfOrNull { it.second }

    /** The valid [mounts] of [component], oldest first. */
    fun kept(component: AtUri, mounts: List<GearMount>, graph: MountGraph): List<GearMount> =
        review(component, mounts, graph).filter { it.second == null }.map { it.first }

    private fun isSibling(component: AtUri, parent: AtUri) =
        parent != component &&
            parent.authority == component.authority &&
            parent.collection == Nsid.GEAR_ITEM &&
            !parent.rkey.isNullOrEmpty()

    private fun overlaps(a: GearMount, b: GearMount): Boolean = (a.to == null || b.from < a.to) && (b.to == null || a.from < b.to)

    /** Whether following [parent]'s parents reaches [component]. */
    private fun loops(component: AtUri, parent: AtUri, graph: MountGraph): Boolean {
        val seen = mutableSetOf<AtUri>()
        var level = setOf(parent)
        while (level.isNotEmpty()) {
            if (component in level) return true
            seen += level
            level = level.flatMap(graph.parentsOf).filterTo(mutableSetOf()) { it !in seen }
        }
        return false
    }

    /** The most mount levels from [item] following [next]: 0 when there's none. Stops past [MAX_DEPTH], which is too deep either way. */
    private fun longest(item: AtUri, next: (AtUri) -> Collection<AtUri>): Int {
        var depth = 0
        var level = setOf(item)
        while (depth <= MAX_DEPTH) {
            level = level.flatMapTo(mutableSetOf(), next)
            if (level.isEmpty()) return depth
            depth++
        }
        return depth
    }
}
