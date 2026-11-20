package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.defs#trailAttributes` on segments, paths and candidates (segments/trail-attributes), as `AtJson` writes and reads it. */
class TrailAttributesSerializationTest {
    private val trail =
        TrailAttributes(
            surfaces = listOf(TrailSurface(TrailSurfaces.PAVED, 70), TrailSurface(TrailSurfaces.GRAVEL, 30)),
            mtbScale = 2,
            dog = TrailDog.LEASHED,
            openFrom = "06-15",
            openUntil = "10-15",
            website = "https://kingdomtrails.org",
        )

    @Test
    fun segmentRoundTripsItsTrail() {
        val record =
            SegmentRecord(
                name = "Hogback Trail",
                sport = "ride",
                line = "_p~iF~ps|U_ulLnnqC",
                source = SegmentSource.OSM,
                license = "ODbL-1.0",
                createdAt = Instant.parse("2026-11-20T00:00:00Z"),
                trail = trail,
            )
        val json = AtJson.encodeToJsonElement(SegmentRecord.serializer(), record).jsonObject
        val written = json.getValue("trail").jsonObject
        assertEquals(70, written.getValue("surfaces").jsonArray[0].jsonObject.getValue("percent").jsonPrimitive.int)
        assertFalse("smoothness" in written, "unknown attributes are left out")
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentRecord.serializer(), json))
    }

    @Test
    fun pathAndCandidateRoundTripTheirTrail() {
        val path =
            PathRecord(
                name = "Long Trail",
                kind = PathKind.TRAIL,
                lines = listOf("_p~iF~ps|U_ulLnnqC"),
                source = SegmentSource.OSM,
                license = "ODbL-1.0",
                createdAt = Instant.parse("2026-11-20T00:00:00Z"),
                trail = trail,
            )
        assertEquals(path, AtJson.decodeFromString(PathRecord.serializer(), AtJson.encodeToString(PathRecord.serializer(), path)))
        val candidate =
            SegmentCandidate("osm-1", "ride", "Hogback Trail", "_p~iF~ps|U_ulLnnqC", SegmentSource.OSM, "ODbL-1.0", trail = trail)
        val text = AtJson.encodeToString(SegmentCandidate.serializer(), candidate)
        assertEquals(candidate, AtJson.decodeFromString(SegmentCandidate.serializer(), text))
    }

    @Test
    fun recordWithoutTrailSaysNothing() {
        val text =
            """{"${'$'}type":"app.activitytracker.feed.segment","name":"A","sport":"run","line":"_p~iF~ps|U_ulLnnqC","source":"osm",""" +
                """"license":"ODbL-1.0","createdAt":"2026-11-20T00:00:00Z"}"""
        assertNull(AtJson.decodeFromString(SegmentRecord.serializer(), text).trail)
        assertTrue(TrailAttributes().isEmpty)
        assertFalse(trail.isEmpty)
    }

    @Test
    fun unknownWordsStillDecode() {
        val decoded = AtJson.decodeFromString(TrailAttributes.serializer(), """{"lit":"sometimes","future":1}""")
        assertEquals("sometimes", decoded.lit)
    }
}
