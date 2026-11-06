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
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select

/** One athlete's place on a [CountBoard]: their [rank], [count] and the local date it was [reached]. */
internal data class CountEntry(val rank: Int, val athlete: String, val count: Long, val reached: String)

/** A page of a [CountBoard] in board order, and the cursor for the next page (null on the last). */
internal data class CountPage(val entries: List<CountEntry>, val next: String?)

/**
 * A board of athletes by a count of their rows (segment Mayors, dive-site Regulars): most first, then by the date each count was [reached],
 * earliest first, then by athlete. It pages by a `HAVING` keyset on (count, date, athlete) behind a `"<count>::<date>::<did>"` cursor and
 * ranks over the whole board, so equal counts reached on one date share a rank and the next skips. Each board passes the Op that picks its
 * rows ([page]'s `board`); that Op carries all its visibility and consent rules, which this never adds to or drops.
 */
internal class CountBoard(
    private val rows: () -> ColumnSet,
    private val athlete: Column<String>,
    private val count: ExpressionWithColumnType<Long>,
    private val reached: ExpressionWithColumnType<String?>,
) {
    /** At most [limit] entries of [board] after [cursor] (from the start when there is none or it is malformed). */
    fun page(board: Op<Boolean>, cursor: String?, limit: Int): CountPage {
        val after =
            cursor?.let(::parseCursor)?.let { (count, reached, did) ->
                (this.count less count) or
                    ((this.count eq count) and (this.reached greater reached)) or
                    ((this.count eq count) and (this.reached eq reached) and (athlete greater did))
            } ?: Op.TRUE
        val rows =
            counts(board, after)
                .orderBy(count to SortOrder.DESC, reached to SortOrder.ASC, athlete to SortOrder.ASC)
                .limit(limit)
                .mapNotNull(::row)
        val next = rows.takeIf { it.size == limit }?.last()?.let { (did, count, reached) -> cursorOf(count, reached, did) }
        return CountPage(ranked(board, rows), next)
    }

    /** [did]'s entry on [board], ranked over the whole board; null when they have none. */
    fun entryOf(board: Op<Boolean>, did: String): CountEntry? =
        counts(board and (athlete eq did), Op.TRUE).firstOrNull()?.let(::row)?.let { ranked(board, listOf(it)).single() }

    /** Each athlete's count and date on [board], filtered by [having]. */
    private fun counts(board: Op<Boolean>, having: Op<Boolean>): Query =
        rows().select(athlete, count, reached).where { board }.groupBy(athlete).having { having }

    /** A [counts] row as athlete, count and the date it was reached; null for an empty group. */
    private fun row(row: ResultRow): Triple<String, Long, String>? = row[reached]?.let { Triple(row[athlete], row[count], it) }

    /** [page] (in board order) with ranks counted over the whole [board]. */
    private fun ranked(board: Op<Boolean>, page: List<Triple<String, Long, String>>): List<CountEntry> {
        val ranks =
            ranksOf(
                page,
                ahead = { (_, count, reached) ->
                    counts(board, (this.count greater count) or ((this.count eq count) and (this.reached less reached))).count()
                },
                before = { (did, count, reached) ->
                    counts(
                            board,
                            (this.count greater count) or
                                ((this.count eq count) and (this.reached less reached)) or
                                ((this.count eq count) and (this.reached eq reached) and (athlete less did)),
                        )
                        .count()
                },
                tied = { a, b -> a.second == b.second && a.third == b.third },
            )
        return page.mapIndexed { i, (did, count, reached) -> CountEntry(ranks[i], did, count, reached) }
    }

    private companion object {
        const val CURSOR_PARTS = 3

        /** Splits a `"<count>::<date>::<did>"` cursor; null when it is malformed. */
        fun parseCursor(cursor: String): Triple<Long, String, String>? {
            val (count, reached, did) = cursorParts(cursor, CURSOR_PARTS) ?: return null
            return count.toLongOrNull()?.let { Triple(it, reached, did) }
        }
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
