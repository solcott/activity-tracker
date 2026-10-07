package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZonesTest {
    private fun ranges(zones: List<Zone>?) = zones?.map { it.low to it.high }

    @Test
    fun cogganPowerFromFtp() {
        val zones = Zones.compute(ZoneModels.cogganPower6, TrainingDetails(functionalThresholdPower = 231))
        assertEquals(listOf(0 to 128, 129 to 175, 176 to 209, 210 to 244, 245 to 279, 280 to null), ranges(zones))
        assertEquals(ZoneName.AnaerobicCapacity, zones?.last()?.name)
    }

    @Test
    fun cogganPowerSevenAddsNeuromuscular() {
        val zones = Zones.compute(ZoneModels.cogganPower7, TrainingDetails(functionalThresholdPower = 200))
        assertEquals(listOf(0 to 111, 112 to 151, 152 to 181, 182 to 211, 212 to 241, 242 to 301, 302 to null), ranges(zones))
    }

    @Test
    fun frielRunningFromLthr() {
        val zones = Zones.compute(ZoneModels.frielRunning, TrainingDetails(thresholdHeartRate = 168))
        assertEquals(
            listOf(0 to 142, 143 to 150, 151 to 159, 160 to 167, 168 to 172, 173 to 179, 180 to null),
            ranges(zones),
        )
        assertEquals(listOf("1", "2", "3", "4", "5a", "5b", "5c"), zones?.map { it.label })
    }

    @Test
    fun percentOfMaxHeartRate() {
        val zones = Zones.compute(ZoneModels.percentMaxHeartRate, TrainingDetails(maxHeartRate = 190))
        assertEquals(listOf(0 to 113, 114 to 132, 133 to 151, 152 to 170, 171 to null), ranges(zones))
    }

    @Test
    fun heartRateReserveAddsRestingHeartRate() {
        val zones = Zones.compute(ZoneModels.heartRateReserve, TrainingDetails(maxHeartRate = 190, restingHeartRate = 55))
        // Reserve 135: 60% = 81, 70% = 94.5, 80% = 108, 90% = 121.5.
        assertEquals(listOf(0 to 135, 136 to 149, 150 to 162, 163 to 176, 177 to null), ranges(zones))
    }

    @Test
    fun missingInputsGiveNoZones() {
        assertNull(Zones.compute(ZoneModels.cogganHeartRate, TrainingDetails(maxHeartRate = 190)))
        assertNull(Zones.compute(ZoneModels.cogganPower6, TrainingDetails()))
        assertNull(Zones.compute(ZoneModels.heartRateReserve, TrainingDetails(maxHeartRate = 190)))
        assertNull(Zones.compute(ZoneModels.heartRateReserve, TrainingDetails(maxHeartRate = 60, restingHeartRate = 60)))
    }

    @Test
    fun unknownModelFallsBackToDefault() {
        assertEquals(ZoneModels.defaultHeartRate, ZoneModels.heartRate("someOtherClientsModel"))
        assertEquals(ZoneModels.frielCycling, ZoneModels.heartRate(ZoneModels.frielCycling.token))
        assertEquals(ZoneModels.defaultPower, ZoneModels.power(null))
    }

    @Test
    fun thresholdInput() {
        assertEquals(168, ThresholdInput.heartRate(" 168 "))
        assertNull(ThresholdInput.heartRate("300"))
        assertNull(ThresholdInput.power("0"))
        assertEquals(true, ThresholdInput.isValidPower(""))
        assertEquals(false, ThresholdInput.isValidHeartRate("abc"))
    }

    @Test
    fun frielRunningPaceFromThresholdPace() {
        // 8:12 per mile, as on TrainingPeaks' example; boundaries land within two seconds of theirs.
        val threshold = PaceText.parse("8:12", PaceUnit.PerMile)
        assertEquals(3271, threshold)
        val zones = Zones.compute(ZoneModels.frielRunningPace, EffectiveTraining(thresholdSpeed = threshold))
        assertEquals(
            listOf(0 to 2535, 2536 to 2868, 2869 to 3085, 3086 to 3270, 3271 to 3371, 3372 to 3633, 3634 to null),
            ranges(zones),
        )
        assertEquals(
            listOf("10:35", "9:21", "8:41", "8:12", "7:57", "7:23"),
            zones?.drop(1)?.map { PaceText.format(it.low.toLong(), PaceUnit.PerMile) },
        )
    }

    @Test
    fun frielSwimmingPaceFromThresholdPace() {
        val threshold = PaceText.parse("1:22", PaceUnit.Per100Yards)
        val zones = Zones.compute(ZoneModels.frielSwimmingPace, EffectiveTraining(thresholdSpeed = threshold))
        assertEquals(
            listOf("1:46", "1:33", "1:27", "1:22", "1:20", "1:14"),
            zones?.drop(1)?.map { PaceText.format(it.low.toLong(), PaceUnit.Per100Yards) },
        )
        assertNull(Zones.compute(ZoneModels.frielSwimmingPace, EffectiveTraining()))
    }

    @Test
    fun speedModelsDefaultBySport() {
        assertEquals(ZoneModels.frielSwimmingPace, ZoneModels.speed(null, "swim"))
        assertEquals(ZoneModels.frielRunningPace, ZoneModels.speed("someOtherClientsModel", "run"))
        assertEquals(ZoneModels.frielSwimmingPace, ZoneModels.speed(ZoneModels.frielSwimmingPace.token, "run"))
    }
}
