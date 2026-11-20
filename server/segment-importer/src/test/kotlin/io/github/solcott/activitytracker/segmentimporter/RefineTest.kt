package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
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
import kotlinx.serialization.json.jsonArray
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
            },
        )

    private val coarse = line(trail(), 5.0)
    private val fine = SegmentGeometry.encodePolyline(CandidateCutter.simplified(trail()))

    private fun segment(line: String, name: String = "Rush") =
        AtJson.parseToJsonElement(
                """{"${'$'}type":"${Nsid.FEED_SEGMENT}","name":"$name","sport":"ride","line":"$line","source":"osm",""" +
                    """"license":"ODbL-1.0","createdAt":"2026-10-21T12:00:00Z","later":1}""",
            )
            .jsonObject

    private fun candidate(id: String, line: String = fine, name: String = "Rush") =
        SegmentCandidate(id, "ride", name, line, "osm", "ODbL-1.0")

    private fun file(vararg candidates: SegmentCandidate) = CandidatesFile("us-utah", candidates.toList())

    /** The fake PDS lists [repo] [pageSize] at a time and applies each commit's updates to it; [failPuts] refuses them. */
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
        fake.on(FakeServices.APPLY_WRITES) { call ->
            if (failPuts) return@on error(HttpStatusCode.InternalServerError, "InternalServerError")
            val body = call.body!!
            assertEquals(FakeServices.DID, body["repo"]?.jsonPrimitive?.content)
            for (write in body.getValue("writes").jsonArray.map { it.jsonObject }) {
                assertEquals("com.atproto.repo.applyWrites#update", write["\$type"]?.jsonPrimitive?.content)
                assertEquals(Nsid.FEED_SEGMENT, write["collection"]?.jsonPrimitive?.content)
                repo[write.getValue("rkey").jsonPrimitive.content] = write.getValue("value").jsonObject
            }
            json("""{"results":[]}""")
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
    fun aSegmentPublishedWithoutAttributesIsBackfilled() = runTest {
        repo["c1"] = segment(fine)
        pds()
        val dirt = TrailAttributes(surfaces = listOf(TrailSurface("dirt", 100)))

        val result = refine(clients, file(candidate("c1").copy(trail = dirt)))

        assertEquals(listOf("c1"), result.trail)
        assertEquals(listOf("c1"), result.unchanged, "its line was already fine")
        val stored = repo.getValue("c1")
        assertEquals(dirt, AtJson.decodeFromJsonElement(TrailAttributes.serializer(), stored.getValue("trail")))
        assertEquals(segment(fine), JsonObject(stored - "trail"), "every other field kept")
    }

    @Test
    fun equalAttributesInAnotherOrderAreUnchanged() = runTest {
        repo["c1"] =
            JsonObject(
                segment(fine) +
                    ("trail" to AtJson.parseToJsonElement("""{"dog":"leashed","surfaces":[{"percent":100,"surface":"dirt"}]}""")),
            )
        pds()
        val trail = TrailAttributes(surfaces = listOf(TrailSurface("dirt", 100)), dog = "leashed")

        val result = refine(clients, file(candidate("c1").copy(trail = trail)))

        assertEquals(emptyList(), result.trail)
        assertEquals(emptyList(), fake.commits())
    }

    @Test
    fun attributesGoneFromTheCandidateAreRemoved() = runTest {
        repo["c1"] = JsonObject(segment(fine) + ("trail" to AtJson.parseToJsonElement("""{"lit":"yes"}""")))
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.trail)
        assertEquals(segment(fine), repo.getValue("c1"))
    }

    @Test
    fun aMovedTrailKeepsItsLine() = runTest {
        // The trail now runs 20 m east of where the segment was published.
        repo["c1"] = segment(line(trail(east = 20.0), 5.0))
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.moved)
        assertEquals(emptyList(), fake.commits())
    }

    @Test
    fun aSegmentWhoseCandidateIsGoneIsLeft() = runTest {
        repo["gone"] = segment(coarse)
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("gone"), result.missing)
        assertEquals(emptyList(), fake.commits())
    }

    @Test
    fun aSecondRunWritesNothing() = runTest {
        repo["c1"] = segment(coarse)
        repo["c2"] = segment(coarse)
        pds(pageSize = 1)
        refine(clients, file(candidate("c1"), candidate("c2")))
        assertEquals(2, fake.commits().size, "both pages")

        val again = refine(clients, file(candidate("c1"), candidate("c2")))

        assertEquals(listOf("c1", "c2"), again.unchanged)
        assertEquals(2, fake.commits().size)
    }

    @Test
    fun aSegmentPublishedWithALongNameTakesItsCandidatesName() = runTest {
        val long = "Gas Line Trail: Lower Corner Canyon Trail to Hoof N Boot Path"
        val short = "Gas Line: Lower Corner Canyon → Hoof N Boot"
        repo["c1"] = segment(fine, name = long)
        pds()

        val result = refine(clients, file(candidate("c1", name = short)))

        assertEquals(listOf(Renamed("c1", long, short)), result.renamed)
        assertEquals(listOf("c1"), result.unchanged, "its line was already fine")
        assertEquals(segment(fine, name = short), repo.getValue("c1"), "every other field kept")
    }

    @Test
    fun aMovedTrailIsStillRenamed() = runTest {
        val moved = line(trail(east = 20.0), 5.0)
        repo["c1"] = segment(moved, name = "Rush Trail")
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.moved)
        assertEquals(listOf(Renamed("c1", "Rush Trail", "Rush")), result.renamed)
        assertEquals(segment(moved), repo.getValue("c1"), "its line kept")
    }

    @Test
    fun aNewLineAndNameAreOneWrite() = runTest {
        repo["c1"] = segment(coarse, name = "Rush Trail")
        pds()

        val result = refine(clients, file(candidate("c1")))

        assertEquals(listOf("c1"), result.refined)
        assertEquals(listOf("c1"), result.renamed.map { it.id })
        assertEquals(1, fake.commits().size)
        assertEquals(segment(fine), repo.getValue("c1"))
        assertTrue(refine(clients, file(candidate("c1"))).renamed.isEmpty(), "a second run renames nothing")
        assertEquals(1, fake.commits().size)
    }

    @Test
    fun aSegmentPublishedBeforeTheFlagTakesItsCandidatesOneWay() = runTest {
        repo["c1"] = segment(fine, name = "Clarks")
        pds()

        val result = refine(clients, file(candidate("c1", name = "Clarks").copy(oneWay = true)))

        assertEquals(listOf("c1"), result.oneWay)
        assertTrue(result.renamed.isEmpty())
        assertEquals(1, fake.commits().size)
        assertEquals(JsonObject(segment(fine, name = "Clarks") + ("oneWay" to JsonPrimitive(true))), repo.getValue("c1"))
        assertTrue(refine(clients, file(candidate("c1", name = "Clarks").copy(oneWay = true))).oneWay.isEmpty(), "a second run")
        assertEquals(1, fake.commits().size)
    }

    @Test
    fun aFlagAndANameAreOneWriteAndATwoWaySegmentLosesItsFlag() = runTest {
        repo["c1"] = JsonObject(segment(fine, name = "Clarks (eastbound)") + ("oneWay" to JsonPrimitive(true)))
        pds()

        val result = refine(clients, file(candidate("c1", name = "Clarks (uphill)")))

        assertEquals(listOf("c1"), result.oneWay)
        assertEquals(listOf(Renamed("c1", "Clarks (eastbound)", "Clarks (uphill)")), result.renamed)
        assertEquals(1, fake.commits().size)
        assertEquals(segment(fine, name = "Clarks (uphill)"), repo.getValue("c1"), "the flag removed, not stored as false")
    }

    @Test
    fun aSegmentPublishedBeforeClimbsTakesItsCandidatesClimbOnce() = runTest {
        repo["c1"] = segment(fine, name = "Clarks")
        pds()

        val result = refine(clients, file(candidate("c1", name = "Clarks").copy(climb = "downhill")))

        assertEquals(listOf("c1"), result.climb)
        assertEquals(1, fake.commits().size)
        assertEquals(JsonObject(segment(fine, name = "Clarks") + ("climb" to JsonPrimitive("downhill"))), repo.getValue("c1"))
        assertTrue(refine(clients, file(candidate("c1", name = "Clarks").copy(climb = "downhill"))).climb.isEmpty(), "a second run")
        assertEquals(1, fake.commits().size)
    }

    @Test
    fun aCandidateWithoutAClimbRemovesTheSegments() = runTest {
        repo["c1"] = JsonObject(segment(fine, name = "Clarks") + ("climb" to JsonPrimitive("uphill")))
        pds()

        val result = refine(clients, file(candidate("c1", name = "Clarks")))

        assertEquals(listOf("c1"), result.climb)
        assertEquals(segment(fine, name = "Clarks"), repo.getValue("c1"), "the climb removed")
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
