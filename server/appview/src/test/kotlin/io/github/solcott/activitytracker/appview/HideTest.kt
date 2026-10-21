package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** `!hide` (`moderation/labels`): out of lists, feeds, counts and search for others, still reachable by URI. */
class HideTest : ModerationTestBase() {
    @Test
    fun aHiddenActivityLeavesFeedsAndCountsButOpensByUri() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        follow(alice, bob)
        post(bob, "3kb1")
        post(bob, "3kb2")
        label(activityUri(bob, "3kb1"), "!hide")

        assertEquals(listOf(activityUri(bob, "3kb2")), client.timeline(alice))
        val page = client.query(GetAuthorActivities.method, alice, "?actor=$bob").activities.map { it.uri.rkey }
        assertEquals(listOf("3kb2"), page)
        assertEquals(
            page.size.toLong(),
            client.query(GetProfile.method, alice, "?actor=$bob").activitiesCount,
            "the count matches the page",
        )
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb1"), alice), "still reachable by URI")
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb1"), null))

        // Bob sees his own as before.
        assertEquals(2, client.query(GetAuthorActivities.method, bob, "?actor=$bob").activities.size)
        assertEquals(2L, client.query(GetProfile.method, bob, "?actor=$bob").activitiesCount)
    }

    @Test
    fun aHiddenCommentIsNotListedOrCountedForOthers() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        post(alice, "3ka")
        comment(bob, alice, "3ka")
        comment(carol, alice, "3ka")
        label(commentUri(bob, alice), "!hide")
        // commenters checks the count against the list.
        assertEquals(listOf(carol), client.commenters(activityUri(alice, "3ka"), alice))
        assertEquals(listOf(carol), client.commenters(activityUri(alice, "3ka"), null))
        assertEquals(listOf(bob, carol), client.commenters(activityUri(alice, "3ka"), bob), "bob still sees it")
    }

    @Test
    fun aHiddenAccountLeavesEveryListButItsProfileOpens() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        follow(alice, bob)
        follow(bob, carol)
        follow(alice, carol)
        post(bob, "3kb")
        post(carol, "3kc")
        comment(bob, carol, "3kc")
        like(bob, carol, "3kc")
        label(bob, "!hide")

        assertEquals(listOf(activityUri(carol, "3kc")), client.timeline(alice))
        assertEquals(emptyList(), client.commenters(activityUri(carol, "3kc"), alice))
        assertEquals(emptyList(), client.likers(activityUri(carol, "3kc"), alice))
        assertEquals(emptyList(), client.query(SearchActors.method, alice, "?q=bob").actors)
        val followers = client.query(GetFollowers.method, alice, "?actor=$carol").followers.map { it.did.value }
        assertEquals(listOf(alice), followers)
        assertEquals(followers.size.toLong(), client.query(GetProfile.method, alice, "?actor=$carol").followersCount, "the count matches")
        assertEquals(HttpStatusCode.OK, client.profileStatus(bob, alice), "bob's profile still opens")
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), alice))

        label(bob, "!hide", neg = true)
        assertEquals(setOf(activityUri(bob, "3kb"), activityUri(carol, "3kc")), client.timeline(alice).toSet())
    }

    @Test
    fun anUnknownLabelValueChangesNothing() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        follow(alice, bob)
        post(bob, "3kb")
        label(activityUri(bob, "3kb"), "rude")
        assertEquals(listOf(activityUri(bob, "3kb")), client.timeline(alice))
    }
}
