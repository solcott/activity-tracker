package io.github.solcott.activitytracker.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a signed-in session shows while a leave is recorded here, or after one on another device (auth/leaving). */
class SessionModeTest {
    @Test
    fun nothingShowsUntilItsKnownWhetherALeaveIsRecorded() {
        assertEquals(SessionMode.Unknown, sessionModeOf(leaving = null, left = false))
        assertEquals(SessionMode.Unknown, sessionModeOf(leaving = null, left = true))
    }

    @Test
    fun aLeaveRecordedHereShowsOnlyTheLeaveEvenOnceItsSpacesAreGone() {
        assertEquals(SessionMode.Leaving, sessionModeOf(leaving = true, left = false))
        assertEquals(SessionMode.Leaving, sessionModeOf(leaving = true, left = true))
    }

    @Test
    fun aLeaveElsewhereShowsTheChoice() {
        assertEquals(SessionMode.LeftElsewhere, sessionModeOf(leaving = false, left = true))
    }

    @Test
    fun otherwiseTheAppOpens() {
        assertEquals(SessionMode.Open, sessionModeOf(leaving = false, left = false))
    }
}
