package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
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
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

                            failAppView -> {
                                respond("""{"error":"InternalServerError"}""", HttpStatusCode.InternalServerError, json)
                            }

                            request.url.parameters["cursor"] == null -> {
                                respond("""{"subject":$bob,"followers":[$carol],"cursor":"c1"}""", headers = json)
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
        return ProfileRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger)
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
            assertEquals(listOf("carol.test"), first.accounts.map { it.handle.value })
            assertEquals(true, first.canLoadMore)

            first.eventSink(FollowListEvent.LoadMore)
            var second = awaitItem()
            while (second.isLoading || second.accounts.size < 2) second = awaitItem()
            assertEquals(listOf("carol.test", "dave.test"), second.accounts.map { it.handle.value })
            assertEquals(false, second.canLoadMore)

            second.eventSink(FollowListEvent.Open(second.accounts.last().did))
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
            val failed = awaitLoaded()
            assertNotNull(failed.error)
            assertEquals(false, failed.canLoadMore)

            failAppView = false
            failed.eventSink(FollowListEvent.Retry)
            var recovered = awaitItem()
            while (recovered.isLoading || recovered.error != null) recovered = awaitItem()
            assertEquals(listOf("carol.test"), recovered.accounts.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<FollowListState>.awaitLoaded() = awaitUntil { !it.isLoading }

    @Test
    fun ownFollowersCanBeRemovedWhileApprovingFollowers() = runTest {
        val own = FollowListScreen(alice.value, FollowListKind.Followers)
        val repo = profiles()
        val answers = requests()
        presenterTestOf({ FollowListPresenter(own, FakeNavigator(own), repo, answers) }) {
            val loaded = awaitUntil { it.canRemove }
            loaded.eventSink(FollowListEvent.Remove(Did("did:plc:carol")))
            val removed = awaitUntil { it.accounts.size == 1 && it.removing.isEmpty() }
            assertEquals(listOf("dave.test"), removed.accounts.map { it.handle.value })
            assertEquals(1, deletes.size)
            assertTrue(deletes.single().contains(Nsid.GRAPH_FOLLOW_APPROVAL) && deletes.single().contains("did:plc:carol"))
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
            assertEquals(2, loaded.accounts.size)
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
