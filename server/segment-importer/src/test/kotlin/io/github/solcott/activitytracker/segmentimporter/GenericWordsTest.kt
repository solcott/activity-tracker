package io.github.solcott.activitytracker.segmentimporter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which generic words names drop from their end (segments/trail-candidates). */
class GenericWordsTest {
    private fun shorten(name: String) = GenericWords.ENGLISH.shorten(name)

    @Test
    fun trailAndPathAreDropped() {
        assertEquals("Gas Line", shorten("Gas Line Trail"))
        assertEquals("Hoof N Boot", shorten("Hoof N Boot Path"))
        assertEquals("Coyote Hollow", shorten("Coyote Hollow Foot Path"))
        assertEquals("Lake", shorten("Lake Footpath"))
    }

    @Test
    fun roadIsKept() {
        assertEquals("Corner Canyon Road", shorten("Corner Canyon Road"))
    }

    @Test
    fun aSectionedNameLosesAWordFromEachSide() {
        assertEquals("Bonneville Shoreline - Draper", shorten("Bonneville Shoreline Trail - Draper Section"))
        assertEquals("Rush - 2112", shorten("Rush Trail - 2112 Section"))
    }

    @Test
    fun theWordBeforeAQualifierIsDropped() {
        assertEquals("Peak View (Lower)", shorten("Peak View Trail (Lower)"))
    }

    @Test
    fun aWordIsKeptWhenNothingOrOnlyAnArticleWouldBeLeft() {
        assertEquals("Trail", shorten("Trail"))
        assertEquals("The Trail", shorten("The Trail"))
        assertEquals("Rush - Section", shorten("Rush Trail - Section"))
    }

    @Test
    fun onlyOneTrailingWholeWordIsDropped() {
        assertEquals("Section 2", shorten("Section 2 Path"))
        assertEquals("Pine Trail", shorten("Pine Trail Trail"))
        assertEquals("Lost Trailhead", shorten("Lost Trailhead"))
        assertEquals("Pathfinder", shorten("Pathfinder"))
        assertEquals("Upper Rattler Connector", shorten("Upper Rattler Connector Trail"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals("SKYLINE", shorten("SKYLINE TRAIL"))
    }

    @Test
    fun aLanguageWithNoListKeepsItsNames() {
        val german = GenericWords.forLanguage("de")
        assertFalse(german.known)
        assertTrue(GenericWords.ENGLISH.known)
        assertEquals("Hochwald Weg", german.shorten("Hochwald Weg"))
        assertEquals("Oak Trail", german.shorten("Oak Trail"))
    }
}
