package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.data.segments.BoardMeta
import io.github.solcott.activitytracker.data.segments.LeaderboardBrackets
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.hasAnswer

/**
 * A board screen's choices (segments/segment-leaderboards, segments/segment-mayors): its period, one of [kind]'s, the scope, the board type
 * asked for and the brackets (segments/leaderboard-brackets). It owns their events ([fields]); each `choose` ignores a value it doesn't
 * know.
 */
@Stable
internal class BoardFilters(private val kind: BoardKind, period: String, board: String?) {
    private val periods: List<String> = if (kind == BoardKind.Mayors) MayorPeriod.all else LeaderboardPeriod.all

    var period by mutableStateOf(period)
        private set

    var scope by mutableStateOf(LeaderboardScope.EVERYONE)
        private set

    /** The board type asked for; the one shown is the AppView's answer ([rememberBoardTypes]). */
    var requested by mutableStateOf(board)
        private set

    var ageGroup by mutableStateOf<String?>(null)
        private set

    var weightClass by mutableStateOf<String?>(null)
        private set

    var gender by mutableStateOf<String?>(null)
        private set

    val brackets: LeaderboardBrackets
        get() = LeaderboardBrackets(ageGroup, weightClass, gender)

    /** Every choice, for keying the board: each is its own list. */
    val key: List<String?>
        get() = listOf(period, scope, requested, ageGroup, weightClass, gender)

    /** The choices as the state shows them, with [types] the board's type control; their events come back here. */
    fun fields(types: BoardTypes): BoardFilterFields =
        BoardFilterFields(kind, period, scope, types.shown, types.listed, ageGroup, weightClass, gender) { onEvent(it, types) }

    private fun onEvent(event: BoardFilterEvent, types: BoardTypes) {
        when (event) {
            is BoardFilterEvent.PeriodChanged -> choosePeriod(event.period)
            is BoardFilterEvent.ScopeChanged -> chooseScope(event.scope)
            is BoardFilterEvent.BoardChanged -> chooseBoard(event.board, types)
            is BoardFilterEvent.AgeGroupChanged -> chooseAgeGroup(event.ageGroup)
            is BoardFilterEvent.WeightClassChanged -> chooseWeightClass(event.weightClass)
            is BoardFilterEvent.GenderChanged -> chooseGender(event.gender)
        }
    }

    fun choosePeriod(value: String) {
        if (value in periods) period = value
    }

    fun chooseScope(value: String) {
        if (value in LeaderboardScope.all) scope = value
    }

    /** Asks for [value] when [types] admits it. */
    fun chooseBoard(value: String, types: BoardTypes) {
        if (types.admits(value)) requested = value
    }

    fun chooseAgeGroup(value: String?) {
        if (value == null || value in LeaderboardAgeGroup.all) ageGroup = value
    }

    fun chooseWeightClass(value: String?) {
        if (value == null || value in LeaderboardWeightClass.all) weightClass = value
    }

    fun chooseGender(value: String?) {
        if (value == null || value in LeaderboardGender.all) gender = value
    }
}

/**
 * [BoardFilters] for a [kind] board starting on its default period, every athlete, the board type [board] and no brackets, saved across
 * recreation.
 */
@Composable
internal fun rememberBoardFilters(kind: BoardKind, board: String?): BoardFilters =
    rememberSaveable(saver = boardFiltersSaver(kind)) { BoardFilters(kind, defaultPeriod(kind), board) }

/** The period a [kind] board opens on. */
private fun defaultPeriod(kind: BoardKind): String = if (kind == BoardKind.Mayors) MayorPeriod.LAST_90_DAYS else LeaderboardPeriod.ALL_TIME

private fun boardFiltersSaver(kind: BoardKind): Saver<BoardFilters, Any> =
    mapSaver(
        save = {
            mapOf(
                "period" to it.period,
                "scope" to it.scope,
                "requested" to it.requested,
                "ageGroup" to it.ageGroup,
                "weightClass" to it.weightClass,
                "gender" to it.gender,
            )
        },
        restore = { saved ->
            BoardFilters(kind, saved["period"] as String, saved["requested"] as String?).apply {
                chooseScope(saved["scope"] as String)
                chooseAgeGroup(saved["ageGroup"] as String?)
                chooseWeightClass(saved["weightClass"] as String?)
                chooseGender(saved["gender"] as String?)
            }
        },
    )

/** The viewer's own entry, from the page meta, when it isn't among the entries loaded; null before the first answer. */
internal fun <E> PagedState<E, BoardMeta<E>>.viewerApart(athleteOf: (E) -> Did): E? =
    meta?.viewer?.takeIf { own -> hasAnswer && items.none { athleteOf(it) == athleteOf(own) } }
