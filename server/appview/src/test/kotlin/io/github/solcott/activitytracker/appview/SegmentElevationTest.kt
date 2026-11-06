package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentElevationSource
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.location.ElevationProfile
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Where segment elevation comes from and who sees it (segments/segment-elevation), with terrain tiles around the test origin. */
class SegmentElevationTest : SegmentTestBase(terrainTiles()) {
    private suspend fun HttpClient.segmentView(rkey: String, did: String = carol): SegmentView =
        xrpcOk(GetSegment.method, mapOf("uri" to segmentUri(rkey, did))).segment

    /** [did]'s public run [rkey] along the segment, its track's altitude climbing [grade] meters a meter north. */
    private fun climb(rkey: String, did: String, grade: Double) {
        activity(rkey, did = did)
        val data = trackData()
        val withAltitude =
            data.copy(
                pieces =
                    data.pieces.map { piece ->
                        piece.copy(
                            alt = piece.lat.map { lat -> ((lat / 1e7 - origin.lat) * metersPerDegreeLat * grade * 100).roundToInt() }
                        )
                    }
            )
        putTrack(rkey, AtJson.encodeToString(SegmentTrackData.serializer(), withAltitude).encodeToByteArray(), did)
    }

    private fun assertNear(expected: Long, actual: Long?, message: String? = null) {
        assertTrue(actual != null && abs(actual - expected) <= 3, "${message.orEmpty()} expected about $expected, was $actual")
    }

    @Test
    fun aCoveredSegmentGetsTerrainElevationWithoutRiders() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        assertTrue(SegmentJobs.ELEVATION to segmentUri("3ks", carol) in jobs())
        match()

        val view = client.segmentView("3ks")
        assertNear(TERRAIN_GRADE_GAIN, view.elevationGain)
        assertEquals(0, view.elevationLoss)
        assertEquals(SegmentElevationSource.TERRAIN, view.elevationSource)

