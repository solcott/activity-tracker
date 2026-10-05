package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.electric_bike
import io.github.solcott.activitytracker.composeApp.resources.sports_and_outdoors
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SportIconsTest {
    @Test
    fun everyCatalogSportHasItsOwnEntry() {
        val missing = SportCatalog.sports.map { it.sport }.filterNot { it in SPORT_ICONS }
        assertTrue(missing.isEmpty(), "No icon for $missing")
    }

    @Test
    fun subSportsOverrideTheirSportAndUnknownSportsFallBack() {
        assertEquals(Res.drawable.electric_bike, activityTypeIcon(ActivityType(Sport.RIDE, SubSport.E_BIKE)))
        assertEquals(SPORT_ICONS[Sport.RIDE], activityTypeIcon(ActivityType(Sport.RIDE, SubSport.GRAVEL)))
        assertEquals(Res.drawable.sports_and_outdoors, activityTypeIcon(ActivityType("quidditch")))
    }
}
