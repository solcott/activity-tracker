package io.github.solcott.activitytracker.app.boards

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One option of a board's choice: the token it sends ([value], null for the option that shows everyone) and its [label]. */
@Immutable data class BoardOption(val value: String?, val label: AppText)

/**
 * A choice over a ranked board (segments' leaderboards and Mayors, dive sites' Regulars; dev/shared-display-text): its [options] in the
 * order both UIs offer them, the token [chosen], and whether that choice [narrows] the board, which the UIs mark.
 */
@Immutable
data class BoardChoice(val options: ImmutableList<BoardOption>, val chosen: String?, val narrows: Boolean) {
    /** The chosen option's label, or the chosen token as itself when no option lists it. */
    val label: AppText
        get() = options.firstOrNull { it.value == chosen }?.label ?: AppText.written(chosen.orEmpty())

    /** Whether there is anything to choose: two or more options make a menu, one is shown as its label. */
    val offersMenu: Boolean
        get() = options.size > 1

    fun isChosen(option: BoardOption): Boolean = option.value == chosen

    companion object {
        /** A choice among [values], labelled by [label], with [chosen] marked as narrowing the board when [narrows]. */
        fun of(values: List<String>, chosen: String?, narrows: Boolean = false, label: (String) -> AppText): BoardChoice =
            BoardChoice(values.map { BoardOption(it, label(it)) }.toImmutableList(), chosen, narrows)

        /** A choice of everyone, [all], or one of [values]: it narrows the board while one is [chosen]. */
        fun orAll(all: AppText, values: List<String>, chosen: String?, label: (String) -> AppText): BoardChoice =
            BoardChoice(
                (listOf(BoardOption(null, all)) + values.map { BoardOption(it, label(it)) }).toImmutableList(),
                chosen,
                narrows = chosen != null,
            )
    }
}

/** What every ranked board says (dev/shared-display-text). */
object RankBoardTexts {
    /** Everyone or Following, a [LeaderboardScope] value [chosen]; Following narrows the board. */
    fun scope(chosen: String): BoardChoice =
        BoardChoice.of(LeaderboardScope.all, chosen, narrows = chosen != LeaderboardScope.EVERYONE) {
            AppText.named("leaderboard_scope_$it")
        }

    /** A place on a board: a label, not a quantity, so never grouped (ui/number-format). */
    fun rank(rank: Int): AppText = AppText.written(rank.toString())

    /** "Reached [day]": the day an entry reached its count. */
    fun reached(day: AppText): AppText = AppText.named("mayors_reached", day)
}
