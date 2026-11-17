package io.github.solcott.activitytracker.data

import io.github.solcott.uistate.pageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AreaReadTest {
    /** Pages by index: page i answers [pages][i] and a cursor to the next while there is one. */
    private class Pages(private val pages: List<List<String>>) {
        val asked = mutableListOf<String?>()

        fun read(cursor: String?) = run {
            asked += cursor
            val i = cursor?.toInt() ?: 0
            pageOf(pages[i], (i + 1).takeIf { it < pages.size }?.toString())
        }
    }

    @Test
    fun followsEveryCursorKeepingEachItemOnceInFirstReadOrder() = runTest {
        val pages = Pages(listOf(listOf("a", "b"), listOf("b", "c"), listOf("a", "d")))
        val read = readArea(cap = 10, maxRequests = 10, keyOf = { it }) { pages.read(it) }
        assertEquals(listOf("a", "b", "c", "d"), read.items)
        assertFalse(read.capped)
        assertEquals(listOf(null, "1", "2"), pages.asked)
    }

    @Test
    fun stopsOnceTheCapIsReachedAndKeepsWhatItRead() = runTest {
        val pages = Pages(listOf(listOf("a", "b", "c"), listOf("d")))
        val read = readArea(cap = 2, maxRequests = 10, keyOf = { it }) { pages.read(it) }
        assertEquals(listOf("a", "b", "c"), read.items, "cutting to the cap is the caller's")
        assertTrue(read.capped)
        assertEquals(listOf<String?>(null), pages.asked)
    }

    @Test
    fun stopsAfterTheLastRequestAllowed() = runTest {
        val pages = Pages(listOf(emptyList(), emptyList(), listOf("a")))
        val read = readArea(cap = 10, maxRequests = 2, keyOf = { it }) { pages.read(it) }
        assertEquals(emptyList(), read.items)
        assertTrue(read.capped, "a cursor was left")
    }

    @Test
    fun exactlyTheCapOnTheLastPageIsNotCapped() = runTest {
        val pages = Pages(listOf(listOf("a"), listOf("b")))
        val read = readArea(cap = 2, maxRequests = 10, keyOf = { it }) { pages.read(it) }
        assertEquals(listOf("a", "b"), read.items)
        assertFalse(read.capped)
    }
}
