package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvTest {
    @Test
    fun readsPlainAndQuotedFields() {
        val csv = Csv.parse("Name,Note\nLunch loop,\"Windy, cold\"\r\n\"Say \"\"hi\"\"\",plain\n")
        assertEquals(2, csv.rows.size)
        assertEquals("Windy, cold", csv.value(csv.rows[0], "Note"))
        assertEquals("Say \"hi\"", csv.value(csv.rows[1], "Name"))
    }

    @Test
    fun keepsNewlinesInsideQuotes() {
        val csv = Csv.parse("Name,Description\nRide,\"Line one\nLine two\"\nRun,x")
        assertEquals(listOf("Ride", "Run"), csv.rows.map { csv.value(it, "Name") })
        assertEquals("Line one\nLine two", csv.value(csv.rows[0], "Description"))
    }

    @Test
    fun firstOfARepeatedColumnWins() {
        val csv = Csv.parse("Distance,Elapsed Time,Distance\n12.5,600,12500")
        assertEquals("12.5", csv.value(csv.rows.single(), "distance"))
    }

    @Test
    fun missingColumnsAndBlankValuesAreNull() {
        val csv = Csv.parse("﻿Name,Note\nA,\n")
        assertTrue(csv.has("Name"))
        assertFalse(csv.has("Gear"))
        assertNull(csv.value(csv.rows.single(), "Note"))
        assertNull(csv.value(csv.rows.single(), "Gear"))
    }
}
