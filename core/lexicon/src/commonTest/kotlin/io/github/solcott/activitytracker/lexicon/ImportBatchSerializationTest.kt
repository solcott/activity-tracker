package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CardRoute
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportCount
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ImportBatchSerializationTest {
    private val record =
        ActivityRecord(
            sport = Sport.RIDE,
            startedAt = Instant.parse("2019-03-02T08:00:00Z"),
            elapsedTime = 3600,
            createdAt = Instant.parse("2026-11-01T12:00:00Z"),
        )
    private val author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test"))
    private val view =
        ActivityView(
            uri = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kabc"),
            cid = Cid("bafyreib"),
            author = author,
            record = record,
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-11-01T12:00:01Z"),
        )
    private val card =
        ImportCardView(
            author = author,
            batch = "3m5abcdefghij",
            count = 40,
            earliestStartedAt = Instant.parse("2019-03-02T08:00:00Z"),
            latestStartedAt = Instant.parse("2026-09-01T08:00:00Z"),
            sports = listOf(SportCount(Sport.RIDE, 30), SportCount(Sport.RUN, 10)),
            distance = 1_200_000,
            routes = listOf(CardRoute("_p~iF~ps|U_ulLnnqC")),
            postedAt = Instant.parse("2026-11-01T12:00:00Z"),
        )

    @Test
    fun importBatchRoundTrips() {
        val tagged = record.copy(importBatch = "3m5abcdefghij")
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), tagged).jsonObject
        assertEquals("3m5abcdefghij", json["importBatch"]?.jsonPrimitive?.content)
        assertEquals(tagged, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json))
    }

    @Test
    fun untaggedRecordOmitsImportBatch() {
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), record).jsonObject
        assertFalse("importBatch" in json)
        assertEquals(record, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json))
    }

    @Test
    fun tidValidity() {
        assertTrue(Tid.isValid(Tid.next()))
        assertTrue(Tid.isValid("3m5abcdefghij"))
        assertFalse(Tid.isValid("3m5abcdefghi"))
        assertFalse(Tid.isValid("3m5abcdefghi1"))
        assertFalse(Tid.isValid("zm5abcdefghij"))
        assertFalse(Tid.isValid("3M5ABCDEFGHIJ"))
    }

    @Test
    fun timelineItemsCarryTheirType() {
        val output = GetTimeline.Output(listOf(TimelineItem.ImportCard(card), TimelineItem.Activity(view)), cursor = "c")
        val json = AtJson.encodeToJsonElement(GetTimeline.Output.serializer(), output).jsonObject
        val (first, second) = json.getValue("feed").jsonArray.map { it.jsonObject }
        assertEquals(ImportCardView.TYPE, first[$$"$type"]?.jsonPrimitive?.content)
        assertEquals(TimelineItem.ACTIVITY_TYPE, second[$$"$type"]?.jsonPrimitive?.content)
        assertEquals(output, AtJson.decodeFromJsonElement(GetTimeline.Output.serializer(), json))
        assertEquals(listOf(view), output.activities)
        assertEquals(listOf(card), output.importCards)
    }

    @Test
    fun unknownItemDoesNotFailThePage() {
        val body =
            """{"feed":[{"${'$'}type":"app.activitytracker.feed.defs#somethingNew","x":1},""" +
                AtJson.encodeToString(TimelineItem.serializer(), TimelineItem.Activity(view)) +
                "]}"
        val output = AtJson.decodeFromString(GetTimeline.Output.serializer(), body)
        assertIs<TimelineItem.Unknown>(output.feed.first())
        assertEquals(listOf(view), output.activities)
    }
}
