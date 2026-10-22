package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.mayorWindow
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
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
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/**
 * `getSegmentMayors` (segments/segment-mayors): athletes by how many efforts a leaderboard could rank in a window, most first, the first to
 * reach a count ahead, ranked over the whole board and only among the efforts each viewer may see.
 */
class SegmentMayorsTest : SegmentTestBase() {
    private val dave = "did:plc:davy"
    private val today = LocalDate.now(ZoneOffset.UTC)

    /** [did]'s activity [rkey] on [date] at [tier], passing the segment [laps] times and matched later. */
    @Suppress("LongParameterList") // The fields a test varies.
    private fun effort(
        did: String,
        rkey: String,
        date: LocalDate = today,
        laps: Int = 1,
        tier: String = Visibility.PUBLIC,
        sport: String = "run",
        subSport: String? = null,
        excluded: Boolean = false,
    ) {
        matching(true, did)
        // Noon UTC, two hours ahead: the same local date.
        activity(rkey, tier = tier, sport = sport, subSport = subSport, did = did, startedAt = "${date}T10:00:00Z")
        track(rkey, pieces = List(laps) { alongStraight }, did = did, excluded = excluded)
    }

    /** Matches what's queued, then marks Carol's segment active, whoever rode it. */
    private fun matchActive() {
        match()
        runBlocking { db.tx { Segments.update({ Segments.uri eq segmentUri("3ks", carol) }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    private fun ApplicationTestBuilder.start() = application { appView(config, db, verifier) }

    private suspend fun HttpClient.mayors(viewer: String? = null, vararg params: Pair<String, String>) =
        xrpcOk(GetSegmentMayors.method, mapOf("segment" to segmentUri("3ks", carol)) + params, viewer)

    private val List<MayorEntry>.places
        get() = map { Triple(it.athlete.did.value, it.rank, it.efforts) }

    @Test
    fun everyEffortCountsMostFirst() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", date = today.minusDays(3), laps = 3)
        effort(alice, "3kb", date = today.minusDays(1))
        effort(bob, "3kc", date = today.minusDays(2))
        effort(bob, "3kd", date = today.minusDays(2))
        effort(carol, "3ke")
        matchActive()

        val board = client.mayors()
        assertEquals(listOf(Triple(alice, 1, 4), Triple(bob, 2, 2), Triple(carol, 3, 1)), board.entries.places, "three laps count three")
        assertEquals(today.minusDays(1).toString(), board.entries[0].reachedOn, "her latest counted effort")
        assertNull(board.viewer)
        assertNull(board.cursor)
        assertEquals(Triple(bob, 2, 2), client.mayors(bob).viewer?.let { Triple(it.athlete.did.value, it.rank, it.efforts) })
    }

    @Test
    fun theFirstToReachACountLeadsAndTheSameDaySharesARank() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", date = today.minusDays(9))
        effort(alice, "3kb", date = today.minusDays(5))
        effort(bob, "3kc", date = today.minusDays(4))
        effort(bob, "3kd", date = today.minusDays(2))
        effort(dave, "3ke", date = today.minusDays(3))
        effort(dave, "3kf", date = today.minusDays(2))
        effort(carol, "3kg", date = today.minusDays(20))
        matchActive()

        val places = client.mayors().entries.map { it.athlete.did.value to it.rank }
        assertEquals(alice to 1, places[0], "two efforts, reached first")
        assertEquals(setOf(bob to 2, dave to 2), places.subList(1, 3).toSet(), "two efforts each, reached the same day")
        assertEquals(carol to 4, places[3])

        // Riding again takes Bob to three, past Alice.
        effort(bob, "3kh")
        matchActive()
        assertEquals(listOf(bob to 1, alice to 2), client.mayors().entries.take(2).map { it.athlete.did.value to it.rank })
    }

    @Test
    fun pagesKeepCountingRanksAndTheViewersPlace() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", date = today.minusDays(9), laps = 2)
        effort(bob, "3kc", date = today.minusDays(2), laps = 2)
        effort(dave, "3ke", date = today.minusDays(2), laps = 2)
        effort(carol, "3kg")
        matchActive()

        val first = client.mayors(null, "limit" to "2")
        assertEquals(listOf(1, 2), first.entries.map { it.rank })
        val second = client.mayors(null, "limit" to "2", "cursor" to first.cursor!!)
        assertEquals(listOf(2, 4), second.entries.map { it.rank }, "a tie split across pages keeps its rank")
        assertEquals(
            setOf(alice, bob, dave, carol),
            (first.entries + second.entries).map { it.athlete.did.value }.toSet(),
            "each athlete once",
        )
        val carols = client.mayors(carol, "limit" to "1")
        assertEquals(listOf(alice), carols.entries.map { it.athlete.did.value })
        assertEquals(carol to 4, carols.viewer?.let { it.athlete.did.value to it.rank }, "her place, off the page")
    }

    @Test
    fun windowsFollowTheEffortsLocalDate() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka", date = today.minusDays(100))
        effort(alice, "3kb", date = today)
        effort(bob, "3kc", date = today.minusDays(30))
        effort(bob, "3kd", date = today.minusDays(30))
        val lastYear = today.withDayOfYear(1).minusYears(1)
        effort(carol, "3ke", date = lastYear)
        matchActive()

        assertEquals(listOf(Triple(bob, 1, 2), Triple(alice, 2, 1)), client.mayors().entries.places, "the last 90 days by default")
        assertEquals(
            listOf(Triple(bob, 1, 2), Triple(alice, 2, 2), Triple(carol, 3, 1)),
            client.mayors(null, "period" to MayorPeriod.ALL_TIME).entries.places,
            "Bob reached two first",
        )
        val last = client.mayors(null, "period" to MayorPeriod.LAST_YEAR).entries
        assertTrue(last.any { it.athlete.did.value == carol })
        assertTrue(last.all { it.reachedOn < today.withDayOfYear(1).toString() }, "nothing from this year")
        assertEquals(listOf(alice), client.mayors(null, "period" to MayorPeriod.THIS_WEEK).entries.map { it.athlete.did.value })
    }

