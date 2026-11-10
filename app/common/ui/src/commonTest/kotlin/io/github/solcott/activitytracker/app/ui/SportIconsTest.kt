package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.electric_bike
import io.github.solcott.activitytracker.composeApp.resources.sports_and_outdoors
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals

/** A type's icon is the vendored drawable its shared name says (`SportIconNames`, whose own test covers which name). */
class SportIconsTest {
    @Test
    fun aNameIsFoundAmongTheDrawables() {
        assertEquals(Res.drawable.electric_bike, activityTypeIcon(ActivityType(Sport.RIDE, SubSport.E_BIKE)))
        assertEquals(Res.drawable.sports_and_outdoors, activityTypeIcon(ActivityType("quidditch")))
    }
}
