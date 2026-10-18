package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityDefaultsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailVisibilities
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The records of per-category detail visibility: `actor.activityDefaults` with its category defaults, and `feed.activityDetails`. */
class DetailRecordsSerializationTest {
    @Test
    fun activityDefaultsRoundTripAndResolve() {
        val record = ActivityDefaultsRecord(visibility = Visibility.MUTUALS, details = DetailVisibilities(heartRate = Visibility.SELF))
        val json = AtJson.encodeToJsonElement(ActivityDefaultsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.ACTOR_ACTIVITY_DEFAULTS, json["\$type"]?.jsonPrimitive?.content)
        assertEquals("mutuals", json["visibility"]?.jsonPrimitive?.content)
        assertEquals("self", json["details"]?.jsonObject?.get("heartRate")?.jsonPrimitive?.content)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityDefaultsRecord.serializer(), json))
        val (activity, categories) = record.resolved()
        assertEquals(Visibility.MUTUALS, activity)
        assertEquals(Visibility.SELF, categories[DetailCategory.HEART_RATE])
        assertEquals(Visibility.MUTUALS, categories[DetailCategory.POWER])

        val empty = AtJson.decodeFromString(ActivityDefaultsRecord.serializer(), """{"${'$'}type":"${Nsid.ACTOR_ACTIVITY_DEFAULTS}"}""")
        assertEquals(Visibility.PUBLIC to DetailCategory.all.associateWith { Visibility.PUBLIC }, empty.resolved())
        val wide = ActivityDefaultsRecord(visibility = Visibility.FOLLOWERS, details = DetailVisibilities(calories = Visibility.PUBLIC))
        assertEquals(Visibility.FOLLOWERS, wide.resolved().second[DetailCategory.CALORIES])
        assertEquals(Visibility.SELF, ActivityDefaultsRecord(visibility = "friends").resolved().first)
    }

    @Test
    fun oldChartsDefaultIgnored() {
        val old =
            AtJson.decodeFromString(
                ActivityDefaultsRecord.serializer(),
                """{"${'$'}type":"${Nsid.ACTOR_ACTIVITY_DEFAULTS}","visibility":"public","chartsVisibility":"self"}""",
            )
        assertEquals(Visibility.PUBLIC to DetailCategory.all.associateWith { Visibility.PUBLIC }, old.resolved())
    }

    @Test
    fun activityDetailsRoundTrip() {
        val record =
            ActivityDetailsRecord(
                activity = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3k"),
                createdAt = Instant.parse("2026-01-02T03:04:05Z"),
                startedAt = Instant.parse("2026-01-02T06:12:00Z"),
                polyline = "_p~iF~ps|U",
                avgHeartRate = 150,
                maxHeartRate = 181,
                avgPower = 220,
                work = 900,
                calories = 700,
            )
        val json = AtJson.encodeToJsonElement(ActivityDetailsRecord.serializer(), record).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_DETAILS, json["\$type"]?.jsonPrimitive?.content)
        assertNull(json["avgSpeed"])
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), json))
    }
}
