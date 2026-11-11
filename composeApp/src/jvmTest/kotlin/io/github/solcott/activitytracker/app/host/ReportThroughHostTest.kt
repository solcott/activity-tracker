package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.subcircuit.SubCircuitOuterEvent
import com.slack.circuit.subcircuit.SubPresenterFactory
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.report.BlockOffer
import io.github.solcott.activitytracker.app.report.ReportEvent
import io.github.solcott.activitytracker.app.report.ReportOuterEvent
import io.github.solcott.activitytracker.app.report.ReportPresenter
import io.github.solcott.activitytracker.app.report.ReportScreen
import io.github.solcott.activitytracker.app.report.ReportState
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.app.report.rememberReporting
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The real report sheet, over a mock labeler, run by the presenter host as the sub-screen of a record carrying `Reporting`
 * (dev/presenter-host, "The report sheet runs through the host"): what the native profile's sheet does. The parent is a stand-in holding
 * `rememberReporting` as the profile's presenter does, and the outer events are mapped as `ReportingHost` (and the native modifier) maps
 * them.
 */
class ReportThroughHostTest {
    private val bob = Did("did:plc:bob")
    private val sent = mutableListOf<String>()
    private val blocked = mutableListOf<Did>()
    private var fail = false

    private val http =
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath != "/xrpc/com.atproto.moderation.createReport") {
                    return@MockEngine respond("", HttpStatusCode.NotFound)
                }
                sent += (request.body as TextContent).text
                if (fail) {
                    respond("""{"error":"UpstreamFailure"}""", HttpStatusCode.BadGateway, json)
                } else {
                    respond(
                        """{"id":1,"reasonType":"x","reportedBy":"did:plc:alice","createdAt":"2026-10-20T10:00:00Z",
                          "subject":{"${'$'}type":"com.atproto.admin.defs#repoRef","did":"$bob"}}""",
                        headers = json,
                    )
                }
            },
        ) {
            expectSuccess = false
        }

    @Test
    fun aReportIsSentOnceThroughTheHostAndItsBlockOfferReachesTheCarrier() = runTest {
        val record = carrier()
        record.open()
        val parent = record.parentWith { it.sheet != null }
        val sheet = record.sub(checkNotNull(parent.reporting.sheet), outerSink(record))

        var state = sheet.state.filterIsInstance<ReportState>().first()
        assertEquals("Bob", state.authorName)
        state.eventSink(ReportEvent.PickReason(ReportReason.HARASSMENT))
        state = sheet.state.filterIsInstance<ReportState>().first { it.canSend }
        state.eventSink(ReportEvent.Send)
        state.eventSink(ReportEvent.Send)
        state = sheet.state.filterIsInstance<ReportState>().first { it.sent }
        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("com.atproto.moderation.defs#reasonRude"))
        assertTrue(state.offerBlock)

        // Block closes the carrier's sheet and asks its confirmation, naming the author; yes blocks them.
        state.eventSink(ReportEvent.Block)
        val confirming = record.parentWith { it.confirmBlock != null }
        assertNull(confirming.reporting.sheet)
        assertEquals(BlockOffer(bob, "Bob"), confirming.reporting.confirmBlock)
        confirming.reporting.eventSink(ReportingEvent.ConfirmBlock(true))
        record.parentWith { it.confirmBlock == null }
        assertEquals(listOf(bob), blocked)
    }

    @Test
    fun aClosedSheetReopensAfresh() = runTest {
        val record = carrier()
        record.open()
        val screen = checkNotNull(record.parentWith { it.sheet != null }.reporting.sheet)
        val first = record.sub(screen, outerSink(record))
        first.state.filterIsInstance<ReportState>().first().eventSink(ReportEvent.PickReason(ReportReason.SPAM))
        first.state.filterIsInstance<ReportState>().first { it.reason == ReportReason.SPAM }.eventSink(ReportEvent.Close)
        record.parentWith { it.sheet == null }
        // The native sheet closes its sub-record once dismissed; the next, equal screen starts with no reason.
        first.close()

        record.open()
        val again = checkNotNull(record.parentWith { it.sheet != null }.reporting.sheet)
        assertEquals(screen, again)
        val second = record.sub(again, outerSink(record))
        assertNotSame(first, second)
        assertNull(second.state.filterIsInstance<ReportState>().first().reason)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aFailureKeepsTheReasonAndNote() = runTest {
        fail = true
        val record = carrier()
        record.open()
        val sheet = record.sub(checkNotNull(record.parentWith { it.sheet != null }.reporting.sheet), outerSink(record))
        val first = sheet.state.filterIsInstance<ReportState>().first()
        first.eventSink(ReportEvent.PickReason(ReportReason.SPAM))
        first.note.setTextAndPlaceCursorAtEnd("ads")
        sheet.state.filterIsInstance<ReportState>().first { it.canSend }.eventSink(ReportEvent.Send)
        val failed = sheet.state.filterIsInstance<ReportState>().first { it.error != null }
        assertEquals(UiError.Kind.SendReport, failed.error?.kind)
        assertEquals(ReportReason.SPAM, failed.reason)
        assertEquals("ads", failed.note.text.toString())
        assertTrue(record.parentWith { true }.reporting.sheet != null, "the sheet stays open for a retry")
    }

    private suspend fun TestScope.carrier(): HostRecord {
        val reports = ReportRepository(signedInTestSession(http, labelerDid = Did("did:plc:labeler")).clients)
        val parent = ParentPresenter(reports) { blocked += it }
        val host =
            sessionHostOf(
                session = session,
                factories = setOf(Presenter.Factory { screen, _, _ -> if (screen == FeedScreen) parent else IdlePresenter }),
                subFactories = setOf(SubPresenterFactory { screen -> (screen as? ReportScreen)?.let { ReportPresenter(it, reports) } }),
                steps = OpenSteps,
            )
        backgroundScope.launch { host.run(HostClock.Immediate) }
        return host.stack(TopLevelDestination.Feed).records.value.single()
    }

    /** Report on bob's account, as the profile's menu opens it. */
    private suspend fun HostRecord.open() = parentWith {
        it.available
    }
        .reporting
        .eventSink(ReportingEvent.Open(ReportTarget.Account(bob), "Bob", offerBlock = true))

    private suspend fun HostRecord.parentWith(predicate: (Reporting) -> Boolean): ParentState =
        state.filterIsInstance<ParentState>().first { predicate(it.reporting) }

    /** Hands the sheet's outer events to the carrier's newest `Reporting`, as `ReportingHost` does. */
    private fun outerSink(record: HostRecord): (SubCircuitOuterEvent) -> Unit = { event ->
        val parent = record.state.value as ParentState
        parent.reporting.eventSink(ReportingEvent.Sheet(event as ReportOuterEvent))
    }

    private data class ParentState(val reporting: Reporting) : CircuitUiState

    /** Carries `Reporting` as the profile's presenter does, blocking through [onBlock]. */
    private class ParentPresenter(private val reports: ReportRepository, private val onBlock: (Did) -> Unit) : Presenter<ParentState> {
        @Composable override fun present(): ParentState = ParentState(rememberReporting(reports) { author, _ -> onBlock(author) })
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

    private val session =
        OAuthSession(
            did = Did("did:plc:alice"),
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

    private companion object {
        val json = headersOf("Content-Type", "application/json")
    }
}
