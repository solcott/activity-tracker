package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** [member] supplied a channel of a combined activity from [from] to [to]. */
data class Credit(val member: AtUri, val from: Instant, val to: Instant)

/**
 * Which member each chart of a combined activity came from (`feed/combined-sources`), worked out from what every viewer is served: its
 * stored ranking and its members' spans. A member is credited for the whole of its span while it's the highest-ranked member covering it,
 * so every viewer gets the same answer; a short gap the next member filled isn't told apart.
 */
object ChartAttribution {
    /**
     * Who supplied [channel], in time order, consecutive stretches by one member joined; empty when [combined] doesn't rank it, or it was
     * left out.
     */
    fun of(channel: String, combined: CombinedActivity, members: List<MemberView>): List<Credit> {
        val ranking = combined.sourcesOf(channel).orEmpty()
        val spans = members.associate { it.uri to Span(it.startedAt, it.startedAt + it.elapsedTime.coerceAtLeast(0).seconds) }
        val ranked = ranking.filter { it in spans }
        if (ranked.isEmpty()) return emptyList()
        val bounds = ranked.flatMap { spans.getValue(it).let { span -> listOf(span.start, span.end) } }.distinct().sorted()
        val credits = mutableListOf<Credit>()
        for ((from, to) in bounds.zipWithNext()) {
            val top = ranked.firstOrNull { uri -> spans.getValue(uri).let { it.start <= from && to <= it.end } } ?: continue
            val last = credits.lastOrNull()
            if (last != null && last.member == top && last.to == from) credits[credits.lastIndex] = last.copy(to = to)
            else credits += Credit(top, from, to)
        }
        return credits
    }

    /** The members who supplied [channel], each once, in the order they first did. */
    fun membersOf(channel: String, combined: CombinedActivity, members: List<MemberView>): List<AtUri> =
        of(channel, combined, members).map { it.member }.distinct()
}
