package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.boards.BoardChoice
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf

/**
 * What a segment's leaderboard and Mayors board offer and say (segments/segment-leaderboards, segments/segment-mayors,
 * segments/leaderboard-brackets, dev/shared-display-text): each choice's options and labels, and an entry's day and count.
 */
object SegmentBoardTexts {
    /** All time, This year or This month, a [LeaderboardPeriod] value [chosen]. */
    fun leaderboardPeriod(chosen: String): BoardChoice =
        BoardChoice.of(LeaderboardPeriod.all, chosen) {
            when (it) {
                LeaderboardPeriod.YEAR -> AppText.named("leaderboard_period_year")
                LeaderboardPeriod.MONTH -> AppText.named("leaderboard_period_month")
                else -> AppText.named("leaderboard_period_all_time")
            }
        }

    /** The Mayors board's window, a [MayorPeriod] value [chosen], among every window. */
    fun mayorPeriod(chosen: String): BoardChoice = BoardChoice.of(MayorPeriod.all, chosen, narrows = true, label = ::mayorPeriodLabel)

    private fun mayorPeriodLabel(period: String): AppText =
        AppText.named(
            when (period) {
                MayorPeriod.ALL_TIME -> "leaderboard_period_all_time"
                MayorPeriod.THIS_YEAR -> "leaderboard_period_year"
                MayorPeriod.LAST_YEAR -> "mayor_period_last_year"
                MayorPeriod.THIS_MONTH -> "leaderboard_period_month"
                MayorPeriod.LAST_MONTH -> "mayor_period_last_month"
                MayorPeriod.THIS_WEEK -> "mayor_period_this_week"
                MayorPeriod.LAST_WEEK -> "mayor_period_last_week"
                else -> "mayor_period_last_90_days"
            },
        )

    /**
     * The board's type [board] among [boards], the types the viewer may choose: a menu when there are two or more, else [board] alone,
     * shown as its label; null while there is no type to show.
     */
    fun type(board: String?, boards: List<String>): BoardChoice? {
        board ?: return null
        return BoardChoice.of(if (boards.size < 2) listOf(board) else boards, board, narrows = true, label = ::boardLabel)
    }

    /** A board type's label: its sport's name, or "E-bike" for [SegmentBoard.E_BIKE]. */
    fun boardLabel(board: String): AppText =
        if (board == SegmentBoard.E_BIKE) AppText.named("leaderboard_board_ebike") else LabelText.sport(board)

    /** The day of a leaderboard entry's effort. */
    fun effortDay(entry: LeaderboardEntry): AppText = DateText.isoDate(entry.effort.startedOn)

    /** A leaderboard entry's time, in the clock form of a stat ("3:50"). */
    fun time(entry: LeaderboardEntry): AppText = AppText.written(StatText.duration(entry.effort.elapsed))

    /** "Reached" the day a Mayors entry reached its count. */
    fun reached(entry: MayorEntry): AppText = RankBoardTexts.reached(DateText.isoDate(entry.reachedOn))

    /** A Mayors entry's count: "14 efforts". */
    fun efforts(entry: MayorEntry): AppText = efforts(entry.efforts)

    /** A count of efforts: "14 efforts", grouped from a thousand (ui/number-format). */
    fun efforts(count: Int): AppText = AppText.Plural("mayor_efforts", count, persistentListOf(AppText.written(formatWhole(count))))
}

/**
 * The age, weight and gender brackets a segment's leaderboard and Mayors board offer (segments/leaderboard-brackets,
 * dev/shared-display-text): each with every athlete first, and the chosen bracket marked as narrowing the board.
 */
object LeaderboardBracketTexts {
    /** Every age, or a [LeaderboardAgeGroup] value [chosen]: `0-19` is "19 and under", `75+` "75 and over". */
    fun ageGroup(chosen: String?): BoardChoice =
        BoardChoice.orAll(AppText.named("leaderboard_age_all"), LeaderboardAgeGroup.all, chosen) { group ->
            val (from, to) = boundsOf(group)
            when {
                to == null -> AppText.named("leaderboard_age_over", whole(from))
                from == 0 -> AppText.named("leaderboard_age_under", whole(to))
                else -> AppText.named("leaderboard_age_range", whole(from), whole(to))
            }
        }

    /**
     * Every weight, or a [LeaderboardWeightClass] value [chosen], in [units]. A class is whole kilograms, so in pounds each bound is its
     * kilogram bound converted and rounded, and a class ends a pound before the next one starts.
     */
    fun weightClass(chosen: String?, units: UnitSystem): BoardChoice {
        val imperial = units == UnitSystem.Imperial
        val unit = if (imperial) "lb" else "kg"
        fun bound(kg: Int): Int =
            if (imperial) (kg * WeightConversions.GRAMS_PER_KG / WeightConversions.GRAMS_PER_POUND).roundToInt() else kg
        return BoardChoice.orAll(AppText.named("leaderboard_weight_all"), LeaderboardWeightClass.all, chosen) { weightClass ->
            val (from, to) = boundsOf(weightClass)
            when {
                to == null -> AppText.named("leaderboard_weight_over_$unit", whole(bound(from)))
                from == 0 -> AppText.named("leaderboard_weight_under_$unit", whole(bound(to + 1)))
                else -> AppText.named("leaderboard_weight_range_$unit", whole(bound(from)), whole(bound(to + 1) - 1))
            }
        }
    }

    /** Every gender, or a [LeaderboardGender] value [chosen]. */
    fun gender(chosen: String?): BoardChoice =
        BoardChoice.orAll(AppText.named("leaderboard_gender_all"), LeaderboardGender.all, chosen) {
            AppText.named(
                when (it) {
                    LeaderboardGender.FEMALE -> "leaderboard_gender_female"
                    LeaderboardGender.MALE -> "leaderboard_gender_male"
                    else -> "leaderboard_gender_non_binary"
                },
            )
        }

    /** A bound of a bracket: a label, never grouped. */
    private fun whole(value: Int): AppText = AppText.written(value.toString())

    /** A bracket token's bounds: `55-64` is 55 to 64, `75+` is 75 with no upper bound. */
    private fun boundsOf(token: String): Bounds =
        if (token.endsWith("+")) Bounds(token.dropLast(1).toInt(), null)
        else token.split('-').let { (from, to) -> Bounds(from.toInt(), to.toInt()) }

    /** A bracket's bounds, [to] null for the last bracket, which has none. */
    private data class Bounds(val from: Int, val to: Int?)
}

/** What the starred segments list says (segments/segment-favorites, dev/shared-display-text). */
object StarredSegmentsTexts {
    /** A starred segment's line: its length in [units] and its type ("1.6 km · Ride", "1.2 km · Ski lift"). */
    fun summary(segment: SegmentView, units: UnitSystem): AppText =
        AppText.named(
            "starred_segments_summary",
            StatText.distance(segment.distanceMeters.toDouble(), units),
            SegmentTexts.type(segment.record.sport, segment.record.kind),
        )
}
