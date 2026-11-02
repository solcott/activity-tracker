package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `actor.groupPrefs` and `feed.segmentTrack#groupExcluded` as `AtJson` writes and reads them (feed/group-activities). */
class GroupRecordsTest {
    @Test
    fun groupPrefsRoundTrip() {
        val prefs = GroupPrefsRecord(mode = GroupMode.CONNECTED, updatedAt = Instant.parse("2026-11-01T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(GroupPrefsRecord.serializer(), prefs).jsonObject
        assertEquals(Nsid.ACTOR_GROUP_PREFS, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(prefs, AtJson.decodeFromJsonElement(GroupPrefsRecord.serializer(), json))
    }

    @Test
    fun anUnknownModeDecodesAndCountsAsOff() {
        val prefs =
            AtJson.decodeFromString(
                GroupPrefsRecord.serializer(),
                """{"${'$'}type":"${Nsid.ACTOR_GROUP_PREFS}","mode":"friendsOfFriends","updatedAt":"2026-11-01T12:00:00Z"}""",
            )
        assertEquals("friendsOfFriends", prefs.mode)
        assertFalse(GroupMode.isOn(prefs.mode))
        assertFalse(GroupMode.isOn(GroupMode.OFF))
        assertFalse(GroupMode.isOn(null))
        assertTrue(GroupMode.isOn(GroupMode.CONNECTED))
        assertTrue(GroupMode.isOn(GroupMode.ANYONE))
    }

    @Test
    fun segmentTrackGroupExclusionRoundTrips() {
        val activity = AtUri.of(Did("did:plc:alice"), Nsid.FEED_ACTIVITY, "3l2abcdefgh22")
        val absent =
            AtJson.decodeFromString(
                SegmentTrackRecord.serializer(),
                """{"${'$'}type":"${Nsid.FEED_SEGMENT_TRACK}","activity":"$activity","createdAt":"2026-10-15T12:00:00Z"}""",
            )
        assertNull(absent.groupExcluded)
        assertFalse("groupExcluded" in AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), absent).jsonObject)

        val left = absent.copy(groupExcluded = true, excludeFromLeaderboards = true)
        val json = AtJson.encodeToJsonElement(SegmentTrackRecord.serializer(), left).jsonObject
        assertEquals("true", json["groupExcluded"]?.jsonPrimitive?.content)
        assertEquals(left, AtJson.decodeFromJsonElement(SegmentTrackRecord.serializer(), json))
    }
}
