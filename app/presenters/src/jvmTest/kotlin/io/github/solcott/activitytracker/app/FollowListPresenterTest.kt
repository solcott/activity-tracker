package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import co.touchlab.kermit.Logger
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.profile.FollowListEvent
import io.github.solcott.activitytracker.app.profile.FollowListKind
import io.github.solcott.activitytracker.app.profile.FollowListPresenter
import io.github.solcott.activitytracker.app.profile.FollowListScreen
import io.github.solcott.activitytracker.app.profile.FollowListState
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.profile.FollowRequestsRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.TrainingNarrowing
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.dataresult.DataError
import io.github.solcott.uistate.LoadStatus
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.pagedStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class FollowListPresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = """{"did":"did:plc:bob","handle":"bob.test","displayName":"Bob"}"""
    private val carol = """{"did":"did:plc:carol","handle":"carol.test"}"""
    private val dave = """{"did":"did:plc:dave","handle":"dave.test"}"""
    private var failAppView = false
    private var alicePolicy: String? = "approve"
    private val deletes = mutableListOf<String>()
    private var failDeletes = false
    private val erin = """{"did":"did:plc:erin","handle":"erin.test"}"""

    /** Bob's followers come in three pages (carol, dave, erin) instead of two, while set. */
    private var threePages = false

    /** Cursors whose `getFollowers` fails as a dropped connection would. */
    private val failingCursors = mutableSetOf<String?>()

    /** The cursor of each `getFollowers` of bob asked for, in order. */
    private val requested = mutableListOf<String?>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.getRecord" -> {
                        val policy = alicePolicy?.let { ""","followPolicy":"$it"""" }.orEmpty()
                        respond(
                            """{"uri":"at://$alice/${Nsid.ACTOR_PROFILE}/self","value":{"displayName":"Alice"$policy}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        if (failDeletes)
                            return@MockEngine respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                        deletes += String((request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes())
                        respond("{}", headers = json)
                    }

                    "/xrpc/${Nsid.GRAPH_GET_FOLLOWERS}" -> {
                        when {
                            request.url.parameters["actor"] == alice.value -> {
                                respond(
                                    """{"subject":{"did":"$alice","handle":"alice.test"},"followers":[$carol,$dave]}""",
                                    headers = json,
                                )
                            }

                            request.url.parameters["cursor"].also { requested += it } in failingCursors -> {
                                throw kotlinx.io.IOException("Connection refused")
                            }

                            failAppView -> {
                                respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            }

                            request.url.parameters["cursor"] == null -> {
                                respond("""{"subject":$bob,"followers":[$carol],"cursor":"c1"}""", headers = json)
                            }

                            threePages && request.url.parameters["cursor"] == "c1" -> {
                                respond("""{"subject":$bob,"followers":[$dave],"cursor":"c2"}""", headers = json)
                            }

                            threePages -> {
                                respond("""{"subject":$bob,"followers":[$erin]}""", headers = json)
                            }

                            else -> {
                                respond("""{"subject":$bob,"followers":[$dave]}""", headers = json)
                            }
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

    private suspend fun profiles(): ProfileRepository {
        return ProfileRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger, TrainingNarrowing {})
    }

    private suspend fun requests() = FollowRequestsRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger)

    private val screen = FollowListScreen("bob.test", FollowListKind.Followers)

    @Test
    fun loadsPagesAndOpensProfiles() = runTest {
        val navigator = FakeNavigator(screen)
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(screen, navigator, repo, answers) }) {
            val first = awaitLoaded()
            assertEquals("bob.test", first.subject?.handle?.value)
            assertEquals(listOf("carol.test"), first.handles)
            assertEquals(true, first.accounts.canLoadMore)

            first.eventSink(FollowListEvent.LoadMore)
            val second = awaitUntil { it.handles.size == 2 }
            assertEquals(listOf("carol.test", "dave.test"), second.handles)
            assertEquals(false, second.accounts.canLoadMore)

            second.eventSink(FollowListEvent.Open(second.accounts.items.last().did))
            assertEquals(ProfileScreen("did:plc:dave"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failureSurfacesErrorAndRetryRecovers() = runTest {
        failAppView = true
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(screen, FakeNavigator(screen), repo, answers) }) {
            val failed = awaitUntil { it.accounts.refresh is LoadStatus.Failed }
            assertIs<DataError.Api>(assertIs<LoadStatus.Failed>(failed.accounts.refresh).error)
            assertEquals(false, failed.accounts.canLoadMore)
            assertNull(failed.subject)

            failAppView = false
            failed.eventSink(FollowListEvent.Retry)
            val recovered = awaitUntil { it.accounts.refresh is LoadStatus.Idle && it.accounts.hasLoaded }
            assertEquals(listOf("carol.test"), recovered.handles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<FollowListState>.awaitLoaded() = awaitUntil {
        it.accounts.hasLoaded && it.accounts.refresh !is LoadStatus.Loading
    }

    private val FollowListState.handles
        get() = accounts.items.map { it.handle.value }

    @Test
    fun aFailedPageKeepsTheEarlierPagesAndRetriesOnlyItself() = runTest {
        threePages = true
        failingCursors += "c2"
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(screen, FakeNavigator(screen), repo, answers) }) {
            awaitUntil { it.accounts.canLoadMore }.eventSink(FollowListEvent.LoadMore)
            awaitUntil { it.handles.size == 2 && it.accounts.canLoadMore }.eventSink(FollowListEvent.LoadMore)
            val failed = awaitUntil { it.accounts.append is LoadStatus.Failed }
            assertEquals(DataError.Network, assertIs<LoadStatus.Failed>(failed.accounts.append).error)
            assertEquals(listOf("carol.test", "dave.test"), failed.handles, "both pages stay")

            failingCursors.clear()
            failed.eventSink(FollowListEvent.Retry)
            assertEquals(listOf("carol.test", "dave.test", "erin.test"), awaitUntil { it.handles.size == 3 }.handles)
            assertEquals(listOf(null, "c1", "c2", "c2"), requested, "only the failed page is asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsEveryPageAtOnce() = runTest {
        threePages = true
        val repo = profiles()
        val answers = requests()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) FollowListPresenter(screen, FakeNavigator(screen), repo, answers)
                else FollowListState(screen.kind, pagedStateOf()) {}
            }
        }) {
            awaitUntil { it.accounts.canLoadMore }.eventSink(FollowListEvent.LoadMore)
            awaitUntil { it.handles.size == 2 && it.accounts.canLoadMore }.eventSink(FollowListEvent.LoadMore)
            awaitUntil { it.handles.size == 3 }
            registry.saveAll()
            shown = false
            awaitUntil { it.accounts.items.isEmpty() }
            shown = true
            val back = awaitItem()
            assertEquals(listOf("carol.test", "dave.test", "erin.test"), back.handles, "no loading state in between")
            assertEquals("bob.test", back.subject?.handle?.value)
            assertEquals(listOf(null, "c1", "c2"), requested, "no page is asked for again")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ownFollowersCanBeRemovedWhileApprovingFollowers() = runTest {
        val own = FollowListScreen(alice.value, FollowListKind.Followers)
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(own, FakeNavigator(own), repo, answers) }) {
            val loaded = awaitUntil { it.canRemove }
            loaded.eventSink(FollowListEvent.Remove(Did("did:plc:carol")))
            val removed = awaitUntil { it.accounts.items.size == 1 && it.removing.isEmpty() }
            assertEquals(listOf("dave.test"), removed.handles)
            assertEquals(1, deletes.size)
            assertTrue(deletes.single().contains(Nsid.GRAPH_FOLLOW_APPROVAL) && deletes.single().contains("did:plc:carol"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRemovalKeepsTheFollowerAndSaysWhy() = runTest {
        failDeletes = true
        val own = FollowListScreen(alice.value, FollowListKind.Followers)
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(own, FakeNavigator(own), repo, answers) }) {
            awaitUntil { it.canRemove }.eventSink(FollowListEvent.Remove(Did("did:plc:carol")))
            val failed = awaitUntil { it.actionError != null && it.removing.isEmpty() }
            assertEquals(UiError.Kind.RemoveFollower, failed.actionError?.kind)
            assertEquals(listOf("carol.test", "dave.test"), failed.handles)
            assertEquals(LoadStatus.Idle, failed.accounts.refresh, "it isn't shown as a failed load")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOpenPolicyOffersNoRemove() = runTest {
        alicePolicy = null
        val own = FollowListScreen(alice.value, FollowListKind.Followers)
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(own, FakeNavigator(own), repo, answers) }) {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.accounts.items.size)
            assertFalse(loaded.canRemove)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anotherAthletesFollowersOfferNoRemove() = runTest {
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(screen, FakeNavigator(screen), repo, answers) }) {
            assertFalse(awaitLoaded().canRemove)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
