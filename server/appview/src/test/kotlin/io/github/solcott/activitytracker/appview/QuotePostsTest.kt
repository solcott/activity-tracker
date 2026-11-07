package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetPost
import io.github.solcott.activitytracker.lexicon.app.GetQuotes
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking

/** Serving quote posts (feed/quote-posts): who is shown a quote, its quoted post, and quote counts and lists. */
class QuotePostsTest : PostTestBase() {
    private val dave = "did:plc:dave"
    private val bobQuote = postUri("3kq", bob)

    private fun postFor(viewer: String?, uri: String = bobQuote): PostView = runBlocking {
        views.post(viewer?.let(::Did), GetPost.Params(AtUri(uri))).post
    }

    private fun notFound(block: suspend () -> Unit) = assertEquals("NotFound", assertFailsWith<XrpcError> { runBlocking { block() } }.error)

    private fun quotesOf(viewer: String?, uri: String = postUri(), limit: Int? = null): List<String> = runBlocking {
        buildList {
            var cursor: String? = null
            do {
                val page = views.quotes(viewer?.let(::Did), GetQuotes.Params(AtUri(uri), limit = limit, cursor = cursor))
                addAll(page.posts.map { it.uri.value })
                cursor = page.cursor
            } while (cursor != null)
        }
    }

    private fun PostView.quoted(): QuotedPost = assertIs<PostEmbedView.Quote>(embed).quoted

    private fun shown(viewer: String, uri: String = bobQuote) = timelinePosts(viewer).any { it.uri.value == uri }

    /** Alice shares a Followers post; bob, her follower, quotes it for Followers; carol follows only bob, dave both. */
    private fun bobQuotesAlicesFollowersPost() {
        aliceProposes()
        share(tier = Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, bob)
        follow(dave, alice)
        follow(dave, bob)
        quote(bob, "3kq", tier = Visibility.FOLLOWERS)
    }

    // ---------------------------------------------------------------- Who is shown a quote

    @Test
    fun aViewerOutsideTheQuotedPostsAudienceIsNotShownTheQuote() {
        bobQuotesAlicesFollowersPost()
        put(dave, Nsid.FEED_LIKE, "3kl", """{"subject":{"uri":"$bobQuote","cid":"bafy"},"createdAt":"$now"}""", AthleteSpace.INTERACTIONS)
        assertTrue(shown(dave))
        assertEquals(1, postFor(dave).likeCount)
        assertTrue(!shown(carol))
        notFound { views.post(Did(carol), GetPost.Params(AtUri(bobQuote))) }
        assertTrue(profilePosts(bob, carol).isEmpty())
        notFound { views.quotes(Did(carol), GetQuotes.Params(AtUri(postUri()))) }
        notFound { views.likes(Did(carol), GetLikes.Params(AtUri(bobQuote))) }
    }

