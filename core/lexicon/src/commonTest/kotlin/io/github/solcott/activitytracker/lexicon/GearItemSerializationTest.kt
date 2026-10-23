package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityMedia
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `gear.item`, one piece of an athlete's gear. */
class GearItemSerializationTest {
    @Test
    fun fullBikeRoundTrips() {
        val bike =
            GearItemRecord(
                name = "Stumpjumper",
                type = GearType.BIKE,
                createdAt = Instant.parse("2026-10-20T12:00:00Z"),
                kind = "mountain",
                brand = "Specialized",
                model = "Stumpjumper EVO",
                sports = listOf(Sport.RIDE),
                weight = 14_200,
                notes = "Size S4",
                photo =
                    ActivityMedia.Image(
                        image = BlobRef(ref = BlobRef.Link("bafkreibike"), mimeType = "image/webp", size = 120_000),
                        aspectRatio = AspectRatio(4, 3),
                    ),
                acquiredAt = Instant.parse("2024-04-12T00:00:00Z"),
                retiredAt = Instant.parse("2026-10-21T00:00:00Z"),
                startingDistance = 2_000_000,
                startingTime = 360_000,
            )
        val json = AtJson.encodeToJsonElement(GearItemRecord.serializer(), bike).jsonObject
        assertEquals(2_000_000, json["startingDistance"]?.jsonPrimitive?.content?.toLong())
        assertEquals(Nsid.GEAR_ITEM, json["\$type"]?.jsonPrimitive?.content)
        assertEquals(GearType.BIKE, json["type"]?.jsonPrimitive?.content)
        assertEquals(bike, AtJson.decodeFromJsonElement(GearItemRecord.serializer(), json))
    }

    @Test
    fun nameOnlyItemWritesNoOptionalFields() {
        val shoes = GearItemRecord("Old trainers", GearType.SHOES, Instant.parse("2026-10-20T12:00:00Z"))
        val json = AtJson.encodeToJsonElement(GearItemRecord.serializer(), shoes).jsonObject
        assertEquals(setOf("\$type", "name", "type", "createdAt"), json.keys)
        assertEquals(shoes, AtJson.decodeFromJsonElement(GearItemRecord.serializer(), json))
    }

    @Test
    fun unknownTypeAndKindRoundTripUnchanged() {
        val text =
            """{"${'$'}type":"${Nsid.GEAR_ITEM}","name":"Unicycle","type":"unicycle","kind":"muni","createdAt":"2026-10-20T12:00:00Z"}"""
        val item = AtJson.decodeFromString(GearItemRecord.serializer(), text)
        assertEquals("unicycle", item.type)
        assertEquals("muni", item.kind)
        assertEquals(GearType.OTHER, GearType.known(item.type))
        assertNull(GearType.knownKind(item.type, item.kind))
        assertEquals(item, AtJson.decodeFromString(GearItemRecord.serializer(), AtJson.encodeToString(GearItemRecord.serializer(), item)))
    }

    @Test
    fun bikeAndComponentKindsMatchTheSpec() {
        assertEquals(
            listOf("road", "gravel", "mountain", "cyclocross", "timeTrial", "eBike", "eMountainBike", "fatBike", "kids", "tandem"),
            GearType.kinds(GearType.BIKE),
        )
        assertEquals(14, GearType.kinds(GearType.COMPONENT).size)
        assertNull(GearType.knownKind(GearType.SHOES, "mountain"))
    }
}
