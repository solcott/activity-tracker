package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.FeedFilterParams
import kotlin.time.Instant

/**
 * What the AppView narrows a feed to: activities of any of [types] (a type with no sub-sport matches every sub-sport) that started in
 * [since, until). [None] is the unfiltered feed. Sent as `getTimeline`/`getAuthorActivities` params, and each distinct filter is cached
 * under its own [cacheKey].
 */
data class ActivityFilter(val types: Set<ActivityType> = emptySet(), val since: Instant? = null, val until: Instant? = null) {
    /** The `type` param: `sport` or `sport:subSport`, sorted so equivalent filters send the same request. */
    fun typeTokens(): List<String> =
        types.map { type -> type.subSport?.let { "${type.sport}${FeedFilterParams.TYPE_SEPARATOR}$it" } ?: type.sport }.sorted()

    /**
     * [base] (`timeline`, `author:<did>`) itself when unfiltered, so existing cache rows stay valid; else [base] plus the canonical filter.
     */
    fun cacheKey(base: String): String =
        if (this == None) {
            base
        } else {
            buildString {
                append(base)
                typeTokens().takeIf { it.isNotEmpty() }?.let { append("|type=").append(it.joinToString(",")) }
                since?.let { append("|since=").append(it) }
                until?.let { append("|until=").append(it) }
            }
        }

    companion object {
        val None = ActivityFilter()
    }
}
