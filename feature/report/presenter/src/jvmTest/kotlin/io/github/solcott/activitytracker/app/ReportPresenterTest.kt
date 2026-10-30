package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.subcircuit.test.SubCircuitReceiveTurbine
import com.slack.circuit.subcircuit.test.test
import io.github.solcott.activitytracker.app.report.ReportEvent
import io.github.solcott.activitytracker.app.report.ReportOuterEvent
import io.github.solcott.activitytracker.app.report.ReportPresenter
import io.github.solcott.activitytracker.app.report.ReportScreen
import io.github.solcott.activitytracker.app.report.ReportState
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.moderation.ReportReason
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** The report sheet (`moderation/reports`): reason, note, send once, thanks, the block offer, and a failure that keeps the input. */
class ReportPresenterTest {
    private val bob = Did("did:plc:bob")
    private val comment = ReportTarget.Comment(AtUri.of(bob, Nsid.FEED_COMMENT, "3c"), Cid("bafycom"), bob)

    private val sent = mutableListOf<String>()
    private var gate = CompletableDeferred(Unit)
    private var fail = false

    private val http =
        HttpClient(
            MockEngine { request ->
                if (request.url.encodedPath != "/xrpc/com.atproto.moderation.createReport") {
                    return@MockEngine respond("", HttpStatusCode.NotFound)
                }
                sent += (request.body as TextContent).text
                gate.await()
                if (fail) {
                    respond("""{"error":"UpstreamFailure"}""", HttpStatusCode.BadGateway, json)
                } else {
                    respond(
                        """{"id":1,"reasonType":"x","reportedBy":"did:plc:alice","createdAt":"2026-10-20T10:00:00Z",
                          "subject":{"${'$'}type":"com.atproto.admin.defs#repoRef","did":"$bob"}}""",
                        headers = json,
                    )
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun presenter(offerBlock: Boolean = true): ReportPresenter {
        val clients = signedInTestSession(http, labelerDid = Did("did:plc:labeler")).clients
        return ReportPresenter(ReportScreen(comment, "Bob", offerBlock), ReportRepository(clients))
    }

    private suspend fun SubCircuitReceiveTurbine<ReportOuterEvent, ReportState>.send(state: ReportState, event: ReportEvent): ReportState {
        state.eventSink(event)
        return awaitItem()
    }

    @Test
    fun harassmentSendsOnceForADoubleTapThenThanksAndOffersBlock() = runTest {
        gate = CompletableDeferred()
        presenter().test {
            var state = awaitItem()
            assertFalse(state.canSend, "no reason yet")
            state = send(state, ReportEvent.PickReason(ReportReason.HARASSMENT))
            assertTrue(state.canSend)
            state.eventSink(ReportEvent.Send)
            state.eventSink(ReportEvent.Send)
            state = awaitItem()
            assertTrue(state.sending)
            gate.complete(Unit)
            while (!state.sent) state = awaitItem()
            assertEquals(1, sent.size)
            assertTrue(sent.single().contains("com.atproto.moderation.defs#reasonRude"))
            assertTrue(state.offerBlock)
            state.eventSink(ReportEvent.Block)
            assertEquals(ReportOuterEvent.Block(bob), outerEvents.awaitEvent())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun otherWithoutANoteCantBeSent() = runTest {
        presenter().test {
            var state = send(awaitItem(), ReportEvent.PickReason(ReportReason.OTHER))
            assertFalse(state.canSend)
            state.eventSink(ReportEvent.Send)
            // The note is the state's own TextFieldState, read live: no new state for typing.
            state.note.setTextAndPlaceCursorAtEnd("Stolen route")
            assertTrue(sent.isEmpty())
            assertTrue(state.canSend)
            state.eventSink(ReportEvent.Send)
            while (!state.sent) state = awaitItem()
            assertTrue(sent.single().contains("Stolen route"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailureKeepsTheReasonAndNote() = runTest {
        fail = true
        presenter().test {
            var state = send(awaitItem(), ReportEvent.PickReason(ReportReason.SPAM))
            state.note.setTextAndPlaceCursorAtEnd("ads")
            state.eventSink(ReportEvent.Send)
            while (state.error == null) state = awaitItem()
            assertEquals(UiError.Kind.SendReport, state.error?.kind)
            assertFalse(state.sent)
            assertEquals(ReportReason.SPAM, state.reason)
            assertEquals("ads", state.note.text.toString())
            outerEvents.assertNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noBlockOfferWhenAlreadyBlocking() = runTest {
        presenter(offerBlock = false).test {
            var state = send(awaitItem(), ReportEvent.PickReason(ReportReason.SPAM))
            state.eventSink(ReportEvent.Send)
            while (!state.sent) state = awaitItem()
            assertFalse(state.offerBlock)
            state.eventSink(ReportEvent.Block)
            state.eventSink(ReportEvent.Close)
            assertEquals(ReportOuterEvent.Closed, outerEvents.awaitEvent())
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val json = headersOf("Content-Type", "application/json")
    }
}
