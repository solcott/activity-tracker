package io.github.solcott.activitytracker.appview.api

import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.like

/**
 * The LIKE patterns a name search ranks by, for an already lower-cased query [q], its LIKE metacharacters escaped: names starting with it,
 * names with a word starting with it, and names holding it anywhere. Athlete search and segment name search share them.
 */
internal class NameMatch(q: String) {
    val prefix: LikePattern = LikePattern.ofLiteral(q, ESCAPE) + "%"
    val wordPrefix: LikePattern = LikePattern("% ", ESCAPE) + prefix
    val anywhere: LikePattern = LikePattern("%", ESCAPE) + prefix

    private companion object {
        const val ESCAPE = '\\'
    }
}

/** How well a name matches a name search: the whole name, its start, a word's start, anywhere. Lower is better. */
internal const val NAME_WHOLE = 0
internal const val NAME_PREFIX = 1
internal const val NAME_WORD = 2
internal const val NAME_ANYWHERE = 3

/**
 * How well a lower-cased [name] matches [q] in SQL, as [rankOf] ranks it in Kotlin ([NAME_WHOLE] best). Segment and dive site name searches
 * order by it.
 */
internal fun nameRank(name: ExpressionWithColumnType<String>, q: String, match: NameMatch): Expression<Int> =
    Case()
        .When(name eq q, intLiteral(NAME_WHOLE))
        .When(name like match.prefix, intLiteral(NAME_PREFIX))
        .When(name like match.wordPrefix, intLiteral(NAME_WORD))
        .Else(intLiteral(NAME_ANYWHERE))
