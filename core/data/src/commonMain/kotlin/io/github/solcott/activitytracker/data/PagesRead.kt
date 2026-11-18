package io.github.solcott.activitytracker.data

import io.github.solcott.uistate.Page

/** What [readPages] read: every page's items in order, and whether it stopped at its page limit with a cursor left unread. */
internal class PagesRead<T>(val items: List<T>, val truncated: Boolean)

/**
 * Follows a list's cursors from its first page ([page] of a null cursor), reading at most [maxPages] pages. It stops at the last page, and
 * also at an empty page or a cursor that doesn't move, since either would page forever; neither counts as [PagesRead.truncated]. For a
 * search whose pages may hold none with a cursor (a browse area), use [readArea].
 */
internal suspend fun <T> readPages(
    maxPages: Int = Int.MAX_VALUE,
    page: suspend (cursor: String?) -> Page<T, Unit>,
): PagesRead<T> {
    val items = mutableListOf<T>()
    var cursor: String? = null
    var pages = 0
    do {
        val read = page(cursor)
        pages++
        items += read.items
        cursor = read.next?.takeIf { read.items.isNotEmpty() && it != cursor }
    } while (cursor != null && pages < maxPages)
    return PagesRead(items, truncated = cursor != null)
}