    @Test
    fun onlyEffortsALeaderboardCouldRankCount() = testApplication {
        start()
        segment("3ks", did = carol)
        follow(bob, alice)
        effort(alice, "3ka", laps = 2)
        effort(alice, "3kb", tier = Visibility.FOLLOWERS, laps = 3)
        effort(dave, "3kd", laps = 2)
        effort(dave, "3ke", laps = 4, excluded = true)
        effort(carol, "3kc", laps = 9)
        spaceWrite(
            self(carol),
            Nsid.ACTOR_SEGMENT_PREFS,
            "self",
            """{"matching":true,"hideFromLeaderboards":true,"updatedAt":"2026-10-15T12:00:00Z"}""",
        )
        matchActive()

        assertEquals(listOf(Triple(alice, 1, 5), Triple(dave, 2, 2)), client.mayors(bob).entries.places, "Bob follows Alice")
        assertEquals(listOf(Triple(alice, 1, 2), Triple(dave, 1, 2)), client.mayors().entries.places.sortedBy { it.first })
        assertNull(client.mayors(carol).viewer, "Carol hides")
    }

    @Test
    fun boardScopeAndBracketsFilterTheBoard() = testApplication {
        start()
        segment("3ks", did = carol, sport = "ride")
        effort(alice, "3ka", laps = 3, sport = "ride", subSport = SubSport.E_MOUNTAIN)
        effort(alice, "3kb", sport = "ride")
        effort(bob, "3kc", laps = 2, sport = "ride")
        effort(dave, "3kd", laps = 3, sport = "ride")
        follow(bob, alice)
        spaceWrite(
            self(bob),
            Nsid.ACTOR_LEADERBOARD_BRACKETS,
            "self",
            """{"${'$'}type":"${Nsid.ACTOR_LEADERBOARD_BRACKETS}","ageGroup":"35-44","updatedAt":"2026-10-22T12:00:00Z"}""",
        )
        matchActive()

        val ride = client.mayors()
        assertEquals(listOf(Triple(dave, 1, 3), Triple(bob, 2, 2), Triple(alice, 3, 1)), ride.entries.places)
        assertEquals("ride" to listOf("ride", "eBike"), ride.board to ride.boards)
        assertEquals(listOf(Triple(alice, 1, 3)), client.mayors(null, "board" to "eBike").entries.places)
        assertEquals(listOf(Triple(bob, 1, 2), Triple(alice, 2, 1)), client.mayors(bob, "scope" to "following").entries.places)
        assertEquals(listOf(Triple(bob, 1, 2)), client.mayors(null, "ageGroup" to "35-44").entries.places)
    }

