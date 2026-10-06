package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitDivesTest {
    private fun messages(
        gases: List<FitDiveGas> = emptyList(),
        switches: List<Int> = emptyList(),
        summaries: List<FitDiveSummary> = emptyList(),
        tanks: List<FitTankSummary> = emptyList(),
    ) = FitDiveMessages(summaries, 40, 85, gases, switches, tanks, null, null, null, null, null)

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
        assertEquals(listOf(DiveTank(210.0, 50.0, null)), dive.tanks)
    }

    @Test
    fun onlyDivesHaveDiveData() {
        assertNull(FitDives.interpret(messages(), "running", null))
        assertNotNull(FitDives.interpret(messages(), "diving", null), "a dive with only a depth profile")
        assertNotNull(FitDives.interpret(messages(listOf(gas(0, 21))), null, null))
    }
}
