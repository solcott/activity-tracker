package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.mayPropose
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Resort skiing as `alpine` segments and ski lifts as segments of kind `lift` (segments/segment-records, segments/segment-lifts): who may
 * publish a lift, how wide a run's corridor is, and that lifts are matched but never ranked, boarded or renamed.
 */
class AlpineSegmentsTest : SegmentTestBase() {
    private fun ApplicationTestBuilder.start() = application { appView(config, db, verifier) }

    /** A `feed.segment` of [sport] and [kind] along [path], written by [did] under [rkey]. */
    @Suppress("LongParameterList") // The fields a test varies.
    private fun kinded(
        rkey: String,
        kind: String?,
        did: String = importer,
        sport: String = SegmentSportGroup.ALPINE,
        path: List<Pair<Double, Double>> = straight,
        name: String = "Summit Quad",
    ) =
        commit(
            did,
            Nsid.FEED_SEGMENT,
            rkey,
            """{"name":"$name","sport":"$sport",${kind?.let { "\"kind\":\"$it\"," }.orEmpty()}"line":"${lineOf(path)}",""" +
                """"source":"osm","license":"ODbL-1.0","createdAt":"2026-10-01T00:00:00Z"}""",
        )

    private fun row(uri: String) = runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq uri }.firstOrNull() } }

    private fun activate(uri: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri eq uri }) { it[status] = SegmentStatus.ACTIVE } }
    }

    /** [did]'s resort day [rkey] of [sport], its track following [path], matched later. */
    private fun skiDay(
        rkey: String,
        path: List<Pair<Double, Double>> = alongStraight,
        did: String = alice,
        sport: String = Sport.SKI,
        speed: Double = 5.0,
    ) {
        matching(true, did)
        activity(rkey, sport = sport, did = did)
        track(rkey, pieces = listOf(path), did = did, speed = speed)
    }

    /** Swings up to 45 m either side of [straight] on the way down it. */
    private val carving = listOf(0.0 to -100.0, 0.0 to 0.0, 45.0 to 300.0, -45.0 to 600.0, 0.0 to 1000.0, 0.0 to 1100.0)

    @Test
    fun onlyTheImporterPublishesLiftsAndOnlyAlpineOnes() {
        kinded("3kl", SegmentKind.LIFT, did = alice)
        assertNull(row(segmentUri("3kl", alice)), "an athlete's lift")

        kinded("hike-lift", SegmentKind.LIFT, sport = SegmentSportGroup.HIKE)
        assertNull(row(segmentUri("hike-lift", importer)), "a lift outside alpine")

        kinded("tunnel", "tunnel")
        assertNull(row(segmentUri("tunnel", importer)), "an unknown kind")

        kinded("quad", SegmentKind.LIFT)
        assertEquals(SegmentKind.LIFT, row(segmentUri("quad", importer))?.get(Segments.kind))

        kinded("3kr", null, did = alice, name = "Lynx")
        assertEquals(SegmentKind.RUN, row(segmentUri("3kr", alice))?.get(Segments.kind), "no kind is a run")
    }

    @Test
    fun aRunBesideALiftIsntItsDuplicate() {
        kinded("quad", SegmentKind.LIFT)
        kinded("3kr", null, did = alice, name = "Lynx")
        assertEquals(SegmentStatus.PROPOSED, row(segmentUri("3kr", alice))?.get(Segments.status))

        kinded("3ks", SegmentKind.RUN, did = bob, name = "Lynx again")
        assertEquals(SegmentStatus.DUPLICATE, row(segmentUri("3ks", bob))?.get(Segments.status), "another run along it is")
    }

    @Test
    fun alpineRunsMatchAWiderCorridorAndLiftsTheUsualOne() {
        kinded("3kr", null, did = carol, name = "Lynx")
        kinded("quad", SegmentKind.LIFT)
        skiDay("3ka", path = carving)
        match()

        assertEquals(listOf(segmentUri("3kr", carol)), efforts().map { it.segment }, "the run, not the lift 45 m off at times")

        skiDay("3kb", did = bob, sport = Sport.SNOWBOARD)
        skiDay("3kc", did = carol, path = alongStraight.reversed())
        match()
        assertEquals(
            setOf(segmentUri("3kr", carol), segmentUri("quad", importer)),
            efforts().filter { it.activity == activityUri("3kb", bob) }.map { it.segment }.toSet(),
            "a snowboard day straight up the line passes both",
        )
        assertTrue(efforts().none { it.activity == activityUri("3kc", carol) }, "riding a lift down, or a run up, gives nothing")
    }

    @Test
    fun backcountryTouringDoesntMatchAlpine() {
        kinded("3kr", null, did = carol, name = "Lynx")
        matching(true)
        activity("3ka", sport = Sport.SKI, subSport = "backcountry")
        track("3ka")
        match()
        assertTrue(efforts().isEmpty())
    }

    @Test
    fun liftsHaveNoBoardsAndTheirRidesNoMarks() = testApplication {
        start()
        kinded("3kr", null, did = carol, name = "Lynx")
        kinded("quad", SegmentKind.LIFT)
        skiDay("3ka")
        skiDay("3kb", did = bob, sport = Sport.SNOWBOARD)
        match()
        activate(segmentUri("3kr", carol))
        activate(segmentUri("quad", importer))

        val lift = mapOf("segment" to segmentUri("quad", importer))
        assertTrue(client.xrpcOk(GetSegmentLeaderboard.method, lift).entries.isEmpty(), "no leaderboard")
        assertTrue(client.xrpcOk(GetSegmentMayors.method, lift).entries.isEmpty(), "no Mayors")

        val run = client.xrpcOk(GetSegmentLeaderboard.method, mapOf("segment" to segmentUri("3kr", carol)))
        assertEquals(setOf(Sport.SKI, Sport.SNOWBOARD), run.boards.toSet(), "an alpine run ranks skiers and snowboarders apart")

        val own = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts
        val ride = own.single { it.segment.kind == SegmentKind.LIFT }
        assertNull(ride.best, "a lift ride is nobody's fastest")
        assertNull(ride.rank)
        assertNull(ride.notRanked)
        val descent = own.single { it.segment.kind == null }
        assertEquals(true, descent.best)
        assertEquals(1, descent.rank)
    }

    @Test
    fun anAlpineEffortOverTheCapIsntRanked() = testApplication {
        start()
        kinded("3kr", null, did = carol, name = "Lynx")
        // 1 km in 25 s: 144 km/h.
        skiDay("3ka", speed = 40.0)
        match()
        activate(segmentUri("3kr", carol))

        val board = client.xrpcOk(GetSegmentLeaderboard.method, mapOf("segment" to segmentUri("3kr", carol)))
        assertTrue(board.entries.isEmpty())
        val effort = client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts.single()
        assertEquals("implausible", effort.notRanked, "it stays on the day, marked")
    }

    @Test
    fun noOneMayProposeALiftsName() {
        kinded("quad", SegmentKind.LIFT)
        val rules = SegmentNameRules(proposerDays = 1)
        for (day in 1..5) {
            matching(true)
            activity("3ka$day", sport = Sport.SKI, startedAt = "2026-10-0${day}T10:00:00Z")
            track("3ka$day")
        }
        match()
        activate(segmentUri("quad", importer))
        assertTrue(efforts().isNotEmpty())
        assertFalse(runBlocking { db.tx { mayPropose(segmentUri("quad", importer), alice, 0L, rules) } })
    }

    @Test
    fun liftCandidatesCarryTheirKind() = runBlocking {
        val lift = candidate("quad", sport = SegmentSportGroup.ALPINE).copy(kind = SegmentKind.LIFT)
        val rideLift = candidate("ride-lift").copy(kind = SegmentKind.LIFT)
        val run = candidate("lynx", sport = SegmentSportGroup.ALPINE)
        assertEquals(listOf("ride-lift"), stage(lift, rideLift, run))

        for ((i, did) in listOf(alice, bob, carol).withIndex()) skiDay("3k$i", did = did)
        match()
        assertEquals(true, ridden("quad"))

        val views = SegmentCandidateViews(db, Did(importer), indexer.segmentJobs)
        val listed = views.ridden(Did(importer), GetRiddenSegmentCandidates.Params("us-vermont")).candidates.associateBy { it.id }
        assertEquals(SegmentKind.LIFT, listed["quad"]?.kind)
        assertNull(listed["lynx"]?.kind)

        // A live run along the lift's line is no duplicate of it.
        kinded("3kr", null, did = carol, name = "Lynx")
        val checks = views.check(Did(importer), CheckSegmentCandidates.Input("us-vermont", listOf("quad", "lynx")))
        assertEquals(
            mapOf("quad" to emptyList(), "lynx" to listOf(AtUri(segmentUri("3kr", carol)))),
            checks.candidates.associate { it.id to it.duplicates },
        )

        stage(lift.copy(kind = null), run)
        assertEquals(false, ridden("quad"), "a changed kind counts riders again")
    }
}
