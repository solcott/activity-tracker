package io.github.solcott.activitytracker.data

import io.github.solcott.uistate.pageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PagesReadTest {
    @Test
    fun followsEveryCursorToTheLastPage() = runTest {
        val asked = mutableListOf<String?>()
        val pages = listOf(listOf("a", "b"), listOf("b", "c"), listOf("d"))
        val read = readPages { cursor ->
            asked += cursor
            val i = cursor?.toInt() ?: 0
            pageOf(pages[i], (i + 1).takeIf { it < pages.size }?.toString())
        }
        assertEquals(listOf("a", "b", "b", "c", "d"), read.items, "every item, in order, repeats kept")
        assertFalse(read.truncated)
        assertEquals(listOf(null, "1", "2"), asked)
    }

    @Test
    fun stopsAtThePageLimitWithACursorLeft() = runTest {
        var reads = 0
        val read = readPages(maxPages = 3) { cursor -> pageOf(listOf(reads++), "${cursor.orEmpty()}x") }
        assertEquals(listOf(0, 1, 2), read.items)
        assertTrue(read.truncated)
    }

    @Test
    fun anEmptyPageOrAStuckCursorEndsTheRead() = runTest {
        var reads = 0
        val empty = readPages { _ -> pageOf(if (reads++ == 0) listOf("a") else emptyList(), "more") }
        assertEquals(listOf("a"), empty.items)
        assertFalse(empty.truncated)

        var stuck = 0
        val same = readPages { _ -> pageOf(listOf(stuck++), "same") }
        assertEquals(listOf(0, 1), same.items, "the second page repeats the cursor it was read with")
        assertFalse(same.truncated)
    }
}
