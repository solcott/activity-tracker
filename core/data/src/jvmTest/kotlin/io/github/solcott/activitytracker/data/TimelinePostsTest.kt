package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.ActivityFilter
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.feed.LocalPosts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetAuthorPosts
import io.github.solcott.activitytracker.lexicon.app.GetPost
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
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

/** Posts (feed/posts) in the client's timeline cache and profile pages, with the athlete's own changes laid over them. */
class TimelinePostsTest {
    private val did = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")

    private fun activity(rkey: String) =
        ActivityView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(bob, Handle("b.test")),
            record = ActivityRecord("ride", Instant.parse("2026-03-05T08:00:00Z"), 1800, Instant.parse("2026-03-05T09:00:00Z")),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-03-05T09:00:00Z"),
        )

    private fun post(author: Did, rkey: String, text: String? = "why", at: String = "2026-03-06T09:00:00Z") =
        PostView(
            uri = AtUri.of(author, Nsid.FEED_POST, rkey),
            cid = Cid("bafyp$rkey"),
            author = ProfileViewBasic(author, Handle("${author.value.last()}.test")),
            embed =
                PostEmbedView.Proposal(
                    proposal = StrongRef(AtUri.of(author, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey), Cid("bafyq")),
                    name = "Wiggly Woods",
                    status = PostProposalStatus.OPEN,
                    openedAt = Instant.parse(at),
                    up = 4,
                    down = 1,
                ),
            createdAt = Instant.parse(at),
            indexedAt = Instant.parse(at),
            text = text,
        )

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    "/xrpc/${Nsid.FEED_GET_TIMELINE}" -> {
                        val output =
                            if (request.url.parameters["type"] != null) GetTimeline.Output.ofActivities(listOf(activity("3kb")))
                            else GetTimeline.Output(listOf(TimelineItem.Post(post(bob, "3kp")), TimelineItem.Activity(activity("3kb"))))
                        respond(AtJson.encodeToString(GetTimeline.Output.serializer(), output), headers = json)
                    }
                    "/xrpc/${Nsid.FEED_GET_POST}" -> {
                        if (request.url.parameters["uri"] == post(bob, "3kgone").uri.value) {
                            respond("""{"error":"NotFound","message":"Post not found"}""", HttpStatusCode.BadRequest, json)
                        } else {
                            val output = GetPost.Output(post(bob, "3kp").copy(likeCount = 2, commentCount = 3))
                            respond(AtJson.encodeToString(GetPost.Output.serializer(), output), headers = json)
                        }
                    }
                    "/xrpc/${Nsid.FEED_GET_AUTHOR_POSTS}" -> {
                        val output = GetAuthorPosts.Output(listOf(post(did, "3kq")), cursor = "next")
                        respond(AtJson.encodeToString(GetAuthorPosts.Output.serializer(), output), headers = json)
                    }
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        )

    private val posts = LocalPosts()
    private val likes = LocalLikes()

    private suspend fun feed(): FeedRepository {
        val (db, _, clients) = signedInTestSession(http, did = did)
        return FeedRepository(db, clients, likes, Logger, posts)
    }

    private fun List<TimelineItem>.keys() = map {
        when (it) {
            is TimelineItem.Activity -> it.view.uri.rkey
            is TimelineItem.Post -> "post:${it.post.uri.rkey}"
            else -> "other"
        }
    }

    @Test
    fun postsAreCachedInTheirPlace() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        assertEquals(listOf("post:3kp", "3kb"), feed.timelineItems().first().keys())
        assertEquals(
            "Wiggly Woods",
            (feed.timelineItems().first().first() as TimelineItem.Post).post.let { (it.embed as PostEmbedView.Proposal).name },
        )
    }

    @Test
    fun ownNewPostsShowFirstOnlyUnfiltered() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        posts.put(post(did, "3kn", at = "2026-03-07T09:00:00Z"))
        assertEquals(listOf("post:3kn", "post:3kp", "3kb"), feed.timelineItems().first().keys())
        val rides = ActivityFilter(types = setOf(ActivityType("ride")))
        feed.refreshTimeline(rides)
        assertEquals(listOf("3kb"), feed.timelineItems(rides).first().keys())
    }

    @Test
    fun aRemovedOrChangedPostShowsAsLeft() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        posts.put(post(bob, "3kp", text = "changed").copy(visibility = Visibility.FOLLOWERS))
        val changed = (feed.timelineItems().first().first() as TimelineItem.Post).post
        assertEquals("changed" to Visibility.FOLLOWERS, changed.text to changed.visibility)
        assertEquals(4, (changed.embed as PostEmbedView.Proposal).up, "the AppView's live proposal stays")
        posts.remove(post(bob, "3kp").uri.value)
        assertEquals(listOf("3kb"), feed.timelineItems().first().keys())
    }

    @Test
    fun ownProfileShowsUnlistedPostsOnTheFirstPageOnly() = runTest {
        val feed = feed()
        posts.put(post(did, "3kn"))
        fun keys(outcome: Outcome<Page<PostView, Unit>>) =
            assertIs<Outcome.Data<Page<PostView, Unit>>>(outcome).data.items.map { it.uri.rkey }
        assertEquals(listOf("3kn", "3kq"), keys(feed.authorPostsPage(did, cursor = null)))
        assertEquals(listOf("3kq"), keys(feed.authorPostsPage(did, cursor = "next")))
        assertEquals(listOf("3kq"), keys(feed.authorPostsPage(bob, cursor = null)), "another athlete's profile doesn't show them")
    }

    // ---------------------------------------------------------------- Likes and the post page (feed/post-page)

    @Test
    fun aLikeTogglesOverTheTimelineAndProfilePostsUntilTheAppViewCatchesUp() = runTest {
        val feed = feed()
        feed.refreshTimeline()
        val liked = AtUri.of(did, Nsid.FEED_LIKE, "3klike")
        likes.set(post(bob, "3kp").uri, liked)
        feed.refreshTimeline()
        val shown = (feed.timelineItems().first().first() as TimelineItem.Post).post
        assertEquals(1, shown.likeCount)
        assertEquals(liked, shown.viewer?.like)

        likes.set(post(did, "3kq").uri, liked)
        val page = assertIs<Outcome.Data<Page<PostView, Unit>>>(feed.authorPostsPage(did, null))
        assertEquals(1, page.data.items.single().likeCount)
    }

    @Test
    fun thePostIsReadWithTheViewersLikeLaidOver() = runTest {
        val feed = feed()
        assertEquals(2, feed.post(post(bob, "3kp").uri).likeCount)
        likes.set(post(bob, "3kp").uri, null)
        val read = feed.post(post(bob, "3kp").uri)
        assertEquals(2, read.likeCount, "the AppView didn't report a like of the viewer's, so unliking takes none away")
        assertEquals(3, read.commentCount)
        likes.set(post(bob, "3kp").uri, AtUri.of(did, Nsid.FEED_LIKE, "3klike"))
        assertEquals(3, feed.post(post(bob, "3kp").uri).likeCount)
    }

    @Test
    fun aPostThatIsntServedIsNotFound() = runTest {
        val outcome = feed().postOutcome(post(bob, "3kgone").uri).first { it !is Outcome.Loading }
        assertIs<Outcome.Error>(outcome)
    }
}
