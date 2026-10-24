package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `gear.autoAssign`, `gear.defs#gearView` and an activity's `gear` list. */
class GearAutoAssignSerializationTest {
    @Test
    fun defaultsRoundTrip() {
        val record =
            GearAutoAssignRecord(
                updatedAt = Instant.parse("2026-10-22T12:00:00Z"),
                sports =
                    listOf(
                        GearSportDefault(Sport.RIDE, listOf(TARMAC, HELMET)),
                        GearSportDefault(Sport.RIDE, listOf(STUMPJUMPER), subSport = SubSport.MOUNTAIN),
                    ),
            )
        val json = AtJson.encodeToJsonElement(GearAutoAssignRecord.serializer(), record).jsonObject
        assertEquals(Nsid.GEAR_AUTO_ASSIGN, json["\$type"]?.jsonPrimitive?.content)
        val first = json["sports"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("sport", "gear"), first.keys)
        assertEquals(record, AtJson.decodeFromJsonElement(GearAutoAssignRecord.serializer(), json))
    }

    @Test
    fun linksAndIgnoresRoundTrip() {
        val crank = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")
        val strap = SensorKey(manufacturer = "Garmin", model = "HRM 200")
        val edge = SensorKey(manufacturer = "Garmin", antDeviceNumber = 51_234, antTransmissionType = 5)
        val record =
            GearAutoAssignRecord(
                updatedAt = Instant.parse("2026-10-22T12:00:00Z"),
                sensors = listOf(GearSensorLink(crank, TARMAC), GearSensorLink(strap, HELMET)),
                profiles = listOf(GearProfileLink("MOUNTAIN", STUMPJUMPER)),
                ignored = listOf(edge),
            )
        val json = AtJson.encodeToJsonElement(GearAutoAssignRecord.serializer(), record).jsonObject
        assertEquals(null, json["sports"], "no defaults are written")
        val link = json["sensors"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("key", "gear"), link.keys)
        assertEquals(setOf("manufacturer", "serialNumber"), link.getValue("key").jsonObject.keys)
        assertEquals(setOf("manufacturer", "antDeviceNumber", "antTransmissionType"), json["ignored"]!!.jsonArray.first().jsonObject.keys)
        assertEquals(record, AtJson.decodeFromJsonElement(GearAutoAssignRecord.serializer(), json))
    }

    @Test
    fun activityGearRoundTrips() {
        val ride =
            ActivityRecord(
                sport = Sport.RIDE,
                startedAt = Instant.parse("2026-10-22T12:00:00Z"),
                elapsedTime = 3600,
                createdAt = Instant.parse("2026-10-22T13:00:00Z"),
                gear = listOf(STUMPJUMPER, HELMET),
            )
        val json = AtJson.encodeToJsonElement(ActivityRecord.serializer(), ride).jsonObject
        assertEquals(listOf(STUMPJUMPER.value, HELMET.value), json["gear"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(ride, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), json))
    }

    @Test
    fun gearViewRoundTrips() {
        val view = GearView(TARMAC, "Tarmac", GearType.BIKE, kind = "road", brand = "Specialized", photo = "https://example.test/p")
        assertEquals(view, AtJson.decodeFromString(GearView.serializer(), AtJson.encodeToString(GearView.serializer(), view)))
    }

    private companion object {
        val TARMAC = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3tarmac")
        val STUMPJUMPER = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3stumpy")
        val HELMET = AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3helmet")
    }
}
