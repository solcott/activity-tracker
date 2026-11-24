package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An activity's title and description against the lexicon's limits, counted and cut in characters. */
class ActivityTextTest {
    @Test
    fun aCharacterOutsideTheBasicPlaneCountsOnce() {
        assertEquals(3, ActivityRecord.textLength("a😀b"))
    }

    @Test
    fun cuttingNeverSplitsASurrogatePair() {
        assertEquals("a😀", ActivityRecord.clampText("a😀😀", 2))
        assertEquals("short", ActivityRecord.clampText("short", 10))
    }

    @Test
    fun aPublishedTitleIsTrimmedCutAndNullWhenBlank() {
        assertEquals("x".repeat(ActivityRecord.MAX_TITLE_GRAPHEMES), ActivityRecord.publishedTitle("  " + "x".repeat(130)))
        assertEquals("Ride", ActivityRecord.publishedTitle(" Ride \n"))
        assertNull(ActivityRecord.publishedTitle("   "))
        assertNull(ActivityRecord.publishedDescription(null))
    }
}
