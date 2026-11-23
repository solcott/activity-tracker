package io.github.solcott.activitytracker.appview.api

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select

/** One athlete's place on a [CountBoard]: their [rank], [count] and the local date it was [reached]. */
internal data class CountEntry(val rank: Int, val athlete: String, val count: Long, val reached: String)

/** A page of a [CountBoard] in board order, and the cursor for the next page (null on the last). */
internal data class CountPage(val entries: List<CountEntry>, val next: String?)

/**
 * A board of athletes by a count of their rows (segment Mayors, dive-site Regulars): most first, then by the date each count was [reached],
 * earliest first, then by each of [tieBreaks], smallest first, then by athlete. It pages by a `HAVING` keyset on all of those behind a
 * `"<count>::<date>::<tie-breaks…>::<did>"` cursor (a cursor with the wrong number of parts reads from the start). Without [tieBreaks] it
 * gives competition ranks over the whole board, so equal counts reached on one date share a rank and the next skips; with them every
 * entry's rank is its place on the board, never shared (segments/segment-mayors). Each board passes the Op that picks its rows ([page]'s
 * `board`); that Op carries all its visibility and consent rules, which this never adds to or drops.
 */
internal class CountBoard(
    private val rows: () -> ColumnSet,
    private val athlete: Column<String>,
    private val count: ExpressionWithColumnType<Long>,
    private val reached: ExpressionWithColumnType<String?>,
    private val tieBreaks: List<ExpressionWithColumnType<Long?>> = emptyList(),
) {
    /** At most [limit] entries of [board] after [cursor] (from the start when there is none or it is malformed). */
    fun page(board: Op<Boolean>, cursor: String?, limit: Int): CountPage {
        val after = cursor?.let(::parseCursor)?.let { after(it, orEqual = false) } ?: Op.TRUE
        val order =
            listOf(count to SortOrder.DESC, reached to SortOrder.ASC) + tieBreaks.map { it to SortOrder.ASC } + (athlete to SortOrder.ASC)
        val read = order.fold(counts(board, after)) { query, (key, direction) -> query.orderBy(key, direction) }.limit(limit).toList()
        val rows = read.mapNotNull(::row)
        // Judged on the rows read, not those kept: a dropped empty group mustn't end the board early.
        val next = rows.takeIf { read.size == limit }?.lastOrNull()?.let(::cursorAfter)
        return CountPage(ranked(board, rows), next)
    }

    /** [did]'s entry on [board], ranked over the whole board; null when they have none. */
    fun entryOf(board: Op<Boolean>, did: String): CountEntry? =
        counts(board and (athlete eq did), Op.TRUE).firstOrNull()?.let(::row)?.let { ranked(board, listOf(it)).single() }

    /** Each athlete's count, date and tie-breaks on [board], filtered by [having]. */
    private fun counts(board: Op<Boolean>, having: Op<Boolean>): Query =
        rows().select(listOf(athlete, count, reached) + tieBreaks).where { board }.groupBy(athlete).having { having }

    /** A [counts] row as its place on the board; null for an empty group. */
    private fun row(row: ResultRow): Place? =
        row[reached]?.let { reached -> Place(row[athlete], row[count], reached, tieBreaks.map { row[it] ?: 0L }) }

    /**
     * The athletes after [place] on the board, and [place]'s equals too when [orEqual]: a keyset over count, date and tie-breaks, then the
     * athlete when [withAthlete] (without it, athletes equal on every other key count as equal).
     */
    private fun after(place: Place, orEqual: Boolean, withAthlete: Boolean = true): Op<Boolean> {
        // Each term: every earlier key equal, this one past the place's.
        var equal: Op<Boolean> = this.count eq place.count
        var past: Op<Boolean> = (this.count less place.count) or (equal and (reached greater place.reached))
        equal = equal and (reached eq place.reached)
        tieBreaks.zip(place.tieBreaks).forEach { (key, value) ->
            past = past or (equal and (key greater value))
            equal = equal and (key eq value)
        }
        if (withAthlete) {
            past = past or (equal and (athlete greater place.athlete))
            equal = equal and (athlete eq place.athlete)
        }
        return if (orEqual) past or equal else past
    }

    /** The athletes strictly before [place] on the board: those [after] it never are, nor it. */
    private fun before(place: Place, withAthlete: Boolean): Op<Boolean> = not(after(place, orEqual = true, withAthlete))

    /** [page] (in board order) with ranks counted over the whole [board]. */
    private fun ranked(board: Op<Boolean>, page: List<Place>): List<CountEntry> {
        val shared = tieBreaks.isEmpty()
        val ranks =
            ranksOf(
                page,
                ahead = { counts(board, before(it, withAthlete = !shared)).count() },
                before = { counts(board, before(it, withAthlete = true)).count() },
                tied = { a, b -> shared && a.count == b.count && a.reached == b.reached },
            )
        return page.mapIndexed { i, place -> CountEntry(ranks[i], place.athlete, place.count, place.reached) }
    }

    /** Splits a cursor into a place; null when it is malformed. */
    private fun parseCursor(cursor: String): Place? =
        cursorParts(cursor, FIXED_PARTS + tieBreaks.size)?.let { parts ->
            val count = parts.first().toLongOrNull()
            val keys = parts.subList(2, 2 + tieBreaks.size).mapNotNull(String::toLongOrNull)
            if (count == null || keys.size != tieBreaks.size) null else Place(parts.last(), count, parts[1], keys)
        }

    /** The cursor after [place]. */
    private fun cursorAfter(place: Place): String = cursorOf(listOf(place.count, place.reached) + place.tieBreaks + place.athlete)

    /** One athlete's place: their [count], the date it was [reached], and the values of the board's [tieBreaks]. */
    private data class Place(val athlete: String, val count: Long, val reached: String, val tieBreaks: List<Long>)

    private companion object {
        /** A cursor's parts besides its tie-breaks: count, date and athlete. */
        const val FIXED_PARTS = 3
    }
}

/**
 * Competition ranks for [page], in board order: the first entry's is one more than the athletes [ahead] of it, and each later one shares
 * the rank of the entry before when [tied] with it, else takes its place on the board, counted from the athletes listed [before] the first
 * (ties with the first entry may sit on earlier pages).
 */
internal fun <T> ranksOf(page: List<T>, ahead: (T) -> Long, before: (T) -> Long, tied: (T, T) -> Boolean): IntArray {
    val ranks = IntArray(page.size)
    if (page.isEmpty()) return ranks
    val position = before(page.first())
    page.forEachIndexed { i, entry ->
        ranks[i] =
            when {
                i == 0 -> ahead(entry).toInt() + 1
                tied(entry, page[i - 1]) -> ranks[i - 1]
                else -> position.toInt() + i + 1
            }
    }
    return ranks
}
