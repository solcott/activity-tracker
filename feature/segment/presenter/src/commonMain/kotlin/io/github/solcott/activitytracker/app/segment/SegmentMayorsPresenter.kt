package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flow

private val PERIODS = MayorPeriod.all.toImmutableList()

@CircuitInject(SegmentMayorsScreen::class, SessionScope::class)
@Composable
fun SegmentMayorsPresenter(screen: SegmentMayorsScreen, navigator: Navigator, segments: SegmentRepository): SegmentMayorsState {
    val uri = AtUri(screen.uri)
    val filters = rememberBoardFilters(PERIODS, MayorPeriod.LAST_90_DAYS, screen.board)
    // Each board is its own list, as on the leaderboard: a new choice starts from nothing in the same frame, and the viewer's own entry,
    // each page's meta, changes with it.
    val entries =
        key(filters.key) {
            rememberRetainedPaging(uri, idOf = { entry: MayorEntry -> entry.athlete.did }) { cursor ->
                flow { emit(segments.mayorsPage(uri, filters.period, filters.scope, filters.requested, cursor, filters.brackets)) }
            }
        }
    val board = entries.state
    val types = rememberBoardTypes(board.meta, filters.requested)

    return SegmentMayorsState(
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
            SegmentMayorsEvent.Back -> navigator.pop()
            is SegmentMayorsEvent.PeriodChanged -> filters.choosePeriod(event.period)
            is SegmentMayorsEvent.ScopeChanged -> filters.chooseScope(event.scope)
            is SegmentMayorsEvent.BoardChanged -> filters.chooseBoard(event.board, types)
            is SegmentMayorsEvent.AgeGroupChanged -> filters.chooseAgeGroup(event.ageGroup)
            is SegmentMayorsEvent.WeightClassChanged -> filters.chooseWeightClass(event.weightClass)
            is SegmentMayorsEvent.GenderChanged -> filters.chooseGender(event.gender)
            SegmentMayorsEvent.LoadMore -> entries.loadMore()
            SegmentMayorsEvent.Retry -> entries.retry()
            is SegmentMayorsEvent.OpenAthlete -> navigator.goTo(ProfileScreen(event.did.value))
        }
    }
}
