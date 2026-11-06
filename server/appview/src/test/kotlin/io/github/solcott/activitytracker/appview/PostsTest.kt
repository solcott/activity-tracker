package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.MediaUrls
import io.github.solcott.activitytracker.appview.api.Views
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetAuthorPosts
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Posts (feed/posts): which are indexed, who sees them, and how the timeline and profiles list them. */
class PostsTest : SegmentTestBase() {
    private val labeler = "did:plc:labeler"
    private val views = Views(db, AvatarUrls("https://appview.test"), MediaUrls("https://appview.test"))
    private val seg = segmentUri("3ks", importer)
    private val now = Clock.System.now()
    private var effortIds = 0

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    private fun activeSegment() {
        segment("3ks", sport = "ride", did = importer, name = "Trail 214")
        runBlocking { db.tx { Segments.update({ Segments.uri eq seg }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    /** [days] efforts by [did] on distinct dates, so they're a regular after 5. */
    private fun efforts(did: String, days: Int) = runBlocking {
        db.tx {
            SegmentEfforts.batchInsert(0 until days) { i ->
                this[SegmentEfforts.id] = "effort-${effortIds++}"
                this[SegmentEfforts.segmentUri] = seg
                this[SegmentEfforts.activityUri] = activityUri("3ke$i", did)
                this[SegmentEfforts.trackUri] = trackUri("3ke$i", did)
                this[SegmentEfforts.authorDid] = did
                this[SegmentEfforts.sport] = "ride"
                this[SegmentEfforts.eBike] = false
                this[SegmentEfforts.startOffsetS] = i * 10L
                this[SegmentEfforts.elapsedS] = 600
                this[SegmentEfforts.visibility] = Visibility.SELF
                this[SegmentEfforts.startedOn] = LocalDate.of(2026, 9, 1).plusDays(i.toLong()).toString()
            }
        }
    }

    private fun proposalUri(rkey: String = "3kp", did: String = alice) = "at://$did/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/$rkey"

    private fun postUri(rkey: String = "3kp", did: String = alice) = "at://$did/${Nsid.FEED_POST}/$rkey"

    private fun propose(name: String = "Wiggly Woods", rkey: String = "3kp", did: String = alice) =
        commit(did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey, """{"segment":"$seg","name":"$name","createdAt":"${now - 2.hours}"}""")

    /** [did] shares [proposal] at [tier], under [rkey]. */
    @Suppress("LongParameterList")
    private fun share(
        tier: String = Visibility.PUBLIC,
        rkey: String = "3kp",
        did: String = alice,
        proposal: String = proposalUri(rkey, did),
        text: String? = "Everyone already calls it this",
        createdAt: kotlin.time.Instant = now - 1.hours,
    ) {
        val textField = text?.let { ""","text":"$it"""" }.orEmpty()
        put(
            did,
            Nsid.FEED_POST,
            rkey,
            """{"embed":{"${'$'}type":"${Nsid.FEED_POST}#proposal","proposal":"$proposal"},"createdAt":"$createdAt"$textField}""",
            tier,
        )
    }

    /** Alice, a regular on the active segment, proposes "Wiggly Woods"; bob and carol are known accounts, bob a rider. */
    private fun aliceProposes() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        commit(bob, Nsid.ACTOR_PROFILE, "self", """{"displayName":"b"}""")
        commit(carol, Nsid.ACTOR_PROFILE, "self", """{"displayName":"c"}""")
        propose()
    }

    private fun timeline(viewer: String, params: GetTimeline.Params = GetTimeline.Params(limit = 50)) = runBlocking {
        views.timeline(Did(viewer), params)
    }

    private fun timelinePosts(viewer: String): List<PostView> = timeline(viewer).posts

    private fun profilePosts(actor: String, viewer: String?): List<PostView> = runBlocking {
        views.authorPosts(viewer?.let(::Did), GetAuthorPosts.Params(actor)).posts
    }

    private fun PostView.proposal() = embed as PostEmbedView.Proposal

    // ---------------------------------------------------------------- Storage and acceptance

    @Test
    fun aFollowerSeesASharedProposalLive() {
        aliceProposes()
        follow(bob, alice)
        share()
        val post = timelinePosts(bob).single()
        assertEquals(postUri(), post.uri.value)
        assertEquals("Everyone already calls it this", post.text)
        assertEquals("Wiggly Woods", post.proposal().name)
        assertEquals(PostProposalStatus.OPEN, post.proposal().status)
        assertEquals("Trail 214", post.proposal().segment?.name)
        assertTrue(post.proposal().canVote, "bob rides the segment")
        assertNull(post.visibility)
    }

    @Test
    fun aNonRiderMayNotVoteAndTheAuthorNeither() {
        aliceProposes()
        follow(carol, alice)
        share()
        assertFalse(timelinePosts(carol).single().proposal().canVote)
        assertFalse(timelinePosts(alice).single().proposal().canVote)
    }

    @Test
    fun aViewersVoteAndTotalsAreShown() {
        aliceProposes()
        follow(bob, alice)
        share()
        val cid = runBlocking { db.tx { SegmentNameProposals.select(SegmentNameProposals.cid).single()[SegmentNameProposals.cid] } }
        spaceWrite(
            space(AthleteSpace.INTERACTIONS, bob),
            Nsid.FEED_SEGMENT_NAME_VOTE,
            "3kv",
            """{"subject":{"uri":"${proposalUri()}","cid":"$cid"},"direction":"up","createdAt":"2026-10-28T00:00:00Z"}""",
        )
        val proposal = timelinePosts(bob).single().proposal()
        assertEquals(1, proposal.up)
        assertEquals(SegmentNameVoteDirection.UP, proposal.viewerVote?.direction)
    }

    @Test
    fun aPostSharingSomeoneElsesProposalIsIgnored() {
        aliceProposes()
        follow(alice, bob)
        share(did = bob, proposal = proposalUri())
        assertTrue(timelinePosts(alice).isEmpty())
    }

    @Test
    fun aPostUnderAnotherRkeyIsIgnored() {
        aliceProposes()
        share(rkey = "3kq", proposal = proposalUri("3kp"))
        assertTrue(profilePosts(alice, alice).isEmpty())
    }

    @Test
    fun aPostInSelfIsIgnored() {
        aliceProposes()
        share(tier = Visibility.SELF)
        assertTrue(profilePosts(alice, alice).isEmpty())
    }

    @Test
    fun aPostWithTooMuchTextIsIgnored() {
        aliceProposes()
        share(text = "x".repeat(1001))
        assertTrue(profilePosts(alice, alice).isEmpty())
    }

    // ---------------------------------------------------------------- Served only with its proposal

    @Test
    fun aPostArrivingFirstIsServedOnceTheProposalIs() {
        activeSegment()
        efforts(alice, 5)
        share()
        assertTrue(profilePosts(alice, alice).isEmpty())
        propose()
        assertEquals(1, profilePosts(alice, alice).size)
    }

    @Test
    fun aPostSharingAnIgnoredProposalIsNeverServed() {
        activeSegment()
        efforts(bob, 1)
        commit(bob, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp", """{"segment":"$seg","name":"Bob's Bend","createdAt":"$now"}""")
        share(did = bob)
        assertTrue(profilePosts(bob, bob).isEmpty())
    }

    @Test
    fun withdrawingTheProposalHidesThePost() {
        aliceProposes()
        share()
        commit(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp", null)
        assertTrue(profilePosts(alice, alice).isEmpty())
    }

    @Test
    fun anEditedProposalShowsItsNewName() {
        aliceProposes()
        share()
        commit(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp", """{"segment":"$seg","name":"Wiggly Woods Loop","createdAt":"$now"}""")
        assertEquals("Wiggly Woods Loop", profilePosts(alice, alice).single().proposal().name)
    }

    @Test
    fun aClosedProposalShowsItsFinalTotalsAndNoVote() {
        aliceProposes()
        share()
        runBlocking {
            db.tx {
                SegmentNameProposals.update({ SegmentNameProposals.uri eq proposalUri() }) {
                    it[state] = ProposalState.REJECTED
                    it[finalUp] = 2
                    it[finalDown] = 7
                }
            }
        }
        val proposal = profilePosts(alice, bob).single().proposal()
        assertEquals(PostProposalStatus.REJECTED, proposal.status)
        assertEquals(2 to 7, proposal.up to proposal.down)
        assertFalse(proposal.canVote)
    }

    // ---------------------------------------------------------------- By tier

    @Test
    fun aFollowersPostIsHiddenFromNonFollowers() {
        aliceProposes()
        share(tier = Visibility.FOLLOWERS)
        follow(bob, alice)
        assertEquals(Visibility.FOLLOWERS, profilePosts(alice, bob).single().visibility)
        assertTrue(profilePosts(alice, carol).isEmpty())
        assertTrue(profilePosts(alice, null).isEmpty())
    }

    @Test
    fun aMutualsPostNeedsAFollowBack() {
        aliceProposes()
        share(tier = Visibility.MUTUALS)
        follow(bob, alice)
        assertTrue(profilePosts(alice, bob).isEmpty())
        follow(alice, bob)
        assertEquals(1, profilePosts(alice, bob).size)
    }

    @Test
    fun movingAPostToANarrowerTierServesTheNarrowest() {
        aliceProposes()
        share()
        share(tier = Visibility.FOLLOWERS)
        assertTrue(profilePosts(alice, carol).isEmpty(), "the narrower copy holds the row")
        remove(alice, Nsid.FEED_POST, "3kp")
        follow(bob, alice)
        assertEquals(Visibility.FOLLOWERS, profilePosts(alice, bob).single().visibility)
        remove(alice, Nsid.FEED_POST, "3kp", Visibility.FOLLOWERS)
        assertTrue(profilePosts(alice, alice).isEmpty())
    }

    // ---------------------------------------------------------------- Timeline

    @Test
    fun postsInterleaveWithActivitiesByPosition() {
        aliceProposes()
        follow(bob, alice)
        put(alice, Nsid.FEED_ACTIVITY, "3ka1", activityJson(now - 3.hours))
        put(alice, Nsid.FEED_ACTIVITY, "3ka2", activityJson(now - 30.minutes))
        share(createdAt = now - 1.hours)
        val items =
            timeline(bob).feed.map {
                when (it) {
                    is TimelineItem.Post -> "post"
                    is TimelineItem.Activity -> it.view.uri.rkey
                    else -> "other"
                }
            }
        assertEquals(listOf("3ka2", "post", "3ka1"), items)
    }

    @Test
    fun timelinePagesAcrossPostsAndActivities() {
        aliceProposes()
        follow(bob, alice)
        repeat(3) { put(alice, Nsid.FEED_ACTIVITY, "3ka$it", activityJson(now - (it * 2 + 2).hours)) }
        share(createdAt = now - 3.hours)
        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = timeline(bob, GetTimeline.Params(limit = 2, cursor = cursor))
            seen += page.feed.map { (it as? TimelineItem.Activity)?.view?.uri?.rkey ?: "post" }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf("3ka0", "post", "3ka1", "3ka2"), seen)
    }

    @Test
    fun aFilteredTimelineHasNoPosts() {
        aliceProposes()
        follow(bob, alice)
        share()
        assertTrue(timeline(bob, GetTimeline.Params(type = listOf("ride"))).posts.isEmpty())
        assertTrue(timeline(bob, GetTimeline.Params(since = now - 48.hours)).posts.isEmpty())
    }

    @Test
    fun aPostIsPlacedNoLaterThanItWasIndexed() {
        aliceProposes()
        share(createdAt = now + 48.hours)
        assertTrue(profilePosts(alice, alice).single().createdAt <= Clock.System.now())
    }

    // ---------------------------------------------------------------- Blocks, mutes, labels

    @Test
    fun aBlockHidesPostsEitherWay() {
        aliceProposes()
        follow(bob, alice)
        share()
        block(bob, alice)
        assertTrue(timelinePosts(bob).isEmpty())
        assertTrue(profilePosts(alice, bob).isEmpty())
    }

    @Test
    fun aMuteLeavesTheTimelineButNotTheProfile() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        spaceWrite(
            space(AthleteSpace.INTERACTIONS, bob),
            Nsid.GRAPH_MUTE,
            alice,
            """{"subject":"$alice","createdAt":"2026-10-21T09:00:00Z"}""",
        )
        assertTrue(timelinePosts(bob).isEmpty())
        assertEquals(1, profilePosts(alice, bob).size)
    }

    private fun label(uri: String, value: String) = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null).apply(labeler, 1, listOf(testLabel(labeler, uri, value)))
    }

    @Test
    fun aTakenDownPostIsServedToNoOneElse() {
        aliceProposes()
        follow(bob, alice)
        share()
        label(postUri(), "!takedown")
        assertTrue(timelinePosts(bob).isEmpty())
    }

    @Test
    fun aTakenDownProposalTakesItsPostWithIt() {
        aliceProposes()
        follow(bob, alice)
        share()
        label(proposalUri(), "!takedown")
        assertTrue(timelinePosts(bob).isEmpty())
    }

    @Test
    fun droppingTheSpaceDropsItsPosts() {
        aliceProposes()
        share(tier = Visibility.FOLLOWERS)
        runBlocking { db.tx { assertEquals(1L, Posts.selectAll().count()) } }
        runBlocking { indexer.dropSpace(space(Visibility.FOLLOWERS)) }
        runBlocking { db.tx { assertEquals(0L, Posts.selectAll().count()) } }
    }

    private fun activityJson(at: kotlin.time.Instant) =
        """{"sport":"ride","startedAt":"${at - 1.hours}","elapsedTime":3600,"createdAt":"$at"}"""
}
