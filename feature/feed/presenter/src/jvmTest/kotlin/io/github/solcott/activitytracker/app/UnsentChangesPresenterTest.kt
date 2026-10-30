package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.feed.UnsentChangesEvent
import io.github.solcott.activitytracker.app.feed.UnsentChangesPresenter
import io.github.solcott.activitytracker.app.feed.UnsentChangesScreen
import io.github.solcott.activitytracker.app.feed.UnsentChangesState
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.RejectedChanges
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The changes the server refused (data/outbox): listed, retried once at a time, and discarded after confirming. */
class UnsentChangesPresenterTest {
    private val did = "did:plc:alice"

    /** Whether the PDS accepts puts; it refuses them as invalid records otherwise. */
    @Volatile private var accepting = false

    /** Held until completed, so a test can send an event twice while the first put is in flight. */
    @Volatile private var gate: CompletableDeferred<Unit>? = null

    private val puts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                if (request.url.encodedPath.endsWith("com.atproto.repo.putRecord")) {
                    gate?.await()
                    puts += request.url.encodedPath
                    if (accepting) respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", HttpStatusCode.OK, json)
                    else respond("""{"error":"InvalidRecord"}""", HttpStatusCode.BadRequest, json)
                } else {
                    respond("{}", HttpStatusCode.OK, json)
                }
            }
        )

    private suspend fun AppDatabase.reject(collection: String, rkey: String) {
        val id =
            outbox()
                .insert(OutboxEntity(did = did, operation = "put", collection = collection, rkey = rkey, recordJson = "{}", createdAt = 0))
        outbox().markRejected(id, at = 1, error = "refused")
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator = FakeNavigator(UnsentChangesScreen),
        seed: suspend AppDatabase.() -> Unit,
        block: suspend ReceiveTurbine<UnsentChangesState>.(AppDatabase) -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(http)
        db.seed()
        val likes = LocalLikes()
        val rejected = RejectedChanges(db, clients, OutboxProcessor(db, clients, Logger), FeedRepository(db, clients, likes, Logger), likes)
        presenterTestOf({ UnsentChangesPresenter(navigator, rejected) }, timeout = 10.seconds) { block(db) }
    }

    @Test
    fun listsTheRefusedChanges() = runTest {
        presenterTest(
            seed = {
                reject(Nsid.FEED_ACTIVITY, "3kact")
                reject(Nsid.FEED_LIKE, "3klike")
            }
        ) { _ ->
            val state = awaitUntil { it.changes != null }
            assertEquals(listOf(RejectedChange.Kind.Activity, RejectedChange.Kind.Like), state.changes!!.map { it.kind })
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRetryTheServerAcceptsRemovesTheChangeAndRunsOnce() = runTest {
        accepting = true
        gate = CompletableDeferred()
        presenterTest(seed = { reject(Nsid.FEED_ACTIVITY, "3kact") }) { db ->
            val state = awaitUntil { it.changes?.size == 1 }
            // Two taps on the same item, the second while the first's put waits at the gate.
            state.eventSink(UnsentChangesEvent.Retry("3kact"))
            state.eventSink(UnsentChangesEvent.Retry("3kact"))
            gate!!.complete(Unit)

            awaitUntil { it.changes?.isEmpty() == true }
            db.outbox().count(did).first { it == 0 }
            assertEquals(1, puts.size, "a second tap while the first runs sends nothing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRetryRefusedAgainKeepsTheChange() = runTest {
        presenterTest(seed = { reject(Nsid.FEED_ACTIVITY, "3kact") }) { db ->
            val seeded = awaitUntil { it.changes?.size == 1 }
            seeded.eventSink(UnsentChangesEvent.Retry("3kact"))
            // The retry clears the refusal, so the list empties before the PDS refuses it again: wait for the new refusal itself.
            val refused = awaitUntil { state ->
                state.busy.isEmpty() && state.changes?.singleOrNull()?.let { it.rejectedAt > seeded.changes!!.single().rejectedAt } == true
            }
            assertEquals("3kact", refused.changes!!.single().rkey)
            assertEquals(1, puts.size)
            assertEquals(1, db.outbox().pending(did).size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun discardingAsksFirstThenDropsTheChange() = runTest {
        presenterTest(seed = { reject(Nsid.FEED_ACTIVITY, "3kact") }) { db ->
            awaitUntil { it.changes?.size == 1 }.eventSink(UnsentChangesEvent.Discard("3kact"))
            awaitUntil { it.confirmingDiscard != null }.eventSink(UnsentChangesEvent.CancelDiscard)
            awaitUntil { it.confirmingDiscard == null }.eventSink(UnsentChangesEvent.Discard("3kact"))
            assertEquals(1, db.outbox().pending(did).size, "nothing is dropped before confirming")

            // As the dialog sends it: dismissed first, then confirmed.
            val confirming = awaitUntil { it.confirmingDiscard != null }
            confirming.eventSink(UnsentChangesEvent.CancelDiscard)
            confirming.eventSink(UnsentChangesEvent.ConfirmDiscard("3kact"))
            awaitUntil { it.changes?.isEmpty() == true }
            assertEquals(emptyList(), db.outbox().pending(did))
            assertEquals(emptyList(), puts, "a discarded change is never sent")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backPops() = runTest {
        val navigator = FakeNavigator(UnsentChangesScreen)
        presenterTest(navigator, seed = {}) { _ ->
            awaitUntil { it.changes != null }.eventSink(UnsentChangesEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
