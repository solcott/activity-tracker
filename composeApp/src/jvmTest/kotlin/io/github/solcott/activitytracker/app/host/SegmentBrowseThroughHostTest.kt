package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.presenter.presenterOf
import io.github.solcott.activitytracker.app.SessionTestFixture
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseEvent
import io.github.solcott.activitytracker.app.segment.SegmentBrowsePresenter
import io.github.solcott.activitytracker.app.segment.SegmentBrowseScreen
import io.github.solcott.activitytracker.app.segment.SegmentBrowseState
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.location.FakeLocationTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/**
 * The real segment browse presenter, over the shared session fixture, run by the presenter host as the screen the Search tab's root embeds
 * (dev/presenter-host, segments/segment-browse): what the native Search tab does in Segments mode. What it opens goes on the Search tab.
 */
class SegmentBrowseThroughHostTest {
    @Test
    fun browseRunsInsideTheSearchRecordAndOpensOnTheSearchTab() = runTest {
        val fixture = SessionTestFixture()
        val repos = fixture.repos()
        val segments = SegmentRepository(repos.clients)
        val tracker = FakeLocationTracker()
        val host =
            sessionHostOf(
                session = sessionFor(fixture),
                factories =
                    setOf(
                        Presenter.Factory { screen, navigator, _ ->
                            when (screen) {
                                SegmentBrowseScreen -> presenterOf { SegmentBrowsePresenter(navigator, segments, repos.feed, tracker) }
                                else -> IdlePresenter
                            }
                        },
                    ),
                subFactories = emptySet(),
                steps = OpenSteps,
            )
        backgroundScope.launch { host.run(HostClock.Immediate) }
        host.select(TopLevelDestination.Search)
        val search = host.stack(TopLevelDestination.Search)
        val root = search.records.value.single()
        assertEquals(AthleteSearchScreen, root.screen)

        val browse = root.embed(SegmentBrowseScreen)
        assertEquals("SegmentBrowseScreen", browse.screenName)
        val nowhere = browse.state.filterIsInstance<SegmentBrowseState>().first()
        assertSame(browse, root.embed(SegmentBrowseScreen))

        val segment = AtUri("at://did:plc:bob/app.activitytracker.feed.segment/ridge")
        nowhere.eventSink(SegmentBrowseEvent.Open(segment))
        search.records.first { it.size == 2 }
        assertEquals(SegmentScreen(segment.value), search.records.value.last().screen)
    }

    private object IdleState : CircuitUiState

    private object IdlePresenter : Presenter<IdleState> {
        @Composable override fun present(): IdleState = IdleState
    }

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
