package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `extract` end to end on the PBF fixture, alone and with the agency fixtures, against `expected-candidates.ndjson` and
 * `expected-candidates-agency.ndjson`. After a deliberate change to the output, regenerate the expected files with `UPDATE_EXPECTED=1
 * ./gradlew :server-segment-importer:test --tests "*.ExtractTest"` and review its diff.
 */
class ExtractTest {
    @Test
    fun extractsTheFixturesCandidates() {
        val out = createTempFile("candidates", ".ndjson").toFile()
        extract(fixture(), emptyList(), "test-region", out)
        matches("expected-candidates.ndjson", out)
    }

    @Test
    fun extractsOsmAndAgencyCandidatesTogether() {
        val out = createTempFile("candidates", ".ndjson").toFile()
        extract(
            fixture(),
            listOf(
                AgencyRules.USFS to resource("usfs-trails.geojsonl"),
                AgencyRules.NPS to resource("nps-trails.geojsonl"),
                AgencyRules.BLM to resource("blm-trails.geojsonl"),
            ),
            "test-region",
            out,
        )
        matches("expected-candidates-agency.ndjson", out)
    }

    @Test
    fun extractTakesAnyMixOfInputs() {
        val pbf = File("region.osm.pbf")
        val out = File("out.ndjson")
        fun extract(vararg args: String) = parse(listOf("extract", *args))
        assertEquals(
            Command.Extract(pbf, emptyList(), "us-vermont", out),
            extract("region.osm.pbf", "--region", "us-vermont", "--out", "out.ndjson"),
        )
        assertEquals(
            Command.Extract(
                null,
                listOf(AgencyRules.USFS to File("usfs.geojsonl"), AgencyRules.NPS to File("nps.geojsonl")),
                "us-vermont",
                out,
            ),
            extract("--agency", "usfs=usfs.geojsonl", "--region", "us-vermont", "--agency", "nps=nps.geojsonl", "--out", "out.ndjson"),
        )
        assertEquals(
            Command.Extract(pbf, listOf(AgencyRules.USFS to File("usfs.geojsonl")), "us-vermont", out),
            extract("region.osm.pbf", "--agency", "usfs=usfs.geojsonl", "--region", "us-vermont", "--out", "out.ndjson"),
        )
        assertEquals(
            Command.Extract(null, listOf(AgencyRules.BLM to File("blm.geojsonl")), "us-utah", out),
            extract("--agency", "blm=blm.geojsonl", "--region", "us-utah", "--out", "out.ndjson"),
        )
        assertNull(extract("--agency", "fws=fws.geojsonl", "--region", "us-vermont", "--out", "out.ndjson"), "unknown kind")
        assertNull(extract("--agency", "usfs", "--region", "us-vermont", "--out", "out.ndjson"), "no file")
        assertNull(extract("--region", "us-vermont", "--out", "out.ndjson"), "no input")
        assertNull(extract("a.osm.pbf", "b.osm.pbf", "--region", "us-vermont", "--out", "out.ndjson"), "two extracts")
        assertNull(extract("a.osm.pbf", "--region", "us-vermont", "--region", "us-maine", "--out", "out.ndjson"), "two regions")
    }

    private fun resource(name: String) = File(requireNotNull(javaClass.getResource("/$name")).toURI())

    /** [out] matches the expected file [name], or replaces it with `UPDATE_EXPECTED=1`. */
    private fun matches(name: String, out: File) {
        if (System.getenv("UPDATE_EXPECTED") == "1") out.copyTo(File("src/test/resources/$name"), overwrite = true)
        else assertEquals(resource(name).readText(), out.readText())
    }
}
