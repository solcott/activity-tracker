package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityTypeSearchTest {
    private val ride = ActivityType(Sport.RIDE)
    private val gravel = ActivityType(Sport.RIDE, SubSport.GRAVEL)
    private val road = ActivityType(Sport.RIDE, SubSport.ROAD)
    private val sup = ActivityType(Sport.SUP)
    private val labels = mapOf(ride to "Ride", gravel to "Gravel Ride", road to "Road Ride", sup to "Stand Up Paddle")

    private fun matches(type: ActivityType, query: String) = matchesTypeQuery(type, query, labels)

    @Test
    fun subSportLabel() {
        assertTrue(matches(gravel, "gravel"))
        assertFalse(matches(road, "gravel"))
    }

    @Test
    fun sportLabelMatchesItsSubSports() {
        assertTrue(matches(ride, "ride"))
        assertTrue(matches(gravel, "ride"))
        assertTrue(matches(road, "ride"))
    }

    @Test
    fun tokens() {
        assertTrue(matches(sup, "sup"), "the sport token, though the label is Stand Up Paddle")
        assertTrue(matches(gravel, SubSport.GRAVEL))
    }

    @Test
    fun ignoresCaseAndSurroundingSpace() {
        assertTrue(matches(gravel, "  GRAVEL "))
    }

    @Test
    fun blankMatchesEverything() {
        assertTrue(matches(road, ""))
        assertTrue(matches(road, "   "))
    }
}
