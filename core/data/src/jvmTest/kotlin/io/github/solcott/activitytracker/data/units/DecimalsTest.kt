package io.github.solcott.activitytracker.data.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalsTest {
    @Test
    fun parsesEitherSeparator() {
        assertEquals(5.2, parseDecimal("5,2"))
        assertEquals(5.2, parseDecimal(" 5.2 "))
        assertEquals(-5.5, parseDecimal("-5,5"))
        assertEquals(30.0, parseDecimal("30"))
    }

    @Test
    fun rejectsTextThatIsNotAFiniteNumber() {
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("NaN"))
        assertNull(parseDecimal("Infinity"))
        assertNull(parseDecimal("1,5,2"))
    }

    @Test
    fun formatsWithTheGivenSeparator() {
        assertEquals("5.20", formatDecimal(5.2, 2, separator = '.'))
        assertEquals("5,20", formatDecimal(5.2, 2, separator = ','))
        assertEquals("0,07", formatDecimal(0.066, 2, separator = ','))
        assertEquals("-3.5", formatDecimal(-3.46, 1, separator = '.'))
        assertEquals("12", formatDecimal(12.3, 0, separator = '.'))
    }

    @Test
    fun trimsZerosForInputText() {
        assertEquals("61,5", formatDecimal(61.5, 1, trimZeros = true, separator = ','))
        assertEquals("150", formatDecimal(150.04, 1, trimZeros = true, separator = ','))
    }
}
