package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** `moderation/account-status`: what Jetstream's `#account` events do to what's served. */
class AccountStatusTest : ModerationTestBase() {
    private fun bobPostsAndInteracts() {
        listOf(alice, bob, carol).forEach(::profile)
        follow(alice, bob)
        post(bob, "3kb")
        post(alice, "3ka")
        comment(bob, alice, "3ka")
        like(bob, alice, "3ka")
        comment(carol, alice, "3ka")
    }

    private suspend fun io.ktor.client.HttpClient.assertBobGone(viewer: String?) {
        assertEquals(notFound, profileStatus(bob, viewer), "bob's profile answers not found")
        assertEquals(notFound, activityStatus(activityUri(bob, "3kb"), viewer))
        assertEquals(listOf(carol), commenters(activityUri(alice, "3ka"), viewer))
        assertEquals(emptyList(), likers(activityUri(alice, "3ka"), viewer))
    }

    private suspend fun io.ktor.client.HttpClient.assertBobServed(viewer: String?) {
        assertEquals(HttpStatusCode.OK, profileStatus(bob, viewer))
        assertEquals(HttpStatusCode.OK, activityStatus(activityUri(bob, "3kb"), viewer))
        assertEquals(listOf(bob, carol), commenters(activityUri(alice, "3ka"), viewer))
        assertEquals(listOf(bob), likers(activityUri(alice, "3ka"), viewer))
    }

    @Test
    fun takenDownByThePds() = testApplication {
        start()
        bobPostsAndInteracts()
        account(bob, active = false, status = "takendown")
        client.assertBobGone(alice)
        client.assertBobGone(null)
        assertEquals(listOf(activityUri(alice, "3ka")), client.timeline(alice), "and out of the timeline")
    }

    @Test
    fun suspendedIsntServed() = testApplication {
        start()
        bobPostsAndInteracts()
        account(bob, active = false, status = "suspended")
        client.assertBobGone(alice)
    }

    @Test
    fun deactivatedIsKeptButNotServedUntilReactivated() = testApplication {
        start()
        bobPostsAndInteracts()
        account(bob, active = false, status = "deactivated")
        client.assertBobGone(alice)
        // Bob still sees his own.
        assertEquals(listOf("3kb"), client.query(GetAuthorActivities.method, bob, "?actor=$bob").activities.map { it.uri.rkey })

        account(bob, active = true)
        client.assertBobServed(alice)
        client.assertBobServed(null)
    }

    @Test
    fun anUnknownStatusChangesNothing() = testApplication {
        start()
        bobPostsAndInteracts()
        account(bob, active = false, status = "throttled")
        client.assertBobServed(alice)
    }

    @Test
    fun theStatusSurvivesARestart() {
        bobPostsAndInteracts()
        account(bob, active = false, status = "suspended")
        // A new AppView process over the same database.
        testApplication {
            application { appView(config, db, ServiceAuthVerifier(service, keys = { null })) }
            client.assertBobGone(null)
        }
    }
}
