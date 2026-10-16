package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.Sport as GarminSport
import com.garmin.fit.SubSport as GarminSubSport
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FitSportsTest {
    @Test
    fun everyEntryImportsBackAsItsType() {
        for ((type, fit) in FitSports.entries) {
            val sport = assertNotNull(GarminSport.getByValue(fit.sport.toShort()), "$type: sport ${fit.sport}")
            val subSport = assertNotNull(GarminSubSport.getByValue(fit.subSport.toShort()), "$type: sub-sport ${fit.subSport}")
            assertEquals(type, DecodedActivityMapper.activityType(sport.name, subSport.name), "$type as ${sport.name}/${subSport.name}")
        }
    }

    @Test
    fun aSubSportFitLacksFallsBackToItsSport() {
        assertEquals(FitSports.of(ActivityType(Sport.RUN)), FitSports.of(ActivityType(Sport.RUN, SubSport.STROLLER)))
    }

    @Test
    fun aTypeFitLacksIsGeneric() {
        assertEquals(FitSports.GENERIC, FitSports.of(ActivityType(Sport.GAMING)))
    }

    @Test
    fun garminConventions() {
        assertEquals(
            FitSport(GarminSport.RUNNING.value.toInt(), GarminSubSport.TRAIL.value.toInt()),
            FitSports.of(ActivityType(Sport.RUN, SubSport.TRAIL)),
        )
        assertEquals(
            FitSport(GarminSport.TRAINING.value.toInt(), GarminSubSport.YOGA.value.toInt()),
            FitSports.of(ActivityType(Sport.YOGA)),
        )
        assertEquals(
            FitSport(GarminSport.CYCLING.value.toInt(), GarminSubSport.INDOOR_CYCLING.value.toInt()),
            FitSports.of(ActivityType(Sport.RIDE, SubSport.INDOOR)),
        )
    }
}
