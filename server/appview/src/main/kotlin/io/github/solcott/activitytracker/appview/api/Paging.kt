package io.github.solcott.activitytracker.appview.api

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or

// Cursors and paging every view shares: keysets (`"<sort>::<key>"`, plain or over a `GROUP BY … HAVING` aggregate) and offsets.

/** Rows strictly after a `"<sort>::<uri>"` cursor in (sort, uri) descending order; everything when there is no valid cursor. */
internal fun keysetBefore(sort: Column<Long>, uri: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, uriValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (sort less sortValue) or ((sort eq sortValue) and (uri less uriValue))
}

/**
 * The ascending counterpart of [keysetBefore]: rows after the cursor's (sort, uri). The redundant `sort >= cursor` lets an index on (…,
 * sort) seek to the cursor instead of filtering every earlier row, which Postgres doesn't do for the `OR` alone.
 */
internal fun keysetAfter(sort: Column<Long>, uri: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, uriValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (sort greaterEq sortValue) and ((sort greater sortValue) or ((sort eq sortValue) and (uri greater uriValue)))
}

/** [keysetBefore] for an aggregate in a `GROUP BY … HAVING` keyset, such as each athlete's latest like, broken by [key]. */
internal fun havingBefore(aggregate: ExpressionWithColumnType<Long?>, key: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, keyValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (aggregate less sortValue) or ((aggregate eq sortValue) and (key less keyValue))
}

/** [keysetAfter] for an aggregate in a `GROUP BY … HAVING` keyset, such as each thread's first place, broken by [key]. */
internal fun havingAfter(aggregate: ExpressionWithColumnType<Long?>, key: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, keyValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (aggregate greaterEq sortValue) and ((aggregate greater sortValue) or ((aggregate eq sortValue) and (key greater keyValue)))
}

/** The cursor after a full page of [rows] ([pageSize] of them), from its last row by [cursorFor]; null after a short page, the last. */
internal inline fun <T> nextCursor(rows: List<T>, pageSize: Int, cursorFor: (T) -> String?): String? =
    if (rows.size == pageSize) rows.lastOrNull()?.let(cursorFor) else null

/** The row offset an offset-paged query's [cursor] names: 0 without one, or when it isn't a number. */
internal fun offsetOf(cursor: String?): Int = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0

/** The cursor of the page after [offset] when [read] filled it ([pageSize] rows); null when it was the last. */
internal fun nextOffset(offset: Int, pageSize: Int, read: Int): String? = (offset + pageSize).toString().takeIf { read == pageSize }

/** Splits a `"<sort>::<key>"` cursor; null when it is malformed. */
internal fun parseCursor(cursor: String): SortCursor? {
    val (sortAt, uri) = cursorParts(cursor, 2) ?: return null
    return sortAt.toLongOrNull()?.let { SortCursor(it, uri) }
}

/** A `"<sort>::<key>"` keyset cursor: the [sort] value of the last row served, and its [key] (a URI or DID) breaking ties. */
internal data class SortCursor(val sort: Long, val key: String)

/** A keyset cursor of [parts], each written as a string and joined by `::`, as [cursorParts] reads it back. */
internal fun cursorOf(vararg parts: Any?): String = parts.joinToString(CURSOR_SEPARATOR)

/** A cursor of [parts] joined with `::`, for a cursor whose number of parts varies. */
internal fun cursorOf(parts: List<Any?>): String = parts.joinToString(CURSOR_SEPARATOR)

/** The [count] `::`-separated parts of [cursor], the last taking the rest; null when it has fewer. */
internal fun cursorParts(cursor: String, count: Int): List<String>? =
    cursor.split(CURSOR_SEPARATOR, limit = count).takeIf { it.size == count }

private const val CURSOR_SEPARATOR = "::"
