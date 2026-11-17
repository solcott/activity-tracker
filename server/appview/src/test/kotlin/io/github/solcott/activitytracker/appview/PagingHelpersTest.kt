package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.nextCursor
import io.github.solcott.activitytracker.appview.api.nextOffset
import io.github.solcott.activitytracker.appview.api.offsetOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The paging helpers every view shares (`api/ViewQueries.kt`). */
class PagingHelpersTest {
    @Test
    fun aFullPageGivesItsLastRowsCursor() {
        assertEquals("c", nextCursor(listOf("a", "b", "c"), 3) { it })
        assertNull(nextCursor(listOf("a", "b"), 3) { it }, "a short page is the last")
        assertNull(nextCursor(emptyList<String>(), 0) { it })
    }

    @Test
    fun anOffsetCursorIsANonNegativeNumber() {
        assertEquals(0, offsetOf(null))
        assertEquals(40, offsetOf("40"))
        assertEquals(0, offsetOf("-5"))
        assertEquals(0, offsetOf("40::x"))
    }

    @Test
    fun aFullOffsetPageNamesTheNext() {
        assertEquals("75", nextOffset(50, 25, read = 25))
        assertNull(nextOffset(50, 25, read = 24))
    }
}
