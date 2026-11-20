package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.TrailAttributesSanitizer
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the AppView keeps of untrusted trail attributes (segments/trail-attributes). */
class TrailAttributesSanitizerTest {
    private fun clean(trail: TrailAttributes) = TrailAttributesSanitizer.clean(trail)

    @Test
    fun anUnsafeWebsiteIsDroppedAndTheRestKept() {
        val cleaned = clean(TrailAttributes(website = "javascript:alert(1)", lit = "yes"))
        assertEquals(TrailAttributes(lit = "yes"), cleaned)
        assertEquals("https://kingdomtrails.org/a?b=1", clean(TrailAttributes(website = "https://kingdomtrails.org/a?b=1"))?.website)
        assertNull(clean(TrailAttributes(website = "https:///nohost")), "no host")
        assertNull(clean(TrailAttributes(website = "ftp://example.org")))
        assertNull(clean(TrailAttributes(website = "https://example.org/" + "a".repeat(600))), "over 512 characters")
    }

    @Test
    fun valuesOutsideTheirRangeOrVocabularyAreDropped() {
        assertNull(clean(TrailAttributes(mtbScale = 9, sacScale = "T3", trailClass = 0, widthCm = 0, dog = "maybe")))
        assertEquals(TrailAttributes(mtbScale = 6), clean(TrailAttributes(mtbScale = 6, lit = "sometimes")))
    }

    @Test
    fun openDatesComeOnlyInValidPairs() {
        assertNull(clean(TrailAttributes(openFrom = "06-15")), "a lone openFrom")
        assertNull(clean(TrailAttributes(openFrom = "13-01", openUntil = "10-15")))
        assertEquals(
            TrailAttributes(openFrom = "06-15", openUntil = "10-15"),
            clean(TrailAttributes(openFrom = "06-15", openUntil = "10-15")),
        )
    }

    @Test
    fun surfacesAndSeasonsAreKeptOnlyWhenTheyFit() {
        val surfaces = listOf(TrailSurface("gravel", 30), TrailSurface("paved", 70), TrailSurface("lava", 10), TrailSurface("dirt", 20))
        assertEquals(
            listOf(TrailSurface("paved", 70), TrailSurface("gravel", 30)),
            clean(TrailAttributes(surfaces = surfaces))?.surfaces,
            "largest first, unknown dropped, and none past 100%",
        )
        assertNull(clean(TrailAttributes(seasons = listOf("monsoon"))))
        assertEquals(listOf("winter"), clean(TrailAttributes(seasons = listOf("winter", "winter")))?.seasons)
        assertNull(clean(TrailAttributes(seasonal = false)))
    }

    @Test
    fun anOverlongOperatorIsDropped() {
        assertNull(clean(TrailAttributes(operator = "x".repeat(101))))
        assertEquals("Green Mountain Club", clean(TrailAttributes(operator = " Green Mountain Club "))?.operator)
    }
}
