package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `gear.collection`. */
class GearCollectionSerializationTest {
    @Test
    fun collectionRoundTrips() {
        val record =
            GearCollectionRecord(
                name = "Race kit",
                createdAt = Instant.parse("2026-10-23T12:00:00Z"),
                notes = "A races",
                items = listOf(TARMAC, HELMET),
            )
        val json = AtJson.encodeToJsonElement(GearCollectionRecord.serializer(), record).jsonObject
        assertEquals(Nsid.GEAR_COLLECTION, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(listOf(TARMAC.value, HELMET.value), json["items"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(record, AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), json))
    }

    @Test
    fun emptyCollectionOmitsOptionalFields() {
        val record = GearCollectionRecord(name = "Winter training", createdAt = Instant.parse("2026-10-23T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(GearCollectionRecord.serializer(), record).jsonObject
        assertEquals(setOf("name", "createdAt", "\$type"), json.keys)
        assertEquals(record, AtJson.decodeFromJsonElement(GearCollectionRecord.serializer(), json))
    }

    private companion object {
        val TARMAC = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3tarmac")
        val HELMET = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3helmet")
    }
}
