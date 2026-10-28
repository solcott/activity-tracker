package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `stage` (segments/segment-import): a whole candidates file uploaded as one run, finished only when every batch went through. */
class StageTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-21T12:34:56Z")
        }

    private fun file(count: Int, version: Int = IMPORTER_VERSION, otherRegion: Int? = null): CandidatesFile {
        val header = """{"region":"us-vermont","extractTimestamp":null,"importerVersion":$version}"""
        val entries =
            (0 until count).map { i ->
                val region = if (i == otherRegion) "us-maine" else "us-vermont"
                """{"id":"c$i","region":"$region","sport":"ride","name":"Trail $i","line":"_p~iF~ps|U_ulLnnqC","lengthMeters":900,""" +
                    """"source":"osm","license":"ODbL-1.0","osmWays":[$i]}"""
            }
        return readCandidates((listOf(header) + entries).asSequence())
    }

    private fun accept(rejecting: Set<String> = emptySet(), failOnBatch: Int? = null) {
        var batches = 0
        fake.on(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES) { call ->
            if (++batches == failOnBatch) return@on error(HttpStatusCode.InternalServerError, "InternalServerError")
            val ids = call.body!!.getValue("candidates").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            val rejected = ids.filter { it in rejecting }.joinToString(",") { """{"id":"$it","reason":"a line of 120 m"}""" }
            json("""{"rejected":[$rejected]}""")
        }
        fake.on(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES) { json("""{"removed":2}""") }
    }

    @Test
    fun aRegionIsUploadedInBatchesThenFinished() = runTest {
        accept()
        val result = stage(clients, file(1200), clock)
        val batches = fake.calls(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES)
        assertEquals(listOf(500, 500, 200), batches.map { it.body!!.getValue("candidates").jsonArray.size })
        assertEquals(setOf("20261021T123456Z"), batches.map { it.body!!.getValue("revision").jsonPrimitive.content }.toSet())
        val finish = fake.calls(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES).single()
        assertEquals(
            "us-vermont" to "20261021T123456Z",
            finish.body!!.getValue("region").jsonPrimitive.content to finish.body.getValue("revision").jsonPrimitive.content,
        )
        assertEquals(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES, fake.calls.last { it.host == "appview.test" }.nsid, "finished last")
        assertEquals(1200, result.staged)
    }

    @Test
    fun aRejectedCandidateIsReportedAndTheRestStaged() = runTest {
        accept(rejecting = setOf("c7"))
        val result = stage(clients, file(10), clock)
        assertEquals(listOf("c7"), result.rejected.map { it.id })
        assertEquals("a line of 120 m", result.rejected.single().reason)
        assertEquals(9, result.staged)
        assertEquals(1, fake.calls(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES).size)
    }

    @Test
    fun aFailedBatchStopsBeforeTheFinish() = runTest {
        accept(failOnBatch = 2)
        assertFailsWith<XrpcException> { stage(clients, file(1200), clock) }
        assertEquals(2, fake.calls(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES).size)
        assertEquals(emptyList(), fake.calls(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES), "the region's earlier candidates stay staged")
    }

    @Test
    fun aFileItCantReadIsRefusedBeforeAnyCall() {
        val version = assertFailsWith<IllegalArgumentException> { file(3, version = IMPORTER_VERSION + 1) }
        assertEquals(true, "format version" in version.message.orEmpty())
        val first = assertFailsWith<IllegalArgumentException> { file(3, version = 1) }
        assertEquals(true, "format version 1" in first.message.orEmpty(), "re-extract a version 1 file")
        val coarse = assertFailsWith<IllegalArgumentException> { file(3, version = 2) }
        assertEquals(true, "format version 2" in coarse.message.orEmpty(), "re-extract a file of 5 m lines")
        val region = assertFailsWith<IllegalArgumentException> { file(3, otherRegion = 1) }
        assertEquals(true, "us-maine" in region.message.orEmpty())
        assertEquals(emptyList(), fake.calls)
    }

    @Test
    fun theFileExtractWritesReadsBack() {
        val candidates =
            listOf(
                Candidate(
                    "abc",
                    "ride",
                    "Trail",
                    "_p~iF~ps|U_ulLnnqC",
                    emptyList(),
                    900,
                    listOf(1L, 2L),
                    kind = TrailKind.NAMED,
                    whole = true,
                )
            )
        val out = java.io.StringWriter()
        write(out, "us-vermont", null, candidates)
        val read = readCandidates(out.toString().lineSequence())
        assertEquals("us-vermont", read.region)
        assertEquals(listOf("abc"), read.candidates.map { it.id })
        assertEquals("osm" to "ODbL-1.0", read.candidates.single().source to read.candidates.single().license)
    }

    @Test
    fun anAgencyCandidatesAttributionIsUploaded() = runTest {
        accept()
        val header = """{"region":"us-vermont","importerVersion":$IMPORTER_VERSION}"""
        val entry =
            """{"id":"a1","region":"us-vermont","sport":"hike","name":"Long Trail","line":"_p~iF~ps|U_ulLnnqC","lengthMeters":900,""" +
                """"source":"agency","license":"CC0-1.0","attribution":"USDA Forest Service","agencyFeatures":["usfs:1001@0"]}"""
        stage(clients, readCandidates(sequenceOf(header, entry)), clock)
        val uploaded = fake.calls(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES).single().body!!.getValue("candidates").jsonArray.single().jsonObject
        assertEquals(
            listOf("agency", "CC0-1.0", "USDA Forest Service"),
            listOf("source", "license", "attribution").map { uploaded.getValue(it).jsonPrimitive.content },
        )
    }

    @Test
    fun stageAndPublishParse() {
        assertEquals(Command.Stage(File("build/us-vermont.ndjson")), parse(listOf("stage", "build/us-vermont.ndjson")))
        assertEquals(Command.Publish("us-vermont"), parse(listOf("publish", "--region", "us-vermont")))
        assertEquals(null, parse(listOf("stage")))
        assertEquals(null, parse(listOf("publish", "--region", "US Vermont")))
        assertEquals(null, parse(listOf("publish")))
    }
}
