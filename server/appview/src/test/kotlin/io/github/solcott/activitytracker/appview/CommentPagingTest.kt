package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
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
import kotlinx.coroutines.runBlocking

/** `getComments`: an activity's comments a page at a time, or looked up by URI, by the same rule `getActivity`'s count uses. */
class CommentPagingTest : CommentTestBase() {
    @Test
    fun pagesListCommentsOldestFirst() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)

        val first = client.page(null)
        assertEquals(rkeys.take(30), first.comments.map { it.rkey }, "the 30 oldest, oldest first")
        val cursor = assertNotNull(first.cursor)

        val last = client.page(null, cursor = cursor)
        assertEquals(rkeys.drop(30), last.comments.map { it.rkey })
        assertNull(last.cursor, "no cursor after the last page")

        assertEquals(rkeys.take(10), client.page(null, limit = 10).comments.map { it.rkey })
        assertEquals(rkeys, client.allPages(null, limit = 7).map { it.rkey }, "no comment repeated or skipped")
    }

    @Test
    fun aPageHoldsAtMostOneHundred() = testApplication {
        start()
        post("3krun")
        bobComments(105)
        val page = client.page(null, limit = 500)
        assertEquals(100, page.comments.size)
        assertEquals(100, page.threads?.size)
        assertNotNull(page.cursor)
    }

    @Test
    fun aCommentIndexedBetweenPagesIsListedOnceOnThePageThatReachesIt() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(45)
        val first = client.page(null)
        comment(carol, "3knew", minute = 100)
        val second = client.page(null, cursor = first.cursor)
        assertEquals(rkeys.drop(30) + "3knew", second.comments.map { it.rkey })
        assertTrue(first.comments.none { it in second.comments })
    }

    @Test
    fun commentsAtTheSameMomentAreNeitherRepeatedNorSkipped() = testApplication {
        start()
        post("3krun")
        val same = listOf("3kca", "3kcb", "3kcc", "3kcd").onEach { comment(bob, it, minute = 1) }
        assertEquals(same, client.allPages(null, limit = 1).map { it.rkey })
        assertEquals(same, client.allPages(null, limit = 3).map { it.rkey })
    }

    @Test
    fun aMalformedCursorReadsAsTheFirstPage() = testApplication {
        start()
        post("3krun")
        val rkeys = bobComments(3)
        assertEquals(rkeys, client.page(null, cursor = "nonsense").comments.map { it.rkey })
        assertEquals(rkeys, client.page(null, cursor = "abc::at://x").comments.map { it.rkey })
    }

    @Test
    fun anActivityTheViewerMayNotSeeIsNotFound() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        val bobs = comment(bob, "3kcb", minute = 1, audience = Visibility.FOLLOWERS)
        for (viewer in listOf(dave, null)) {
            val page = client.raw(GetComments.method.nsid, viewer, pageQuery("3krun", null, null))
            assertEquals(HttpStatusCode.BadRequest, page.status)
            assertTrue("NotFound" in page.bodyAsText())
            assertTrue("3kcb" !in page.bodyAsText())
            val lookup =
                client.raw(
                    GetComments.method.nsid,
                    viewer,
                    "?uri=${uri("3krun").encodeURLParameter()}&uris=${bobs.value.encodeURLParameter()}",
                )
            assertEquals(HttpStatusCode.BadRequest, lookup.status)
            assertTrue("NotFound" in lookup.bodyAsText())
        }
    }

    @Test
    fun pagesApplyTheRuleForWhoSeesEachComment() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, bob) // bob is a mutual, carol isn't.
        follow(erin, alice)
        comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        comment(carol, "3kc2", minute = 2, audience = Visibility.FOLLOWERS)
        comment(bob, "3kc3", minute = 3, audience = Visibility.MUTUALS)
        val hidden = comment(carol, "3kc4", minute = 4, audience = Visibility.FOLLOWERS)
        comment(erin, "3kc5", minute = 5, audience = Visibility.FOLLOWERS)
        comment(dave, "3kc6", minute = 6) // dave can't see the activity, so his comment is shown to no one.
        hide(hidden)
        unfollow(erin, alice)

        fun List<CommentView>.seen() = map { it.rkey to it.hidden }

        assertEquals(
            listOf("3kc1" to null, "3kc2" to null, "3kc3" to null, "3kc4" to true),
            client.allPages(alice, limit = 2).seen(),
            "the athlete sees the hidden one marked",
        )
        assertEquals(listOf("3kc1" to null, "3kc2" to null, "3kc3" to null), client.allPages(bob, limit = 2).seen())
        assertEquals(
            listOf("3kc1" to null, "3kc2" to null, "3kc4" to true),
            client.allPages(carol, limit = 2).seen(),
            "not the mutuals one",
        )
        assertEquals(3, client.count(alice))
        assertEquals(3, client.count(bob))
        assertEquals(2, client.count(carol))
    }

    @Test
    fun aLookupReturnsJustTheListedOnesOfTheGivenComments() = testApplication {
        start()
        post("3krun", Visibility.FOLLOWERS)
        post("3kride", Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, bob)
        val hidden = comment(bob, "3kc1", minute = 1, audience = Visibility.FOLLOWERS)
        val shown = comment(carol, "3kc2", minute = 2, audience = Visibility.FOLLOWERS)
        val mutuals = comment(bob, "3kc3", minute = 3, audience = Visibility.MUTUALS)
        val deleted = comment(carol, "3kc4", minute = 4, audience = Visibility.FOLLOWERS)
        val elsewhere = comment(carol, "3kc5", minute = 5, activity = "3kride", audience = Visibility.FOLLOWERS)
        val never = AtUri("at://$carol/${Nsid.FEED_COMMENT}/3kc9")
        hide(hidden)
        runBlocking {
            indexer.spaceDelete(SpaceUri.of(Did(carol), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS), Nsid.FEED_COMMENT, "3kc4")
        }

        assertEquals(
            listOf("3kc1" to true, "3kc2" to null),
            client.lookup(alice, listOf(shown, hidden)).map { it.rkey to it.hidden },
            "oldest first, the hidden one marked",
        )
        assertEquals(emptyList(), client.lookup(alice, listOf(deleted, never)), "deleted and unknown comments are left out")
        assertEquals(emptyList(), client.lookup(carol, listOf(mutuals)), "carol isn't one of alice's mutuals")
        assertEquals(listOf("3kc3"), client.lookup(bob, listOf(mutuals)).map { it.rkey })
        assertEquals(emptyList(), client.lookup(alice, listOf(elsewhere)), "a comment on another activity")
        assertEquals(listOf("3kc5"), client.lookup(alice, listOf(elsewhere), activity = "3kride").map { it.rkey })
    }

    @Test
    fun aLookupOfMoreThanTwentyFiveIsInvalid() = testApplication {
        start()
        post("3krun")
        val uris = (0..25).map { AtUri("at://$bob/${Nsid.FEED_COMMENT}/3kc$it") }
        val response =
            client.raw(
                GetComments.method.nsid,
                null,
                "?uri=${uri("3krun").encodeURLParameter()}" + uris.joinToString("") { "&uris=${it.value.encodeURLParameter()}" },
            )
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("InvalidRequest" in response.bodyAsText())
        assertEquals(emptyList(), client.lookup(null, uris.take(25)))
    }

    @Test
    fun theCountMatchesThePages() = testApplication {
        start()
        // A Public run whose comments were written while it had other visibilities, so their audiences differ.
        post("3krun")
        follow(bob, alice)
        follow(carol, alice)
        follow(dave, alice)
        follow(alice, bob)
        val commenters = listOf(bob, carol, dave, erin)
        val audiences = listOf(null, Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF)
        val comments =
            (0 until 70).map { i ->
                comment(commenters[i % 4], "3kc" + i.toString().padStart(3, '0'), minute = i, audience = audiences[(i / 4) % 4])
            }
        comments.filter { it.authority == carol }.take(5).forEach(::hide)
        unfollow(dave, alice)

        // Alice, carol (whose comments she hid), bob, erin (who never followed) and someone signed out.
        for (viewer in listOf(alice, carol, bob, erin, null)) {
            val listed = client.allPages(viewer, limit = 30)
            assertEquals(listed.count { it.hidden != true }.toLong(), client.count(viewer), "the count matches the pages for $viewer")
            assertEquals(listed.map { it.uri }.distinct(), listed.map { it.uri }, "no comment listed twice for $viewer")
        }
        assertEquals(5, client.allPages(alice, limit = 30).count { it.hidden == true })
        // Two of carol's hidden ones were written for Mutuals and Only me, which don't admit her, hidden or not.
        assertEquals(3, client.allPages(carol, limit = 30).count { it.hidden == true })
        assertEquals(0, client.allPages(bob, limit = 30).count { it.hidden == true })

        val activity = client.query(GetActivity.method, alice, "?uri=${uri("3krun").encodeURLParameter()}")
        assertEquals(emptyList(), activity.comments, "getActivity no longer lists comments")
    }
}
