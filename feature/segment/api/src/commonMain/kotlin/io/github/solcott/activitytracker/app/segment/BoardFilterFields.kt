package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.boards.BoardChoice
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Which of a segment's boards the filters are for: its periods are a [Leaderboard]'s or the [Mayors] board's windows. */
enum class BoardKind {
    Leaderboard,
    Mayors,
}

/**
 * A segment board's choices (segments/segment-leaderboards, segments/segment-mayors, segments/leaderboard-brackets), on the leaderboard and
 * the Mayors board alike. The holder that owns it (`rememberBoardFilters`) fills [eventSink], so the host declares no filter events of its
 * own.
 */
@Immutable
data class BoardFilterFields(
    val kind: BoardKind = BoardKind.Leaderboard,
    /** A [LeaderboardPeriod] value on the leaderboard, a [MayorPeriod] value (the window the board counts) on the Mayors board. */
    val period: String = if (kind == BoardKind.Mayors) MayorPeriod.LAST_90_DAYS else LeaderboardPeriod.ALL_TIME,
    /** A [LeaderboardScope] value. */
    val scope: String = LeaderboardScope.EVERYONE,
    /** The board type shown (a [SegmentBoard] value), as the AppView served it; null until it answers, or when the segment has none. */
    val board: String? = null,
    /** The types the viewer may choose, most efforts first; a menu when there are two or more. */
    val boards: ImmutableList<String> = persistentListOf(),
    /** A [LeaderboardAgeGroup] value: only athletes in it (segments/leaderboard-brackets); null for every age. */
    val ageGroup: String? = null,
    /** A [LeaderboardWeightClass] value, in kilograms; null for every weight. */
    val weightClass: String? = null,
    /** A [LeaderboardGender] value; null for every gender. */
    val gender: String? = null,
    val eventSink: (BoardFilterEvent) -> Unit = {},
) {
    val periodChoice: BoardChoice
        get() = if (kind == BoardKind.Mayors) SegmentBoardTexts.mayorPeriod(period) else SegmentBoardTexts.leaderboardPeriod(period)

    /** The choice of board type, or null while there is none to show; a label when the segment lists one type. */
    val typeChoice: BoardChoice?
        get() = SegmentBoardTexts.type(board, boards)

    val scopeChoice: BoardChoice
        get() = RankBoardTexts.scope(scope)

    val ageGroupChoice: BoardChoice
        get() = LeaderboardBracketTexts.ageGroup(ageGroup)

    val genderChoice: BoardChoice
        get() = LeaderboardBracketTexts.gender(gender)

    /** The weight classes in [units], the viewer's. */
    fun weightClassChoice(units: UnitSystem): BoardChoice = LeaderboardBracketTexts.weightClass(weightClass, units)
}

/** A choice on a segment board; each shows the board it picks from its first page, and a value the board doesn't know changes nothing. */
sealed interface BoardFilterEvent {
    /** Shows the board for [period], one of the board's periods ([BoardFilterFields.kind]). */
    data class PeriodChanged(val period: String) : BoardFilterEvent

    /** Shows the board for [scope], a [LeaderboardScope] value. */
    data class ScopeChanged(val scope: String) : BoardFilterEvent

    /** Shows the board of [board], a listed [SegmentBoard] type; ignored for the type already shown. */
    data class BoardChanged(val board: String) : BoardFilterEvent

    /** Shows only athletes in [ageGroup], a [LeaderboardAgeGroup] value, or every age when null. */
    data class AgeGroupChanged(val ageGroup: String?) : BoardFilterEvent

    /** Shows only athletes in [weightClass], a [LeaderboardWeightClass] value, or every weight when null. */
    data class WeightClassChanged(val weightClass: String?) : BoardFilterEvent

    /** Shows only athletes of [gender], a [LeaderboardGender] value, or every gender when null. */
    data class GenderChanged(val gender: String?) : BoardFilterEvent
}
