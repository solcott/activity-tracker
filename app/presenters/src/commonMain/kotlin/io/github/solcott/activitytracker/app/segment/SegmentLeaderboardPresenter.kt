package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.CursorPage
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.rememberCursorPaging
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup

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
    // The viewer's own entry comes with each first page, for the filters it was loaded with.
    var viewerEntry by remember { mutableStateOf<LeaderboardEntry?>(null) }
    val board = Board(period, scope, eBike)
    val entries =
        rememberCursorPaging(board, idOf = { entry: LeaderboardEntry -> entry.athlete.did }) { cursor ->
            val page = segments.leaderboard(uri, board.period, board.scope, board.eBike, cursor)
            if (cursor == null) viewerEntry = page.viewer
            CursorPage(page.entries, page.cursor)
        }
    val viewer = viewerEntry?.takeIf { own -> entries.loaded && entries.items.none { it.athlete.did == own.athlete.did } }

    return SegmentLeaderboardState(
        name = screen.name,
        period = period,
        scope = scope,
        eBike = eBike,
        canChooseEBike = screen.sport == SegmentSportGroup.RIDE,
        entries = entries.items,
        viewerEntry = viewer,
        isLoading = entries.isLoading,
        canLoadMore = entries.canLoadMore,
        error = entries.failure?.let { UiError.of(UiError.Kind.LoadLeaderboard, it) },
    ) { event ->
        when (event) {
            SegmentLeaderboardEvent.Back -> navigator.pop()
            is SegmentLeaderboardEvent.PeriodChanged -> if (event.period in LeaderboardPeriod.all) period = event.period
            is SegmentLeaderboardEvent.ScopeChanged -> if (event.scope in LeaderboardScope.all) scope = event.scope
            is SegmentLeaderboardEvent.EBikeChanged -> eBike = event.eBike && screen.sport == SegmentSportGroup.RIDE
            SegmentLeaderboardEvent.LoadMore -> entries.loadMore()
            SegmentLeaderboardEvent.Retry -> entries.retry()
            is SegmentLeaderboardEvent.OpenActivity -> navigator.goTo(ActivityDetailScreen(event.uri.value))
        }
    }
}

/** The filters a board is loaded with; a change reloads it from the first page. */
private data class Board(val period: String, val scope: String, val eBike: Boolean)
