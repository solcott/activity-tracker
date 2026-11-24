package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject

/**
 * Likes on comments (feed/comment-likes): each listed comment's count of givers who may still see it, and the viewer's own like. A like is
 * a `feed.like` naming the comment, in the giver's repo when the comment is public and in their interactions space otherwise.
 */
class CommentLikesTest : CommentTestBase() {
    /** [giver] likes [comment] as their like [rkey], in their repo, or in their interactions space when [inSpace]. */
    private fun like(giver: String, comment: AtUri, rkey: String = "3kl${giver.last()}", inSpace: Boolean = false) {
        val json = """{"subject":{"uri":"${comment.value}","cid":"bafy${comment.rkey}"},"createdAt":"2026-10-14T11:00:00Z"}"""
        if (!inSpace) commit(giver, Nsid.FEED_LIKE, rkey, json)
        else
            runBlocking {
                indexer.spaceWrite(
                    SpaceUri.of(Did(giver), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS),
                    Nsid.FEED_LIKE,
                    rkey,
                    "bafy$rkey",
                    AtJson.parseToJsonElement(json).jsonObject,
                )
            }
    }

    /** [author] blocks [subject]: the app's block, in their interactions space at rkey = the subject. */
    private fun block(author: String, subject: String) = runBlocking {
        indexer.spaceWrite(
            SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS),
            Nsid.GRAPH_BLOCK,
            subject,
            "bafyblock",
            AtJson.parseToJsonElement("""{"subject":"$subject","createdAt":"2026-10-14T09:00:00Z"}""").jsonObject,
        )
    }

    private suspend fun HttpClient.listed(viewer: String?, comment: AtUri): CommentView = everything(viewer).single { it.uri == comment }

    @Test
    fun aCommentCountsItsGiversAndTellsTheViewerTheirs() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        like(carol, bobs, rkey = "3kl1")
        like(dave, bobs, rkey = "3kl2")
        val forCarol = client.listed(carol, bobs)
        assertEquals(2, forCarol.likeCount)
        assertEquals(AtUri("at://$carol/${Nsid.FEED_LIKE}/3kl1"), forCarol.viewer?.like)
        val forErin = client.listed(erin, bobs)
        assertEquals(2, forErin.likeCount)
        assertNull(forErin.viewer?.like, "erin hasn't liked it")
        assertNull(client.listed(null, bobs).viewer, "no viewer state signed out")
    }

    @Test
    fun aGiverCountsOnceAndNeverForTheirOwnComment() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        like(carol, bobs, rkey = "3kl1")
        like(carol, bobs, rkey = "3kl2")
        like(bob, bobs, rkey = "3kl3")
        assertEquals(1, client.listed(erin, bobs).likeCount)
    }

    @Test
    fun aLikeCountsOnlyWhileItsGiverMaySeeTheComment() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        val bobs = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        like(carol, bobs, inSpace = true)
        assertEquals(1, client.listed(bob, bobs).likeCount)
        unfollow(carol, alice)
        assertEquals(0, client.listed(bob, bobs).likeCount, "carol may no longer see the run")
    }

    @Test
    fun aReplysLikesAreCappedByItsRootsAudience() = testApplication {
        start()
        post("3krun")
        follow(bob, alice)
        val bobs = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        // A reply claiming Public in a Followers thread is still read at Followers.
        val reply = comment(bob, "3kc2", minute = 2, parent = bobs)
        like(dave, reply)
        follow(carol, alice)
        like(carol, reply)
        assertEquals(1, client.listed(bob, reply).likeCount, "dave doesn't follow alice, so his like isn't counted")
    }

    @Test
    fun aBlockLeavesTheGiversLikeOut() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        like(carol, bobs)
        like(dave, bobs)
        block(erin, carol)
        assertEquals(1, client.listed(erin, bobs).likeCount, "erin blocked carol")
        block(dave, bob)
        assertEquals(1, client.listed(alice, bobs).likeCount, "dave and bob are in a block")
    }
}
