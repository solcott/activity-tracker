package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityStatsTest {
    @Test fun `strength hides a zero distance`() = assertFalse(showsLength(Sport.STRENGTH, 0.0))

    @Test fun `strength hides an absent distance`() = assertFalse(showsLength(Sport.STRENGTH, null))

    @Test fun `an indoor climb shows its ascent`() = assertTrue(showsLength(Sport.CLIMB, 120.0))

    @Test fun `a run shows an absent distance as a dash`() = assertTrue(showsLength(Sport.RUN, null))
}
