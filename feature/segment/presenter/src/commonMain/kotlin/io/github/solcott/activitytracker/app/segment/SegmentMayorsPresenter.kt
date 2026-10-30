package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.LeaderboardBrackets
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasAnswer
import kotlinx.coroutines.flow.flow

@CircuitInject(SegmentMayorsScreen::class, SessionScope::class)
@Composable
fun SegmentMayorsPresenter(screen: SegmentMayorsScreen, navigator: Navigator, segments: SegmentRepository): SegmentMayorsState {
    val uri = AtUri(screen.uri)
    var period by rememberSaveable { mutableStateOf(MayorPeriod.LAST_90_DAYS) }
    var scope by rememberSaveable { mutableStateOf(LeaderboardScope.EVERYONE) }
    var requested by rememberSaveable { mutableStateOf(screen.board) }
    var ageGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var weightClass by rememberSaveable { mutableStateOf<String?>(null) }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    // Each board is its own list, as on the leaderboard: a new choice starts from nothing in the same frame, and the viewer's own entry,
    // each page's meta, changes with it.
    val entries =
        key(period, scope, requested, ageGroup, weightClass, gender) {
            val brackets = LeaderboardBrackets(ageGroup, weightClass, gender)
            rememberRetainedPaging(uri, idOf = { entry: MayorEntry -> entry.athlete.did }) { cursor ->
                flow { emit(segments.mayorsPage(uri, period, scope, requested, cursor, brackets)) }
            }
        }
    val board = entries.state
    val types = rememberBoardTypes(board.meta, requested)
    val viewer = board.meta?.viewer?.takeIf { own -> board.hasAnswer && board.items.none { it.athlete.did == own.athlete.did } }

    return SegmentMayorsState(
        name = screen.name,
        period = period,
        scope = scope,
        board = types.shown,
        boards = types.listed,
        ageGroup = ageGroup,
        weightClass = weightClass,
        gender = gender,
        entries = board,
        viewerEntry = viewer,
    ) { event ->
        when (event) {
            SegmentMayorsEvent.Back -> navigator.pop()
            is SegmentMayorsEvent.PeriodChanged -> if (event.period in MayorPeriod.all) period = event.period
            is SegmentMayorsEvent.ScopeChanged -> if (event.scope in LeaderboardScope.all) scope = event.scope
            is SegmentMayorsEvent.BoardChanged -> if (types.admits(event.board)) requested = event.board
            is SegmentMayorsEvent.AgeGroupChanged ->
                if (event.ageGroup == null || event.ageGroup in LeaderboardAgeGroup.all) ageGroup = event.ageGroup
            is SegmentMayorsEvent.WeightClassChanged ->
                if (event.weightClass == null || event.weightClass in LeaderboardWeightClass.all) weightClass = event.weightClass
            is SegmentMayorsEvent.GenderChanged -> if (event.gender == null || event.gender in LeaderboardGender.all) gender = event.gender
            SegmentMayorsEvent.LoadMore -> entries.loadMore()
            SegmentMayorsEvent.Retry -> entries.retry()
            is SegmentMayorsEvent.OpenAthlete -> navigator.goTo(ProfileScreen(event.did.value))
        }
    }
}
