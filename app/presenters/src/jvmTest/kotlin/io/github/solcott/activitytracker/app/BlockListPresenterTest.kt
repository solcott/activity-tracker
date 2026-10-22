package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Settings' Blocked and Muted accounts lists (`graph/blocks`, `graph/mutes`). */
class BlockListPresenterTest {
    private val alice = Did("did:plc:alice")

    /** Each record deleted from the interactions space, as `<collection>/<rkey>`. */
    private val spaceDeletes = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.GRAPH_GET_BLOCKS}" ->
                        respond(
                            """{"blocks":[${block("bob", "\"app\"", 21)},${block("carol", "\"app\",\"bluesky\"", 20)},""" +
                                """${block("dave", "\"bluesky\"", 19)}]}""",
                            headers = json,
                        )
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

    private val navigator = FakeNavigator(BlockListScreen(BlockListKind.Blocked))

    private suspend fun presenter(kind: BlockListKind): @Composable () -> BlockListState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val zones = TestZones(db, clients)
        val blocks = BlockRepository(clients, zones.writer, zones.outbox, feed, Clock.System)
        return { BlockListPresenter(BlockListScreen(kind), navigator, blocks) }
    }

    @Test
    fun blueskyOnlyBlocksAreListedButNotRemovable() = runTest {
        presenterTestOf(presenter(BlockListKind.Blocked)) {
            val loaded = awaitUntil { it.accounts.size == 3 }
            assertEquals(listOf(true, true, false), loaded.accounts.map { it.removable })
            assertEquals(listOf(false, true, true), loaded.accounts.map { it.onBluesky })

            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:dave")))
            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:bob")))
            val removed = awaitUntil { it.accounts.size == 2 }
            assertEquals(listOf("did:plc:carol", "did:plc:dave"), removed.accounts.map { it.profile.did.value })
            assertEquals(listOf("${Nsid.GRAPH_BLOCK}/did:plc:bob"), spaceDeletes, "only bob's app block is deleted")

            removed.eventSink(BlockListEvent.Remove(Did("did:plc:carol")))
            val carol = awaitUntil { s -> s.accounts.first().let { it.profile.did.value == "did:plc:carol" && !it.removable } }
            assertEquals(2, carol.accounts.size, "carol's Bluesky block still holds")

            carol.eventSink(BlockListEvent.Open(Did("did:plc:dave")))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unmutingRemovesTheAccount() = runTest {
        presenterTestOf(presenter(BlockListKind.Muted)) {
            val loaded = awaitUntil { it.accounts.size == 1 }
            loaded.eventSink(BlockListEvent.Remove(Did("did:plc:bob")))
            awaitUntil { it.accounts.isEmpty() && it.removing.isEmpty() }
            assertEquals(listOf("${Nsid.GRAPH_MUTE}/did:plc:bob"), spaceDeletes)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
