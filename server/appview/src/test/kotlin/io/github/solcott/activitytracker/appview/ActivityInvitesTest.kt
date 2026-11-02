package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityInvitePairs
import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvitees
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvites
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Activity invites (feed/activity-invites) in the AppView: indexing invites from `self`, pairing copies, and the drops. */
class ActivityInvitesTest : SegmentTestBase() {
    private fun inviteRkey(rkey: String, invitee: String) = "$rkey:$invitee"

    private fun inviteUri(rkey: String, invitee: String, inviter: String = alice) =
        "at://$inviter/${Nsid.FEED_ACTIVITY_INVITE}/${inviteRkey(rkey, invitee)}"

    /** [inviter]'s invite of [invitee] to their activity [rkey], in their `self` space. */
    private fun invite(
        rkey: String,
        invitee: String,
        inviter: String = alice,
        createdAt: String = "2026-10-06T09:00:00Z",
        key: String = inviteRkey(rkey, invitee),
    ) =
        spaceWrite(
            self(inviter),
            Nsid.FEED_ACTIVITY_INVITE,
            key,
            """{"subject":"${activityUri(rkey, inviter)}","invitee":"$invitee","createdAt":"$createdAt"}""",
        )

    private fun cancel(rkey: String, invitee: String, inviter: String = alice) = runBlocking {
        indexer.spaceDelete(self(inviter), Nsid.FEED_ACTIVITY_INVITE, inviteRkey(rkey, invitee))
    }

    /** [did]'s activity [rkey], copied from [original] when given. */
    private fun copy(rkey: String, did: String, original: String?, tier: String = "public") =
        activity(rkey, tier = tier, sport = "ride", did = did, extra = original?.let { """"copyOf":"$it",""" }.orEmpty())

    private fun invites(): Set<String> = runBlocking {
        db.tx {
            ActivityInvites.selectAll()
                .map { "at://${it[ActivityInvites.inviterDid]}/${Nsid.FEED_ACTIVITY_INVITE}/${it[ActivityInvites.rkey]}" }
                .toSet()
        }
    }

    private fun invitePairs(): Set<Pair<String, String>> = runBlocking {
        db.tx { ActivityInvitePairs.selectAll().map { it[ActivityInvitePairs.originalUri] to it[ActivityInvitePairs.copyUri] }.toSet() }
    }

    @Test
    fun anInviteIsIndexedFromSelfOnly() {
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        assertEquals(setOf(inviteUri("3ka", bob)), invites())

        spaceWrite(
            space("followers"),
            Nsid.FEED_ACTIVITY_INVITE,
            inviteRkey("3ka", carol),
            """{"subject":"${activityUri("3ka")}","invitee":"$carol","createdAt":"2026-10-06T09:00:00Z"}""",
        )
        commit(
            alice,
            Nsid.FEED_ACTIVITY_INVITE,
            inviteRkey("3ka", carol),
            """{"subject":"${activityUri("3ka")}","invitee":"$carol","createdAt":"2026-10-06T09:00:00Z"}""",
        )
        assertEquals(setOf(inviteUri("3ka", bob)), invites(), "a copy in a tier space or the repo is ignored")
    }

    @Test
    fun anInviteUnderAnotherRkeyToAnothersActivityOrToOneselfIsIgnored() {
        activity("3ka", sport = "ride")
        invite("3ka", bob, key = "3kzzzzzzzzzzz")
        spaceWrite(
            self(alice),
            Nsid.FEED_ACTIVITY_INVITE,
            inviteRkey("3kb", carol),
            """{"subject":"${activityUri("3kb", bob)}","invitee":"$carol","createdAt":"2026-10-06T09:00:00Z"}""",
        )
        invite("3ka", alice)
        assertEquals(emptySet(), invites())
    }

    @Test
    fun aDeclineIsNeverStored() {
        val tables = runBlocking { db.tx { ActivityInvites.selectAll().count() } }
        spaceWrite(
            self(bob),
            Nsid.FEED_ACTIVITY_INVITE_DECLINE,
            "3kd",
            """{"subject":"${inviteUri("3ka", bob)}","createdAt":"2026-10-06T10:00:00Z"}""",
        )
        assertEquals(tables, runBlocking { db.tx { ActivityInvites.selectAll().count() } })
        assertEquals(emptySet(), invites())
    }

    @Test
    fun aValidCopyPairsAndDeletingTheInviteDropsThePair() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(setOf(activityUri("3ka") to activityUri("3kb", bob)), invitePairs())

