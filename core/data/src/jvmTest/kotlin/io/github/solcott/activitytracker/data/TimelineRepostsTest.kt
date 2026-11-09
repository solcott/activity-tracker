package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.CachedLike
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetReposts
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import io.github.solcott.activitytracker.lexicon.app.RepostedPostView
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Posts in the client's timeline through a repost (feed/reposts), and who reposted a post. */
class TimelineRepostsTest {
    private val did = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val at = Instant.parse("2026-11-07T09:00:00Z")

    private fun profile(of: Did) = ProfileViewBasic(of, Handle("${of.value.last()}.test"))

    private val post =
        PostView(
            uri = AtUri.of(bob, Nsid.FEED_POST, "3kp"),
            cid = Cid("bafyp"),
            author = profile(bob),
            embed =
                PostEmbedView.Proposal(
                    proposal = StrongRef(AtUri.of(bob, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp"), Cid("bafyq")),
                    name = "Wiggly Woods",
                    status = PostProposalStatus.OPEN,
                    openedAt = at,
                    up = 4,
                    down = 1,
                ),
            createdAt = at,
            indexedAt = at,
            repostCount = 2,
        )

    private val reposted = RepostedPostView(post, ReasonRepost(profile(carol), at, others = 1))

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" ->
                        respond(
                            AtJson.encodeToString(
                                GetTimeline.Output.serializer(),
                                GetTimeline.Output(listOf(TimelineItem.Repost(reposted))),
                            ),
                            headers = json,
                        )
                    "/xrpc/${Nsid.FEED_GET_REPOSTS}" ->
                        if (request.url.parameters["uri"] == post.uri.value) {
                            val output = GetReposts.Output(post.uri, listOf(profile(carol), profile(bob)))
                            respond(AtJson.encodeToString(GetReposts.Output.serializer(), output), headers = json)
                        } else {
                            respond("""{"error":"NotFound","message":"Post not found"}""", HttpStatusCode.BadRequest, json)
                        }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            },
        )

    private val likes = LocalLikes()

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, likes, Logger)
    }

    @Test
    fun aRepostedPostIsCachedWithWhyAndReadBack() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        val item = assertIs<TimelineItem.Repost>(feed.timelineItems().first().single())
        assertEquals(reposted, item.reposted)
    }

    @Test
    fun aLikeOrRepostOfAPostHeldThroughARepostUpdatesItsCachedRow() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        val like = AtUri.of(did, Nsid.FEED_LIKE, "3klike")
        feed.updateCachedCounts(post.uri, likes = 1, like = CachedLike.Set(like))
        val repost = AtUri.of(did, Nsid.FEED_REPOST, "3krepost")
        feed.localReposts.set(post.uri, repost)
        val shown = assertIs<TimelineItem.Repost>(feed.timelineItems().first().single()).reposted
        assertEquals(1, shown.post.likeCount)
        assertEquals(like, shown.post.viewer?.like)
        assertEquals(3, shown.post.repostCount)
        assertEquals(repost, shown.post.viewer?.repost)
        assertEquals(reposted.reason, shown.reason)
    }

    @Test
    fun aRepostedItemLeavesWhenTheViewerBlocksTheReposterOrMutesTheAuthor() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        feed.localBlocks.block(carol, AtUri.of(did, Nsid.GRAPH_BLOCK, carol.value))
        assertEquals(emptyList(), feed.timelineItems().first())
        feed.localBlocks.block(carol, null)
        assertEquals(1, feed.timelineItems().first().size)
        feed.localBlocks.mute(bob, AtUri.of(did, Nsid.GRAPH_MUTE, bob.value))
        assertEquals(emptyList(), feed.timelineItems().first())
    }

    @Test
    fun repostersAreListedAndNoneForAPostNoLongerShown() = runTest {
        val feed = feed()
        assertEquals(listOf(carol, bob), feed.reposts(post.uri).map { it.did })
        assertEquals(emptyList(), feed.reposts(AtUri.of(bob, Nsid.FEED_POST, "3kgone")))
    }
}
