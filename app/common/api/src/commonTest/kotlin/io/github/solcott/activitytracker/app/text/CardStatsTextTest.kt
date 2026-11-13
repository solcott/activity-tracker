package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardStatsTextTest {
    @Test fun strengthHidesAZeroDistance() = assertFalse(CardStatsText.showsLength(Sport.STRENGTH, 0.0))

    @Test fun strengthHidesAnAbsentDistance() = assertFalse(CardStatsText.showsLength(Sport.STRENGTH, null))

    @Test fun anIndoorClimbShowsItsAscent() = assertTrue(CardStatsText.showsLength(Sport.CLIMB, 120.0))

    @Test fun aRunShowsAnAbsentDistanceAsADash() = assertTrue(CardStatsText.showsLength(Sport.RUN, null))
}
