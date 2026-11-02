package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Group activities (feed/group-activities) in the AppView: indexing the choice, pairing tracks, the drops, and serving partners. */
class GroupActivitiesTest : SegmentTestBase() {
    /** A ride 10 km north from just south of the origin, [east] meters across, at 5 m/s: about 34 minutes. */
    private val longRide = listOf(listOf(0.0 to -100.0, 0.0 to 10_000.0))

    private fun grouping(mode: String, did: String = alice) =
        spaceWrite(self(did), Nsid.ACTOR_GROUP_PREFS, "self", """{"mode":"$mode","updatedAt":"2026-11-01T12:00:00Z"}""")

    private fun noGroupPrefs(did: String) = runBlocking { indexer.spaceDelete(self(did), Nsid.ACTOR_GROUP_PREFS, "self") }

    /** [did]'s activity [rkey] with a groupable track along [longRide], shifted [east] meters. */
    @Suppress("LongParameterList")
    private fun ride(
        rkey: String,
        did: String,
        east: Double = 0.0,
        sport: String = "ride",
        subSport: String? = null,
        startedAt: String = "2026-10-05T23:30:00Z",
        tier: String = Visibility.PUBLIC,
        groupExcluded: Boolean = false,
    ) {
        activity(rkey, tier = tier, sport = sport, subSport = subSport, did = did, startedAt = startedAt, extra = "")
        putGroupTrack(rkey, did, longRide.map { piece -> piece.map { (e, n) -> e + east to n } }, groupExcluded)
    }

