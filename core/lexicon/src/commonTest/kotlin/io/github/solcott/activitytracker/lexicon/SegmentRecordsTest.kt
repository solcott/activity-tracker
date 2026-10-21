package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackPiece
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.segmentTrack`, its data blob and `actor.segmentPrefs` as `AtJson` writes and reads them. */
class SegmentRecordsTest {
    @Test
    fun segmentTrackAndPrefsRoundTrip() {
        val activity = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val record =
            SegmentTrackRecord(
                activity = activity,
                createdAt = Instant.parse("2026-10-15T12:00:00Z"),
                zonesRevision = "3l2abcdefgh33",
                data = BlobRef(ref = BlobRef.Link("bafkreidata"), mimeType = SegmentTrackRecord.MIME_TYPE, size = 120),
            )
        val json = AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT_TRACK, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json))
        val bare =
            AtJson.decodeFromString(
                SegmentTrackRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT_TRACK}","activity":"$activity","createdAt":"2026-10-15T12:00:00Z","later":1}""",
            )
        assertNull(bare.zonesRevision)

        val data =
            SegmentTrackData(
                listOf(
                    SegmentTrackPiece(lat = listOf(515_000_000), lng = listOf(-1_275_000), t = listOf(0L), alt = listOf(null)),
                    SegmentTrackPiece(lat = listOf(515_100_000), lng = listOf(-1_265_000), t = listOf(600L)),
                )
            )
        val dataJson = AtJson.encodeToJsonElement(SegmentTrackData.serializer(), data).jsonObject
        assertFalse("alt" in dataJson["pieces"]!!.jsonArray[1].jsonObject)
        assertEquals(data, AtJson.decodeFromJsonElement(SegmentTrackData.serializer(), dataJson))

        val prefs = SegmentPrefsRecord(matching = true, updatedAt = Instant.parse("2026-10-15T12:00:00Z"))
        val prefsJson = AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), prefs).jsonObject
        assertEquals(Nsid.ACTOR_SEGMENT_PREFS, prefsJson["\$type"]?.jsonPrimitive?.content)
        assertEquals(prefs, AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), prefsJson))
    }
}
