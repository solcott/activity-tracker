package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/** Alice follows Bob against a fake PDS and an AppView that shows Bob as last indexed, which lags her writes. */
class ProfileFollowTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")

    /** Alice's follow of Bob and his follower count, as the AppView has indexed them. */
    private var indexedFollow: AtUri? = null
    private var indexedFollowers = 3L
    private var indexedRequested: Boolean? = null
    private var bobPolicy: String? = null
    private val writes = mutableListOf<String>()

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> {
                        respond("""{"token":"service-jwt"}""", headers = json)
                    }

                    "/xrpc/${Nsid.ACTOR_GET_PROFILE}" -> {
                        respond(AtJson.encodeToString(ProfileView.serializer(), bobAsIndexed()), headers = json)
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        writes += "put"
                        respond("""{"uri":"at://$alice/x/y","cid":"bafyrec"}""", headers = json)
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        writes += "delete"
                        respond("{}", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private fun bobAsIndexed() =
        ProfileView(
            did = bob,
            handle = Handle("bob.test"),
            followersCount = indexedFollowers,
            followPolicy = bobPolicy,
            viewer = ProfileViewerState(indexedFollow, indexedRequested),
        )

    private suspend fun repository() =
        ProfileRepository(signedInTestSession(http, did = alice).clients, Clock.System, Logger, TrainingNarrowing {})

    @Test
    fun followShowsAtOnceAndUntilTheAppViewIndexesIt() = runTest {
        val profiles = repository()
        val followed = profiles.follow(profiles.profile(bob.value))
        val uri = assertNotNull(followed.viewer?.following)
        assertEquals(Nsid.GRAPH_FOLLOW, uri.collection)
        assertEquals(4L, followed.followersCount)

        // The AppView hasn't seen the follow yet: still shown as following, and following again writes nothing.
        val lagging = profiles.profile(bob.value)
        assertEquals(uri, lagging.viewer?.following)
        assertEquals(4L, lagging.followersCount)
        assertEquals(lagging, profiles.follow(lagging))
        assertEquals(listOf("put"), writes)

        // Once indexed, the AppView's own count is shown, not one more.
        indexedFollow = uri
        indexedFollowers = 4
        assertEquals(bobAsIndexed(), profiles.profile(bob.value))
    }

    @Test
    fun unfollowShowsAtOnceAndUntilTheAppViewIndexesIt() = runTest {
        indexedFollow = AtUri("at://$alice/${Nsid.GRAPH_FOLLOW}/3kf1")
        val profiles = repository()
        val unfollowed = profiles.unfollow(profiles.profile(bob.value))
        assertNull(unfollowed.viewer?.following)
        assertEquals(2L, unfollowed.followersCount)

        val lagging = profiles.profile(bob.value)
        assertNull(lagging.viewer?.following)
        assertEquals(2L, lagging.followersCount)
        assertEquals(listOf("delete"), writes)

        indexedFollow = null
        indexedFollowers = 2
        assertEquals(bobAsIndexed(), profiles.profile(bob.value))
    }

    @Test
    fun followingAnAthleteWhoApprovesIsARequestUntilIndexed() = runTest {
        bobPolicy = FollowPolicy.APPROVE
        val profiles = repository()
        val requested = profiles.follow(profiles.profile(bob.value))
        val uri = assertNotNull(requested.viewer?.following)
        assertEquals(true, requested.viewer?.followRequested)
        assertEquals(3L, requested.followersCount, "a request doesn't count")
        assertEquals(requested, profiles.profile(bob.value))

        // Indexed as still pending: the AppView's view, unchanged.
        indexedFollow = uri
        indexedRequested = true
        assertEquals(bobAsIndexed(), profiles.profile(bob.value))
    }

    @Test
    fun cancellingARequestChangesNoCount() = runTest {
        bobPolicy = FollowPolicy.APPROVE
        indexedFollow = AtUri("at://$alice/${Nsid.GRAPH_FOLLOW}/3kf1")
        indexedRequested = true
        val profiles = repository()
        val cancelled = profiles.unfollow(profiles.profile(bob.value))
        assertNull(cancelled.viewer?.following)
        assertNull(cancelled.viewer?.followRequested)
        assertEquals(3L, cancelled.followersCount)
        assertEquals(listOf("delete"), writes)
    }
}
