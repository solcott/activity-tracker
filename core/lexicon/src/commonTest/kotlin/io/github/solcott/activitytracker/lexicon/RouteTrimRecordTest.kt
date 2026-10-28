package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.RouteTrimRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `actor.routeTrim`'s optional fields: the zones revision and the polyline detail (feed/route-line-detail). */
class RouteTrimRecordTest {
    @Test
    fun anAbsentDetailIsLeftOut() {
        val trim = RouteTrimRecord(revision = "3l2abcdefgh22", updatedAt = Instant.parse("2026-10-15T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(RouteTrimRecord.serializer(), trim).jsonObject
        assertNull(json["lineVersion"], "read back as 0")
        assertEquals(trim, AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), json))
    }

    @Test
    fun aRebuildWithNoZonesEverSavedHasNoRevision() {
        val rebuilt = RouteTrimRecord(updatedAt = Instant.parse("2026-10-27T12:00:00Z"), lineVersion = 1)
        val json = AtJson.encodeToJsonElement(RouteTrimRecord.serializer(), rebuilt).jsonObject
        assertNull(json["revision"])
        assertEquals(1, json["lineVersion"]?.jsonPrimitive?.content?.toInt())
        assertEquals(rebuilt, AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), json))
    }
}
