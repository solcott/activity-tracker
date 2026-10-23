package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActorGearItem
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.GetActorGear
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonObject

/** `gear.defs#gearUsage` and the gear usage queries' outputs, and the main total each type shows. */
class GearUsageSerializationTest {
    private val bike = GearView(AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3tarmac"), "Tarmac", GearType.BIKE)

    @Test
    fun getGearOutputRoundTrips() {
        val totals =
            GearTotals(activities = 3, movingTime = 9_000, distance = 152_000, elevationGain = 1_200, elevationLoss = 1_150, days = 2)
        val output =
            GetGear.Output(
                gear = bike,
                usage =
                    GearUsage(
                        totals = totals.copy(distance = 2_152_000),
                        bySport = listOf(GearSportTotals(Sport.RIDE, totals, subSport = "road")),
                        byYear = listOf(GearYearTotals(2026, totals)),
                        starting = GearStarting(distance = 2_000_000),
                    ),
                retired = false,
                visibility = Visibility.PUBLIC,
            )
        val json = AtJson.encodeToJsonElement(GetGear.Output.serializer(), output).jsonObject
        assertEquals(setOf("gear", "usage", "retired", "visibility"), json.keys)
        assertEquals(output, AtJson.decodeFromJsonElement(GetGear.Output.serializer(), json))
    }

    @Test
    fun zeroTotalsAreWrittenAndDiveFieldsOmitted() {
        val json = AtJson.encodeToJsonElement(GearTotals.serializer(), GearTotals.ZERO).jsonObject
        assertEquals(setOf("activities", "movingTime", "distance", "elevationGain", "elevationLoss", "days"), json.keys)
    }

    @Test
    fun actorGearOutputRoundTrips() {
        val output =
            GetActorGear.Output(
                listOf(ActorGearItem(bike, GearTotals.ZERO.copy(activities = 1, dives = 1, bottomTime = 2_400), retired = true)),
                "cursor",
            )
        val text = AtJson.encodeToString(GetActorGear.Output.serializer(), output)
        assertEquals(output, AtJson.decodeFromString(GetActorGear.Output.serializer(), text))
    }

    @Test
    fun everyTypeHasTheSpecsMainTotal() {
        val expected =
            mapOf(
                GearType.BIKE to GearMainTotal.DISTANCE,
                GearType.COMPONENT to GearMainTotal.DISTANCE,
                GearType.SHOES to GearMainTotal.DISTANCE,
                GearType.SKI to GearMainTotal.DAYS_AND_VERTICAL,
                GearType.SNOWBOARD to GearMainTotal.DAYS_AND_VERTICAL,
                GearType.SKI_BOOTS to GearMainTotal.DAYS_AND_VERTICAL,
                GearType.SNOWBOARD_BOOTS to GearMainTotal.DAYS_AND_VERTICAL,
                GearType.XC_SKI to GearMainTotal.DAYS_AND_VERTICAL,
                GearType.SKATES to GearMainTotal.DISTANCE,
                GearType.BOARD to GearMainTotal.TIME,
                GearType.BOAT to GearMainTotal.TIME,
                GearType.PADDLE to GearMainTotal.TIME,
                GearType.WETSUIT to GearMainTotal.TIME,
                GearType.DIVE_GEAR to GearMainTotal.DIVES,
                GearType.HELMET to GearMainTotal.TIME,
                GearType.TRAINER to GearMainTotal.TIME,
                GearType.WHEELCHAIR to GearMainTotal.DISTANCE,
                GearType.STROLLER to GearMainTotal.DISTANCE,
                GearType.SENSOR to GearMainTotal.TIME,
                GearType.DEVICE to GearMainTotal.TIME,
                GearType.OTHER to GearMainTotal.TIME,
            )
        assertEquals(GearType.all.toSet(), expected.keys)
        GearType.all.forEach { assertEquals(expected[it], GearType.mainTotal(it), it) }
        assertEquals(GearMainTotal.TIME, GearType.mainTotal("unicycle"))
    }
}
