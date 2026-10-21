package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackPiece
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `feed.segment`, its views, `feed.segmentTrack`, its data blob and `actor.segmentPrefs` as `AtJson` writes and reads them. */
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

    @Test
    fun segmentRoundTrip() {
        val alice = Did("did:plc:alice")
        val activity = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val record =
            SegmentRecord(
                name = "Hill climb",
                sport = SegmentSportGroup.RIDE,
                line = "_p~iF~ps|U_ulLnnqC",
                source = SegmentSource.ATHLETE,
                license = "CC0-1.0",
                createdAt = Instant.parse("2026-10-15T12:00:00Z"),
                derivedFrom = StrongRef(activity, Cid("bafyactivity")),
            )
        val json = AtJson.encodeToJsonElement(SegmentRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_SEGMENT, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(SegmentRecord.serializer(), json))

        val bare =
            AtJson.decodeFromString(
                SegmentRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT}","name":"Hill","sport":"ride","line":"_p~iF","source":"osm",""" +
                    """"license":"ODbL-1.0","createdAt":"2026-10-15T12:00:00Z","later":1}""",
            )
        assertNull(bare.derivedFrom)
        assertFalse("derivedFrom" in AtJson.encodeToJsonElement(SegmentRecord.serializer(), bare).jsonObject)

        val uri = AtUri.of(alice, Nsid.FEED_SEGMENT, "3l2abcdefgh44")
        val effort =
            EffortView(
                segment = SegmentViewBasic(uri, record.name, record.sport, distanceMeters = 1_200),
                activity = activity,
                startOffset = 120,
                elapsed = 611,
                startedOn = "2026-10-15",
                best = true,
            )
        val view =
            SegmentView(
                uri = uri,
                cid = Cid("bafysegment"),
                creator = ProfileViewBasic(alice, Handle("alice.test")),
                record = record,
                distanceMeters = 1_200,
                viewer = SegmentViewerState(best = effort),
            )
        val output = GetSegment.Output(view)
        val outputJson = AtJson.encodeToJsonElement(GetSegment.Output.serializer(), output).jsonObject
        assertFalse("eBike" in outputJson["segment"]!!.jsonObject["viewer"]!!.jsonObject["best"]!!.jsonObject)
        assertEquals(output, AtJson.decodeFromJsonElement(GetSegment.Output.serializer(), outputJson))
    }
}
