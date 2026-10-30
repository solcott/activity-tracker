package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.app.EffortView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Efforts on one activity whose time spans overlap (segments/segment-efforts), each listed above the efforts nested under it. */
@Immutable data class SegmentEffortGroup(val rows: ImmutableList<SegmentEffortRow>)

/**
 * One effort in a [SegmentEffortGroup].
 *
 * @property label the segment's name, without the trail name it shares with the effort it's nested under.
 * @property depth how many efforts it's nested under; 0 at the top of its group.
 * @property guides for each depth from 1 to [depth] - 1, whether the vertical guide line of the ancestor at that depth continues past this
 *   row to a later sibling of that ancestor's child.
 * @property lastChild it's the last effort nested directly under its parent; false at depth 0.
 * @property spanStart where its span starts within its group's span, from 0 to 1; null in a group of one.
 * @property spanEnd where its span ends within its group's span, from 0 to 1; null in a group of one.
 */
@Immutable
data class SegmentEffortRow(
    val effort: EffortView,
    val label: String,
    val depth: Int = 0,
    val guides: ImmutableList<Boolean> = persistentListOf(),
    val lastChild: Boolean = false,
    val spanStart: Float? = null,
    val spanEnd: Float? = null,
)

/** Seconds an effort's ends may differ by and still count as the same moment: each is rounded to whole seconds. */
private const val TOLERANCE_S = 2L

/**
 * The share of the shorter of two efforts they must overlap by to be grouped. Segments meeting at a junction overlap by a few seconds, as
 * each is timed from its own start line to its own end line; a share of the shorter one tells that apart from riding the same stretch.
 */
private const val GROUP_OVERLAP_SHARE = 0.25

private val TRAIL_NAME_ENDS = listOf(": ", " (", " › ", " → ", " via ", " from ")
private val NESTED_NAME_SEPARATORS = listOf(": ", " - ")

/**
 * [efforts] grouped by overlapping time spans, in the order the groups began: two efforts are in one group when they overlap by more than
 * [TOLERANCE_S] and by more than [GROUP_OVERLAP_SHARE] of the shorter one, or are each in one group with a third. Within a group each
 * effort is nested under the shortest longer effort containing it (to within [TOLERANCE_S] at each end), and listed depth first: each
 * effort above those nested under it, siblings in the order they began, the longer first when they began together.
 */
fun groupEfforts(efforts: List<EffortView>): ImmutableList<SegmentEffortGroup> {
    val spans = efforts.mapIndexed { index, effort -> Span(effort, index) }.sortedWith(spanOrder)
    // Union-find over every pair: an activity has a few dozen efforts at most.
    val root = IntArray(spans.size) { it }
    fun find(i: Int): Int = if (root[i] == i) i else find(root[i]).also { root[i] = it }
    for (i in spans.indices) for (j in i + 1 until spans.size) if (spans[i].groupsWith(spans[j])) root[find(j)] = find(i)
    // Grouped in sorted order, so the groups and each one's spans come out in the order they began.
    return spans.indices.groupBy(::find).values.map { members -> group(members.map(spans::get)) }.toImmutableList()
}

private class Span(val effort: EffortView, val index: Int) {
    val start = effort.startOffset
    val end = effort.startOffset + effort.elapsed
    val length = end - start

    fun groupsWith(other: Span): Boolean {
        val overlap = minOf(end, other.end) - maxOf(start, other.start)
        return overlap > TOLERANCE_S && overlap > GROUP_OVERLAP_SHARE * minOf(length, other.length)
    }

    fun contains(other: Span) = length > other.length + TOLERANCE_S && other.start >= start - TOLERANCE_S && other.end <= end + TOLERANCE_S
}

private val spanOrder = compareBy<Span> { it.start }.thenByDescending { it.end }.thenBy { it.index }

private fun group(spans: List<Span>): SegmentEffortGroup {
    val parents = spans.associateWith { span -> spans.filter { it.contains(span) }.minByOrNull { it.length } }
    val children = spans.groupBy { parents[it] }.mapValues { (_, kids) -> kids.sortedWith(spanOrder) }
    val groupStart = spans.minOf { it.start }
    val groupLength = (spans.maxOf { it.end } - groupStart).coerceAtLeast(1)
    val rows = mutableListOf<SegmentEffortRow>()

    fun visit(span: Span, parent: Span?, guides: List<Boolean>, last: Boolean) {
        val shown = spans.size > 1
        rows +=
            SegmentEffortRow(
                effort = span.effort,
                label = parent?.let { nestedLabel(span.effort.segment.name, it.effort.segment.name) } ?: span.effort.segment.name,
                depth = guides.size + if (parent == null) 0 else 1,
                guides = guides.toImmutableList(),
                lastChild = parent != null && last,
                spanStart = if (shown) (span.start - groupStart).toFloat() / groupLength else null,
                spanEnd = if (shown) ((span.end - groupStart).toFloat() / groupLength).coerceAtMost(1f) else null,
            )
        val kids = children[span].orEmpty()
        // A child's own guides: its ancestors' (for depths above its parent's), plus whether its parent's line continues past it.
        val kidGuides = if (parent == null) guides else guides + !last
        kids.forEachIndexed { i, kid -> visit(kid, span, kidGuides, i == kids.lastIndex) }
    }

    children[null].orEmpty().forEach { visit(it, null, emptyList(), last = false) }
    return SegmentEffortGroup(rows.toImmutableList())
}

/** [name] without the trail name of [parentName] ahead of ": " or " - ", when it starts with them and more follows; else [name]. */
private fun nestedLabel(name: String, parentName: String): String {
    val trailEnd = TRAIL_NAME_ENDS.mapNotNull { parentName.indexOf(it).takeIf { i -> i > 0 } }.minOrNull() ?: parentName.length
    val trail = parentName.substring(0, trailEnd)
    return NESTED_NAME_SEPARATORS.firstNotNullOfOrNull { sep -> name.removePrefix(trail + sep).takeIf { it != name && it.isNotBlank() } }
        ?: name
}
