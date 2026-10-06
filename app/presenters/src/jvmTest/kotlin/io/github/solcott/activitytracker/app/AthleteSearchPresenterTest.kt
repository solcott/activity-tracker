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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class AthleteSearchPresenterTest {
    private val carol = """{"did":"did:plc:carol","handle":"carol.test","displayName":"Carol"}"""
    private val dave = """{"did":"did:plc:dave","handle":"dave.test"}"""
    private var failAppView = false

    /** The `q` of each search request, in order. */
    private val queries = mutableListOf<String>()

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
                        queries += q
                        when {
                            failAppView -> respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            q != "c" -> respond("""{"actors":[]}""", headers = json)
                            request.url.parameters["cursor"] == null -> respond("""{"actors":[$carol],"cursor":"1"}""", headers = json)
                            else -> respond("""{"actors":[$dave]}""", headers = json)
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

    @Test
    fun searchesAsTheAthleteTypesPagesAndOpensProfiles() = runTest {
        val navigator = FakeNavigator(AthleteSearchScreen)
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(navigator, repo) }) {
            val blank = awaitItem()
            assertEquals("", blank.query.text.toString())
            assertEquals(false, blank.searched)
            assertEquals(false, blank.isLoading)

            blank.query.setTextAndPlaceCursorAtEnd(" c ")
            val first = awaitSearched()
            // Surrounding spaces don't reach the AppView.
            assertEquals(listOf("c"), queries)
            assertEquals(listOf("carol.test"), first.results.map { it.handle.value })
            assertEquals(true, first.canLoadMore)

            first.eventSink(AthleteSearchEvent.LoadMore)
            val second = awaitUntil { !it.isLoading && it.results.size == 2 }
            assertEquals(listOf("carol.test", "dave.test"), second.results.map { it.handle.value })
            assertEquals(false, second.canLoadMore)

            second.eventSink(AthleteSearchEvent.Open(second.results.last().did))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())

            second.query.setTextAndPlaceCursorAtEnd("zz")
            val none = awaitSearched()
            assertEquals(emptyList(), none.results)
            assertNull(none.error)

            none.query.clearText()
            val cleared = awaitUntil { it.query.text.isEmpty() && !it.isLoading }
            assertEquals(false, cleared.searched)
            assertEquals(emptyList(), cleared.results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failureSurfacesErrorAndRetryRecovers() = runTest {
        failAppView = true
        val repo = athletes()
        presenterTestOf({ AthleteSearchPresenter(FakeNavigator(AthleteSearchScreen), repo) }) {
            awaitItem().query.setTextAndPlaceCursorAtEnd("c")
            val failed = awaitUntil { it.error != null }
            assertNotNull(failed.error)
            assertEquals(false, failed.canLoadMore)

            failAppView = false
            failed.eventSink(AthleteSearchEvent.Retry)
            val recovered = awaitUntil { it.searched && it.error == null }
            assertEquals(listOf("carol.test"), recovered.results.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<AthleteSearchState>.awaitSearched() = awaitUntil { it.searched }
}
