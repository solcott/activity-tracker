package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityInviteDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `feed.activityInvite`, `feed.activityInviteDecline` and `feed.activity#copyOf` as `AtJson` writes and reads them (feed/activity-invites).
 */
class ActivityInviteRecordsTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val ride = AtUri.of(alice, Nsid.FEED_ACTIVITY, "3l2abcdefgh22")

    @Test
    fun inviteRoundTrip() {
        val invite = ActivityInviteRecord(subject = ride, invitee = bob, createdAt = Instant.parse("2026-11-01T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(ActivityInviteRecord.serializer(), invite).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_INVITE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(bob.value, json["invitee"]?.jsonPrimitive?.content)
        assertEquals(invite, AtJson.decodeFromJsonElement(ActivityInviteRecord.serializer(), json))
    }

    @Test
    fun inviteRkeyJoinsTheActivityAndTheInvitee() {
        assertEquals("3l2abcdefgh22:did:plc:bob", ActivityInviteRecord.rkey("3l2abcdefgh22", bob))
    }

    @Test
    fun declineRoundTrip() {
        val invite = AtUri.of(alice, Nsid.FEED_ACTIVITY_INVITE, ActivityInviteRecord.rkey("3l2abcdefgh22", bob))
        val decline = ActivityInviteDeclineRecord(subject = invite, createdAt = Instant.parse("2026-11-01T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(ActivityInviteDeclineRecord.serializer(), decline).jsonObject
        assertEquals(Nsid.FEED_ACTIVITY_INVITE_DECLINE, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(decline, AtJson.decodeFromJsonElement(ActivityInviteDeclineRecord.serializer(), json))
    }

    @Test
    fun copyOfRoundTripsAndIsDroppedWhenAbsent() {
        val record =
            ActivityRecord(
                sport = "ride",
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                elapsedTime = 3600,
                createdAt = Instant.parse("2026-11-01T12:00:00Z"),
            )
        val absent = AtJson.encodeToJsonElement(ActivityRecord.serializer(), record).jsonObject
        assertFalse("copyOf" in absent)
        assertNull(AtJson.decodeFromJsonElement(ActivityRecord.serializer(), absent).copyOf)

        val copy = record.copy(copyOf = ride)
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), copy).jsonObject
        assertEquals(ride.value, json["copyOf"]?.jsonPrimitive?.content)
        assertEquals(copy, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json))
    }
}
