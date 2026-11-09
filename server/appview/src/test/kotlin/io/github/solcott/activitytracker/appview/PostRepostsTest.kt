package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetReposts
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Reposts of posts (feed/reposts): indexed like likes, shown only while both the viewer and the reposter may see the post. */
class PostRepostsTest : PostTestBase() {
    private val dave = "did:plc:dave"
    private val post
        get() = AtUri(postUri())

    /** Where a repost of a post at [tier] goes: the reposter's repo when public, else their interactions space. */
    private fun placeOf(tier: String) = if (tier == Visibility.PUBLIC) Visibility.PUBLIC else AthleteSpace.INTERACTIONS

    private fun repost(author: String, rkey: String, tier: String, subject: String = postUri()) =
        put(author, Nsid.FEED_REPOST, rkey, """{"subject":{"uri":"$subject","cid":"bafy"},"createdAt":"$now"}""", placeOf(tier))

    private fun unrepost(author: String, rkey: String, tier: String) = remove(author, Nsid.FEED_REPOST, rkey, placeOf(tier))

    private fun repostRows(): Long = runBlocking { db.tx { Reposts.selectAll().count() } }

    private fun reposters(viewer: String?, limit: Int? = null): List<String> = runBlocking {
        buildList {
            var cursor: String? = null
            do {
                val page = views.reposts(viewer?.let(::Did), GetReposts.Params(post, limit = limit, cursor = cursor))
                addAll(page.reposts.map { it.did.value })
                cursor = page.cursor
            } while (cursor != null)
        }
    }

    private fun postFor(viewer: String?): PostView = runBlocking {
        views.post(viewer?.let(::Did), io.github.solcott.activitytracker.lexicon.app.GetPost.Params(post)).post
    }

    private fun repostsNotFound(viewer: String?, uri: AtUri = post) {
        val error = assertFailsWith<XrpcError> { runBlocking { views.reposts(viewer?.let(::Did), GetReposts.Params(uri)) } }
        assertEquals("NotFound", error.error)
    }

    private fun knownDave() = commit(dave, Nsid.ACTOR_PROFILE, "self", """{"displayName":"d"}""")

    private fun mute(author: String, subject: String) =
        spaceWrite(space(AthleteSpace.INTERACTIONS, author), Nsid.GRAPH_MUTE, subject, """{"subject":"$subject","createdAt":"$now"}""")

    // ---------------------------------------------------------------- Indexing

    @Test
    fun aRepostIsIndexedFromTheRepoAndFromAnInteractionsSpace() {
        aliceProposes()
        follow(bob, alice)
        follow(carol, alice)
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        repost(carol, "3kr2", Visibility.FOLLOWERS)
        assertEquals(2, repostRows())
    }

