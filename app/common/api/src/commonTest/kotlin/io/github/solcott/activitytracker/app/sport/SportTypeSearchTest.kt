package io.github.solcott.activitytracker.app.sport

import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportTypeSearchTest {
    private val ride = ActivityType(Sport.RIDE)
    private val gravel = ActivityType(Sport.RIDE, SubSport.GRAVEL)
    private val road = ActivityType(Sport.RIDE, SubSport.ROAD)
    private val sup = ActivityType(Sport.SUP)
    private val labels = mapOf(ride to "Ride", gravel to "Gravel Ride", road to "Road Ride", sup to "Stand Up Paddle")

    private fun matches(type: ActivityType, query: String) =
        SportTypeSearch.matches(type, query, labels[type], labels[ActivityType(type.sport)])

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

    @Test
    fun aTextSourceReadsTheLabels() {
        // A source that knows only Gravel Ride's sub-sport label: the sport's label falls back to its token's name.
        val source =
            object : TextSource {
                override fun string(name: String, args: List<String>) = if (name == "sub_sport_ride_gravel") "Gravel Ride" else null

                override fun plural(name: String, quantity: Int, args: List<String>): String? = null

                override fun time(epochSeconds: Long, utcOffsetMinutes: Int?) = ""
            }
        assertTrue(SportTypeSearch.matches(gravel, "GRAVEL", source))
        assertFalse(SportTypeSearch.matches(road, "gravel", source))
        assertTrue(SportTypeSearch.searching(" x "))
        assertFalse(SportTypeSearch.searching("  "))
    }
}
