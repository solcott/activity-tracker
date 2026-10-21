package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `extract` end to end on the PBF fixture, against `expected-candidates.ndjson`. After a deliberate change to the output, regenerate the
 * expected file with `UPDATE_EXPECTED=1 ./gradlew :server-segment-importer:test --tests "*.ExtractTest"` and review its diff.
 */
class ExtractTest {
    @Test
    fun extractsTheFixturesCandidates() {
        val out = createTempFile("candidates", ".ndjson").toFile()
        extract(fixture(), "test-region", out)
        val expected = File(requireNotNull(javaClass.getResource("/expected-candidates.ndjson")).toURI())
        if (System.getenv("UPDATE_EXPECTED") == "1") out.copyTo(File("src/test/resources/expected-candidates.ndjson"), overwrite = true)
        else assertEquals(expected.readText(), out.readText())
    }
}
