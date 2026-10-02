package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
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
}
