package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRegularEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import io.github.solcott.uistate.hasAnswer
import kotlinx.coroutines.flow.flow

@CircuitInject(DiveSiteRegularsScreen::class, SessionScope::class)
@Composable
fun DiveSiteRegularsPresenter(screen: DiveSiteRegularsScreen, navigator: Navigator, sites: DiveSiteRepository): DiveSiteRegularsState {
    val uri = AtUri(screen.uri)
    var period by rememberRetained { mutableStateOf(RegularsPeriod.ALL_TIME) }
    var scope by rememberRetained { mutableStateOf(LeaderboardScope.EVERYONE) }
    // Each choice is its own list, as on the Mayors board: a new one starts from nothing in the same frame.
    val entries =
        key(period, scope) {
            rememberRetainedPaging(uri, idOf = { entry: DiveSiteRegularEntry -> entry.athlete.did }) { cursor ->
                flow { emit(sites.regularsPage(uri, period, scope, cursor)) }
            }
        }
    val board = entries.state
    val viewer = board.meta?.viewer?.takeIf { own -> board.hasAnswer && board.items.none { it.athlete.did == own.athlete.did } }

    return DiveSiteRegularsState(screen.name, period, scope, board, viewer) { event ->
        when (event) {
            DiveSiteRegularsEvent.Back -> navigator.pop()
            is DiveSiteRegularsEvent.PeriodChanged -> if (event.period in RegularsPeriod.all) period = event.period
            is DiveSiteRegularsEvent.ScopeChanged -> if (event.scope in LeaderboardScope.all) scope = event.scope
            DiveSiteRegularsEvent.LoadMore -> entries.loadMore()
            DiveSiteRegularsEvent.Retry -> entries.retry()
            is DiveSiteRegularsEvent.OpenAthlete -> navigator.goTo(ProfileScreen(event.did.value))
        }
    }
}
