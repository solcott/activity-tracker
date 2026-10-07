package io.github.solcott.activitytracker.data.training

import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.HeartRateZoneModel
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportZonesTest {
    private val run =
        SportTraining(
            sport = Sport.RUN,
            thresholdHeartRate = 172,
            thresholdSpeed = 3271,
            heartRateZoneModel = HeartRateZoneModel.FRIEL_RUNNING,
        )
    private val training =
        TrainingDetails(
            functionalThresholdPower = 231,
            maxHeartRate = 190,
            thresholdHeartRate = 165,
            restingHeartRate = 50,
            heartRateZoneModel = HeartRateZoneModel.FRIEL_CYCLING,
            powerZoneModel = PowerZoneModel.COGGAN_7,
            sports = listOf(run),
        )

    @Test
    fun anOverrideReplacesOnlyWhatItSets() {
        assertEquals(
            EffectiveTraining(
                functionalThresholdPower = 231,
                maxHeartRate = 190,
                thresholdHeartRate = 172,
                restingHeartRate = 50,
                thresholdSpeed = 3271,
                heartRateZoneModel = HeartRateZoneModel.FRIEL_RUNNING,
                powerZoneModel = PowerZoneModel.COGGAN_7,
            ),
            training.forSport(Sport.RUN),
        )
    }

    @Test
    fun otherSportsUseTheDefaults() {
        val defaults = training.forSport(null)
        assertEquals(defaults, training.forSport(Sport.RIDE))
        assertEquals(165, defaults.thresholdHeartRate)
        assertNull(defaults.thresholdSpeed)
    }

    @Test
    fun withOverrideReplacesInPlaceAddsLastAndRemoves() {
        val swim = SportTraining(sport = Sport.SWIM, thresholdSpeed = 1115)
        val both = training.withOverride(Sport.SWIM, swim)
        assertEquals(listOf(run, swim), both.sports)
        assertEquals(listOf(run.copy(thresholdSpeed = 3000), swim), both.withOverride(Sport.RUN, run.copy(thresholdSpeed = 3000)).sports)
        assertEquals(listOf(swim), both.withOverride(Sport.RUN, null).sports)
    }

    @Test
    fun eachSportOffersOnlyTheZonesThatFit() {
        assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Power), SportZones.kinds(null))
        assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Power, ZoneKind.Speed), SportZones.kinds(Sport.RUN))
        assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Speed), SportZones.kinds(Sport.SWIM))
        assertEquals(setOf(ZoneKind.HeartRate, ZoneKind.Power), SportZones.kinds(Sport.ROW))
        assertEquals(setOf(ZoneKind.HeartRate), SportZones.kinds(Sport.HIKE))
    }

    @Test
    fun paceUnitsFollowSportAndUnits() {
        assertEquals(PaceUnit.PerMile, SportZones.paceUnit(Sport.RUN, UnitSystem.Imperial))
        assertEquals(PaceUnit.PerKilometer, SportZones.paceUnit(Sport.RUN, UnitSystem.Metric))
        assertEquals(PaceUnit.Per100Yards, SportZones.paceUnit(Sport.SWIM, UnitSystem.Imperial))
        assertEquals(PaceUnit.Per100Meters, SportZones.paceUnit(Sport.SWIM, UnitSystem.Metric))
    }

    @Test
    fun paceTextRoundTripsAndRejectsNonsense() {
        assertEquals("8:12", PaceText.format(PaceText.parse("8:12", PaceUnit.PerMile)!!, PaceUnit.PerMile))
        assertEquals("1:22", PaceText.format(PaceText.parse("82", PaceUnit.Per100Yards)!!, PaceUnit.Per100Yards))
        assertEquals(PaceText.parse("0:45", PaceUnit.Per100Meters), PaceText.parse(":45", PaceUnit.Per100Meters))
        for (bad in listOf("", "0", "5:7", "5:60", "a:10", "1:2:3")) assertNull(PaceText.parse(bad, PaceUnit.PerKilometer), bad)
    }
}
