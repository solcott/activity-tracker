package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySensor
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.SpeedZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.ThresholdSource
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.TrainingSetAt
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RecordSerializationTest {
    private val record =
        ActivityRecord(
            sport = Sport.RUN,
            startedAt = Instant.parse("2026-09-30T14:00:00Z"),
            elapsedTime = 1800,
            createdAt = Instant.parse("2026-09-30T14:31:00Z"),
            distance = 5000,
        )

    @Test
    fun recordCarriesTypeAndOmitsNulls() {
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("2026-09-30T14:00:00Z", json["startedAt"]?.jsonPrimitive?.content)
        assertFalse("title" in json)
    }

    @Test
    fun roundTripsAndIgnoresUnknownFields() {
        val json =
            """
            {"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","startedAt":"2026-09-30T14:00:00Z",
             "elapsedTime":1800,"createdAt":"2026-09-30T14:31:00Z","distance":5000,"futureField":true}
            """
                .trimIndent()
        assertEquals(record, AtJson.decodeFromString(ActivityRecord.serializer(), json))
    }

    @Test
    fun subSportRoundTripsAndOlderRecordsLackIt() {
        val mtb = record.copy(sport = Sport.RIDE, subSport = SubSport.MOUNTAIN)
        val json = AtJson.encodeToString(ActivityRecord.serializer(), mtb)
        assertTrue("\"subSport\":\"mountain\"" in json)
        assertEquals(mtb, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("subSport" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun sensorsRoundTripAndOlderRecordsLackThem() {
        val ride =
            record.copy(
                sport = Sport.RIDE,
                sensors = listOf(ActivitySensor(SensorType.HEART_RATE, "Garmin", "HRM 200"), ActivitySensor(SensorType.POWER, "SRAM")),
            )
        val json = AtJson.encodeToString(ActivityRecord.serializer(), ride)
        assertTrue("""{"kind":"power","manufacturer":"SRAM"}""" in json, "a missing model is omitted: $json")
        assertEquals(ride, AtJson.decodeFromString(ActivityRecord.serializer(), json))
        assertFalse("sensors" in AtJson.encodeToString(ActivityRecord.serializer(), record))
    }

    @Test
    fun negativeTemperatureRoundTrips() {
        val cold = record.copy(avgTemperature = -55)
        val json = AtJson.encodeToString(ActivityRecord.serializer(), cold)
        assertTrue("\"avgTemperature\":-55" in json)
        assertEquals(cold, AtJson.decodeFromString(ActivityRecord.serializer(), json))
    }

    @Test
    fun paramsRoundTripThroughQueryString() {
        val params = GetTimeline.Params(limit = 25, cursor = "abc")
        val encoded = XrpcParams.encode(GetTimeline.Params.serializer(), params)
        assertEquals(listOf("limit" to "25", "cursor" to "abc"), encoded)
        val decoded =
            XrpcParams.decode(
                GetTimeline.Params.serializer(),
                encoded.groupBy({ it.first }, { it.second }),
            )
        assertEquals(params, decoded)
        assertTrue(XrpcParams.encode(GetTimeline.Params.serializer(), GetTimeline.Params()).isEmpty())
    }

    @Test
    fun tidsAreSortableAndUnique() {
        val tids = List(100) { Tid.next() }
        assertEquals(tids.sorted(), tids)
        assertEquals(tids.size, tids.toSet().size)
        assertTrue(tids.all { it.length == 13 })
    }

    @Test
    fun atUriParts() {
        val uri = AtUri.of(Did("did:plc:abc"), Nsid.FEED_ACTIVITY, "3k2")
        assertEquals("did:plc:abc", uri.authority)
        assertEquals(Nsid.FEED_ACTIVITY, uri.collection)
        assertEquals("3k2", uri.rkey)
    }

    @Test
    fun profileDetailsAndAvatarRoundTrip() {
        val profile =
            ProfileRecord(
                displayName = "Alice",
                avatar = BlobRef(ref = BlobRef.Link("bafkavatar"), mimeType = "image/jpeg", size = 1234),
                details =
                    ProfileDetails(
                        location = Location(locality = "Portland", country = "US"),
                        weight = 61_500,
                        height = 1_750,
                        training =
                            TrainingDetails(
                                functionalThresholdPower = 231,
                                heartRateZoneModel = HeartRateZoneModel.COGGAN,
                                autoUpdate = listOf(Threshold.FUNCTIONAL_THRESHOLD_POWER),
                                setAt = TrainingSetAt(functionalThresholdPower = Instant.parse("2026-10-01T07:00:00Z")),
                                sports =
                                    listOf(
                                        SportTraining(
                                            sport = Sport.RUN,
                                            thresholdSpeed = 3271,
                                            speedZoneModel = SpeedZoneModel.FRIEL_RUNNING,
                                            setAt = TrainingSetAt(thresholdSpeed = Instant.parse("2026-10-02T07:00:00Z")),
                                        )
                                    ),
                            ),
                    ),
            )
        val json = AtJson.encodeToJsonElement(ProfileRecord.serializer(), profile).jsonObject
        assertEquals("blob", json["avatar"]?.jsonObject?.get("\$type")?.jsonPrimitive?.content)
        val details = json.getValue("details").jsonObject
        assertEquals("61500", details["weight"]?.jsonPrimitive?.content)
        assertEquals("1750", details["height"]?.jsonPrimitive?.content)
        assertFalse("birthDate" in details)
        assertFalse("region" in details.getValue("location").jsonObject)
        val training = details.getValue("training").jsonObject
        assertEquals("231", training["functionalThresholdPower"]?.jsonPrimitive?.content)
        assertFalse("maxHeartRate" in training)
        assertEquals("2026-10-01T07:00:00Z", training.getValue("setAt").jsonObject["functionalThresholdPower"]?.jsonPrimitive?.content)
        val run = training.getValue("sports").jsonArray.single().jsonObject
        assertEquals("run", run["sport"]?.jsonPrimitive?.content)
        assertEquals("3271", run["thresholdSpeed"]?.jsonPrimitive?.content)
        assertFalse("thresholdHeartRate" in run)
        assertEquals("2026-10-02T07:00:00Z", run.getValue("setAt").jsonObject["thresholdSpeed"]?.jsonPrimitive?.content)
        assertEquals(profile, AtJson.decodeFromJsonElement(ProfileRecord.serializer(), json))
    }

    @Test
    fun thresholdHistoryRoundTrips() {
        val training =
            TrainingDetails(
                history =
                    listOf(
                        ThresholdEntry(
                            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
                            value = 231,
                            setAt = Instant.parse("2026-10-01T07:00:00Z"),
                            source = ThresholdSource.DEVICE,
                            device = "Garmin Edge 840",
                        ),
                        ThresholdEntry(
                            threshold = Threshold.THRESHOLD_SPEED,
                            value = 3271,
                            setAt = Instant.parse("2026-10-02T07:00:00Z"),
                            sport = Sport.RUN,
                        ),
                    )
            )
        val json = AtJson.encodeToJsonElement(TrainingDetails.serializer(), training).jsonObject
        val (ftp, pace) = json.getValue("history").jsonArray.map { it.jsonObject }
        assertEquals("functionalThresholdPower", ftp["threshold"]?.jsonPrimitive?.content)
        assertEquals("2026-10-01T07:00:00Z", ftp["setAt"]?.jsonPrimitive?.content)
        assertEquals("device", ftp["source"]?.jsonPrimitive?.content)
        assertEquals("Garmin Edge 840", ftp["device"]?.jsonPrimitive?.content)
        assertFalse("sport" in ftp)
        assertEquals("run", pace["sport"]?.jsonPrimitive?.content)
        assertFalse("source" in pace)
        assertFalse("device" in pace)
        assertEquals(training, AtJson.decodeFromJsonElement(TrainingDetails.serializer(), json))
    }
}
