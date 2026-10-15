package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject

/** Replies in `getComments`: indexing, the root's audience cap, pages of threads, placeholders, a thread's replies, and the count. */
class CommentThreadsTest : CommentTestBase() {
    @Test
    fun aReplyIsIndexedInItsRootsThread() {
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        val carols = comment(carol, "3kc2", minute = 2, parent = bobs)
        val daves = comment(dave, "3kc3", minute = 3, parent = carols, root = bobs)
        assertEquals(bobs.value to null, threadOf(bobs), "a top-level comment is its own thread")
        assertEquals(bobs.value to bobs.value, threadOf(carols))
        assertEquals(bobs.value to carols.value, threadOf(daves), "a reply to a reply joins the same thread")
    }

    @Test
    fun aReplyToSomethingOtherThanACommentIsSkipped() {
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        val like = AtUri("at://$bob/${Nsid.FEED_LIKE}/3kl1")
        assertNull(threadOf(comment(carol, "3kc2", minute = 2, parent = like, root = bobs)), "a like as parent")
        assertNull(threadOf(comment(carol, "3kc3", minute = 3, parent = bobs, root = like)), "a like as root")
        assertNull(threadOf(comment(carol, "3kc4", minute = 4, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}"))), "no rkey")

        // An edit that makes a listed comment's reply malformed drops it, rather than leaving the earlier version listed.
        val edited = comment(carol, "3kc5", minute = 5, parent = bobs)
        assertEquals(bobs.value to bobs.value, threadOf(edited))
        comment(carol, "3kc5", minute = 5, parent = like, root = bobs)
        assertNull(threadOf(edited))
    }

    @Test
    fun aReplyToAFollowersCommentOnANowPublicActivityStaysWithFollowers() = testApplication {
        start()
        post("3krun") // Since made Public: bob wrote for Followers.
        follow(bob, alice)
        follow(carol, alice)
        val bobs = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        comment(carol, "3kc2", minute = 2, audience = Visibility.FOLLOWERS, parent = bobs)
        assertEquals(listOf("3kc1" to listOf("3kc2")), client.threads(carol).threads!!.map { it.shape() })
        assertEquals(emptyList(), client.threads(erin).threads)
        assertEquals(emptyList(), client.threads(null).threads)
        assertEquals(2, client.count(carol))
        assertEquals(0, client.count(erin))
    }

    @Test
    fun aMutualsReplyOnlyReachesMutuals() = testApplication {
        start()
        post("3krun", Visibility.MUTUALS)
        follow(bob, alice)
        follow(alice, bob)
        follow(carol, alice)
        follow(alice, carol)
        val carols = comment(carol, "3kc1", minute = 1) // Public, from when the run was.
        comment(bob, "3kc2", minute = 2, audience = Visibility.MUTUALS, parent = carols)
        assertEquals(listOf("3kc1" to listOf("3kc2")), client.threads(carol).threads!!.map { it.shape() })
        assertEquals(Visibility.MUTUALS, client.threads(carol).threads!!.single().replies.single().audience)
        assertNull(client.threads(carol).threads!!.single().root!!.audience, "public is absent")
    }

    @Test
    fun aWiderReplyFromAnotherClientIsCappedByItsRoot() = testApplication {
        start()
        post("3krun")
        follow(bob, alice)
        follow(alice, bob) // bob is a mutual.
        follow(dave, alice) // dave only follows.
        val bobs = comment(bob, "3kc1", minute = 1, audience = Visibility.MUTUALS)
        comment(carol, "3kc2", minute = 2, parent = bobs) // Public, in carol's repo.
        for (viewer in listOf(alice, bob)) {
            assertEquals(listOf("3kc1" to listOf("3kc2")), client.threads(viewer).threads!!.map { it.shape() }, "for $viewer")
            assertEquals(2, client.count(viewer))
        }
        for (viewer in listOf(dave, carol, null)) {
            assertEquals(emptyList(), client.threads(viewer).threads, "for $viewer")
            assertEquals(emptyList(), client.replies(viewer, bobs).comments)
            assertEquals(0, client.count(viewer))
        }
        // The cap holds only while the root is indexed.
        deleteSpaceComment(bob, "3kc1")
        assertEquals(listOf("?" to listOf("3kc2")), client.threads(dave).threads!!.map { it.shape() })
        assertEquals(1, client.count(dave))
    }

    @Test
    fun pagesListThreadsOldestFirst() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)
        // Replies, all later than every top-level comment, don't move their threads or become threads of their own.
        comment(carol, "3kr1", minute = 200, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}/3kc000"))
        comment(dave, "3kr2", minute = 201, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}/3kc044"))

        val first = client.threads(null)
        assertEquals(rkeys.take(30), first.threads!!.map { it.root!!.rkey }, "the 30 oldest threads, oldest first")
        assertEquals(listOf("3kr1"), first.threads!!.first().replies.map { it.rkey })
        assertEquals(rkeys.take(30), first.comments.map { it.rkey }, "comments holds the page's top-level ones, for older clients")
        val last = client.threads(null, cursor = assertNotNull(first.cursor))
        assertEquals(rkeys.drop(30), last.threads!!.map { it.root!!.rkey })
        assertEquals(listOf("3kr2"), last.threads!!.last().replies.map { it.rkey })
        assertNull(last.cursor)
        assertTrue(last.comments.none { it.rkey.startsWith("3kr") }, "no reply in comments")

        assertEquals(rkeys.take(10), client.threads(null, limit = 10).threads!!.map { it.root!!.rkey })
        assertEquals(rkeys, client.allThreads(null, limit = 7).map { it.root!!.rkey }, "no thread repeated or skipped")
    }