    @Test
    fun onlyActiveSegmentsHaveMayors() = testApplication {
        start()
        segment("3ks", did = carol)
        effort(alice, "3ka")
        match()

        assertEquals(emptyList(), client.mayors(alice).entries, "proposed")
        assertNull(client.mayors(alice).viewer)
        assertEquals("NotFound", client.xrpcError(GetSegmentMayors.method, mapOf("segment" to segmentUri("3kz", carol))))
    }

    @Test
    fun badRequestsAreRefused() = testApplication {
        start()
        segment("3ks", did = carol)
        matchActive()
        val segment = segmentUri("3ks", carol)
        val method = GetSegmentMayors.method
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "scope" to "following")))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "period" to "month"), alice))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "scope" to "club"), alice))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "gender" to "other")))
        assertEquals("InvalidRequest", client.xrpcError(method, mapOf("segment" to segment, "board" to "road")))
    }

    @Test
    fun theBusiestTypeIsTheDefaultAndARequestedOneIsServed() = testApplication {
        start()
        segment("3ks", did = carol, sport = "paddle")
        effort(alice, "3ka", laps = 3, sport = "kayak")
        effort(bob, "3kb", sport = "canoe")
        matchActive()

        val kayak = client.mayors()
        assertEquals(listOf(Triple(alice, 1, 3)), kayak.entries.places)
        assertEquals("kayak" to listOf("kayak", "canoe"), kayak.board to kayak.boards)
        val canoe = client.mayors(null, "board" to "canoe")
        assertEquals(listOf(Triple(bob, 1, 1)), canoe.entries.places)
        assertEquals("canoe" to listOf("kayak", "canoe"), canoe.board to canoe.boards)
    }

    @Test
    fun windowsAreCountedFromTheUtcDate() {
        fun window(period: String, on: String) = mayorWindow(period, LocalDate.parse(on))

        // Thursday 22 October 2026.
        assertEquals("2026-07-25" to null, window(MayorPeriod.LAST_90_DAYS, "2026-10-22"))
        assertEquals(null to null, window(MayorPeriod.ALL_TIME, "2026-10-22"))
        assertEquals("2026-01-01" to null, window(MayorPeriod.THIS_YEAR, "2026-10-22"))
        assertEquals("2025-01-01" to "2026-01-01", window(MayorPeriod.LAST_YEAR, "2026-10-22"))
        assertEquals("2026-10-01" to null, window(MayorPeriod.THIS_MONTH, "2026-10-22"))
        assertEquals("2026-09-01" to "2026-10-01", window(MayorPeriod.LAST_MONTH, "2026-10-22"))
        assertEquals("2026-10-19" to null, window(MayorPeriod.THIS_WEEK, "2026-10-22"))
        assertEquals("2026-10-12" to "2026-10-19", window(MayorPeriod.LAST_WEEK, "2026-10-22"))
        // A Monday starts its own week.
        assertEquals("2026-10-19" to null, window(MayorPeriod.THIS_WEEK, "2026-10-19"))
        assertEquals("2026-10-12" to "2026-10-19", window(MayorPeriod.LAST_WEEK, "2026-10-19"))
        // 1 January: last month and last week reach into last year.
        assertEquals("2025-12-01" to "2026-01-01", window(MayorPeriod.LAST_MONTH, "2026-01-01"))
        assertEquals("2025-12-22" to "2025-12-29", window(MayorPeriod.LAST_WEEK, "2026-01-01"))
        assertEquals("2026-01-01" to null, window(MayorPeriod.THIS_YEAR, "2026-01-01"))
        // A leap year: 1 March 2028 less 89 days is 3 December 2027.
        assertEquals("2027-12-03" to null, window(MayorPeriod.LAST_90_DAYS, "2028-03-01"))
        assertEquals("2028-02-01" to "2028-03-01", window(MayorPeriod.LAST_MONTH, "2028-03-01"))
    }
}
