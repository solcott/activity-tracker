package io.github.solcott.activitytracker.app.names

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import co.touchlab.kermit.Logger
import com.slack.circuit.subcircuit.test.test
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
class ProposePlaceNamePresenterTest {
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
                    // Nothing is shared yet: the post is found neither in the repo nor in a space.
                    "/xrpc/com.atproto.repo.getRecord",
                    "/xrpc/com.atproto.space.getRecord" ->
                        respond("""{"error":"RecordNotFound","message":"none"}""", HttpStatusCode.BadRequest, json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    /** The next state the sheet emits that [predicate] matches, skipping any in between. */
    private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.nextWhere(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend fun presenter(screen: ProposePlaceNameScreen): ProposePlaceNamePresenter {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val names = SegmentNames(clients, OutboxWriter(db, clients, Clock.System), OutboxProcessor(db, clients, Logger), Clock.System)
        return ProposePlaceNamePresenter(screen, names)
    }

    @Test
    fun proposingSendsTheTrimmedNameOnceAndCloses() = runTest {
        gate = CompletableDeferred()
        presenter(ProposePlaceNameScreen(segment, "Trail 214")).test {
            var state = awaitItem()
            assertFalse(state.editing)
            assertFalse(state.canSend, "no name yet")
            state.name.setTextAndPlaceCursorAtEnd("  Wiggly Woods ")
            assertTrue(state.canSend)
            state.eventSink(ProposePlaceNameEvent.Send)
            state.eventSink(ProposePlaceNameEvent.Send)
            state = awaitItem()
            assertTrue(state.sending)
            gate.complete(Unit)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(1, puts.size)
            assertTrue(puts.single().contains(Nsid.FEED_SEGMENT_NAME_PROPOSAL))
            assertTrue(puts.single().contains("\"name\":\"Wiggly Woods\""))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun editingStartsFromTheProposalsNameAndRewritesItsRkey() = runTest {
        presenter(ProposePlaceNameScreen(segment, "Trail 214", proposal, "Rooty Ridge")).test {
            // Nothing is sent until the proposal's sharing has been read.
            val state = nextWhere { !it.shareLoading }
            assertTrue(state.editing)
            assertEquals("Rooty Ridge", state.name.text.toString())
            assertFalse(state.canSend, "an unchanged name isn't sent")
            state.name.setTextAndPlaceCursorAtEnd("Rooty Ridge Loop")
            state.eventSink(ProposePlaceNameEvent.Send)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertTrue(puts.single().contains("\"rkey\":\"3kali\""))
            assertTrue(puts.single().contains("Rooty Ridge Loop"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankOverLongAndCurrentNamesArentSent() = runTest {
        presenter(ProposePlaceNameScreen(segment, "Trail 214")).test {
            val state = awaitItem()
            for (name in listOf("   ", "x".repeat(101), " trail 214")) {
                state.name.setTextAndPlaceCursorAtEnd(name)
                assertFalse(state.valid, name)
                state.eventSink(ProposePlaceNameEvent.Send)
            }
            state.eventSink(ProposePlaceNameEvent.Cancel)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(emptyList(), puts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---------------------------------------------------------------- Share to feed (feed/posts)

    @Test
    fun sharingPutsAPostAfterTheProposal() = runTest {
        presenter(ProposePlaceNameScreen(segment, "Trail 214")).test {
            val state = awaitItem()
            assertFalse(state.sharing, "off by default")
            state.name.setTextAndPlaceCursorAtEnd("Wiggly Woods")
            state.shareText.setTextAndPlaceCursorAtEnd(" Everyone calls it that ")
            state.eventSink(ProposePlaceNameEvent.SharingChanged(true))
            val sharing = nextWhere { it.sharing }
            sharing.eventSink(ProposePlaceNameEvent.Send)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(2, puts.size)
            assertTrue(puts[0].contains(Nsid.FEED_SEGMENT_NAME_PROPOSAL))
            assertTrue(puts[1].contains("\"collection\":\"${Nsid.FEED_POST}\""))
            assertTrue(puts[1].contains("\"text\":\"Everyone calls it that\""))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anEditThatOnlySharesKeepsTheProposal() = runTest {
        presenter(ProposePlaceNameScreen(segment, "Trail 214", proposal, "Rooty Ridge")).test {
            val loaded = nextWhere { !it.shareLoading }
            assertFalse(loaded.initialSharing)
            assertFalse(loaded.canSend)
            loaded.eventSink(ProposePlaceNameEvent.SharingChanged(true))
            val sharing = nextWhere { it.sharing }
            assertTrue(sharing.canSend, "a change of sharing alone may be saved")
            sharing.eventSink(ProposePlaceNameEvent.Send)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(1, puts.size, "the proposal isn't rewritten, so it keeps its votes")
            assertTrue(puts.single().contains("\"rkey\":\"3kali\""))
            assertTrue(puts.single().contains(Nsid.FEED_POST))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun overLongShareTextIsntSent() = runTest {
        presenter(ProposePlaceNameScreen(segment, "Trail 214")).test {
            val state = awaitItem()
            state.name.setTextAndPlaceCursorAtEnd("Wiggly Woods")
            state.shareText.setTextAndPlaceCursorAtEnd("x".repeat(1001))
            state.eventSink(ProposePlaceNameEvent.SharingChanged(true))
            val sharing = nextWhere { it.sharing }
            assertFalse(sharing.canSend)
            sharing.eventSink(ProposePlaceNameEvent.Send)
            sharing.eventSink(ProposePlaceNameEvent.Cancel)
            assertEquals(ProposePlaceNameOuterEvent.Closed, outerEvents.awaitEvent())
            assertEquals(emptyList(), puts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
