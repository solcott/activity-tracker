package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.subcircuit.SubPresenterFactory
import io.github.solcott.activitytracker.app.SessionTestFixture
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterEvent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterOuterEvent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterPresenter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterState
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * The real feed filter bar, over the shared session fixture, run by the presenter host as the sub-screen of the feed's record
 * (dev/presenter-host): what a native feed would do. The parent is a stand-in that keeps what the bar reports, as the feed's presenter
 * does, and the outer events are mapped as `FeedUi` maps them.
 */
class FeedFilterThroughHostTest {
    @Test
    fun theFilterBarRunsAsTheFeedsSubScreenAndReportsItsFilter() = runTest {
        val fixture = SessionTestFixture()
        val repos = fixture.repos()
        val host =
            sessionHostOf(
                session = sessionFor(fixture),
                factories = setOf(Presenter.Factory { screen, _, _ -> if (screen == FeedScreen) ParentPresenter else IdlePresenter }),
                subFactories =
                    setOf(
                        SubPresenterFactory { screen -> (screen as? FeedFilterScreen)?.let { FeedFilterPresenter(it, repos.recentTypes) } },
                    ),
                steps = OpenSteps,
            )
        backgroundScope.launch { host.run(HostClock.Immediate) }
        val feed = host.stack(TopLevelDestination.Feed).records.value.single()
        val parent = feed.state.filterIsInstance<ParentState>().first()

        val bar = feed.sub(parent.filterScreen) { event -> if (event is FeedFilterOuterEvent.Changed) parent.eventSink(event.filter) }
        val first = bar.state.filterIsInstance<FeedFilterState>().first()
        assertEquals(FeedFilter.Default, first.filter)
        assertEquals("FeedFilterScreen", bar.screenName)

        first.eventSink(FeedFilterEvent.SetRange(DateRange.LastDays(30)))
        bar.state.filterIsInstance<FeedFilterState>().first { it.filter.range == DateRange.LastDays(30) }
        feed.state.filterIsInstance<ParentState>().first { it.filter.range == DateRange.LastDays(30) }

        first.eventSink(FeedFilterEvent.OpenTypes)
        val picking = bar.state.filterIsInstance<FeedFilterState>().first { it.typePicker != null }
        picking.eventSink(FeedFilterEvent.ToggleType(ActivityType(Sport.RIDE)))
        bar.state
            .filterIsInstance<FeedFilterState>()
            .first { it.typePicker?.draft?.isNotEmpty() == true }
            .eventSink(FeedFilterEvent.ApplyTypes)

        val narrowed = feed.state.filterIsInstance<ParentState>().first { it.filter.types.isNotEmpty() }
        assertEquals(FeedFilter(persistentSetOf(ActivityType(Sport.RIDE)), DateRange.LastDays(30)), narrowed.filter)
        assertEquals(null, bar.state.filterIsInstance<FeedFilterState>().first { it.typePicker == null }.typePicker)
    }

    private data class ParentState(val filterScreen: FeedFilterScreen, val filter: FeedFilter, val eventSink: (FeedFilter) -> Unit) :
        CircuitUiState

    /** Keeps the filter the bar reports and hands the bar a screen made once, as the feed's presenter does. */
    private object ParentPresenter : Presenter<ParentState> {
        @Composable
        override fun present(): ParentState {
            var filter by rememberRetained { mutableStateOf(FeedFilter.Default) }
            val screen = rememberRetained { FeedFilterScreen(FeedFilter.Default) }
            return ParentState(screen, filter) { filter = it }
        }
    }

    /** The other tabs' first screens, which this test never shows. */
    private object IdleState : CircuitUiState

    private object IdlePresenter : Presenter<IdleState> {
        @Composable override fun present(): IdleState = IdleState
    }

    /** A session that opens at once and does no background work. */
    private object OpenSteps : SessionSteps {
        override val leaving: Flow<Boolean> = flowOf(false)
        override val left = MutableStateFlow(false)

        override suspend fun resolveSpacesSupport() = Unit

        override val units = MutableStateFlow(UnitSystem.Metric)

        override suspend fun refreshUnits() = Unit

        override suspend fun checkStillHere() = Unit

        override suspend fun runWork() = awaitCancellation()
    }

    private fun sessionFor(fixture: SessionTestFixture): OAuthSession =
        OAuthSession(
            did = fixture.did,
            handle = Handle("alice.test"),
            pdsUrl = "https://pds.test",
            issuer = "https://pds.test",
            tokenEndpoint = "https://pds.test/oauth/token",
            accessToken = "token",
            refreshToken = null,
            expiresAt = null,
            scope = "atproto",
            dpopKey = DpopKey.Serialized(privateKeyDer = "", publicKeyRaw = ""),
        )
}
