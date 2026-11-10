package io.github.solcott.activitytracker.app.icon

import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportIconNamesTest {
    @Test
    fun everyCatalogSportHasItsOwnEntry() {
        val missing = SportCatalog.sports.map { it.sport }.filterNot(SportIconNames::names)
        assertTrue(missing.isEmpty(), "No icon for $missing")
    }

    @Test
    fun subSportsOverrideTheirSportAndUnknownSportsFallBack() {
        assertEquals("electric_bike", SportIconNames.of(ActivityType(Sport.RIDE, SubSport.E_BIKE)))
        assertEquals("directions_bike", SportIconNames.of(ActivityType(Sport.RIDE, SubSport.GRAVEL)))
        assertEquals(SportIconNames.GENERIC, SportIconNames.of(ActivityType("quidditch")))
    }
}
