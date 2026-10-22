package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchEvent
import io.github.solcott.activitytracker.app.search.AthleteSearchPresenter
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchState
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.profile.AthleteSearchRepository
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.hasAnswer
import io.github.solcott.uistate.hasLoaded
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** Athlete search (ui/content-loading, "A new search keeps the last results until it answers"). */
class AthleteSearchPresenterTest {
    private val carol = """{"did":"did:plc:carol","handle":"carol.test","displayName":"Carol"}"""
    private val dave = """{"did":"did:plc:dave","handle":"dave.test"}"""
    private val erin = """{"did":"did:plc:erin","handle":"erin.test"}"""
    private var failAppView = false

    /** Each search asked for, as `q` or `q@cursor`, in order. */
    private val queries = mutableListOf<String>()

    /** Searches (`q` or `q@cursor`) that wait for their gate before answering. */
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Completed when each search (`q` or `q@cursor`) in it reaches the AppView. */
    private val arrived = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Searches (`q` or `q@cursor`) that fail once as a dropped connection would. */
    private val failOnce = mutableSetOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_SEARCH_ACTORS}" -> {
                        val q = request.url.parameters["q"].orEmpty()
                        val cursor = request.url.parameters["cursor"]
                        val asked = cursor?.let { "$q@$it" } ?: q
                        queries += asked
                        arrived[asked]?.complete(Unit)
                        gates[asked]?.await()
                        if (failOnce.remove(asked)) throw IOException("Connection refused")
                        when {
                            failAppView -> respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            q == "c" && cursor == null -> respond("""{"actors":[$carol],"cursor":"1"}""", headers = json)
                            q == "c" -> respond("""{"actors":[$dave]}""", headers = json)
                            q == "ann" && cursor == null -> respond("""{"actors":[$carol],"cursor":"1"}""", headers = json)
                            q == "ann" -> respond("""{"actors":[$erin]}""", headers = json)
                            q == "anna" -> respond("""{"actors":[$dave]}""", headers = json)
                            else -> respond("""{"actors":[]}""", headers = json)
                        }
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun athletes(): AthleteSearchRepository =
        AthleteSearchRepository(signedInTestSession(http, did = Did("did:plc:alice")).clients)

    private val AthleteSearchState.handles
        get() = results.items.map { it.handle.value }

    /** Whether the search has answered and nothing is loading in its place. */
    private val AthleteSearchState.searched
        get() = results.hasAnswer && results.refresh is LoadStatus.Idle

    @Test
    fun searchesAsTheAthleteTypesPagesAndOpensProfiles() = runTest {
        val navigator = FakeNavigator(AthleteSearchScreen)
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(navigator, repo) }) {
            // Nothing loads, and nothing shows as loading, before anything is typed.
            val blank = awaitUntil { it.results.refresh is LoadStatus.Idle }
            assertEquals("", blank.query.text.toString())
            assertFalse(blank.results.hasLoaded)

            blank.query.setTextAndPlaceCursorAtEnd(" c ")
            val first = awaitUntil { it.searched }
            // Surrounding spaces don't reach the AppView.
            assertEquals(listOf("c"), queries)
            assertEquals(listOf("carol.test"), first.handles)
            assertEquals(true, first.results.canLoadMore)

            first.eventSink(AthleteSearchEvent.LoadMore)
            val second = awaitUntil { it.handles.size == 2 }
            assertEquals(listOf("carol.test", "dave.test"), second.handles)
            assertEquals(false, second.results.canLoadMore)

            second.eventSink(AthleteSearchEvent.Open(second.results.items.last().did))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())

            second.query.setTextAndPlaceCursorAtEnd("zz")
            val none = awaitUntil { it.searched && it.results.items.isEmpty() }
            assertEquals(LoadStatus.Idle, none.results.append)

            none.query.clearText()
            val cleared = awaitUntil { it.query.text.isEmpty() && !it.results.hasLoaded }
            assertEquals(LoadStatus.Idle, cleared.results.refresh, "nothing shows as searching")
            assertEquals(emptyList(), cleared.results.items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun typingFastSearchesOnce() = runTest {
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            val blank = awaitItem()
            blank.query.setTextAndPlaceCursorAtEnd("a")
            blank.query.setTextAndPlaceCursorAtEnd("an")
            blank.query.setTextAndPlaceCursorAtEnd("anna")
            assertEquals(listOf("dave.test"), awaitUntil { it.searched && it.handles.isNotEmpty() }.handles)
            assertEquals(listOf("anna"), queries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refiningAQueryKeepsTheLastResultsUntilTheNewOnesAnswer() = runTest {
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("ann")
            val ann = awaitUntil { it.searched }
            assertEquals(listOf("carol.test"), ann.handles)

            // A page of "ann" still in flight when the query changes never joins the "anna" results.
            val annPage = CompletableDeferred<Unit>().also { gates["ann@1"] = it }
            val annPageAsked = CompletableDeferred<Unit>().also { arrived["ann@1"] = it }
            val anna = CompletableDeferred<Unit>().also { gates["anna"] = it }
            ann.eventSink(AthleteSearchEvent.LoadMore)
            awaitUntil { it.results.append is LoadStatus.Loading }
            annPageAsked.await()
            ann.query.setTextAndPlaceCursorAtEnd("anna")
            val refining = awaitUntil { it.results.refresh is LoadStatus.Loading && it.query.text.toString() == "anna" }
            assertEquals(listOf("carol.test"), refining.handles, "the last results stay while searching")

            annPage.complete(Unit)
            anna.complete(Unit)
            val refined = awaitUntil { it.searched && it.handles == listOf("dave.test") }
            assertEquals(LoadStatus.Idle, refined.results.append)
            assertEquals(listOf("ann", "ann@1", "anna"), queries, "the \"ann\" page was in flight, and answered first")
            assertEquals(listOf("dave.test"), refined.handles, "but it isn't added to the \"anna\" results")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aBlankQuerySearchesForNothing() = runTest {
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("   ")
            val blank = awaitUntil { it.query.text.toString() == "   " && it.results.refresh is LoadStatus.Idle }
            assertFalse(blank.results.hasLoaded)
            assertEquals(emptyList(), queries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun offlineSaysWhyAndRetryRecovers() = runTest {
        failOnce += "c"
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("c")
            val failed = awaitUntil { it.results.refresh is LoadStatus.Failed }
            assertEquals(DataError.Network, assertIs<LoadStatus.Failed>(failed.results.refresh).error)
            assertEquals(false, failed.results.canLoadMore)

            failed.eventSink(AthleteSearchEvent.Retry)
            val recovered = awaitUntil { it.searched }
            assertEquals(listOf("carol.test"), recovered.handles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aServerErrorSaysWhy() = runTest {
        failAppView = true
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("c")
            val failed = awaitUntil { it.results.refresh is LoadStatus.Failed }
            assertIs<DataError.Api>(assertIs<LoadStatus.Failed>(failed.results.refresh).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedPageKeepsTheFirstAndRetriesOnlyItself() = runTest {
        failOnce += "c@1"
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("c")
            awaitUntil { it.searched }.eventSink(AthleteSearchEvent.LoadMore)
            val failed = awaitUntil { it.results.append is LoadStatus.Failed }
            assertEquals(listOf("carol.test"), failed.handles)

            failed.eventSink(AthleteSearchEvent.Retry)
            assertEquals(listOf("carol.test", "dave.test"), awaitUntil { it.handles.size == 2 }.handles)
            assertEquals(listOf("c", "c@1", "c@1"), queries, "only the failed page is asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
