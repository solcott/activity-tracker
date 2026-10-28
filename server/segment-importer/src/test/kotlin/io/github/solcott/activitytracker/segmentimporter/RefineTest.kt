package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `refine` (segments/segment-import): published segments get their candidate's finer line along the same path, and nothing else. */
class RefineTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)

    /** The importer's repo: `feed.segment` records by rkey. */
    private val repo = linkedMapOf<String, JsonObject>()

    private val metersPerLngDegree = METERS_PER_DEGREE * cos(LAT * PI / 180)

    /** A trail of switchbacks mapped a node every meter or so: [turns] hairpins 10 m across joined by 40 m straights, [east] m east. */
    private fun trail(turns: Int = 4, east: Double = 0.0): List<GeoPoint> = buildList {
        var y = 0.0
        for (turn in 0 until turns) {
            val right = turn % 2 == 0
            for (i in 0..40) add(point(if (right) i.toDouble() else 40.0 - i, y, east))
            for (k in 1 until 16) {
                val a = PI * k / 16
                add(point(if (right) 40 + 5 * sin(a) else -5 * sin(a), y + 5 - 5 * cos(a), east))
            }
            y += 10
        }
    }

    private fun point(x: Double, y: Double, east: Double) = GeoPoint(north(LAT, y), -72.7 + (x + east) / metersPerLngDegree)

    /** [points] simplified at [tolerance] and encoded as `feed.segment` stores lines. */
    private fun line(points: List<GeoPoint>, tolerance: Double): String =
        SegmentGeometry.encodePolyline(
            TrackMath.simplify(points.map { LocationSample(it.lat, it.lng, null, null, null, Instant.DISTANT_PAST) }, tolerance).map {
                GeoPoint(it.latitude, it.longitude)
            }
        )

    private val coarse = line(trail(), 5.0)
    private val fine = SegmentGeometry.encodePolyline(CandidateCutter.simplified(trail()))

    private fun segment(line: String) =
        AtJson.parseToJsonElement(
                """{"${'$'}type":"${Nsid.FEED_SEGMENT}","name":"Rush Trail","sport":"ride","line":"$line","source":"osm",""" +
                    """"license":"ODbL-1.0","createdAt":"2026-10-21T12:00:00Z","later":1}"""
            )
            .jsonObject

    private fun candidate(id: String, line: String = fine) = SegmentCandidate(id, "ride", "Rush Trail", line, "osm", "ODbL-1.0")

    private fun file(vararg candidates: SegmentCandidate) = CandidatesFile("us-utah", candidates.toList())

    /** The fake PDS lists [repo] [pageSize] at a time and stores each put in it; [failPuts] refuses them. */
    private fun pds(pageSize: Int = 100, failPuts: Boolean = false) {
        fake.on("com.atproto.repo.listRecords") { call ->
            assertEquals(FakeServices.DID, call.params["repo"])
            assertEquals(Nsid.FEED_SEGMENT, call.params["collection"])
            val keys = repo.keys.toList()
            val from = call.params["cursor"]?.let { keys.indexOf(it) + 1 } ?: 0
            val page = keys.drop(from).take(pageSize)
            val records =
                page.joinToString(",") {
                    """{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$it","cid":"bafy$it","value":${repo[it]}}"""
                }
            val cursor = if (from + page.size < keys.size) ""","cursor":"${page.last()}"""" else ""
            json("""{"records":[$records]$cursor}""")
        }
        fake.on("com.atproto.repo.putRecord") { call ->
            if (failPuts) return@on error(HttpStatusCode.InternalServerError, "InternalServerError")
            val body = call.body!!
            assertEquals(FakeServices.DID, body["repo"]?.jsonPrimitive?.content)
            val rkey = body["rkey"]!!.jsonPrimitive.content
            repo[rkey] = body["record"]!!.jsonObject
            json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$rkey","cid":"bafynew"}""")
        }
    }

    @Test
    fun aSegmentPublishedAtFiveMetersGetsItsCandidatesFinerLine() = runTest {
        repo["c1"] = segment(coarse)
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.refined)
        assertNull(result.stoppedBy)
        val stored = repo.getValue("c1")
        assertEquals(fine, stored["line"]?.jsonPrimitive?.content)
        assertEquals(segment(coarse) + ("line" to JsonPrimitive(fine)), stored, "every other field kept, unknown ones too")
        assertTrue(SegmentGeometry.decodePolyline(fine)!!.size > SegmentGeometry.decodePolyline(coarse)!!.size)
    }

    @Test
    fun aMovedTrailKeepsItsLine() = runTest {
        // The trail now runs 20 m east of where the segment was published.
        repo["c1"] = segment(line(trail(east = 20.0), 5.0))
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.moved)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.putRecord"))
    }

    @Test
    fun aSegmentWhoseCandidateIsGoneIsLeft() = runTest {
        repo["gone"] = segment(coarse)
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("gone"), result.missing)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.putRecord"))
    }

    @Test
    fun aSecondRunWritesNothing() = runTest {
        repo["c1"] = segment(coarse)
        repo["c2"] = segment(coarse)
        pds(pageSize = 1)
        refine(clients, file(candidate("c1"), candidate("c2")))
        assertEquals(2, fake.calls("com.atproto.repo.putRecord").size, "both pages")

        val again = refine(clients, file(candidate("c1"), candidate("c2")))

        assertEquals(listOf("c1", "c2"), again.unchanged)
        assertEquals(2, fake.calls("com.atproto.repo.putRecord").size)
    }

    @Test
    fun aFailedPutStopsTheRunAndKeepsWhatItDid() = runTest {
        repo["c1"] = segment(coarse)
        pds(failPuts = true)

        val result = refine(clients, file(candidate("c1")))

        assertIs<Exception>(result.stoppedBy)
        assertEquals(coarse, repo.getValue("c1")["line"]?.jsonPrimitive?.content)
    }

    @Test
    fun onlyTheImportersOwnRepoIsRead() = runTest {
        pds()
        refine(clients, file(candidate("c1")))
        assertTrue(fake.calls("com.atproto.repo.listRecords").all { it.params["repo"] == FakeServices.DID })
    }

    @Test
    fun samePathToleratesTheOldSimplification() {
        assertTrue(samePath(coarse, fine))
        assertTrue(samePath(fine, fine))
        assertFalse(samePath(fine, line(trail(east = 20.0), 1.0)))
    }

    @Test
    fun refineTakesOneFile() {
        assertEquals(Command.Refine(File("us-utah.ndjson")), parse(listOf("refine", "us-utah.ndjson")))
        assertNull(parse(listOf("refine")))
        assertNull(parse(listOf("refine", "--region", "us-utah")))
    }

    private companion object {
        const val LAT = 40.49
        const val METERS_PER_DEGREE = 111_195.0
    }
}
