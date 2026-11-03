package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow

private val PERIODS = LeaderboardPeriod.all.toImmutableList()

@CircuitInject(SegmentLeaderboardScreen::class, SessionScope::class)
@Composable
fun SegmentLeaderboardPresenter(
    screen: SegmentLeaderboardScreen,
    navigator: Navigator,
    segments: SegmentRepository,
): SegmentLeaderboardState {
    val uri = AtUri(screen.uri)
    val filters = rememberBoardFilters(PERIODS, LeaderboardPeriod.ALL_TIME, screen.board)
    // Each board is its own list, so a new filter starts from nothing in the same frame: another board's ranks under its heading would be
    // wrong, not just old. The viewer's own entry is each page's meta, so it changes with the board.
    val entries =
        key(filters.key) {
            rememberRetainedPaging(uri, idOf = { entry: LeaderboardEntry -> entry.athlete.did }) { cursor ->
                flow { emit(segments.leaderboardPage(uri, filters.period, filters.scope, filters.requested, cursor, filters.brackets)) }
            }
        }
    val board = entries.state
    val types = rememberBoardTypes(board.meta, filters.requested)

    return SegmentLeaderboardState(
        name = screen.name,
        period = filters.period,
        scope = filters.scope,
        board = types.shown,
        boards = types.listed,
        ageGroup = filters.ageGroup,
        weightClass = filters.weightClass,
        gender = filters.gender,
        entries = board,
        viewerEntry = board.viewerApart { it.athlete.did },
    ) { event ->
        when (event) {
            SegmentLeaderboardEvent.Back -> navigator.pop()
            is SegmentLeaderboardEvent.PeriodChanged -> filters.choosePeriod(event.period)
            is SegmentLeaderboardEvent.ScopeChanged -> filters.chooseScope(event.scope)
            is SegmentLeaderboardEvent.BoardChanged -> filters.chooseBoard(event.board, types)
            is SegmentLeaderboardEvent.AgeGroupChanged -> filters.chooseAgeGroup(event.ageGroup)
            is SegmentLeaderboardEvent.WeightClassChanged -> filters.chooseWeightClass(event.weightClass)
            is SegmentLeaderboardEvent.GenderChanged -> filters.chooseGender(event.gender)
            SegmentLeaderboardEvent.LoadMore -> entries.loadMore()
            SegmentLeaderboardEvent.Retry -> entries.retry()
            is SegmentLeaderboardEvent.OpenActivity -> navigator.goTo(ActivityDetailScreen(event.uri.value))
        }
    }
}
