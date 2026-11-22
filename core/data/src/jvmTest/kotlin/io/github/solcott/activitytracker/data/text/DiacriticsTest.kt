package io.github.solcott.activitytracker.data.text

import kotlin.test.Test
import kotlin.test.assertEquals

class DiacriticsTest {
    @Test
    fun marksGoCaseStays() {
        assertEquals("Cote d'Ivoire", stripDiacritics("Côte d'Ivoire"))
    }

    @Test
    fun searchKeyFoldsCaseMarksAndSpace() {
        assertEquals("potato", searchKey("  Pötato "))
    }
}
