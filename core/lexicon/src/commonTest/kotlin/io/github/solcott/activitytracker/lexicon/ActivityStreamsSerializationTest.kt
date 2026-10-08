package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActivityStreamsSerializationTest {
    private val data =
        ActivityStreamData(
            time = listOf(0, 1, 2),
            heartRate = listOf(120, null, 122),
            power = listOf(200, 210, 0),
            gears = listOf(GearChange(time = 1, front = 2, rear = 5)),
        )

    @Test
    fun missingSamplesAreNullElementsAndAbsentChannelsAreOmitted() {
        val json = AtJson.encodeToJsonElement(ActivityStreamData.serializer(), data).jsonObject
        assertEquals(JsonNull, json["heartRate"]!!.jsonArray[1])
        assertFalse("cadence" in json)
        assertEquals(data, AtJson.decodeFromJsonElement(ActivityStreamData.serializer(), json))
    }

    @Test
    fun channelsListWhatTheDataHolds() {
        assertEquals(listOf(StreamChannel.HEART_RATE, StreamChannel.POWER, StreamChannel.GEARS), data.channels)
    }

    @Test
    fun recordCarriesTypeAndRoundTrips() {
        val record =
            ActivityStreamsRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                visibility = Visibility.MUTUALS,
                channels = data.channels,
                data = BlobRef(ref = BlobRef.Link("bafyblob"), mimeType = ActivityStreamsRecord.MIME_TYPE, size = 100),
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
                powerZones = ZoneSnapshot("coggan6", listOf(0, 138, 188, 225, 263, 300)),
            )
        val json = AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_STREAMS, json["\$type"]?.jsonPrimitive?.content)
        assertFalse("heartRateZones" in json)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json))
    }
}
