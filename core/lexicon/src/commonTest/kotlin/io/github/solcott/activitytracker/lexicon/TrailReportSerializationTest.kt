package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailConditions
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.trailReport` and its views (segments/trail-reports). */
class TrailReportSerializationTest {
    private val segment = StrongRef(AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-123"), Cid("bafyseg"))
    private val at = Instant.parse("2026-11-23T12:00:00Z")

    @Test
    fun reportWithImagesRoundTrips() {
        val record =
            TrailReportRecord(
                segment = segment,
                conditions = listOf(TrailCondition.MUDDY, TrailCondition.DOWNED_TREE),
                note = "Big tree down past the second bridge.",
                images =
                    listOf(
                        ActivityMedia.Image(
                            image = BlobRef(ref = BlobRef.Link("bafkphoto"), mimeType = "image/webp", size = 120_000),
                            alt = "Fallen tree",
                            aspectRatio = AspectRatio(4, 3),
                        ),
                    ),
                createdAt = at,
            )
        val json = AtJson.encodeToJsonElement(TrailReportRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_TRAIL_REPORT, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("downedTree", json["conditions"]?.jsonArray?.get(1)?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(TrailReportRecord.serializer(), json))
    }

    @Test
    fun reportWithoutImagesOmitsNothingNeeded() {
        val record = TrailReportRecord(segment = segment, conditions = listOf(TrailCondition.DRY), createdAt = at)
        val json = AtJson.encodeToJsonElement(TrailReportRecord.serializer(), record).jsonObject
        assertFalse("note" in json)
        assertEquals(record, AtJson.decodeFromJsonElement(TrailReportRecord.serializer(), json))
    }

    @Test
    fun unknownFieldsAndTagsDecode() {
        val json =
            """
            {"${'$'}type":"${Nsid.FEED_TRAIL_REPORT}","segment":{"uri":"${segment.uri.value}","cid":"bafyseg"},
             "conditions":["foggy","muddy"],"createdAt":"2026-11-23T12:00:00Z","mood":"great"}
            """
        val record = AtJson.decodeFromString(TrailReportRecord.serializer(), json)
        assertEquals(listOf(TrailCondition.MUDDY), TrailCondition.known(record.conditions))
    }

    @Test
    fun tagOrderAndGroups() {
        assertEquals(TrailCondition.all, TrailCondition.surface + TrailCondition.onTrail)
        assertEquals(listOf(TrailCondition.WET, TrailCondition.CLOSED), TrailCondition.known(setOf("closed", "wet", "x")))
        assertTrue(TrailCondition.CLOSED !in TrailCondition.surface)
    }

    @Test
    fun viewerStateAndConditionsDefaults() {
        val state = AtJson.decodeFromString(SegmentViewerState.serializer(), "{}")
        assertFalse(state.canReportConditions)
        assertNull(state.lastEffortAt)
        val conditions = AtJson.decodeFromString(TrailConditions.serializer(), """{"riders":0,"tags":[],"photoCount":0}""")
        assertNull(conditions.lead)
    }
}
