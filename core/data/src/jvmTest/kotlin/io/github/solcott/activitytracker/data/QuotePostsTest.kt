package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.FeedItemEntity
import io.github.solcott.activitytracker.data.feed.QuotePosts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Quote posts (feed/quote-posts): placed by their audience, shown first at once, counted on the quoted post, and deleted. */
class QuotePostsTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val writes = pds.writes
    private val stored = pds.stored
    private val bob = Did("did:plc:bob")
    private val at = Instant.parse("2026-11-07T09:00:00Z")

    private fun Session.quotes() = QuotePosts(clients, OutboxWriter(db, clients, Clock.System), outbox, Clock.System, feed)

    /** Bob's post at [visibility], as Alice sees it, cached in her timeline. */
    private suspend fun Session.post(visibility: String): PostView {
        val view =
            PostView(
                uri = AtUri.of(bob, Nsid.FEED_POST, "3kpost"),
                cid = Cid("bafypost"),
                author = ProfileViewBasic(bob, Handle("bob.test")),
                embed =
                    PostEmbedView.Proposal(
                        proposal = StrongRef(AtUri.of(bob, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kpost"), Cid("bafyq")),
                        name = "Wiggly Woods",
                        status = PostProposalStatus.OPEN,
                        openedAt = at,
                        up = 0,
                        down = 0,
                    ),
                createdAt = at,
                indexedAt = at,
                visibility = visibility.takeIf { it != Visibility.PUBLIC },
            )
        cache(view)
        return view
    }

    private suspend fun Session.cache(view: PostView) =
        db.feed()
            .upsert(listOf(FeedItemEntity("timeline", "post:${view.uri.value}", 0, AtJson.encodeToString(PostView.serializer(), view))))

    private suspend fun Session.timeline(): List<PostView> =
        feed.timelineItems().first().filterIsInstance<TimelineItem.Post>().map { it.post }

    private fun quoteRecord(place: String): Pair<String, PostRecord> {
        val write = writes.single { it.startsWith("put ") }
        val key = write.substringAfter(' ').substringAfter(' ')
        assertTrue(write.startsWith("put $place ${Nsid.FEED_POST}/"), write)
        return key.substringAfterLast('/') to AtJson.decodeFromJsonElement(PostRecord.serializer(), stored.getValue(place).getValue(key))
    }

    @Test
    fun aPublicQuoteGoesInTheRepo() = runTest {
        val s = pds.session()
        val post = s.post(Visibility.PUBLIC)
        s.quotes().quote(post, "  Yes, and the lower half  ", Visibility.PUBLIC)
        val (_, record) = quoteRecord(pds.repo)
        assertEquals(PostEmbed.Quote(StrongRef(post.uri, post.cid)), record.embed)
        assertEquals("Yes, and the lower half", record.text)
    }

    @Test
    fun aFollowersQuoteGoesInTheFollowersSpace() = runTest {
        val s = pds.session()
        s.quotes().quote(s.post(Visibility.FOLLOWERS), "Yes", Visibility.FOLLOWERS)
        quoteRecord(AthleteSpace.FOLLOWERS)
        assertTrue(writes.none { it.startsWith("put ${pds.repo} ") })
    }

    @Test
    fun aQuoteWiderThanThePostOrBlankIsRefused() = runTest {
        val s = pds.session()
        val post = s.post(Visibility.FOLLOWERS)
        assertFailsWith<IllegalArgumentException> { s.quotes().quote(post, "Yes", Visibility.PUBLIC) }
        assertFailsWith<IllegalArgumentException> { s.quotes().quote(post, "   ", Visibility.FOLLOWERS) }
        assertEquals(emptyList(), writes)
    }

    @Test
    fun aSessionWithoutTheSpaceScopeIsRefusedBeforeAnythingChanges() = runTest {
        val s = pds.session(scope = "atproto")
        val post = s.post(Visibility.FOLLOWERS)
        assertEquals(
            SpaceErrors.SCOPE_MISSING,
            assertFailsWith<XrpcException> { s.quotes().quote(post, "Yes", Visibility.FOLLOWERS) }.error,
        )
        assertEquals(emptyList(), writes)
        assertEquals(listOf(post), s.timeline())
        assertEquals(emptyMap(), s.feed.localPosts.changes.value)
    }

    @Test
    fun theQuoteShowsFirstAndCountsAtOnceAndThroughARefresh() = runTest {
        val s = pds.session()
        val post = s.post(Visibility.PUBLIC)
        val uri = s.quotes().quote(post, "Yes", Visibility.PUBLIC)
        val shown = s.timeline()
        assertEquals(listOf(uri, post.uri), shown.map { it.uri })
        assertEquals(post.uri, assertIs<QuotedPost.Post>(assertIs<PostEmbedView.Quote>(shown.first().embed).quoted).uri)
        assertEquals(1, shown.last().quoteCount)
        // A refresh brings back the AppView's view, which hasn't counted the quote yet; then one that has.
        s.cache(post)
        assertEquals(1, s.timeline().last().quoteCount)
        s.cache(post.copy(quoteCount = 1))
        assertEquals(1, s.timeline().last().quoteCount)
        val profile = assertIs<Outcome.Data<Page<PostView, Unit>>>(s.feed.authorPostsPage(did, null))
        assertEquals(uri, profile.data.items.first().uri)
    }

    @Test
    fun deletingRemovesItAtOnceAndDeletesTheRecordWhereItWasWritten() = runTest {
        val s = pds.session()
        val post = s.post(Visibility.MUTUALS)
        val quotes = s.quotes()
        val uri = quotes.quote(post, "Yes", Visibility.MUTUALS)
        val quote = s.timeline().first()
        writes.clear()
        quotes.delete(quote)
        assertEquals(listOf("delete ${AthleteSpace.MUTUALS} ${Nsid.FEED_POST}/${uri.rkey}"), writes)
        val shown = s.timeline()
        assertEquals(listOf(post.uri), shown.map { it.uri })
        assertEquals(0, shown.single().quoteCount)
    }

    @Test
    fun theQuotesListPutsTheViewersUnlistedQuoteFirst() = runTest {
        val s = pds.session()
        val post = s.post(Visibility.PUBLIC)
        val other = post.copy(uri = AtUri.of(bob, Nsid.FEED_POST, "3kother"), quoteCount = 0)
        val uri = s.quotes().quote(post, "Yes", Visibility.PUBLIC)
        s.quotes().quote(other, "Elsewhere", Visibility.PUBLIC)
        val served = post.copy(uri = AtUri.of(Did("did:plc:carol"), Nsid.FEED_POST, "3kcarol"))
        pds.quotesServed[post.uri.value] = listOf(served)
        val page = assertIs<Outcome.Data<Page<PostView, Unit>>>(s.feed.quotesPage(post.uri, null))
        assertEquals(listOf(uri, served.uri), page.data.items.map { it.uri })
    }

    @Test
    fun theQuotesListIsNotFoundForAPostTheViewerMayNotSee() = runTest {
        val s = pds.session()
        val failure = assertIs<Outcome.Error>(s.feed.quotesPage(AtUri.of(bob, Nsid.FEED_POST, "3kgone"), null))
        assertEquals("NotFound", assertIs<DataError.Api>(failure.cause).code)
    }
}
