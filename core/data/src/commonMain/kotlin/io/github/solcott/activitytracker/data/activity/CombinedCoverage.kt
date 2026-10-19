package io.github.solcott.activitytracker.data.activity

import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/** When a combined activity's members really recorded ([ActivityCombiner]), and which of those stretches were recorded at the same time. */
internal object CombinedCoverage {
    /**
     * When [member] really recorded: its samples (streams, else track) in stretches with no gap over [CombinedRoute.BREAK_SECONDS]. A
     * device that dies or is paused for a while leaves a gap, though its file's span goes on. A stretch shorter than [MIN_STRETCH_SECONDS]
     * (a dead device saving a few points later) is left out unless it's all there is. A member with no samples covers its whole span.
     */
    fun of(member: CombineMember): List<Span> {
        val times =
            member.streams?.time?.takeIf { it.isNotEmpty() }?.map { member.start + it.seconds }
                ?: member.track?.map { it.time }?.sorted()?.takeIf { it.isNotEmpty() }
                ?: return listOf(Span(member.start, member.end))
        val stretches = mutableListOf(Span(times.first(), times.first()))
        for (t in times.drop(1)) {
            val last = stretches.last()
            if (t - last.end > CombinedRoute.BREAK_SECONDS.seconds) stretches += Span(t, t)
            else stretches[stretches.lastIndex] = Span(last.start, t)
        }
        return stretches.filter { it.length >= MIN_STRETCH_SECONDS.seconds }.ifEmpty { listOf(stretches.maxBy { it.length }) }
    }

    /**
     * [pieces] in groups recorded at the same time, in order: a piece joins the group before it when they overlap by at least half the
     * shorter of the two, and otherwise starts one of its own; pieces with no length join on starts a few minutes apart. Back-to-back
     * groups of the same members (each paused at once, say) are one group, so only a change of devices makes a new leg.
     */
    fun groups(pieces: List<Piece>): List<Group> {
        val groups = mutableListOf<MutableList<Piece>>()
        for (piece in pieces.sortedWith(compareBy<Piece> { it.from }.thenBy { it.member.uri.value })) {
            val group = groups.lastOrNull()
            if (group != null && overlaps(group, piece)) group += piece else groups += mutableListOf(piece)
        }
        val merged = mutableListOf<MutableList<Piece>>()
        for (group in groups) {
            val last = merged.lastOrNull()
            if (last != null && last.map { it.member.uri }.toSet() == group.map { it.member.uri }.toSet()) last += group
            else merged += group
        }
        return merged.map(::Group)
    }

    private fun overlaps(group: List<Piece>, piece: Piece): Boolean {
        val from = group.minOf { it.from }
        val to = group.maxOf { it.to }
        val shorter = minOf(to - from, piece.length)
        if (shorter <= 0) return abs(piece.from - from) <= CLOCK_DRIFT_SECONDS
        return minOf(to, piece.to) - maxOf(from, piece.from) >= shorter / 2.0
    }

    /** Two members with no length are recordings of the same moment when they start this close. */
    private const val CLOCK_DRIFT_SECONDS = 180

    /** A stretch of recording shorter than this, apart from the rest, is a device's leftover rather than part of the activity. */
    const val MIN_STRETCH_SECONDS = 60
}
