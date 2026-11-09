package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.PostQuoteChains
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.dropQuoteChainsBy
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Indexing quote posts and their chains (feed/quote-posts). */
class QuoteChainsTest : PostTestBase() {
    private val dave = "did:plc:dave"

    private fun quoteRow(uri: String) = runBlocking {
        db.tx { Posts.selectAll().where { Posts.uri eq uri }.singleOrNull()?.let { it[Posts.quotedUri] to it[Posts.visibility] } }
    }

    /** [post]'s chain, by depth. */
    private fun chain(post: String): List<String> = runBlocking {
        db.tx {
            PostQuoteChains.selectAll()
                .where { PostQuoteChains.postUri eq post }
                .sortedBy { it[PostQuoteChains.depth] }
                .also { rows -> assertEquals((1..rows.size).toList(), rows.map { it[PostQuoteChains.depth] }) }
                .map { it[PostQuoteChains.ancestorUri] }
        }
    }

    @Test
    fun aQuoteFromTheRepoIsIndexed() {
        aliceProposes()
        share()
        quote(bob, "3kq")
        assertEquals(postUri() to Visibility.PUBLIC, quoteRow(postUri("3kq", bob)))
        assertEquals(listOf(postUri()), chain(postUri("3kq", bob)))
    }

    @Test
    fun aQuoteFromAFollowersSpaceIsIndexed() {
        aliceProposes()
        share(tier = Visibility.FOLLOWERS)
        quote(bob, "3kq", tier = Visibility.FOLLOWERS)
        assertEquals(postUri() to Visibility.FOLLOWERS, quoteRow(postUri("3kq", bob)))
    }

    @Test
    fun quotesWithoutTextAreIgnored() {
        aliceProposes()
        share()
        quote(bob, "3kq1", text = null)
        quote(bob, "3kq2", text = "   ")
        assertNull(quoteRow(postUri("3kq1", bob)))
        assertNull(quoteRow(postUri("3kq2", bob)))
    }

    @Test
    fun aQuoteOfAnythingButAPostIsIgnored() {
        aliceProposes()
        quote(bob, "3kq", subject = activityUri("3ka"))
        quote(bob, "3kq2", subject = "at://$alice/${Nsid.FEED_POST}")
        assertNull(quoteRow(postUri("3kq", bob)))
        assertNull(quoteRow(postUri("3kq2", bob)))
    }

    @Test
    fun aQuoteOfItselfIsIgnored() {
        aliceProposes()
        quote(bob, "3kq", subject = postUri("3kq", bob))
        assertNull(quoteRow(postUri("3kq", bob)))
    }

    @Test
    fun aChainOfThreeQuotesHasRowsAtEachDepth() {
        aliceProposes()
        share()
        quote(bob, "3kq1")
        quote(carol, "3kq2", subject = postUri("3kq1", bob))
        quote(dave, "3kq3", subject = postUri("3kq2", carol))
        assertEquals(listOf(postUri("3kq2", carol), postUri("3kq1", bob), postUri()), chain(postUri("3kq3", dave)))
    }

    @Test
    fun anAncestorIndexedLateExtendsItsDescendants() {
        aliceProposes()
        quote(dave, "3kq3", subject = postUri("3kq2", carol))
        quote(carol, "3kq2", subject = postUri("3kq1", bob))
        assertEquals(listOf(postUri("3kq2", carol), postUri("3kq1", bob)), chain(postUri("3kq3", dave)))
        quote(bob, "3kq1")
        share()
        assertEquals(listOf(postUri("3kq2", carol), postUri("3kq1", bob), postUri()), chain(postUri("3kq3", dave)))
    }

    @Test
    fun aRewriteThatChangesTheSubjectRebuildsDescendants() {
        aliceProposes()
        share()
        share(rkey = "3kp2", proposal = proposalUri("3kp2"))
        quote(bob, "3kq1")
        quote(carol, "3kq2", subject = postUri("3kq1", bob))
        quote(dave, "3kq3", subject = postUri("3kq2", carol))
        quote(bob, "3kq1", subject = postUri("3kp2"))
        assertEquals(listOf(postUri("3kq2", carol), postUri("3kq1", bob), postUri("3kp2")), chain(postUri("3kq3", dave)))
        quote(carol, "3kq2", subject = postUri("3kp2"))
        assertEquals(listOf(postUri("3kq2", carol), postUri("3kp2")), chain(postUri("3kq3", dave)))
    }

    @Test
    fun aDeleteDropsOnlyThePostsOwnRows() {
        aliceProposes()
        share()
        quote(bob, "3kq1")
        quote(carol, "3kq2", subject = postUri("3kq1", bob))
        remove(bob, Nsid.FEED_POST, "3kq1")
        assertEquals(emptyList(), chain(postUri("3kq1", bob)))
        assertEquals(listOf(postUri("3kq1", bob), postUri()), chain(postUri("3kq2", carol)))
    }

    @Test
    fun chainsStopOnePastTheServedLimit() {
        aliceProposes()
        share()
        var subject = postUri()
        for (i in 1..10) {
            quote(bob, "3kq$i", subject = subject)
            subject = postUri("3kq$i", bob)
        }
        assertEquals(PostQuoteChains.MAX_DEPTH, chain(postUri("3kq10", bob)).size)
        assertEquals(postUri("3kq1", bob), chain(postUri("3kq10", bob)).last())
    }

    @Test
    fun aQuoteClosingALoopIsIgnored() {
        aliceProposes()
        quote(bob, "3kq1", subject = postUri("3kq2", carol))
        quote(carol, "3kq2", subject = postUri("3kq1", bob))
        assertNull(quoteRow(postUri("3kq2", carol)))
        assertEquals(listOf(postUri("3kq2", carol)), chain(postUri("3kq1", bob)))
    }

    @Test
    fun deletingAnAccountDropsOnlyItsOwnChainsWhateverItsDid() {
        // A did:web's encoded port holds `%`, which a LIKE pattern would read as a wildcard.
        val deleted = "did:web:host%3A8081"
        val other = "did:web:hostX%3A8081"
        val ownPost = "at://$deleted/${Nsid.FEED_POST}/3kq1"
        val otherPost = "at://$other/${Nsid.FEED_POST}/3kq1"
        runBlocking {
            db.tx {
                PostQuoteChains.batchInsert(listOf(ownPost, otherPost)) { post ->
                    this[PostQuoteChains.postUri] = post
                    this[PostQuoteChains.ancestorUri] = postUri()
                    this[PostQuoteChains.depth] = 1
                }
                dropQuoteChainsBy(Did(deleted))
            }
        }
        assertEquals(emptyList(), chain(ownPost))
        assertEquals(listOf(postUri()), chain(otherPost))
    }
}