        // Search serves the same.
        val box = mapOf("minLat" to "469900000", "minLng" to "79900000", "maxLat" to "470200000", "maxLng" to "80100000")
        val found = client.xrpcOk(SearchSegments.method, box + ("sport" to "run"), carol).segments.single()
        assertEquals(view.elevationGain, found.elevationGain)
        assertEquals(view.elevationSource, found.elevationSource)
    }

    @Test
    fun aSegmentPastTheTilesHasNoElevation() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 15_000.0), did = carol)
        match()

        val view = client.segmentView("3ks")
        assertNull(view.elevationGain)
        assertNull(view.elevationLoss)
        assertNull(view.elevationSource)
    }

    @Test
    fun aNewLineIsSampledAgain() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 500.0), did = carol)
        match()

        assertNear(TERRAIN_GRADE_GAIN / 2, client.segmentView("3ks").elevationGain)
    }

    @Test
    fun anElevationInTheRecordIsIgnored() = testApplication {
        application { appView(config, db, verifier) }
        commit(
            carol,
            Nsid.FEED_SEGMENT,
            "3ks",
            """{"name":"Hill","sport":"run","line":"${lineOf(straight)}","source":"athlete","license":"CC0-1.0",""" +
                """"elevationGain":5000,"elevationSource":"efforts","createdAt":"2026-10-01T00:00:00Z"}""",
        )
        match()

        val view = client.segmentView("3ks")
        assertNear(TERRAIN_GRADE_GAIN, view.elevationGain)
        assertEquals(SegmentElevationSource.TERRAIN, view.elevationSource)
    }

    @Test
    fun segmentsWithoutTerrainElevationAreSampledOnStart() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        runBlocking {
            db.tx {
                Segments.update({ Segments.uri eq segmentUri("3ks", carol) }) {
                    it[terrainGainM] = null
                    it[terrainLossM] = null
                }
            }
        }
        assertNull(client.segmentView("3ks").elevationGain)

        runBlocking { matcher.backfillTerrain() }
        match()
        assertNear(TERRAIN_GRADE_GAIN, client.segmentView("3ks").elevationGain)
    }

    @Test
    fun theStartBackfillQueuesOnlySegmentsTheTilesReach() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        // 100 km east of the 3 by 3 tiles round the origin.
        segment("3kt", path = listOf(100_000.0 to 0.0, 100_000.0 to 1000.0), did = carol)
        match()
        uncover("3ks")
        assertTrue(jobs().isEmpty())

        runBlocking { matcher.backfillTerrain() }
        // Neither has terrain now, but only the one the tiles reach is queued.
        assertEquals(listOf(SegmentJobs.ELEVATION to segmentUri("3ks", carol)), jobs())
        match()
        assertNear(TERRAIN_GRADE_GAIN, client.segmentView("3ks").elevationGain)
        assertNull(client.segmentView("3kt").elevationGain)
    }

    @Test
    fun theStartBackfillKeepsQueuedJobsInPlace() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        // Queued long ago, as before a restart.
        val elevation = SegmentMatchJobs.kind eq SegmentJobs.ELEVATION
        runBlocking { db.tx { SegmentMatchJobs.update({ elevation }) { it[enqueuedAt] = 42 } } }

        runBlocking { matcher.backfillTerrain() }
        assertEquals(42, runBlocking { db.tx { SegmentMatchJobs.selectAll().where { elevation }.single()[SegmentMatchJobs.enqueuedAt] } })
    }

    /**
     * Clears [rkey]'s terrain elevation and profile, as for a segment the tiles don't cover; no job samples it again until its line
     * changes.
     */
    private fun uncover(rkey: String, did: String = carol) = runBlocking {
        db.tx {
            Segments.update({ Segments.uri eq segmentUri(rkey, did) }) {
                it[terrainGainM] = null
                it[terrainLossM] = null
                it[terrainProfile] = null
            }
        }
    }

    @Test
    fun terrainWinsOverEfforts() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        for (did in listOf(alice, bob, carol)) matching(true, did)
        climb("3ka", alice, EFFORT_GRADE)
        climb("3kb", bob, EFFORT_GRADE)
        climb("3kc", carol, EFFORT_GRADE)
        match()

        val view = client.segmentView("3ks")
        assertEquals(SegmentElevationSource.TERRAIN, view.elevationSource, "three athletes' efforts don't replace the terrain")
        assertNear(TERRAIN_GRADE_GAIN, view.elevationGain)
    }

    @Test
    fun effortsFillInWhereTerrainDoesntCover() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        uncover("3ks")
        for (did in listOf(alice, bob, carol)) matching(true, did)
        climb("3ka", alice, EFFORT_GRADE)
        climb("3kb", bob, EFFORT_GRADE)
        match()

        assertNull(client.segmentView("3ks").elevationSource, "two athletes aren't enough")

        climb("3kc", carol, EFFORT_GRADE)
        match()
        val view = client.segmentView("3ks")
        assertEquals(SegmentElevationSource.EFFORTS, view.elevationSource)
        assertNear(EFFORT_GRADE_GAIN, view.elevationGain)
        assertEquals(0, view.elevationLoss)

        // Carol turns matching off: two athletes again, and no terrain to fall back to.
        matching(false, carol)
        match()
        val after = client.segmentView("3ks")
        assertNull(after.elevationSource)
        assertNull(after.elevationGain)
    }

    @Test
    fun anAthleteWhoRidesOftenCountsOnce() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        uncover("3ks")
        for (did in listOf(alice, bob, carol)) matching(true, did)
        for (rkey in listOf("3ka", "3kb", "3kc")) climb(rkey, alice, EFFORT_GRADE * 1.5)
        climb("3kd", bob, EFFORT_GRADE)
        climb("3ke", carol, EFFORT_GRADE)
        match()

        // Per athlete: 300, 200, 200, whose median is 200; the five efforts' would be 300.
        val view = client.segmentView("3ks")
        assertEquals(SegmentElevationSource.EFFORTS, view.elevationSource)
        assertNear(EFFORT_GRADE_GAIN, view.elevationGain)
    }

    @Test
    fun getSegmentServesTheTerrainProfile() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()

        val profile = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile
        assertEquals(ElevationProfile.PROFILE_POINTS, profile?.heights?.size)
        // Decimeters, from the start's height to the end's, rising all the way.
        assertTrue(abs(profile!!.heights.first() - TERRAIN_BASE_M * 10) <= 30, "starts at ${profile.heights.first()}")
        assertTrue(abs(profile.heights.last() - (TERRAIN_BASE_M + TERRAIN_GRADE_GAIN) * 10) <= 30, "ends at ${profile.heights.last()}")
        assertTrue(profile.heights.zipWithNext().all { (a, b) -> b >= a })
        // The terrain's 10% grade, in tenths of a percent.
        assertNear(TERRAIN_MAX_GRADE, profile.maxGrade?.toLong(), "max grade")

        // Search carries no profile.
        val box = mapOf("minLat" to "469900000", "minLng" to "79900000", "maxLat" to "470200000", "maxLng" to "80100000")
        val body = client.xrpc(SearchSegments.method, box + ("sport" to "run"), carol).bodyAsText()
        assertTrue("elevationGain" in body, body)
        assertFalse("elevationProfile" in body, body)
    }

    @Test
    fun noProfileWithoutTerrain() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        uncover("3ks")
        for (did in listOf(alice, bob, carol)) matching(true, did)
        climb("3ka", alice, EFFORT_GRADE)
        climb("3kb", bob, EFFORT_GRADE)
        climb("3kc", carol, EFFORT_GRADE)
        match()

        val output = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol)))
        assertEquals(SegmentElevationSource.EFFORTS, output.segment.elevationSource)
        assertNull(output.elevationProfile)
    }

    @Test
    fun aNewLineGetsANewProfile() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 500.0), did = carol)
        match()

        val profile = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile!!
        assertTrue(
            abs(profile.heights.last() - (TERRAIN_BASE_M + TERRAIN_GRADE_GAIN / 2) * 10) <= 30,
            "the 500 m line ends at ${profile.heights.last()}",
        )
    }

    @Test
    fun aNewLineGetsANewMaxGrade() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        // Due east, across the slope.
        segment("3ks", path = listOf(0.0 to 0.0, 1000.0 to 0.0), did = carol)
        match()

        val profile = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile!!
        assertNear(0, profile.maxGrade?.toLong(), "a level line's max grade")
    }

    @Test
    fun segmentsWithoutAMaxGradeAreSampledOnStart() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        match()
        // Sampled before maximum grades were.
        runBlocking { db.tx { Segments.update({ Segments.uri eq segmentUri("3ks", carol) }) { it[terrainMaxGrade] = null } } }
        assertNull(client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile!!.maxGrade)

        runBlocking { matcher.backfillTerrain() }
        assertEquals(listOf(SegmentJobs.ELEVATION to segmentUri("3ks", carol)), jobs())
        match()
        val profile = client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile!!
        assertNear(TERRAIN_MAX_GRADE, profile.maxGrade?.toLong(), "max grade")

        // Now it has one, a later start queues nothing.
        runBlocking { matcher.backfillTerrain() }
        assertTrue(jobs().isEmpty())
    }

    @Test
    fun aSegmentPastTheTilesHasNoProfile() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", path = listOf(0.0 to 0.0, 0.0 to 15_000.0), did = carol)
        match()

        assertNull(client.xrpcOk(GetSegment.method, mapOf("uri" to segmentUri("3ks", carol))).elevationProfile)
    }

    @Test
    fun anEffortRecordsElevationOnlyFromAltitude() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        matching(true)
        climb("3ka", alice, EFFORT_GRADE)
        activity("3kb")
        track("3kb")
        match()

        val elevations = runBlocking {
            db.tx {
                SegmentEfforts.select(SegmentEfforts.activityUri, SegmentEfforts.elevationGainM).associate {
                    it[SegmentEfforts.activityUri] to it[SegmentEfforts.elevationGainM]
                }
            }
        }
        assertNear(EFFORT_GRADE_GAIN, elevations[activityUri("3ka")])
        assertTrue(activityUri("3kb") in elevations)
        assertNull(elevations[activityUri("3kb")], "a track without altitude")
    }

    @Test
    fun altitudeThatDoesntLineUpMakesTheTrackInvalid() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        matching(true)
        activity("3ka")
        val data = trackData()
        val broken = data.copy(pieces = data.pieces.map { it.copy(alt = listOf(1, 2, 3)) })
        putTrack("3ka", AtJson.encodeToString(SegmentTrackData.serializer(), broken).encodeToByteArray())
        match()

        assertEquals(emptyList(), efforts())
    }

    @Test
    fun noEffortViewCarriesElevation() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ks", did = carol)
        for (did in listOf(alice, bob, carol)) matching(true, did)
        climb("3ka", alice, EFFORT_GRADE)
        climb("3kb", bob, EFFORT_GRADE)
        climb("3kc", carol, EFFORT_GRADE)
        match()

        val segment = segmentUri("3ks", carol)
        val bodies =
            listOf(
                client.xrpc(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice),
                client.xrpc(GetSegmentEfforts.method, mapOf("segment" to segment, "actor" to alice), alice),
                client.xrpc(GetSegmentLeaderboard.method, mapOf("segment" to segment), alice),
            )
        for (response in bodies) {
            val body = response.bodyAsText()
            assertFalse("elevation" in body, body)
        }
    }

    companion object {
        /** Terrain climbs 0.1 m a meter north of the origin, so the 1 km straight segment gains about 100 m. */
        private const val TERRAIN_GRADE = 0.1
        private const val TERRAIN_GRADE_GAIN = 100L
        private const val TERRAIN_MAX_GRADE = 100L
        private const val EFFORT_GRADE = 0.2
        private const val EFFORT_GRADE_GAIN = 200L
        private const val TERRAIN_BASE_M = 500.0

        /** Terrarium tiles at [TERRAIN_ZOOM] in the 3 by 3 tiles round the test origin, sloping up northwards by [TERRAIN_GRADE]. */
        private fun terrainTiles(): File {
            val dir = createTempDirectory("terrain").toFile().apply { deleteOnExit() }
            val n = 1 shl TERRAIN_ZOOM
            val lat0 = 47.0
            val lng0 = 8.0
            val metersPerDegreeLat = 6_371_000.0 * PI / 180
            val cx = floor((lng0 + 180) / 360 * n).toInt()
            val latRad = lat0 * PI / 180
            val cy = floor((1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * n).toInt()
            for (tx in cx - 1..cx + 1) for (ty in cy - 1..cy + 1) {
                val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
                for (py in 0 until 256) {
                    val mercY = (ty * 256 + py + 0.5) / (n * 256.0)
                    val lat = atan(sinh(PI * (1 - 2 * mercY))) * 180 / PI
                    val height = TERRAIN_BASE_M + (lat - lat0) * metersPerDegreeLat * TERRAIN_GRADE
                    val value = height + 32768
                    val r = floor(value / 256).toInt()
                    val g = floor(value).toInt() % 256
                    val b = ((value - floor(value)) * 256).toInt()
                    for (px in 0 until 256) image.setRGB(px, py, (r shl 16) or (g shl 8) or b)
                }
                File(dir, "$TERRAIN_ZOOM/$tx").mkdirs()
                ImageIO.write(image, "png", File(dir, "$TERRAIN_ZOOM/$tx/$ty.png"))
            }
            return dir
        }
    }
}
