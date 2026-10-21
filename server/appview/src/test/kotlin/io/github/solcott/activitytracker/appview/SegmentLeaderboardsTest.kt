package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * `getSegmentLeaderboard`, and ranks on `getActivitySegments` (segments/segment-leaderboards, segments/segment-efforts): one best effort
 * per athlete, ranked over the whole board, only among the efforts each viewer may see, without athletes who hide, excluded activities or
 * implausibly fast efforts.
 */
class SegmentLeaderboardsTest : SegmentTestBase() {
    private val dave = "did:plc:davy"

    /** [did]'s activity [rkey] at [tier], ridden along [straight] at [speed] m/s and matched later: 1000 / [speed] seconds. */
    @Suppress("LongParameterList") // The fields a test varies.
    private fun effort(
        did: String,
        rkey: String,
        speed: Double = 5.0,
        tier: String = Visibility.PUBLIC,
        sport: String = "run",
        subSport: String? = null,
        startedAt: String = "2026-10-05T23:30:00Z",
        excluded: Boolean = false,
    ) {
        matching(true, did)
        activity(rkey, tier = tier, sport = sport, subSport = subSport, did = did, startedAt = startedAt)
        track(rkey, did = did, speed = speed, excluded = excluded)
    }

    /** Matches what's queued, then marks [rkey] (Carol's) active, whoever rode it. */
    private fun matchActive(rkey: String = "3ks", did: String = carol) {
        match()
        runBlocking { db.tx { Segments.update({ Segments.uri eq segmentUri(rkey, did) }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    private fun ApplicationTestBuilder.start() = application { appView(config, db, verifier) }

    private suspend fun HttpClient.board(viewer: String? = null, vararg params: Pair<String, String>) =
        xrpcOk(GetSegmentLeaderboard.method, mapOf("segment" to segmentUri("3ks", carol)) + params, viewer)

    private val List<LeaderboardEntry>.places
        get() = map { it.athlete.did.value to it.rank }

    @Test
    fun oneBestPerAthleteFastestFirstWithSharedRanks() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(carol, "3kc", speed = 8.0)
        effort(alice, "3ka", speed = 5.0)
        effort(alice, "3kb", speed = 4.0)
        // Bob ties Alice, a day earlier, so he's listed first.
        effort(bob, "3kb", speed = 5.0, startedAt = "2026-10-04T23:30:00Z")
        effort(dave, "3kd", speed = 4.0)
        matchActive()

        val board = client.board()
        assertEquals(listOf(carol to 1, bob to 2, alice to 2, dave to 4), board.entries.places)
        assertEquals(activityUri("3ka"), board.entries[2].effort.activity.value, "Alice's fastest")
        assertEquals("2026-10-06", board.entries[2].effort.startedOn)
        assertNull(board.viewer)
        assertNull(board.cursor)
        assertEquals(alice to 2, client.board(alice).viewer?.let { it.athlete.did.value to it.rank })
    }

    @Test
    fun pagesKeepCountingRanksAndTheViewersPlace() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(carol, "3kc", speed = 8.0)
        effort(bob, "3kb", speed = 5.0)
        effort(alice, "3ka", speed = 5.0)
        effort(dave, "3kd", speed = 4.0)
        matchActive()

        val first = client.board(null, "limit" to "2")
        assertEquals(2, first.entries.size)
        assertEquals(1, first.entries[0].rank)
        val second = client.board(null, "limit" to "2", "cursor" to first.cursor!!)
        assertEquals(listOf(2, 4), second.entries.map { it.rank }, "a tie split across pages keeps its rank")
        val firstDids = first.entries.map { it.athlete.did.value }.toSet()
        assertEquals(setOf(alice, bob, dave) - firstDids, second.entries.map { it.athlete.did.value }.toSet())

        val carols = client.board(carol, "limit" to "1")
        assertEquals(carol to 1, carols.viewer?.let { it.athlete.did.value to it.rank })
        val alices = client.board(alice, "limit" to "1")
        assertEquals(listOf(carol), alices.entries.map { it.athlete.did.value })
        assertEquals(alice to 2, alices.viewer?.let { it.athlete.did.value to it.rank }, "her place, off the page")
    }

    @Test
    fun onlyActiveSegmentsHaveBoards() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka")
        match()

        assertEquals(emptyList(), client.board(alice).entries, "proposed")
        assertNull(client.board(alice).viewer)
        assertEquals(
            "NotFound",
            client.xrpcError(GetSegmentLeaderboard.method, mapOf("segment" to segmentUri("3kz", carol))),
        )
    }

    @Test
    fun badRequestsAreRefused() = testApplication {
        start()
        segment("3ks", did = carol)
        matchActive()
        val segment = segmentUri("3ks", carol)
        val method = GetSegmentLeaderboard.method
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "scope" to "following")))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "period" to "decade"), alice))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "scope" to "club"), alice))
    }

    @Test
    fun theFollowingScopeHoldsTheViewerAndWhoTheyFollow() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(carol, "3kc", speed = 8.0)
        effort(alice, "3ka", speed = 5.0)
        effort(bob, "3kb", speed = 4.0)
        follow(bob, alice)
        // Dave asks for approval; Bob's follow of him is pending.
        commit(dave, Nsid.ACTOR_PROFILE, "self", """{"followPolicy":"approve","createdAt":"2026-10-01T09:00:00Z"}""")
        follow(bob, dave)
        effort(dave, "3kd", speed = 9.0)
        matchActive()

        val board = client.board(bob, "scope" to "following")
        assertEquals(listOf(alice to 1, bob to 2), board.entries.places)
        assertEquals(bob to 2, board.viewer?.let { it.athlete.did.value to it.rank })
    }

    @Test
    fun eBikeEffortsHaveTheirOwnBoard() = testApplication {
        start()
        segment("3ks", did = carol, sport = "ride")
        effort(alice, "3ka", speed = 9.0, sport = "ride", subSport = SubSport.E_MOUNTAIN)
        effort(bob, "3kb", speed = 7.0, sport = "ride")
        matchActive()

        assertEquals(listOf(bob to 1), client.board().entries.places)
        assertEquals(listOf(alice to 1), client.board(null, "eBike" to "true").entries.places)
    }

    @Test
    fun viewersSeeOnlyTheEffortsTheyMaySee() = testApplication {
        start()
        segment("3ks", did = carol)
        follow(bob, alice)
        effort(alice, "3ka", speed = 8.0, tier = Visibility.FOLLOWERS)
        effort(alice, "3kb", speed = 4.0)
        effort(carol, "3kc", speed = 5.0, tier = Visibility.SELF)
        matchActive()

        assertEquals(listOf(alice to 1), client.board(bob).entries.places)
        assertEquals(250, client.board(null).entries.single().effort.elapsed, "the slower, public one")
        assertEquals(listOf(carol to 1, alice to 2), client.board(carol).entries.places, "Carol sees her Only me effort")
        assertEquals(125, client.board(bob).entries.single().effort.elapsed)

        // An activity narrowed after matching drops at once.
        activity("3kb", tier = Visibility.SELF)
        remove(alice, Nsid.FEED_ACTIVITY, "3kb")
        assertEquals(emptyList(), client.board(null).entries)
    }

    @Test
    fun blocksHideAthletesBothWays() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka")
        effort(carol, "3kc", speed = 6.0)
        effort(bob, "3kb", speed = 4.0)
        block(alice, carol)
        matchActive()

        assertEquals(listOf(carol, bob), client.board(carol).entries.map { it.athlete.did.value })
        assertEquals(listOf(alice, bob), client.board(alice).entries.map { it.athlete.did.value })
        assertEquals(3, client.board(bob).entries.size)
    }

    @Test
    fun anAthleteWhoHidesIsOnNoBoardButKeepsTheirEfforts() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", speed = 8.0)
        effort(bob, "3kb")
        spaceWrite(
            self(alice),
            Nsid.ACTOR_SEGMENT_PREFS,
            "self",
            """{"matching":true,"hideFromLeaderboards":true,"updatedAt":"2026-10-15T12:00:00Z"}""",
        )
        matchActive()

        for (viewer in listOf(null, alice, bob)) assertEquals(listOf(bob to 1), client.board(viewer).entries.places, "to $viewer")
        assertNull(client.board(alice).viewer)
        val own = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts.single()
        assertEquals(true, own.best)
        assertNull(own.rank)
        assertEquals(NotRanked.HIDDEN, own.notRanked)
        assertNull(client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), bob).efforts.single().notRanked)

        matching(true)
        assertEquals(listOf(alice to 1, bob to 2), client.board().entries.places, "back on")
    }

    @Test
    fun anExcludedActivityFallsBackToTheNextBest() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", speed = 8.0)
        effort(alice, "3kb", speed = 4.0)
        matchActive()
        assertEquals(125, client.board().entries.single().effort.elapsed)

        track("3ka", speed = 8.0, excluded = true)
        val entry = client.board().entries.single()
        assertEquals(activityUri("3kb"), entry.effort.activity.value)
        assertEquals(250, entry.effort.elapsed)
        val own = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts.single()
        assertEquals(NotRanked.EXCLUDED, own.notRanked)
        assertEquals(true, own.best, "still her PR")
        assertNull(own.rank)
        val boardEntry = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3kb")), carol).efforts.single()
        assertEquals(1, boardEntry.rank, "her board entry carries the rank, though it isn't her PR")
    }

    @Test
    fun implausiblyFastEffortsAreNotRanked() = testApplication {
        start()
        segment("3ks", did = carol)
        // The run cap is 11.111 m/s: 10.5 is ranked, 12 isn't.
        effort(alice, "3ka", speed = 12.0)
        effort(bob, "3kb", speed = 10.5)
        matchActive()

        assertEquals(listOf(bob to 1), client.board().entries.places)
        val own = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts.single()
        assertEquals(NotRanked.IMPLAUSIBLE, own.notRanked)
    }

    @Test
    fun periodsFollowTheEffortsLocalDate() = testApplication {
        start()
        segment("3ks", did = carol)
        val today = LocalDate.now(ZoneOffset.UTC)
        val lastMonth = today.withDayOfMonth(1).minusDays(1)
        effort(alice, "3ka", speed = 8.0, startedAt = "${today.minusYears(1)}T09:30:00Z")
        effort(alice, "3kb", speed = 4.0, startedAt = "${today}T09:30:00Z")
        effort(bob, "3kc", speed = 5.0, startedAt = "${lastMonth}T09:30:00Z")
        matchActive()

        assertEquals(listOf(alice to 1, bob to 2), client.board().entries.places)
        assertEquals(125, client.board().entries[0].effort.elapsed)
        assertEquals(listOf(alice to 1), client.board(null, "period" to "month").entries.places)
        assertEquals(250, client.board(null, "period" to "month").entries[0].effort.elapsed, "this month's effort")
        val year = client.board(null, "period" to "year").entries.places
        if (lastMonth.year == today.year) assertEquals(listOf(bob to 1, alice to 2), year) else assertEquals(listOf(alice to 1), year)
    }

    @Test
    fun anAccountThatIsntServedLeavesTheBoard() = testApplication {
        val labeler = "did:plc:labeler"
        application { appView(config.copy(labeler = AppViewConfig.Labeler(labeler, "http://labeler.test")), db, verifier) }
        segment("3ks", did = carol)
        effort(alice, "3ka")
        effort(bob, "3kb")
        matchActive()
        runBlocking { LabelStore(db, labeler, null).apply(labeler, 1, listOf(testLabel(labeler, alice, "!takedown"))) }

        assertEquals(listOf(bob), client.board(carol).entries.map { it.athlete.did.value })
    }

    @Test
    fun anActivitysBestEffortCarriesItsTopTenRank() = testApplication {
        start()
        segment("3ks", did = carol)
        follow(bob, alice)
        val others = (0 until 10).map { "did:plc:r$it" }
        others.forEachIndexed { i, did -> effort(did, "3kr", speed = 6.0 + i * 0.1) }
        effort(alice, "3ka", speed = 5.0)
        effort(alice, "3kb", speed = 4.0)
        // Faster, but only Bob may see it.
        effort(alice, "3kc", speed = 10.0, tier = Visibility.FOLLOWERS)
        matchActive()

        suspend fun effortOn(activity: String, viewer: String?) =
            client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activity), viewer).efforts.single()
        assertNull(effortOn(activityUri("3ka"), carol).rank, "11th to Carol")
        assertNull(effortOn(activityUri("3kb"), carol).rank, "not her best")
        assertEquals(1, effortOn(activityUri("3kc"), bob).rank)
        assertNull(effortOn(activityUri("3ka"), bob).rank, "not her best Bob may see")
        assertEquals(3, effortOn(activityUri("3kr", others[7]), carol).rank)
        assertNull(effortOn(activityUri("3ka"), carol).notRanked, "only to the athlete")
    }

    @Test
    fun reasonsTakeHiddenThenExcludedThenImplausible() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", speed = 12.0, excluded = true)
        effort(alice, "3kb", speed = 12.0)
        matchActive()
        suspend fun reason(rkey: String) =
            client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri(rkey)), alice).efforts.single().notRanked

        assertEquals(NotRanked.EXCLUDED, reason("3ka"))
        assertEquals(NotRanked.IMPLAUSIBLE, reason("3kb"))
        spaceWrite(
            self(alice),
            Nsid.ACTOR_SEGMENT_PREFS,
            "self",
            """{"matching":true,"hideFromLeaderboards":true,"updatedAt":"2026-10-15T12:00:00Z"}""",
        )
        assertEquals(NotRanked.HIDDEN, reason("3ka"))
        assertEquals(NotRanked.HIDDEN, reason("3kb"))
    }

    @Test
    fun aProposedSegmentsEffortsCarryNoRankOrReason() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", speed = 12.0)
        match()

        val effort = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts.single()
        assertEquals(SegmentStatus.PROPOSED, effort.segment.status)
        assertNull(effort.rank)
        assertNull(effort.notRanked)
    }
}