    @Test
    fun narrowingTheQuotedPostHidesTheQuoteFromThoseNowLeftOut() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        follow(carol, bob)
        assertTrue(shown(carol))
        share(tier = Visibility.MUTUALS)
        remove(alice, Nsid.FEED_POST, "3kp")
        assertTrue(!shown(carol))
        follow(carol, alice)
        follow(alice, carol)
        assertTrue(shown(carol))
    }

    @Test
    fun aBlockWithTheQuotedAuthorHidesTheQuote() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        follow(carol, bob)
        block(carol, alice)
        assertTrue(!shown(carol))
        notFound { views.post(Did(carol), GetPost.Params(AtUri(bobQuote))) }
    }

    @Test
    fun aQuoteOfAQuoteIsHiddenOutsideTheFirstPostsAudience() {
        bobQuotesAlicesFollowersPost()
        follow(carol, alice)
        quote(carol, "3kq2", subject = bobQuote, tier = Visibility.FOLLOWERS)
        val outsider = "did:plc:erin"
        follow(outsider, carol)
        follow(outsider, bob)
        assertTrue(!shown(outsider, postUri("3kq2", carol)))
        assertTrue(!shown(outsider))
        follow(dave, carol)
        assertTrue(shown(dave, postUri("3kq2", carol)))
    }

    @Test
    fun aQuoteNineLevelsDeepIsServedToNoOne() {
        aliceProposes()
        share()
        var subject = postUri()
        for (i in 1..9) {
            quote(bob, "3kq$i", subject = subject, createdAt = now - (20 - i).minutes)
            subject = postUri("3kq$i", bob)
        }
        val served = profilePosts(bob, bob).map { it.uri.value }
        assertTrue(postUri("3kq8", bob) in served)
        assertTrue(postUri("3kq9", bob) !in served)
        notFound { views.post(null, GetPost.Params(AtUri(postUri("3kq9", bob)))) }
    }

    @Test
    fun aQuotedPostServedToNoOneLeavesTheQuoteServed() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        assertIs<QuotedPost.Post>(postFor(carol).quoted())
        remove(alice, Nsid.FEED_POST, "3kp")
        assertEquals(QuotedPost.Unavailable(AtUri(postUri())), postFor(carol).quoted())
        share()
        label(postUri(), "!takedown")
        assertIs<QuotedPost.Unavailable>(postFor(carol).quoted())
    }

    @Test
    fun aWithdrawnProposalLeavesTheQuoteServed() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        remove(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp")
        assertIs<QuotedPost.Unavailable>(postFor(carol).quoted())
    }

    // ---------------------------------------------------------------- The quoted post

    @Test
    fun aQuotedProposalShowsAsItStandsNow() {
        bobQuotesAlicesFollowersPost()
        val quoted = assertIs<QuotedPost.Post>(postFor(dave).quoted())
        assertEquals(alice, quoted.author.did.value)
        assertEquals(Visibility.FOLLOWERS, quoted.visibility)
        assertEquals("Wiggly Woods", assertIs<PostEmbedView.Proposal>(quoted.embed).name)
    }

    @Test
    fun aQuotedQuoteShowsALink() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        quote(carol, "3kq2", subject = bobQuote)
        val quoted = assertIs<QuotedPost.Post>(postFor(dave, postUri("3kq2", carol)).quoted())
        assertEquals(bob, quoted.author.did.value)
        assertEquals(QuotedPostEmbed.Link(AtUri(postUri())), quoted.embed)
    }

    @Test
    fun theQuoterLosingAccessShowsTheQuotedPostUnavailable() {
        bobQuotesAlicesFollowersPost()
        assertIs<QuotedPost.Post>(postFor(dave).quoted())
        remove(bob, Nsid.GRAPH_FOLLOW, "3kfbe")
        assertEquals(QuotedPost.Unavailable(AtUri(postUri())), postFor(dave).quoted())
        follow(bob, alice)
        block(alice, bob)
        assertEquals(QuotedPost.Unavailable(AtUri(postUri())), postFor(dave).quoted())
    }

    // ---------------------------------------------------------------- Counts and lists

    @Test
    fun quoteCountsMatchTheListAcrossPages() {
        aliceProposes()
        share()
        follow(dave, carol)
        quote(bob, "3kq1", tier = Visibility.FOLLOWERS, createdAt = now - 10.minutes)
        quote(carol, "3kq2", createdAt = now - 9.minutes)
        quote(carol, "3kq3", createdAt = now - 8.minutes)
        quote(alice, "3kq4", createdAt = now - 7.minutes)
        val forDave = quotesOf(dave, limit = 1)
        assertEquals(listOf(postUri("3kq4"), postUri("3kq3", carol), postUri("3kq2", carol)), forDave)
        assertEquals(3, postFor(dave, postUri()).quoteCount)
        assertEquals(3, profilePosts(alice, dave).single { it.uri.value == postUri() }.quoteCount)
        assertEquals(3, timelinePosts(alice).single { it.uri.value == postUri() }.quoteCount)
        follow(alice, bob)
        assertEquals(4, quotesOf(alice).size)
        assertEquals(4, postFor(alice, postUri()).quoteCount)
    }

    @Test
    fun getQuotesIsNotFoundOutsideTheAudienceAndForNonPosts() {
        bobQuotesAlicesFollowersPost()
        notFound { views.quotes(Did(carol), GetQuotes.Params(AtUri(postUri()))) }
        notFound { views.quotes(Did(dave), GetQuotes.Params(AtUri(activityUri("3ka")))) }
        assertEquals(listOf(bobQuote), quotesOf(dave))
    }
}
