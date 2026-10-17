package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivitySourceRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityTrackRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityZonesRecord
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.SourceFormat
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun diveChannelsSwitchesTanksAndLapsRoundTrip() {
        val dive =
            ActivityStreamData(
                time = listOf(0, 10, 20),
                depth = listOf(0, 1800, 2100),
                ppO2 = listOf(210, 588, null),
                ndl = listOf(null, 0, 0),
                decoStopDepth = listOf(null, 600, 300),
                gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(20, 50, 0)),
                tankPressures = listOf(TankPressure(listOf(200_000, null, 190_000))),
                laps = listOf(Lap(start = 0, elapsed = 10, distance = 100), Lap(start = 10, elapsed = 10)),
            )
        val json = AtJson.encodeToJsonElement(ActivityStreamData.serializer(), dive).jsonObject
        assertEquals(JsonNull, json["tankPressures"]!!.jsonArray[0].jsonObject["pressure"]!!.jsonArray[1])
        assertFalse("moving" in json["laps"]!!.jsonArray[0].jsonObject)
        assertEquals(dive, AtJson.decodeFromJsonElement(ActivityStreamData.serializer(), json))
        assertEquals(
            listOf(
                StreamChannel.DEPTH,
                StreamChannel.PP_O2,
                StreamChannel.NDL,
                StreamChannel.DECO_STOP_DEPTH,
                StreamChannel.GAS_SWITCHES,
                StreamChannel.TANK_PRESSURES,
                StreamChannel.LAPS,
            ),
            dive.channels,
        )
    }

    @Test
    fun channelTableCoversEveryIntChannelOnce() {
        val tokens = ActivityStreamData.INT_CHANNELS.map { it.token }
        assertEquals(tokens.distinct(), tokens)
        val full = ActivityStreamData.INT_CHANNELS.fold(ActivityStreamData(time = listOf(0))) { d, c -> c.with(d, listOf(1)) }
        assertTrue(ActivityStreamData.INT_CHANNELS.all { it.get(full) == listOf(1) })
        assertTrue(full.channels.size <= ActivityStreamsRecord.MAX_CHANNELS)
        val json = AtJson.encodeToJsonElement(ActivityStreamData.serializer(), full).jsonObject
        assertEquals(tokens.toSet() + "time", json.keys, "each token is its field's JSON name")
    }

    @Test
    fun recordCarriesTypeAndRoundTrips() {
        val record =
            ActivityStreamsRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                channels = data.channels,
                data = BlobRef(ref = BlobRef.Link("bafyblob"), mimeType = ActivityStreamsRecord.MIME_TYPE, size = 100),
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
            )
        val json = AtJson.encodeToJsonElement(ActivityStreamsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_STREAMS, json["\$type"]?.jsonPrimitive?.content)
        assertFalse("powerZones" in json, "zones are a record of their own")
        assertFalse("visibility" in json, "where it's stored is its visibility")
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), json))
    }

    @Test
    fun trackRecordCarriesTypeAndRoundTrips() {
        val record =
            ActivityTrackRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
                track = BlobRef(ref = BlobRef.Link("bafytrack"), mimeType = "application/gpx+xml", size = 100),
            )
        val json = AtJson.encodeToJsonElement(ActivityTrackRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_TRACK, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityTrackRecord.serializer(), json))
    }

    @Test
    fun sourceRecordRoundTripsWithAndWithoutEntry() {
        val record =
            ActivitySourceRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                format = SourceFormat.SUBSURFACE,
                fileName = "log.ssrf",
                size = 12_345,
                compressed = true,
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
                entry = 2,
                file = BlobRef(ref = BlobRef.Link("bafysource"), mimeType = ActivitySourceRecord.MIME_TYPE, size = 1_000),
            )
        val json = AtJson.encodeToJsonElement(ActivitySourceRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_SOURCE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(2, json["entry"]?.jsonPrimitive?.content?.toInt())
        assertEquals(record, AtJson.decodeFromJsonElement(ActivitySourceRecord.serializer(), json))

        val single = record.copy(format = SourceFormat.FIT, fileName = "Morning_Ride.fit", entry = null)
        val singleJson = AtJson.encodeToJsonElement(ActivitySourceRecord.serializer(), single).jsonObject
        assertFalse("entry" in singleJson, "a single file names no entry")
        assertEquals(single, AtJson.decodeFromJsonElement(ActivitySourceRecord.serializer(), singleJson))
    }

    @Test
    fun aStrayVisibilityIsIgnored() {
        val json =
            """{"${'$'}type":"${Nsid.FEED_ACTIVITY_STREAMS}","activity":"at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k","visibility":"self",""" +
                """"channels":["power"],"createdAt":"2026-10-08T12:00:00Z"}"""
        val record = AtJson.decodeFromString(ActivityStreamsRecord.serializer(), json)
        assertEquals(listOf(StreamChannel.POWER), record.channels)
    }

    @Test
    fun strayZonesInAStreamsRecordStillDecode() {
        val json =
            """{"${'$'}type":"${Nsid.FEED_ACTIVITY_STREAMS}","activity":"at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k",""" +
                """"channels":["power"],"createdAt":"2026-10-08T12:00:00Z","powerZones":{"model":"coggan6","lows":[0,138]}}"""
        assertEquals(listOf(StreamChannel.POWER), AtJson.decodeFromString(ActivityStreamsRecord.serializer(), json).channels)
    }

    @Test
    fun zonesRecordCarriesTypeOmitsNullsAndRoundTrips() {
        val record =
            ActivityZonesRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
                powerZones = ZoneSnapshot("coggan6", listOf(0, 138, 188, 225, 263, 300)),
            )
        val json = AtJson.encodeToJsonElement(ActivityZonesRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_ZONES, json["\$type"]?.jsonPrimitive?.content)
        assertFalse("heartRateZones" in json)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityZonesRecord.serializer(), json))
    }

    @Test
    fun zonesRecordCarriesTrainingLoadAndOlderRecordsLackIt() {
        val record =
            ActivityZonesRecord(
                activity = AtUri("at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k"),
                createdAt = Instant.parse("2026-10-08T12:00:00Z"),
                trainingLoad = TrainingLoad(ftp = 250, intensityFactor = 93, trainingStressScore = 1124, aerobicTrainingEffect = 35),
            )
        val json = AtJson.encodeToJsonElement(ActivityZonesRecord.serializer(), record).jsonObject
        assertFalse("anaerobicTrainingEffect" in json["trainingLoad"]!!.jsonObject)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityZonesRecord.serializer(), json))
        val older =
            """{"${'$'}type":"${Nsid.FEED_ACTIVITY_ZONES}","activity":"at://did:plc:a/${Nsid.FEED_ACTIVITY}/3k",""" +
                """"createdAt":"2026-10-08T12:00:00Z","powerZones":{"model":"coggan6","lows":[0,138]}}"""
        assertNull(AtJson.decodeFromString(ActivityZonesRecord.serializer(), older).trainingLoad)
        assertTrue(TrainingLoad().isEmpty)
    }
}