    @Test
    fun aRepostOfAnActivityOrOfOnesOwnPostIsSkipped() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC, subject = "at://$alice/${Nsid.FEED_ACTIVITY}/3ka")
        repost(alice, "3kr2", Visibility.PUBLIC)
        assertEquals(0, repostRows())
    }

    @Test
    fun aRecordDeleteASpaceDropAndAnAccountDeleteEachRemoveTheRow() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        repost(bob, "3kr1", Visibility.FOLLOWERS)
        unrepost(bob, "3kr1", Visibility.FOLLOWERS)
        assertEquals(0, repostRows())

        repost(bob, "3kr2", Visibility.FOLLOWERS)
        runBlocking { indexer.dropSpace(space(AthleteSpace.INTERACTIONS, bob)) }
        assertEquals(0, repostRows())

        repost(bob, "3kr3", Visibility.PUBLIC)
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = bob,
                    time = "2026-10-14T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(bob, active = false, status = "deleted"),
                ),
            )
        }
        assertEquals(0, repostRows())
    }

    // ---------------------------------------------------------------- Serving

    @Test
    fun aFollowersRepostIsCountedAndListedWithTheViewersOwn() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        repost(bob, "3kr1", Visibility.FOLLOWERS)
        val seen = postFor(bob)
        assertEquals(1, seen.repostCount)
        assertEquals("at://$bob/${Nsid.FEED_REPOST}/3kr1", seen.viewer?.repost?.value)
        assertEquals(1, postFor(alice).repostCount)
        assertNull(postFor(alice).viewer?.repost)
        assertEquals(listOf(bob), reposters(alice))
    }

    @Test
    fun narrowingLeavesOnlyRepostsFromRepostersWhoMaySeeIt() {
        aliceProposes()
        knownDave()
        follow(bob, alice)
        follow(alice, bob)
        follow(dave, alice)
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        repost(dave, "3kr2", Visibility.PUBLIC)
        assertEquals(2, postFor(null).repostCount)
        // Moved to Mutuals: only bob is a mutual, so dave's repost, stored in his repo, no longer counts.
        share(tier = Visibility.MUTUALS)
        remove(alice, Nsid.FEED_POST, "3kp")
        assertEquals(1, postFor(bob).repostCount)
        assertEquals(listOf(bob), reposters(alice))
    }

    @Test
    fun aReposterWhoUnfollowsIsNoLongerCounted() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        repost(bob, "3kr1", Visibility.FOLLOWERS)
        commit(bob, Nsid.GRAPH_FOLLOW, "3kfbe", null)
        assertEquals(0, postFor(alice).repostCount)
        assertEquals(emptyList(), reposters(alice))
    }

    @Test
    fun aViewerWhoMayNotSeeThePostGetsNotFound() {
        aliceProposes()
        follow(bob, alice)
        share(tier = Visibility.FOLLOWERS)
        repost(bob, "3kr1", Visibility.FOLLOWERS)
        repostsNotFound(carol)
        repostsNotFound(null)
        repostsNotFound(alice, AtUri("at://$alice/${Nsid.FEED_ACTIVITY}/3ka"))
    }

    @Test
    fun repostsGoWithTheirPost() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        assertEquals(listOf(bob), reposters(carol))
        // The proposal withdrawn: the post isn't served, so neither are its reposts.
        commit(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kp", null)
        repostsNotFound(carol)
        repostsNotFound(alice)
    }

    @Test
    fun aDeletedPostTakesItsRepostsWithIt() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        remove(alice, Nsid.FEED_POST, "3kp")
        repostsNotFound(carol)
    }

    @Test
    fun aTakenDownPostTakesItsRepostsWithIt() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        label(postUri(), "!takedown")
        repostsNotFound(carol)
    }

    @Test
    fun aBlockBetweenViewerAndReposterLeavesThemOutForTheViewerOnly() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        block(carol, bob)
        assertEquals(0, postFor(carol).repostCount)
        assertEquals(emptyList(), reposters(carol))
        assertEquals(listOf(bob), reposters(alice))
    }

    @Test
    fun aBlockBetweenReposterAndAuthorHidesTheRepostForEveryone() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        block(alice, bob)
        assertEquals(0, postFor(carol).repostCount)
        assertEquals(emptyList(), reposters(carol))
    }

    @Test
    fun aHiddenReposterAccountIsLeftOut() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        label(bob, "!hide")
        assertEquals(0, postFor(carol).repostCount)
        assertEquals(emptyList(), reposters(carol))
    }

    @Test
    fun aMuteChangesNoCountOrList() {
        aliceProposes()
        share()
        repost(bob, "3kr1", Visibility.PUBLIC)
        mute(carol, bob)
        assertEquals(1, postFor(carol).repostCount)
        assertEquals(listOf(bob), reposters(carol))
    }

    @Test
    fun theCountMatchesTheListAcrossPagesAndEveryRead() {
        aliceProposes()
        knownDave()
        follow(bob, alice)
        follow(carol, alice)
        follow(dave, alice)
        share(tier = Visibility.FOLLOWERS)
        repost(bob, "3kr1", Visibility.FOLLOWERS)
        repost(carol, "3kr2", Visibility.FOLLOWERS)
        repost(dave, "3kr3", Visibility.PUBLIC) // In his repo, by a client placing it wrongly: still counted once.
        repost(dave, "3kr4", Visibility.FOLLOWERS) // A second record by the same reposter.
        val listed = reposters(alice, limit = 1)
        assertEquals(setOf(bob, carol, dave), listed.toSet())
        assertEquals(listed.size, listed.toSet().size, "each reposter once")
        assertEquals(3, postFor(alice).repostCount)
        assertEquals(3, profilePosts(alice, alice).single().repostCount)
        assertEquals(3, timelinePosts(alice).single().repostCount)
    }
}