    @Test
    fun aBackdatedReplyDoesNotMoveItsThreadEarlier() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)
        val first = client.threads(null)
        // Dated before every comment, but indexed now: the thread on page 2 stays there, and the one on page 1 isn't listed again.
        comment(carol, "3kr1", minute = -1000, indexedMinute = 300, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}/${rkeys[40]}"))
        comment(carol, "3kr2", minute = -1000, indexedMinute = 301, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}/${rkeys[5]}"))
        val second = client.threads(null, cursor = first.cursor)
        assertEquals(rkeys.drop(30), second.threads!!.map { it.root!!.rkey })

        // Nor can a backdated reply under a placeholder pin its thread to the top.
        val daves = comment(dave, "3kd1", minute = 50)
        comment(carol, "3kr3", minute = -1000, indexedMinute = 302, parent = daves)
        commit(dave, Nsid.FEED_COMMENT, "3kd1", null)
        assertEquals("?" to listOf("3kr3"), client.allThreads(null, limit = 30).last().shape(), "placed when it was indexed")
    }

    @Test
    fun aReplyArrivingBetweenPagesDoesNotRepeatItsThread() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)
        val first = client.threads(null)
        comment(carol, "3kr1", minute = 300, parent = AtUri("at://$bob/${Nsid.FEED_COMMENT}/${rkeys[5]}"))
        val second = client.threads(null, cursor = first.cursor)
        assertEquals(rkeys.drop(30), second.threads!!.map { it.root!!.rkey })
    }

    @Test
    fun aThreadComesWithItsThreeOldestReplies() = testApplication {
        start()
        post("3krun")
        follow(bob, alice)
        follow(alice, bob)
        val five = comment(bob, "3kc1", minute = 1)
        val two = comment(bob, "3kc2", minute = 2)
        val fourOneMutuals = comment(bob, "3kc3", minute = 3)
        (1..5).forEach { comment(carol, "3kr1$it", minute = 10 + it, parent = five) }
        (1..2).forEach { comment(carol, "3kr2$it", minute = 20 + it, parent = two) }
        (1..4).forEach {
            comment(dave, "3kr3$it", minute = 30 + it, parent = fourOneMutuals, audience = Visibility.MUTUALS.takeIf { _ -> it == 2 })
        }

        val threads = client.threads(carol).threads!!
        assertEquals(
            listOf(
                "3kc1" to listOf("3kr11", "3kr12", "3kr13"),
                "3kc2" to listOf("3kr21", "3kr22"),
                "3kc3" to listOf("3kr31", "3kr33", "3kr34"),
            ),
            threads.map { it.shape() },
        )
        assertEquals(listOf(5, 2, 3), threads.map { it.replyCount })
        assertNotNull(threads[0].repliesCursor)
        assertNull(threads[1].repliesCursor)
        assertNull(threads[2].repliesCursor, "the Mutuals reply isn't listed for carol")
        assertEquals(listOf("3kr14", "3kr15"), client.replies(carol, five, threads[0].repliesCursor).comments.map { it.rkey })
        assertEquals(4, client.threads(bob).threads!![2].replyCount, "bob is a mutual")
    }

    @Test
    fun aThreadWhoseRootIsNotListedIsAPlaceholderPlacedByItsOldestReply() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        follow(dave, alice)
        follow(erin, alice)
        val deleted = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        val hidden = comment(bob, "3kc2", minute = 2, audience = Visibility.FOLLOWERS)
        val unfollowed = comment(erin, "3kc3", minute = 3, audience = Visibility.FOLLOWERS)
        comment(dave, "3kc4", minute = 20, audience = Visibility.FOLLOWERS)
        comment(carol, "3kr1", minute = 30, audience = Visibility.FOLLOWERS, parent = deleted)
        comment(carol, "3kr2", minute = 10, audience = Visibility.FOLLOWERS, parent = hidden)
        comment(carol, "3kr3", minute = 40, audience = Visibility.FOLLOWERS, parent = unfollowed)
        deleteSpaceComment(bob, "3kc1")
        hide(hidden)
        unfollow(erin, alice)

        // Placed by their oldest listed reply: 3kr2 (10) before dave's (20), then 3kr1 (30), 3kr3 (40).
        assertEquals(
            listOf("?" to listOf("3kr2"), "3kc4" to emptyList(), "?" to listOf("3kr1"), "?" to listOf("3kr3")),
            client.threads(carol).threads!!.map { it.shape() },
        )
        for (viewer in listOf(alice, bob)) {
            val threads = client.threads(viewer).threads!!
            assertEquals("3kc2" to listOf("3kr2"), threads[0].shape(), "$viewer sees the hidden root marked")
            assertEquals(true, threads[0].root!!.hidden)
        }
        assertEquals(4, client.count(carol), "carol's three replies and dave's comment; no placeholder")

        val placeholder = client.threads(carol).threads!!.first()
        assertEquals(hidden, placeholder.uri)
        val json = AtJson.encodeToJsonElement(ThreadView.serializer(), placeholder.copy(replies = emptyList())).jsonObject
        assertEquals(setOf("uri", "replies", "replyCount"), json.keys, "the placeholder reveals nothing")
    }

    @Test
    fun aThreadNoneOfWhoseRepliesAreListedIsNotListed() = testApplication {
        start()
        post("3krun")
        follow(carol, alice)
        follow(alice, carol)
        val bobs = comment(bob, "3kc1", minute = 1)
        comment(carol, "3kr1", minute = 2, audience = Visibility.MUTUALS, parent = bobs)
        commit(bob, Nsid.FEED_COMMENT, "3kc1", null)
        assertEquals(emptyList(), client.threads(dave).threads)
        assertEquals(emptyList(), client.threads(null).threads)
        assertEquals(listOf("?" to listOf("3kr1")), client.threads(alice).threads!!.map { it.shape() })
    }

    @Test
    fun aReplyWhoseRootIsOnAnotherActivityIsListedOnlyUnderAPlaceholderOnItsOwn() = testApplication {
        start()
        post("3krun")
        post("3kride")
        val onRide = comment(bob, "3kc1", minute = 1, activity = "3kride")
        comment(carol, "3kr1", minute = 2, parent = onRide) // On the run.
        assertEquals(listOf("?" to listOf("3kr1")), client.threads(null).threads!!.map { it.shape() })
        assertEquals(listOf("3kc1" to emptyList<String>()), client.threads(null, activity = "3kride").threads!!.map { it.shape() })
        assertEquals(1, client.count(null))
        assertEquals(1, client.count(null, activity = "3kride"))
    }

    @Test
    fun theRestOfAThreadsRepliesComeAPageAtATime() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        val replies = (0 until 80).map { i -> comment(carol, "3kr" + i.toString().padStart(3, '0'), minute = 2 + i, parent = bobs) }
        val thread = client.threads(null).threads!!.single()
        assertEquals(80, thread.replyCount)
        val listed = thread.replies.toMutableList()
        var cursor = thread.repliesCursor
        while (cursor != null) {
            val page = client.replies(null, bobs, cursor, limit = 30)
            assertTrue(page.comments.size <= 30)
            listed += page.comments
            cursor = page.cursor
        }
        assertEquals(replies, listed.map { it.uri }, "each reply once, oldest first")
        assertEquals(replies.take(30), client.replies(null, bobs).comments.map { it.uri }, "with no cursor, from the first reply")
        assertTrue(listed.all { it.root?.uri == bobs && it.parent?.uri == bobs && it.root?.cid?.value == "bafy3kc1" })
    }

    @Test
    fun threadModeOnAnActivityTheViewerMayNotSeeIsNotFound() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        val bobs = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        comment(carol, "3kr1", minute = 2, audience = Visibility.FOLLOWERS, parent = bobs)
        for (viewer in listOf(dave, null)) {
            val response = client.rawReplies(viewer, bobs)
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue("NotFound" in response.bodyAsText())
            assertTrue("3kr1" !in response.bodyAsText())
        }
    }

    @Test
    fun aReplyIsLookedUpWithItsThread() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        val carols = comment(carol, "3kr1", minute = 2, parent = bobs)
        val daves = comment(dave, "3kr2", minute = 3, parent = carols, root = bobs)
        val found = client.lookup(dave, listOf(daves, carols))
        assertEquals(listOf(carols, daves), found.map { it.uri })
        assertEquals(bobs, found[1].root?.uri)
        assertEquals(carols, found[1].parent?.uri)
        assertEquals("bafy3kr1", found[1].parent?.cid?.value)
        assertNull(client.lookup(dave, listOf(bobs)).single().root, "a top-level comment names no thread")

        val both =
            client.raw(
                GetComments.method.nsid,
                null,
                "?uri=${uri("3krun").encodeURLParameter()}&uris=${daves.value.encodeURLParameter()}" +
                    "&root=${bobs.value.encodeURLParameter()}",
            )
        assertEquals(HttpStatusCode.BadRequest, both.status)
        assertTrue("InvalidRequest" in both.bodyAsText())
    }

    @Test
    fun theCountMatchesThePagesOfThreadsAndReplies() = testApplication {
        start()
        post("3krun")
        follow(bob, alice)
        follow(carol, alice)
        follow(dave, alice)
        follow(alice, bob)
        val commenters = listOf(bob, carol, dave, erin)
        val audiences = listOf(null, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)
        // 20 top-level comments, then 60 replies spread over them (some of them replies to replies), with mixed audiences.
        val roots =
            (0 until 20).map { i ->
                comment(commenters[i % 4], "3kc" + i.toString().padStart(3, '0'), minute = i, audience = audiences[(i / 4) % 4])
            }
        // 60 replies over the first 7 threads, so some page beyond their first three; every other one answers an earlier reply.
        val replies = mutableListOf<AtUri>()
        for (i in 0 until 60) {
            replies +=
                comment(
                    commenters[(i + 1) % 4],
                    "3kr" + i.toString().padStart(3, '0'),
                    minute = 100 + i,
                    audience = audiences[(i / 3) % 4],
                    parent = if (i >= 7 && i % 2 == 0) replies[i - 7] else roots[i % 7],
                    root = roots[i % 7],
                )
        }
        (roots + replies).filter { it.authority == carol }.take(6).forEach(::hide)
        commit(bob, Nsid.FEED_COMMENT, "3kc000", null) // A deleted root with replies: a placeholder for everyone.
        unfollow(dave, alice)

        assertTrue(client.allThreads(alice, limit = 30).any { it.repliesCursor != null }, "some threads page their replies")
        for (viewer in listOf(alice, carol, bob, erin, null)) {
            val listed = client.everything(viewer, threadLimit = 4, replyLimit = 2)
            assertEquals(listed.count { it.hidden != true }.toLong(), client.count(viewer), "the count matches the pages for $viewer")
            assertEquals(listed.map { it.uri }.distinct(), listed.map { it.uri }, "nothing listed twice for $viewer")
        }
    }

    @Test
    fun aPlaceholderIsNotCounted() = testApplication {
        start()
        post("3krun")
        val bobs = comment(bob, "3kc1", minute = 1)
        comment(carol, "3kr1", minute = 2, parent = bobs)
        comment(dave, "3kr2", minute = 3, parent = bobs)
        commit(bob, Nsid.FEED_COMMENT, "3kc1", null)
        assertEquals(listOf("?" to listOf("3kr1", "3kr2")), client.threads(null).threads!!.map { it.shape() })
        assertEquals(2, client.count(null))
    }
}
