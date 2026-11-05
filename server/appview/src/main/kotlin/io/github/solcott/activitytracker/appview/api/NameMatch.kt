package io.github.solcott.activitytracker.appview.api

import org.jetbrains.exposed.v1.core.LikePattern

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
