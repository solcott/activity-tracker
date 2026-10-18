package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.WaterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitDivesTest {
    @Suppress("LongParameterList") // Each kind of dive message a test varies.
    private fun messages(
        gases: List<FitDiveGas> = emptyList(),
        switches: List<Int> = emptyList(),
        summaries: List<FitDiveSummary> = emptyList(),
        tanks: List<FitTankSummary> = emptyList(),
        switchTimes: List<Long?> = switches.indices.map { (it + 1) * 60_000L },
        tankUpdates: List<FitTankUpdate> = emptyList(),
    ) =
        FitDiveMessages(
            summaries,
            40,
            85,
            gases,
            switches.zip(switchTimes) { index, time -> FitGasSwitch(time, index) },
            tanks,
            null,
            null,
            null,
            null,
            null,
            tankUpdates = tankUpdates,
        )

    private fun gas(index: Int, o2: Int, he: Int = 0, status: String? = "ENABLED", mode: String? = null) =
        FitDiveGas(index, o2, he, status, mode)

    private fun summary(reference: String?, diveNumber: Int? = null, maxDepth: Double = 30.0) =
        FitDiveSummary(reference, 15.0, maxDepth, 600.0, diveNumber, 1800.0, null, null, null, null, null, null)

    @Test
    fun startsOnTheFirstEnabledGasThenFollowsTheSwitches() {
        // Garmin's Mk1 trimix layout: three gases set up, three disabled 21/0 placeholders, two switches.
        val gases = listOf(gas(0, 16, 45), gas(1, 50), gas(2, 99)) + (3..5).map { gas(it, 21, status = "disabled") }
        val dive = assertNotNull(FitDives.interpret(messages(gases, switches = listOf(1, 2)), "DIVING", "MULTI_GAS_DIVING"))
        assertEquals(listOf(DiveGas(16, 45), DiveGas(50, 0), DiveGas(99, 0)), dive.gases)
        assertFalse(dive.closedCircuit, "a Mk1 gas has no mode")
    }

    @Test
    fun aGasSetUpButNeverSwitchedToWasntBreathed() {
        val dive = assertNotNull(FitDives.interpret(messages(listOf(gas(1, 50), gas(0, 32))), "diving", "singleGasDiving"))
        assertEquals(listOf(DiveGas(32, 0)), dive.gases)
    }

    @Test
    fun aBackupGasCountsOnlyOnceSwitchedTo() {
        val gases = listOf(gas(0, 50, status = "backup_only"), gas(1, 21))
        assertEquals(listOf(DiveGas(21, 0)), FitDives.interpret(messages(gases), "diving", null)!!.gases)
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), FitDives.interpret(messages(gases, listOf(0)), "diving", null)!!.gases)
    }

    @Test
    fun closedCircuitFromTheSubSportOrADiluent() {
        val diluent = listOf(gas(0, 17, 30, mode = "closedCircuitDiluent"))
        assertTrue(FitDives.interpret(messages(diluent), "diving", null)!!.closedCircuit)
        assertTrue(FitDives.interpret(messages(listOf(gas(0, 21))), "DIVING", "CCR_DIVING")!!.closedCircuit)
        assertFalse(FitDives.interpret(messages(listOf(gas(0, 21, mode = "OPEN_CIRCUIT"))), "DIVING", null)!!.closedCircuit)
    }

    @Test
    fun theSessionSummaryWinsOverTheLaps() {
        val summaries = listOf(summary("lap", maxDepth = 10.0), summary("SESSION", diveNumber = 12), summary("lap", maxDepth = 11.0))
        val dive = FitDives.interpret(messages(summaries = summaries), "diving", null)!!
        assertEquals(12, dive.diveNumber)
        assertEquals(30.0, dive.maxDepthMeters)
        assertEquals(10, dive.surfaceIntervalMinutes)

        // An SDK that doesn't name the message passes its number; with no session summary, the last one is used.
        assertEquals(12, FitDives.interpret(messages(summaries = listOf(summary("18", diveNumber = 12))), "diving", null)!!.diveNumber)
        val lapsOnly = listOf(summary("lap", maxDepth = 10.0), summary("lap", maxDepth = 11.0))
        assertEquals(11.0, FitDives.interpret(messages(summaries = lapsOnly), "diving", null)!!.maxDepthMeters)
    }

    @Test
    fun anUnusedTransmitterIsNotATank() {
        val tanks = listOf(FitTankSummary(210.0, 50.0, 0.0), FitTankSummary(0.0, 0.0, null))
        val dive = FitDives.interpret(messages(tanks = tanks), "diving", null)!!
        assertEquals(listOf(DiveTank(210.0, 50.0, null)), dive.tanks.map { it.copy(sensor = null) })
    }

    @Test
    fun switchesNameTheirGasAndDropUnknownSlots() {
        val gases = listOf(gas(0, 21), gas(1, 50), gas(2, 21, status = "disabled"))
        val messages = messages(gases, switches = listOf(1, 7, 2, 0), switchTimes = listOf(1_920_000L, 2_000_000L, 2_100_000L, 2_400_000L))
        assertEquals(
            listOf(DecodedGasSwitch(1_920_000, DiveGas(50, 0)), DecodedGasSwitch(2_400_000, DiveGas(21, 0))),
            FitDives.gasSwitches(messages),
            "slot 7 doesn't exist and slot 2 is disabled; the switch back to air stays",
        )
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), FitDives.interpret(messages, "diving", null)!!.gases, "each gas once")
    }

    @Test
    fun aSwitchWithoutATimeIsDroppedButStillNamesAGasBreathed() {
        val messages = messages(listOf(gas(0, 21), gas(1, 50)), switches = listOf(1), switchTimes = listOf(null))
        assertEquals(emptyList(), FitDives.gasSwitches(messages))
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), FitDives.interpret(messages, "diving", null)!!.gases)
    }

    @Test
    fun tankUpdatesFollowTheTankSummariesSensors() {
        val tanks =
            listOf(
                FitTankSummary(210.0, 50.0, null, sensor = 9),
                FitTankSummary(0.0, 0.0, null, sensor = 3),
                FitTankSummary(200.0, 190.0, null, sensor = 4),
            )
        val updates =
            listOf(
                FitTankUpdate(1_000, 4, 200.0),
                FitTankUpdate(1_000, 9, 210.0),
                FitTankUpdate(2_000, 3, 0.0),
                FitTankUpdate(3_000, 5, 180.0),
                FitTankUpdate(null, 9, 200.0),
            )
        val messages = messages(tanks = tanks, tankUpdates = updates)
        assertEquals(
            listOf("9", "4"),
            FitDives.interpret(messages, "diving", null)!!.tanks.map { it.sensor },
            "in tank_summary order, unused left out",
        )
        assertEquals(
            listOf(DecodedTankUpdate(1_000, "4", 200.0), DecodedTankUpdate(1_000, "9", 210.0)),
            FitDives.tankUpdates(messages),
            "readings from an unused or unknown sensor, or with no time, are dropped",
        )
    }

    @Test
    fun tankUpdatesWithoutSummariesMakeTheirOwnTanks() {
        val updates = listOf(FitTankUpdate(1_000, 4, 200.0), FitTankUpdate(2_000, 4, 150.0))
        val dive = FitDives.interpret(messages(tankUpdates = updates), "diving", null)!!
        assertEquals(listOf(DiveTank(200.0, 150.0, null, sensor = "4")), dive.tanks)
        assertEquals(2, FitDives.tankUpdates(messages(tankUpdates = updates)).size)
    }

    @Test
    fun readsWaterTypeAndTheSummarysNitrogenAndRates() {
        val summary =
            summary("session")
                .copy(startN2 = 10, endN2 = 55, avgAscentRate = 0.15, maxAscentRate = 0.0, maxDescentRate = 0.4, descentTimeSeconds = 300.0)
        val dive =
            FitDives.interpret(
                messages(summaries = listOf(summary)).copy(waterType = "EN13319", waterDensityKgPerM3 = 1020.0),
                "diving",
                null,
            )!!
        assertEquals(10 to 55, dive.startN2 to dive.endN2)
        assertEquals(0.15, dive.avgAscentRateMps)
        assertNull(dive.maxAscentRateMps, "0 is unset")
        assertEquals(0.4, dive.maxDescentRateMps)
        assertEquals(300.0, dive.descentTimeSeconds)
        assertEquals(WaterType.EN13319, dive.waterType)
        assertEquals(1020.0, dive.waterDensityKgPerM3)
        assertNull(FitDives.interpret(messages().copy(waterType = "invalid"), "diving", null)!!.waterType)
    }

    @Test
    fun readsTheDiveComputersSettings() {
        val settings =
            messages()
                .copy(
                    decoModel = "ZHL_16C",
                    ppO2WarnBar = 1.4,
                    ppO2CriticalBar = 1.6,
                    ppO2DecoBar = 1.6,
                    safetyStopEnabled = true,
                    safetyStopTimeSeconds = 180,
                    lowSetpointBar = 0.7,
                    highSetpointBar = 1.3,
                )
        val dive = FitDives.interpret(settings, "diving", null)!!
        assertEquals(DecoModel.ZHL16C, dive.decoModel)
        assertEquals(Triple(1.4, 1.6, 1.6), Triple(dive.ppO2WarnBar, dive.ppO2CriticalBar, dive.ppO2DecoBar))
        assertEquals(0.7 to 1.3, dive.lowSetpointBar to dive.highSetpointBar)
        assertEquals(180, dive.safetyStopSeconds)

        val off = FitDives.interpret(settings.copy(safetyStopEnabled = false, decoModel = "invalid"), "diving", null)!!
        assertNull(off.safetyStopSeconds, "a safety stop that was off has no time")
        assertNull(off.decoModel)
        assertNull(FitDives.interpret(messages(), "diving", null)!!.ppO2WarnBar)
    }

    @Test
    fun alertsResolveTheirKindAndDropDismissalsAndUntimedOnes() {
        val alerts =
            listOf(
                FitDiveAlert(1_500_000, 0),
                FitDiveAlert(1_510_000, 18),
                FitDiveAlert(1_520_000, 19, "ALERT_DISMISSED_BY_TIMEOUT"),
                FitDiveAlert(null, 3),
                FitDiveAlert(1_600_000, 120),
                FitDiveAlert(1_700_000, 121, "SOME_NEW_ALERT"),
            )
        assertEquals(
            listOf(
                DecodedDiveAlert(1_500_000, DiveAlertKind.NDL_REACHED),
                DecodedDiveAlert(1_600_000, "120"),
                DecodedDiveAlert(1_700_000, "someNewAlert"),
            ),
            FitDives.diveAlerts(messages().copy(diveAlerts = alerts)),
        )
    }

    @Test
    fun onlyDivesHaveDiveData() {
        assertNull(FitDives.interpret(messages(), "running", null))
        assertNotNull(FitDives.interpret(messages(), "diving", null), "a dive with only a depth profile")
        assertNotNull(FitDives.interpret(messages(listOf(gas(0, 21))), null, null))
    }
}
