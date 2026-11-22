package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.PathTerrain
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.PathTerrainSampler
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** A path's terrain profile and its segments' stretches along it (segments/path-profile), with terrain sloping up northwards. */
class PathProfileTest : SegmentTestBase(SegmentElevationTest.terrainTiles()) {
    private val withImporter = config.copy(segmentImporter = Did(importer))

    /** Ann's: 5 km running north from the origin, climbing about 500 m on the test terrain. */
    private val anns = listOf(0.0 to 0.0, 0.0 to 5000.0)

    /** A stretch running north past the edge of the test tiles. */
    private val pastTheEdge = listOf(2000.0 to 0.0, 2000.0 to 30_000.0)

    private fun pathUri(rkey: String = "anns") = "at://$importer/${Nsid.FEED_PATH}/$rkey"

    private fun path(rkey: String, vararg pieces: List<Pair<Double, Double>>) {
        val lines = pieces.joinToString(",") { "\"${lineOf(it)}\"" }
        commit(
            importer,
            Nsid.FEED_PATH,
            rkey,
            """{"name":"Ann's","kind":"trail","lines":[$lines],"source":"osm","license":"ODbL-1.0","createdAt":"2026-10-01T00:00:00Z"}""",
        )
    }

    private fun activate(vararg rkeys: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri inList rkeys.map { segmentUri(it) } }) { it[Segments.status] = SegmentStatus.ACTIVE } }
    }

    private suspend fun HttpClient.profile(viewer: String = carol, rkey: String = "anns"): GetPath.PathProfile? =
        xrpcOk(GetPath.method, mapOf("uri" to pathUri(rkey)), viewer).profile

    private fun assertNear(expected: Long, actual: Long, within: Long, message: String) =
        assertTrue(abs(expected - actual) <= within, "$message: expected about $expected, was $actual")

    @Test
    fun aCoveredPathGetsAProfile() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns)
        assertTrue(SegmentJobs.PATH_ELEVATION to pathUri() in jobs())
        match()

        val profile = client.profile()!!
        val piece = profile.pieces.single()
        assertEquals(0, piece.piece)
        assertEquals(101, piece.heights.size, "one height every 50 m, both ends included")
        assertNear(5000, piece.lengthMeters, 5, "length")
        assertNear(500, profile.gain.toLong(), 5, "climb")
        assertEquals(0, profile.loss)
        assertNear(100, piece.maxGrade!!.toLong(), 3, "the terrain's 10% grade in tenths")
        assertTrue(piece.heights.zipWithNext().all { (a, b) -> b >= a }, "rising north")
    }

    @Test
    fun aPiecePastTheEdgeHasNoProfile() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns, pastTheEdge)
        match()

        assertEquals(listOf(0), client.profile()!!.pieces.map { it.piece }, "only the covered piece")
        assertEquals(false, runBlocking { db.tx { Paths.selectAll().single()[Paths.terrainComplete] } })

        path("edge", pastTheEdge)
        match()
        assertNull(client.profile(rkey = "edge"), "no piece covered, no profile")
    }

    @Test
    fun aPiecesHeightsAreAbout50MetresApartUpTo512() {
        assertEquals(2, PathTerrainSampler.pointsFor(10.0))
        assertEquals(121, PathTerrainSampler.pointsFor(6000.0))
        assertEquals(512, PathTerrainSampler.pointsFor(60_000.0))
    }

    @Test
    fun aNewLineIsSampledAgainAndADeleteDropsIt() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns)
        match()
        path("anns", listOf(0.0 to 0.0, 0.0 to 2000.0))
        assertNull(client.profile(), "the old line's profile goes at once")
        match()
        assertEquals(41, client.profile()!!.pieces.single().heights.size)

        commit(importer, Nsid.FEED_PATH, "anns", null)
        assertEquals(0L, runBlocking { db.tx { PathTerrain.selectAll().count() } })
    }

    @Test
    fun theStartBackfillQueuesPathsNotWhollySampled() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns)
        path("edge", pastTheEdge.map { (x, y) -> x to y + 20_000.0 })
        runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }

        runBlocking { matcher.backfillTerrain() }
        assertEquals(
            listOf(pathUri()),
            jobs().filter { it.first == SegmentJobs.PATH_ELEVATION }.map { it.second },
            "only the path the tiles reach",
        )
        match()
        runBlocking { matcher.backfillTerrain() }
        assertEquals(emptyList(), jobs().filter { it.first == SegmentJobs.PATH_ELEVATION }, "sampled whole, not queued again")
    }

    @Test
    fun eachReturnedSegmentHasItsStretch() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns)
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        segment("3kd", path = listOf(0.0 to 4000.0, 0.0 to 2500.0), sport = "ride")
        segment("3kp", path = listOf(0.0 to 300.0, 0.0 to 900.0))
        activate("3ka", "3kd")
        match()

        val spans = client.profile()!!.spans.associate { it.segment.value.substringAfterLast('/') to (it.startMeters to it.endMeters) }
        assertEquals(setOf("3ka", "3kd"), spans.keys, "only segments carol gets back")
        val (aStart, aEnd) = spans.getValue("3ka")
        assertNear(1000, aStart, 5, "3ka's start")
        assertNear(2200, aEnd, 5, "3ka's end")
        val (dStart, dEnd) = spans.getValue("3kd")
        assertNear(2500, dStart, 5, "against the line, nearer end first")
        assertNear(4000, dEnd, 5, "3kd's farther end")

        assertTrue("3kp" in client.profile(viewer = alice)!!.spans.map { it.segment.value.substringAfterLast('/') }, "its creator gets it")
    }

    @Test
    fun aSegmentAlongNoCoveredPieceHasNoStretch() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns, pastTheEdge)
        segment("3ka", path = listOf(2000.0 to 1000.0, 2000.0 to 2500.0))
        segment("3kb", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        activate("3ka", "3kb")
        match()

        val output = client.xrpcOk(GetPath.method, mapOf("uri" to pathUri()), carol)
        assertTrue(segmentUri("3ka") in output.segments.map { it.uri.value }, "it lies on the path")
        assertEquals(listOf(segmentUri("3kb")), output.profile!!.spans.map { it.segment.value }, "but only along the uncovered piece")
    }
}
