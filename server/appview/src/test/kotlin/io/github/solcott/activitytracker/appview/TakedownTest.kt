package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.labels.Label
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/** `!takedown` (`moderation/labels`): gone for everyone but the author, signed out included, until negated. */
class TakedownTest : ModerationTestBase() {
    @Test
    fun aTakenDownActivityIsGoneForOthersAndStaysForItsAuthor() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        follow(alice, bob)
        post(bob, "3kb")
        post(bob, "3kf", Visibility.FOLLOWERS)
        label(activityUri(bob, "3kb"), "!takedown")

        assertEquals(listOf(activityUri(bob, "3kf")), client.timeline(alice))
        assertEquals(notFound, client.activityStatus(activityUri(bob, "3kb"), alice))
        assertEquals(notFound, client.activityStatus(activityUri(bob, "3kb"), null), "signed out too")
        assertEquals(1L, client.query(GetProfile.method, alice, "?actor=$bob").activitiesCount, "and out of the count")
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), bob), "bob still sees it")
        val own = client.query(GetAuthorActivities.method, bob, "?actor=$bob").activities.map { it.uri.rkey!! }
        assertEquals(listOf("3kb", "3kf"), own.sorted())

        label(activityUri(bob, "3kb"), "!takedown", neg = true)
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), null), "served again once negated")
    }

    @Test
    fun aLabelPinnedToAVersionDoesntFollowAnEdit() = testApplication {
        start()
        profile(bob)
        post(bob, "3kb")
        label(activityUri(bob, "3kb"), "!takedown", cid = "bafy3kb")
        assertEquals(notFound, client.activityStatus(activityUri(bob, "3kb"), null))
        // Bob edits it: a new CID, which the label doesn't name.
        val edited = """{"sport":"run","startedAt":"2026-10-02T07:00:00Z","elapsedTime":60,"createdAt":"2026-10-02T07:31:00Z"}"""
        commit(bob, Nsid.FEED_ACTIVITY, "3kb", edited, cid = "bafy3kbedited")
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), null))
    }

    @Test
    fun aTakenDownCommentIsGoneForOthersSignedInOrOut() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        post(alice, "3ka")
        comment(bob, alice, "3ka")
        comment(carol, alice, "3ka")
        label(commentUri(bob, alice), "!takedown")
        assertEquals(listOf(carol), client.commenters(activityUri(alice, "3ka"), alice))
        assertEquals(listOf(carol), client.commenters(activityUri(alice, "3ka"), null))
        assertEquals(listOf(bob, carol), client.commenters(activityUri(alice, "3ka"), bob).sorted(), "bob still sees his")
    }

    @Test
    fun aTakenDownAccountsProfileAnswersNotFound() = testApplication {
        start()
        listOf(alice, bob, carol).forEach(::profile)
        post(bob, "3kb")
        post(alice, "3ka")
        comment(bob, alice, "3ka")
        val unknown = client.profileStatus("did:plc:nobody", alice)
        label(bob, "!takedown")

        assertEquals(unknown, client.profileStatus(bob, alice))
        assertEquals(unknown, client.profileStatus(bob, null), "signed out too")
        assertEquals(HttpStatusCode.OK, client.profileStatus(bob, bob), "bob still sees his own")
        assertEquals(notFound, client.activityStatus(activityUri(bob, "3kb"), null))
        assertEquals(emptyList(), client.commenters(activityUri(alice, "3ka"), carol))

        label(bob, "!takedown", neg = true)
        assertEquals(HttpStatusCode.OK, client.profileStatus(bob, alice))
    }

    @Test
    fun aTakenDownProfileRecordAnswersNotFound() = testApplication {
        start()
        listOf(alice, bob).forEach(::profile)
        label("at://$bob/${Nsid.ACTOR_PROFILE}/self", "!takedown")
        assertEquals(notFound, client.profileStatus(bob, alice))
        assertEquals(HttpStatusCode.OK, client.profileStatus(bob, bob))
    }

    @Test
    fun anExpiredTakedownNoLongerApplies() = testApplication {
        start()
        profile(bob)
        post(bob, "3kb")
        label(Label(testLabel(labeler, activityUri(bob, "3kb"), "!takedown").fields + ("exp" to "2026-01-01T00:00:00Z")))
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), null), "its exp is long past")
    }

    @Test
    fun anUntrustedSourcesStoredLabelDoesNothing() = testApplication {
        // The labeler is no longer configured: what it stored stops applying.
        application { appView(config.copy(labeler = null), db, ServiceAuthVerifier(service, keys = { null })) }
        profile(bob)
        post(bob, "3kb")
        label(activityUri(bob, "3kb"), "!takedown")
        assertEquals(HttpStatusCode.OK, client.activityStatus(activityUri(bob, "3kb"), null))
    }
}
