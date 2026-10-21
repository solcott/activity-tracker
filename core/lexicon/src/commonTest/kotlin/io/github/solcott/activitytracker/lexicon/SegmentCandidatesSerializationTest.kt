package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.FinishSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateRejection
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** The segment importer's calls (`importer.*`) as `AtJson` writes and reads them. */
class SegmentCandidatesSerializationTest {
    private val candidate =
        SegmentCandidate(
            id = "osm-way-123",
            sport = "ride",
            name = "Hogback Trail",
            line = "_p~iF~ps|U_ulLnnqC",
            source = SegmentSource.OSM,
            license = "ODbL-1.0",
        )

    @Test
    fun putRoundTrips() {
        val input = PutSegmentCandidates.Input("us-vermont", "2026-10-21", listOf(candidate))
        val json = AtJson.encodeToJsonElement(PutSegmentCandidates.Input.serializer(), input).jsonObject
        assertEquals(setOf("region", "revision", "candidates"), json.keys)
        assertEquals(
            setOf("id", "sport", "name", "line", "source", "license"),
            json.getValue("candidates").jsonArray.single().jsonObject.keys,
        )
        assertEquals(input, AtJson.decodeFromJsonElement(PutSegmentCandidates.Input.serializer(), json))

        val output = PutSegmentCandidates.Output(listOf(SegmentCandidateRejection("osm-way-9", "a line of 120 m")))
        assertEquals(
            output,
            AtJson.decodeFromString(
                PutSegmentCandidates.Output.serializer(),
                AtJson.encodeToString(PutSegmentCandidates.Output.serializer(), output),
            ),
        )
        assertEquals(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES, PutSegmentCandidates.method.nsid)
    }

    @Test
    fun finishAndListRoundTrip() {
        val finish = FinishSegmentCandidates.Input("us-vermont", "2026-10-21")
        assertEquals(
            finish,
            AtJson.decodeFromString(
                FinishSegmentCandidates.Input.serializer(),
                AtJson.encodeToString(FinishSegmentCandidates.Input.serializer(), finish),
            ),
        )
        assertEquals(3, AtJson.decodeFromString(FinishSegmentCandidates.Output.serializer(), """{"removed":3}""").removed)

        val page = GetRiddenSegmentCandidates.Output(listOf(candidate), cursor = "osm-way-123")
        assertEquals(
            page,
            AtJson.decodeFromString(
                GetRiddenSegmentCandidates.Output.serializer(),
                AtJson.encodeToString(GetRiddenSegmentCandidates.Output.serializer(), page),
            ),
        )
        // Unknown fields from a newer AppView are ignored; a last page has no cursor.
        val last = AtJson.decodeFromString(GetRiddenSegmentCandidates.Output.serializer(), """{"candidates":[],"later":1}""")
        assertNull(last.cursor)
        assertEquals(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES, GetRiddenSegmentCandidates.method.nsid)
        assertEquals(Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES, FinishSegmentCandidates.method.nsid)
    }

    @Test
    fun idsAreLowercaseTokensAndCallsAreNotIndexed() {
        assertTrue(SegmentCandidates.ID_PATTERN.matches("us-vermont"))
        assertFalse(SegmentCandidates.ID_PATTERN.matches("US Vermont"))
        assertFalse(SegmentCandidates.ID_PATTERN.matches("a".repeat(65)))
        assertTrue(Nsid.indexedCollections.none { it.startsWith("${Nsid.NAMESPACE}.importer.") })
    }
}
