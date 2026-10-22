package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.LeaderboardBrackets
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasAnswer
import kotlinx.coroutines.flow.flow

@CircuitInject(SegmentLeaderboardScreen::class, SessionScope::class)
@Composable
fun SegmentLeaderboardPresenter(
    screen: SegmentLeaderboardScreen,
    navigator: Navigator,
    segments: SegmentRepository,
): SegmentLeaderboardState {
    val uri = AtUri(screen.uri)
    var period by rememberSaveable { mutableStateOf(LeaderboardPeriod.ALL_TIME) }
    var scope by rememberSaveable { mutableStateOf(LeaderboardScope.EVERYONE) }
    var eBike by rememberSaveable { mutableStateOf(false) }
    var ageGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var weightClass by rememberSaveable { mutableStateOf<String?>(null) }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    // Each board is its own list, so a new filter starts from nothing in the same frame: another board's ranks under its heading would be
    // wrong, not just old. The viewer's own entry is each page's meta, so it changes with the board.
    val entries =
        key(period, scope, eBike, ageGroup, weightClass, gender) {
            val brackets = LeaderboardBrackets(ageGroup, weightClass, gender)
            rememberRetainedPaging(uri, idOf = { entry: LeaderboardEntry -> entry.athlete.did }) { cursor ->
                flow { emit(segments.leaderboardPage(uri, period, scope, eBike, cursor, brackets)) }
            }
        }
    val board = entries.state
    val viewer = board.meta?.takeIf { own -> board.hasAnswer && board.items.none { it.athlete.did == own.athlete.did } }

    return SegmentLeaderboardState(
        name = screen.name,
        period = period,
        scope = scope,
        eBike = eBike,
        canChooseEBike = screen.sport == SegmentSportGroup.RIDE,
        ageGroup = ageGroup,
        weightClass = weightClass,
        gender = gender,
        entries = board,
        viewerEntry = viewer,
    ) { event ->
        when (event) {
            SegmentLeaderboardEvent.Back -> navigator.pop()
            is SegmentLeaderboardEvent.PeriodChanged -> if (event.period in LeaderboardPeriod.all) period = event.period
            is SegmentLeaderboardEvent.ScopeChanged -> if (event.scope in LeaderboardScope.all) scope = event.scope
            is SegmentLeaderboardEvent.EBikeChanged -> eBike = event.eBike && screen.sport == SegmentSportGroup.RIDE
            is SegmentLeaderboardEvent.AgeGroupChanged ->
                if (event.ageGroup == null || event.ageGroup in LeaderboardAgeGroup.all) ageGroup = event.ageGroup
            is SegmentLeaderboardEvent.WeightClassChanged ->
                if (event.weightClass == null || event.weightClass in LeaderboardWeightClass.all) weightClass = event.weightClass
            is SegmentLeaderboardEvent.GenderChanged ->
                if (event.gender == null || event.gender in LeaderboardGender.all) gender = event.gender
            SegmentLeaderboardEvent.LoadMore -> entries.loadMore()
            SegmentLeaderboardEvent.Retry -> entries.retry()
            is SegmentLeaderboardEvent.OpenActivity -> navigator.goTo(ActivityDetailScreen(event.uri.value))
        }
    }
}