    private fun putGroupTrack(rkey: String, did: String, pieces: List<List<Pair<Double, Double>>>, groupExcluded: Boolean) {
        val bytes = AtJson.encodeToString(SegmentTrackData.serializer(), trackData(pieces)).encodeToByteArray()
        val cid = "bafkdata$rkey${bytes.contentHashCode()}"
        blobs[cid] = bytes
        spaceWrite(
            self(did),
            Nsid.FEED_SEGMENT_TRACK,
            rkey,
            """{"activity":"${activityUri(rkey, did)}","data":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
                """"mimeType":"application/json","size":${bytes.size}},""" +
                (if (groupExcluded) """"groupExcluded":true,""" else "") +
                """"createdAt":"2026-10-15T12:00:00Z"}""",
        )
    }

    /** Every pair, as the two activity URIs in order. */
    private fun pairs(): Set<Pair<String, String>> = runBlocking {
        db.tx {
            ActivityGroupPairs.selectAll()
                .orderBy(ActivityGroupPairs.activityA to SortOrder.ASC)
                .map { it[ActivityGroupPairs.activityA] to it[ActivityGroupPairs.activityB] }
                .toSet()
        }
    }

    private fun pair(a: String, b: String) = if (a < b) a to b else b to a

    private suspend fun HttpClient.partners(rkey: String, did: String, viewer: String?): List<String> =
        xrpcOk(GetActivity.method, mapOf("uri" to activityUri(rkey, did)), viewer).activity.group.orEmpty().map { it.uri.value }

    @Test
    fun aRideAndAMountainBikeRideTogetherPair() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 30.0, subSport = "mountain")
        match()
        assertEquals(setOf(pair(activityUri("3ka", alice), activityUri("3kb", bob))), pairs())
    }

    @Test
    fun theWholeGroupIsFoundWhicheverIsIndexedSecond() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        match()
        assertEquals(emptySet(), pairs())
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size)
    }

    @Test
    fun differentSportsIndoorAndOtherTimesOrPlacesDontPair() {
        for (did in listOf(alice, bob, carol)) grouping(GroupMode.ANYONE, did)
        ride("3ka", alice)
        ride("3kb", bob, sport = "run")
        ride("3kc", carol, subSport = "indoor")
        ride("3kd", bob, startedAt = "2026-10-06T03:30:00Z")
        ride("3ke", carol, east = 3000.0)
        match()
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun theOptInDecides() {
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        grouping(GroupMode.ANYONE, alice)
        match()
        assertEquals(emptySet(), pairs(), "bob never chose")
        grouping(GroupMode.OFF, bob)
        match()
        assertEquals(emptySet(), pairs(), "bob declined")

        grouping(GroupMode.ANYONE, bob)
        // Declining dropped bob's stored track; opting in has his client write it again.
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size)

        grouping("friendsOfFriends", bob)
        assertEquals(emptySet(), pairs(), "an unknown choice counts as off, at once")
    }

    @Test
    fun aChoiceInTheRepoIsIgnored() {
        grouping(GroupMode.ANYONE, alice)
        commit(bob, Nsid.ACTOR_GROUP_PREFS, "self", """{"mode":"anyone","updatedAt":"2026-11-01T12:00:00Z"}""")
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun connectedNeedsAnEffectiveFollowEitherWay() {
        grouping(GroupMode.CONNECTED, alice)
        grouping(GroupMode.ANYONE, bob)
        // Yesterday: a follow pairs activities of the last 30 days.
        val yesterday = (Clock.System.now() - 1.days).toString()
        ride("3ka", alice, startedAt = yesterday)
        ride("3kb", bob, east = 20.0, startedAt = yesterday)
        match()
        assertEquals(emptySet(), pairs())

        follow(bob, alice)
        match()
        assertEquals(1, pairs().size, "a one-way follow is enough, and pairs a ride done before it")
    }

    @Test
    fun aPendingFollowRequestDoesntConnect() {
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"displayName":"Alice","followPolicy":"approve"}""")
        grouping(GroupMode.CONNECTED, alice)
        grouping(GroupMode.CONNECTED, bob)
        follow(bob, alice)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun aBlockDropsThePairAndPreventsNewOnes() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size)

        block(bob, alice)
        assertEquals(emptySet(), pairs(), "dropped as the block is indexed")
        ride("3ka", alice)
        match()
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun pairsArentChained() {
        grouping(GroupMode.CONNECTED, alice)
        grouping(GroupMode.ANYONE, bob)
        grouping(GroupMode.ANYONE, carol)
        follow(alice, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        ride("3kc", carol, east = 40.0)
        match()
        assertEquals(
            setOf(pair(activityUri("3ka", alice), activityUri("3kb", bob)), pair(activityUri("3kb", bob), activityUri("3kc", carol))),
            pairs(),
        )
    }

    @Test
    fun leavingDropsThePairAndRejoiningRestoresIt() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        ride("3kc", bob, east = 20.0, startedAt = "2026-10-07T23:30:00Z")
        ride("3kd", alice, startedAt = "2026-10-07T23:30:00Z")
        match()
        assertEquals(2, pairs().size)

        ride("3kb", bob, east = 20.0, groupExcluded = true)
        assertEquals(setOf(pair(activityUri("3kc", bob), activityUri("3kd", alice))), pairs(), "only that ride left, at once")
        match()
        assertEquals(1, pairs().size, "and it isn't paired again")

        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(2, pairs().size)
    }

    @Test
    fun aHiddenStartTimeStillMatchesByTheExactStart() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        // Bob's record holds only his local midnight; the exact start is on a followers-tier details record.
        ride("3kb", bob, east = 20.0, startedAt = "2026-10-05T22:00:00Z")
        put(
            bob,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3kb",
            """{"activity":"${activityUri("3kb", bob)}","startedAt":"2026-10-05T23:30:00Z","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.FOLLOWERS,
        )
        match()
        assertEquals(1, pairs().size)
    }

    @Test
    fun groupingOffDropsPairsAndTracksOnlyWhenMatchingIsOffToo() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        matching(true, alice)
        segment("3ks", sport = "ride", path = listOf(0.0 to 0.0, 0.0 to 1000.0))
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size)
        assertEquals(1, efforts().size)

        grouping(GroupMode.OFF, alice)
        assertEquals(emptySet(), pairs())
        assertEquals(1, efforts().size, "matching is still on")
        assertEquals(2, trackCount())

        matching(false, alice)
        assertEquals(emptyList(), efforts())
        assertEquals(1, trackCount(), "alice's track goes once both are off")
    }

    @Test
    fun matchingOffKeepsTracksAndCellsForGrouping() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        matching(true, alice)
        segment("3ks", sport = "ride", path = listOf(0.0 to 0.0, 0.0 to 1000.0))
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, efforts().size)

        matching(false, alice)
        assertEquals(emptyList(), efforts())
        assertEquals(2, trackCount())
        assertTrue(cellCount() > 0)
        ride("3ka", alice)
        match()
        assertEquals(1, pairs().size, "still paired")
        assertEquals(emptyList(), efforts(), "and no efforts come back")
    }

    @Test
    fun theSelfSpaceOrPrefsGoingDropsPairs() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        noGroupPrefs(bob)
        assertEquals(emptySet(), pairs())

        grouping(GroupMode.ANYONE, bob)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size, "on again, bob's rewritten track pairs")
        runBlocking { indexer.dropSpace(self(alice)) }
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun deletingAnActivityOrItsTrackDropsItsPairs() {
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        runBlocking { indexer.spaceDelete(self(bob), Nsid.FEED_SEGMENT_TRACK, "3kb") }
        assertEquals(emptySet(), pairs())

        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(1, pairs().size)
        remove(alice, Nsid.FEED_ACTIVITY, "3ka")
        assertEquals(emptySet(), pairs())
    }

    @Test
    fun partnersAreServedOnlyToViewersWhoMaySeeThem() = testApplication {
        application { appView(config, db, verifier) }
        for (did in listOf(alice, bob, carol)) grouping(GroupMode.ANYONE, did)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0, tier = Visibility.FOLLOWERS)
        match()
        assertEquals(1, pairs().size)

        assertEquals(emptyList(), client.partners("3ka", alice, carol), "carol doesn't follow bob")
        assertEquals(emptyList(), client.partners("3ka", alice, null))
        assertEquals(emptyList(), client.partners("3ka", alice, alice), "alice doesn't follow bob either")
        follow(carol, bob)
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, carol))
        assertEquals(listOf(activityUri("3ka", alice)), client.partners("3kb", bob, bob))

        spaceWrite(
            space(AthleteSpace.INTERACTIONS, carol),
            Nsid.GRAPH_MUTE,
            bob,
            """{"subject":"$bob","createdAt":"2026-10-21T09:00:00Z"}""",
        )
        assertEquals(emptyList(), client.partners("3ka", alice, carol), "muted")
    }

    @Test
    fun aMapNarrowerThanTheActivityHidesThePartner() = testApplication {
        application { appView(config, db, verifier) }
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        put(
            bob,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3kb",
            """{"activity":"${activityUri("3kb", bob)}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        match()
        assertEquals(emptyList(), client.partners("3ka", alice, alice))
        assertEquals(listOf(activityUri("3ka", alice)), client.partners("3kb", bob, bob))
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, bob), "bob sees his own")
    }

    @Test
    fun narrowingTheMapAfterPairingRetiersThePartner() = testApplication {
        application { appView(config, db, verifier) }
        grouping(GroupMode.ANYONE, alice)
        grouping(GroupMode.ANYONE, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, carol))

        put(
            bob,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3kb",
            """{"activity":"${activityUri("3kb", bob)}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        match()
        assertEquals(emptyList(), client.partners("3ka", alice, carol))
        assertEquals(1, pairs().size, "still paired, at a narrower tier")
    }

    @Test
    fun anUnfollowUnderConnectedHidesThePairAtOnceAndNothingRevealsTheChoices() = testApplication {
        application { appView(config, db, verifier) }
        grouping(GroupMode.CONNECTED, alice)
        grouping(GroupMode.ANYONE, bob)
        follow(alice, bob)
        ride("3ka", alice)
        ride("3kb", bob, east = 20.0)
        match()
        assertEquals(listOf(activityUri("3kb", bob)), client.partners("3ka", alice, alice))

        val body = client.xrpc(GetActivity.method, mapOf("uri" to activityUri("3kb", bob)), alice).bodyAsText()
        assertFalse("connected" in body || "groupExcluded" in body || "anyone" in body, body)

        commit(alice, Nsid.GRAPH_FOLLOW, "3kf${alice.last()}${bob.last()}", null)
        assertEquals(emptyList(), client.partners("3ka", alice, alice))
    }

    private fun trackCount() = runBlocking { db.tx { SegmentTracks.selectAll().count().toInt() } }

    private fun cellCount() = runBlocking { db.tx { SegmentTrackCells.selectAll().count().toInt() } }
}
