package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import co.touchlab.kermit.Logger
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.profile.BlockListEvent
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListPresenter
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.profile.BlockListState
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.pagedStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Settings' Blocked and Muted accounts lists (`graph/blocks`, `graph/mutes`, ui/content-loading). */
class BlockListPresenterTest {
    private val alice = Did("did:plc:alice")

    /** Each record deleted from the interactions space, as `<collection>/<rkey>`. */
    private val spaceDeletes = mutableListOf<String>()

    /** `getBlocks` pages by the cursor they answer (null for the first); the one page below when empty. */
    private val blockPages = mutableMapOf<String?, String>()

    /** Cursors whose `getBlocks` fails as a dropped connection would. */
    private val failingCursors = mutableSetOf<String?>()

    /** The cursor of each `getBlocks` asked for, in order. */
    private val blockRequests = mutableListOf<String?>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.GRAPH_GET_BLOCKS}" -> {
                        val cursor = request.url.parameters["cursor"]
                        blockRequests += cursor
                        if (cursor in failingCursors) throw IOException("Connection refused")
                        respond(
                            blockPages[cursor]
                                ?: ("""{"blocks":[${block("bob", "\"app\"", 21)},${block("carol", "\"app\",\"bluesky\"", 20)},""" +
                                    """${block("dave", "\"bluesky\"", 19)}]}"""),
                            headers = json,
                        )
                    }
                    "/xrpc/${Nsid.GRAPH_GET_MUTES}" -> respond("""{"mutes":[{"did":"did:plc:bob","handle":"bob.test"}]}""", headers = json)
                    "/.well-known/did.json" ->
                        respond(
                            """{"id":"did:web:appview.test","service":[
                               {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                            headers = json,
                        )
                    "/xrpc/com.atproto.simplespace.createSpace" ->
                        respond("""{"uri":"at://$alice/space/${Nsid.SPACE_ATHLETE}/interactions"}""", headers = json)
                    "/xrpc/com.atproto.space.deleteRecord" -> {
                        val input = AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        val o = input.jsonObject
                        spaceDeletes += "${o.getValue("collection").jsonPrimitive.content}/${o.getValue("rkey").jsonPrimitive.content}"
                        respond("{}", headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    private fun block(name: String, sources: String, day: Int) =
        """{"actor":{"did":"did:plc:$name","handle":"$name.test"},"sources":[$sources],"createdAt":"2026-10-${day}T09:00:00Z"}"""

    /** A `getBlocks` page of app blocks of [names], then [next]. */
    private fun page(vararg names: String, next: String?) =
        """{"blocks":[${names.joinToString(",") { block(it, "\"app\"", 21) }}]${next?.let { ""","cursor":"$it"""" }.orEmpty()}}"""

    private val navigator = FakeNavigator(BlockListScreen(BlockListKind.Blocked))

    /** The repository, for a session with the `space:` scope or, with [scope], only that one. */
    private suspend fun blocks(scope: String? = null): BlockRepository {
        val (db, _, clients) =
            if (scope == null) signedInTestSession(http, did = alice) else signedInTestSession(http, did = alice, scope = scope)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val zones = TestZones(db, clients)
        return BlockRepository(clients, zones.writer, zones.outbox, feed, Clock.System)
    }

    private suspend fun presenter(kind: BlockListKind, scope: String? = null): @Composable () -> BlockListState {
        val blocks = blocks(scope)
        return { BlockListPresenter(BlockListScreen(kind), navigator, blocks) }
    }

    private val BlockListState.dids
        get() = accounts.items.map { it.profile.did.value.substringAfterLast(':') }

    @Test
    fun blueskyOnlyBlocksAreListedButNotRemovable() = runTest {
        presenterTestOf(presenter(BlockListKind.Blocked)) {
            val loaded = awaitUntil { it.accounts.items.size == 3 }
            assertEquals(listOf(true, true, false), loaded.accounts.items.map { it.removable })
            assertEquals(listOf(false, true, true), loaded.accounts.items.map { it.onBluesky })

            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:dave")))
            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:bob")))
            val removed = awaitUntil { it.accounts.items.size == 2 }
            assertEquals(listOf("carol", "dave"), removed.dids)
            assertEquals(listOf("${Nsid.GRAPH_BLOCK}/did:plc:bob"), spaceDeletes, "only bob's app block is deleted")

            removed.eventSink(BlockListEvent.Remove(Did("did:plc:carol")))
            val carol = awaitUntil { s -> s.accounts.items.first().let { it.profile.did.value == "did:plc:carol" && !it.removable } }
            assertEquals(2, carol.accounts.items.size, "carol's Bluesky block still holds")

            carol.eventSink(BlockListEvent.Open(Did("did:plc:dave")))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unmutingRemovesTheAccount() = runTest {
        presenterTestOf(presenter(BlockListKind.Muted)) {
            val loaded = awaitUntil { it.accounts.items.size == 1 }
            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:bob")))
            awaitUntil { it.accounts.items.isEmpty() && it.removing.isEmpty() }
            assertEquals(listOf("${Nsid.GRAPH_MUTE}/did:plc:bob"), spaceDeletes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedFirstLoadSaysWhyAndRetries() = runTest {
        failingCursors += null
        presenterTestOf(presenter(BlockListKind.Blocked)) {
            val failed = awaitUntil { it.accounts.refresh is LoadStatus.Failed }
            assertEquals(DataError.Network, assertIs<LoadStatus.Failed>(failed.accounts.refresh).error)
            assertEquals(emptyList(), failed.accounts.items)

            failingCursors.clear()
            failed.eventSink(BlockListEvent.Retry)
            assertEquals(3, awaitUntil { it.accounts.refresh is LoadStatus.Idle }.accounts.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedPageKeepsTheEarlierPagesAndRetriesOnlyItself() = runTest {
        blockPages[null] = page("bob", next = "p2")
        blockPages["p2"] = page("carol", next = "p3")
        blockPages["p3"] = page("dave", next = null)
        failingCursors += "p3"
        presenterTestOf(presenter(BlockListKind.Blocked)) {
            awaitUntil { it.accounts.canLoadMore }.eventSink(BlockListEvent.LoadMore)
            awaitUntil { it.dids == listOf("bob", "carol") && it.accounts.canLoadMore }.eventSink(BlockListEvent.LoadMore)
            val failed = awaitUntil { it.accounts.append is LoadStatus.Failed }
            assertEquals(listOf("bob", "carol"), failed.dids, "the earlier pages stay")
            assertEquals(LoadStatus.Idle, failed.accounts.refresh)

            failed.eventSink(BlockListEvent.LoadMore)
            failingCursors.clear()
            failed.eventSink(BlockListEvent.Retry)
            val all = awaitUntil { it.dids.size == 3 }
            assertEquals(listOf("bob", "carol", "dave"), all.dids)
            assertEquals(listOf(null, "p2", "p3", "p3"), blockRequests, "only the failed page is asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedUnblockLeavesPagingWorking() = runTest {
        blockPages[null] = page("bob", next = "p2")
        blockPages["p2"] = page("carol", next = null)
        presenterTestOf(presenter(BlockListKind.Blocked, scope = "atproto transition:generic")) {
            val loaded = awaitUntil { it.accounts.canLoadMore }
            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:bob")))
            val failed = awaitUntil { it.actionError != null }
            assertEquals(UiError.Kind.SignInAgain, failed.actionError?.kind, "the session lacks the space scope")
            assertEquals(listOf("bob"), failed.dids, "the account stays listed")

            failed.eventSink(BlockListEvent.LoadMore)
            val more = awaitUntil { it.dids.size == 2 }
            assertNotNull(more.actionError, "it isn't a load error, so loading doesn't clear it")
            assertNull(more.accounts.append as? LoadStatus.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheAccountsAtOnce() = runTest {
        blockPages[null] = page("bob", next = "p2")
        blockPages["p2"] = page("carol", next = null)
        val blocks = blocks()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) BlockListPresenter(BlockListScreen(BlockListKind.Blocked), navigator, blocks)
                else BlockListState(BlockListKind.Blocked, pagedStateOf()) {}
            }
        }) {
            awaitUntil { it.accounts.canLoadMore }.eventSink(BlockListEvent.LoadMore)
            awaitUntil { it.dids.size == 2 && it.accounts.endReached }
            registry.saveAll()
            shown = false
            awaitUntil { it.accounts.items.isEmpty() }
            shown = true
            assertEquals(listOf("bob", "carol"), awaitItem().dids, "no loading state in between")
            assertEquals(listOf(null, "p2"), blockRequests, "nothing is asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
