package io.github.solcott.activitytracker.data

import io.github.solcott.uistate.Page

/** The most items one browse area holds (segments/segment-browse, dive-sites/site-browse). */
internal const val AREA_CAP = 300

/** The most search requests one area makes, since a page may hold none with a cursor. */
internal const val AREA_MAX_REQUESTS = 10

/** What [readArea] read: every distinct item, in the order first read, and whether the area held more than it read. */
internal class AreaRead<T>(val items: List<T>, val capped: Boolean)

/**
 * Follows a search's cursors from its first page ([page] of a null cursor) until there are none, or until [cap] distinct items or
 * [maxRequests] requests, keeping each item once by [keyOf]. [AreaRead.capped] when a cursor was left unread or more than [cap] were read;
 * the items aren't cut to [cap], since how to cut them (whole segment groups, say) is the caller's.
 */
internal suspend fun <T, K> readArea(
    cap: Int,
    maxRequests: Int,
    keyOf: (T) -> K,
    page: suspend (cursor: String?) -> Page<T, Unit>,
): AreaRead<T> {
    val found = LinkedHashMap<K, T>()
    var cursor: String? = null
    var requests = 0
    do {
        val read = page(cursor)
        requests++
        for (item in read.items) {
            val key = keyOf(item)
            if (key !in found) found[key] = item
        }
        cursor = read.next
    } while (cursor != null && found.size < cap && requests < maxRequests)
    return AreaRead(found.values.toList(), capped = cursor != null || found.size > cap)
}