        cancel("3ka", bob)
        assertEquals(emptySet(), invitePairs())
        assertEquals(emptySet(), invites())
    }

    @Test
    fun aForgedCopyDoesntPair() {
        follow(carol, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kc", carol, activityUri("3ka"))
        assertEquals(emptySet(), invitePairs(), "carol was never invited")
    }

    @Test
    fun anInviteThatDoesntCountPairsNothing() {
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(emptySet(), invitePairs(), "bob doesn't follow alice")

        follow(bob, alice)
        block(alice, bob)
        copy("3kc", bob, activityUri("3ka"))
        assertEquals(emptySet(), invitePairs(), "a block")
    }

    @Test
    fun onlyTheFirstCopyPairs() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        copy("3kc", bob, activityUri("3ka"))
        assertEquals(setOf(activityUri("3ka") to activityUri("3kb", bob)), invitePairs())
    }

    @Test
    fun anInvitePastTheTwentiethDoesntCount() {
        activity("3ka", sport = "ride")
        for (i in 10 until 30) {
            val other = "did:plc:other$i"
            invite("3ka", other, createdAt = "2026-10-06T09:$i:00Z")
        }
        follow(bob, alice)
        invite("3ka", bob, createdAt = "2026-10-06T10:00:00Z")
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(emptySet(), invitePairs())
    }

    @Test
    fun anEditClearingCopyOfDropsThePairAndDeletingTheOriginalKeepsTheCopy() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(1, invitePairs().size)
        copy("3kb", bob, null)
        assertEquals(emptySet(), invitePairs(), "bob left the group")

        cancel("3ka", bob)
        invite("3ka", bob, createdAt = "2026-10-07T09:00:00Z")
        copy("3kc", bob, activityUri("3ka"))
        assertEquals(1, invitePairs().size)
        remove(alice, Nsid.FEED_ACTIVITY, "3ka")
        assertEquals(emptySet(), invitePairs())
        assertEquals(
            setOf(activityUri("3kb", bob), activityUri("3kc", bob)),
            runBlocking { db.tx { Activities.selectAll().map { it[Activities.uri] }.toSet() } },
            "bob's copies stay",
        )
    }

    @Test
    fun aBlockDropsInvitePairsAndGroupingOffKeepsThem() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        spaceWrite(self(alice), Nsid.ACTOR_GROUP_PREFS, "self", """{"mode":"${GroupMode.OFF}","updatedAt":"2026-11-01T12:00:00Z"}""")
        match()
        assertEquals(1, invitePairs().size, "grouping off leaves invite pairs")

        block(bob, alice)
        assertEquals(emptySet(), invitePairs())
    }

    @Test
    fun droppingTheInviteesSelfKeepsTheirPair() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        runBlocking { indexer.dropSpace(self(bob)) }
        assertEquals(1, invitePairs().size)
    }

    @Test
    fun droppingSelfDropsTheInvitesAndTheirPairs() {
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        runBlocking { indexer.dropSpace(self(alice)) }
        assertEquals(emptySet(), invites())
        assertEquals(emptySet(), invitePairs())
    }

    private suspend fun HttpClient.inbox(viewer: String): List<String> =
        xrpcOk(GetActivityInvites.method, emptyMap(), viewer).invites.map { it.uri.value }

    private suspend fun HttpClient.invitees(rkey: String, viewer: String, owner: String = alice): List<Pair<String, String>> =
        xrpcOk(GetActivityInvitees.method, mapOf("activity" to activityUri(rkey, owner)), viewer).invitees.map {
            it.invitee.did.value to it.status
        }

    private suspend fun HttpClient.partners(rkey: String, did: String, viewer: String?): List<String> =
        xrpcOk(GetActivity.method, mapOf("uri" to activityUri(rkey, did)), viewer).activity.group.orEmpty().map { it.uri.value }

    @Test
    fun anInviteIsServedOnlyToTheInviterAndTheInvitee() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        assertEquals(listOf(inviteUri("3ka", bob)), client.inbox(bob))
        assertEquals(emptyList(), client.inbox(carol))
        assertEquals(emptyList(), client.inbox(alice))
        assertEquals(listOf(bob to InviteStatus.PENDING), client.invitees("3ka", alice))
        assertEquals(emptyList(), client.invitees("3ka", carol), "only the activity's author")
        assertEquals("NotFound", client.xrpcError(GetActivityInvite.method, mapOf("uri" to inviteUri("3ka", bob)), carol))

        val body = client.xrpc(GetActivity.method, mapOf("uri" to activityUri("3ka")), carol).bodyAsText()
        assertFalse(bob in body, body)
    }

    @Test
    fun anInviteLapsesWithTheFollowOrABlockAndComesBack() = testApplication {
        application { appView(config, db, verifier) }
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        assertEquals(emptyList(), client.inbox(bob), "bob doesn't follow alice")
        assertEquals(emptyList(), client.invitees("3ka", alice))
        follow(bob, alice)
        assertEquals(1, client.inbox(bob).size)
        commit(bob, Nsid.GRAPH_FOLLOW, "3kf${bob.last()}${alice.last()}", null)
        assertEquals(emptyList(), client.inbox(bob), "unfollowed")
        follow(bob, alice)
        assertEquals(1, client.inbox(bob).size, "followed again")
        block(alice, bob)
        assertEquals(emptyList(), client.inbox(bob), "blocked")
        assertEquals(emptyList(), client.invitees("3ka", alice))
    }

    @Test
    fun aDeletedActivityHidesItsInvites() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        remove(alice, Nsid.FEED_ACTIVITY, "3ka")
        assertEquals(emptyList(), client.inbox(bob))
    }

    @Test
    fun aJoinedInviteeIsListedAsJoinedAndLeavesTheInbox() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(emptyList(), client.inbox(bob))
        val joined = client.xrpcOk(GetActivityInvitees.method, mapOf("activity" to activityUri("3ka")), alice).invitees.single()
        assertEquals(InviteStatus.JOINED, joined.status)
        assertEquals(activityUri("3kb", bob), joined.copy?.value)
        assertEquals("NotFound", client.xrpcError(GetActivityInvite.method, mapOf("uri" to inviteUri("3ka", bob)), bob), "accepted")
    }

    @Test
    fun theInviteGrantsTheRouteAndStartButNotHeartRate() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity(
            "3ka",
            tier = Visibility.FOLLOWERS,
            sport = "ride",
            extra = """"distance":42000,"avgHeartRate":150,"description":"Alice's notes",""",
        )
        put(
            alice,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        invite("3ka", bob)

        val seen = client.xrpcOk(GetActivity.method, mapOf("uri" to activityUri("3ka")), bob).activity
        assertEquals(null, seen.details.orEmpty().firstNotNullOfOrNull { it.record.polyline }, "bob may not see the map")

        val granted = client.xrpcOk(GetActivityInvite.method, mapOf("uri" to inviteUri("3ka", bob)), bob).invite.record
        assertEquals("_p~iF~ps|U", granted.polyline)
        assertEquals(42000L, granted.distance)
        assertEquals(null, granted.avgHeartRate)
        assertEquals(null, granted.description)

        cancel("3ka", bob)
        assertEquals("NotFound", client.xrpcError(GetActivityInvite.method, mapOf("uri" to inviteUri("3ka", bob)), bob))
    }

    @Test
    fun anInviteIsGrantedOnAnOnlyMeActivityWhichStaysHidden() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", tier = Visibility.SELF, sport = "ride")
        invite("3ka", bob)
        assertEquals("ride", client.xrpcOk(GetActivityInvite.method, mapOf("uri" to inviteUri("3ka", bob)), bob).invite.record.sport)
        assertEquals("NotFound", client.xrpcError(GetActivity.method, mapOf("uri" to activityUri("3ka")), bob))
    }

    @Test
    fun anInvitePairIsListedWithGroupingOffUnderTheViewerRules() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, alice))
        assertEquals(listOf(activityUri("3ka")), client.partners("3kb", bob, bob))
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, carol))

        commit(bob, Nsid.GRAPH_FOLLOW, "3kf${bob.last()}${alice.last()}", null)
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, carol), "an unfollow after accepting keeps it")

        spaceWrite(
            space(AthleteSpace.INTERACTIONS, carol),
            Nsid.GRAPH_MUTE,
            bob,
            """{"subject":"$bob","createdAt":"2026-10-21T09:00:00Z"}""",
        )
        assertEquals(emptyList(), client.partners("3ka", alice, carol), "muted")
    }

    @Test
    fun anOnlyMeCopyIsHiddenFromOthersButNotTheInviter() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"), tier = Visibility.SELF)
        assertEquals(emptyList(), client.partners("3ka", alice, carol))
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, bob))
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, alice), "the inviter sees who joined")
    }

    @Test
    fun theInviteeSeesAnOnlyMeOriginalOnTheirCopyAndNoOneElseDoes() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        follow(carol, bob)
        activity("3ka", tier = Visibility.SELF, sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        assertEquals(listOf(activityUri("3ka")), client.partners("3kb", bob, bob))
        assertEquals(emptyList(), client.partners("3kb", bob, carol))
        assertEquals(emptyList(), client.partners("3kb", bob, null))

        block(alice, bob)
        assertEquals(emptyList(), client.partners("3kb", bob, bob), "a block still drops it")
    }

    @Test
    fun theOriginalLeavingItsGroupHidesThePairUntilItRejoins() = testApplication {
        application { appView(config, db, verifier) }
        follow(bob, alice)
        activity("3ka", sport = "ride")
        invite("3ka", bob)
        copy("3kb", bob, activityUri("3ka"))
        fun leave(left: Boolean) =
            spaceWrite(
                self(alice),
                Nsid.FEED_SEGMENT_TRACK,
                "3ka",
                """{"activity":"${activityUri("3ka")}","data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkdata3ka"},""" +
                    """"mimeType":"application/json","size":10},"groupExcluded":$left,"createdAt":"2026-10-15T12:00:00Z"}""",
            )
        leave(true)
        assertEquals(emptyList(), client.partners("3ka", alice, alice))
        leave(false)
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, alice))
    }
}
