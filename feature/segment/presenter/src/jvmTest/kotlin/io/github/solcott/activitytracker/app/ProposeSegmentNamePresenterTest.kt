package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import co.touchlab.kermit.Logger
import com.slack.circuit.subcircuit.test.test
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameEvent
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameOuterEvent
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNamePresenter
import io.github.solcott.activitytracker.app.segment.ProposeSegmentNameScreen
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.segments.SegmentNames
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** The name sheet (segments/segment-names): proposing, editing one's own proposal, and the names it won't send. */
class ProposeSegmentNamePresenterTest {
    private val alice = Did("did:plc:alice")
    private val segment = "at://did:plc:importer/${Nsid.FEED_SEGMENT}/osm-1"
    private val proposal = "at://$alice/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kali"
    /** The bodies of the records the outbox put. */
    private val puts = mutableListOf<String>()
    private var gate = CompletableDeferred(Unit)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        puts += (request.body as TextContent).text
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyput"}""", headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun presenter(screen: ProposeSegmentNameScreen): ProposeSegmentNamePresenter {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val names = SegmentNames(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        return ProposeSegmentNamePresenter(screen, names)
    }

    @Test
    fun proposingSendsTheTrimmedNameOnceAndCloses() = runTest {
        gate = CompletableDeferred()
        presenter(ProposeSegmentNameScreen(segment, "Trail 214")).test {
            var state = awaitItem()
            assertFalse(state.editing)
            assertFalse(state.canSend, "no name yet")
            state.name.setTextAndPlaceCursorAtEnd("  Wiggly Woods ")
            assertTrue(state.canSend)
            state.eventSink(ProposeSegmentNameEvent.Send)
            state.eventSink(ProposeSegmentNameEvent.Send)
            state = awaitItem()
            assertTrue(state.sending)
            gate.complete(Unit)
            assertEquals(ProposeSegmentNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(1, puts.size)
            assertTrue(puts.single().contains(Nsid.FEED_SEGMENT_NAME_PROPOSAL))
            assertTrue(puts.single().contains("\"name\":\"Wiggly Woods\""))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingStartsFromTheProposalsNameAndRewritesItsRkey() = runTest {
        presenter(ProposeSegmentNameScreen(segment, "Trail 214", proposal, "Rooty Ridge")).test {
            val state = awaitItem()
            assertTrue(state.editing)
            assertEquals("Rooty Ridge", state.name.text.toString())
            assertFalse(state.canSend, "an unchanged name isn't sent")
            state.name.setTextAndPlaceCursorAtEnd("Rooty Ridge Loop")
            state.eventSink(ProposeSegmentNameEvent.Send)
            assertEquals(ProposeSegmentNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertTrue(puts.single().contains("\"rkey\":\"3kali\""))
            assertTrue(puts.single().contains("Rooty Ridge Loop"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankOverLongAndCurrentNamesArentSent() = runTest {
        presenter(ProposeSegmentNameScreen(segment, "Trail 214")).test {
            val state = awaitItem()
            for (name in listOf("   ", "x".repeat(101), " trail 214")) {
                state.name.setTextAndPlaceCursorAtEnd(name)
                assertFalse(state.valid, name)
                state.eventSink(ProposeSegmentNameEvent.Send)
            }
            state.eventSink(ProposeSegmentNameEvent.Cancel)
            assertEquals(ProposeSegmentNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(emptyList(), puts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
