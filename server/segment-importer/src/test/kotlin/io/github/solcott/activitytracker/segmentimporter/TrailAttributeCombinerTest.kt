package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import io.github.solcott.activitytracker.lexicon.app.TrailSources
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Combining what a line's ways say into its attributes (segments/trail-attributes, "Attributes combine over the ways a line covers"). */
class TrailAttributeCombinerTest {
    private fun combine(vararg stretches: Pair<WayFacts, Double>) =
        TrailAttributeCombiner.combine(stretches.map { (facts, meters) -> FactsAlong(facts, meters) })

    private val none = WayFacts.NONE

    @Test
    fun eachAttributeNamesTheSourceGivingItAlongTheMostOfTheLine() {
        val trail =
            combine(
                WayFacts(surface = "dirt", source = TrailSource.USFS, widthCm = 50) to 1_200.0,
                WayFacts(surface = "dirt", source = TrailSource.OSM, dog = "leashed") to 400.0,
            )
        assertEquals(TrailSources(surface = TrailSource.USFS, width = TrailSource.USFS, dog = TrailSource.OSM), trail?.sources)
    }

    @Test
    fun filledAttributesKeepTheirOwnSources() {
        val osm = TrailAttributes(dog = "leashed", sources = TrailSources(dog = TrailSource.OSM))
        val agency =
            TrailAttributes(dog = "no", trailClass = 3, sources = TrailSources(dog = TrailSource.USFS, trailClass = TrailSource.USFS))
        val filled = osm.filledFrom(agency)
        assertEquals("leashed", filled?.dog)
        assertEquals(TrailSources(dog = TrailSource.OSM, trailClass = TrailSource.USFS), filled?.sources)
    }

    @Test
    fun aMixedSurfaceIsSharedByLength() {
        val trail = combine(WayFacts(surface = "paved") to 1_400.0, WayFacts(surface = "gravel") to 600.0)
        assertEquals(listOf(TrailSurface("paved", 70), TrailSurface("gravel", 30)), trail?.surfaces)
    }

    @Test
    fun sharesRoundDownAndSmallOnesAreDropped() {
        val trail =
            combine(WayFacts(surface = "dirt") to 966.0, WayFacts(surface = "rock") to 30.0, WayFacts(surface = "sand") to 4.0, none to 0.0)
        assertEquals(listOf(TrailSurface("dirt", 96), TrailSurface("rock", 3)), trail?.surfaces, "only 0% sand dropped")
    }

    @Test
    fun aShortStretchOfAnotherSurfaceIsKept() {
        val trail = combine(WayFacts(surface = "dirt") to 3_890.0, WayFacts(surface = "gravel") to 110.0)
        assertEquals(listOf(TrailSurface("dirt", 97), TrailSurface("gravel", 2)), trail?.surfaces, "as its segments show it")
    }

    @Test
    fun untaggedStretchesAreNoOtherSurface() {
        val trail = combine(WayFacts(surface = "dirt") to 740.0, none to 260.0)
        assertEquals(listOf(TrailSurface("dirt", 100)), trail?.surfaces, "shares of the known length, not the whole line")
        val mixed = combine(WayFacts(surface = "paved") to 300.0, WayFacts(surface = "gravel") to 100.0, none to 600.0)
        assertEquals(listOf(TrailSurface("paved", 75), TrailSurface("gravel", 25)), mixed?.surfaces)
    }

    @Test
    fun tooLittleKnownSurfaceSaysNothing() {
        assertNull(combine(WayFacts(surface = "dirt") to 240.0, none to 760.0)?.surfaces, "under a quarter of the line known")
    }

    @Test
    fun oneHardPitchCounts() {
        assertEquals(3, combine(WayFacts(mtbScale = 1) to 2_880.0, WayFacts(mtbScale = 3) to 120.0)?.mtbScale)
    }

    @Test
    fun aShortHardStepIsIgnored() {
        assertEquals(1, combine(WayFacts(mtbScale = 1) to 2_980.0, WayFacts(mtbScale = 4) to 20.0)?.mtbScale)
    }

    @Test
    fun hardStepsTogetherCount() {
        val trail = combine(WayFacts(mtbScale = 1) to 2_940.0, WayFacts(mtbScale = 4) to 30.0, WayFacts(mtbScale = 3) to 30.0)
        assertEquals(3, trail?.mtbScale, "S3 or harder along 60 m")
    }

    @Test
    fun aShortLineTakesItsHardestAlongAllOfIt() {
        assertEquals("alpine_hiking", combine(WayFacts(sacScale = "alpine_hiking") to 30.0)?.sacScale)
    }

    @Test
    fun theLeastDevelopedClassAndTheWorstVisibilityAreHardest() {
        val trail = combine(WayFacts(trailClass = 4, visibility = "good") to 1_000.0, WayFacts(trailClass = 2, visibility = "bad") to 100.0)
        assertEquals(2, trail?.trailClass)
        assertEquals("bad", trail?.visibility)
    }

    @Test
    fun aSparseTagIsLeftOut() {
        assertNull(combine(WayFacts(visibility = "good") to 300.0, none to 1_700.0))
    }

    @Test
    fun oneNoDogsStretchMakesItNoDogs() {
        assertEquals("no", combine(WayFacts(dog = "no") to 200.0, WayFacts(dog = "leashed") to 3_800.0)?.dog)
        assertEquals("no", combine(WayFacts(dog = "no") to 200.0, none to 3_800.0)?.dog, "strict, from any stretch")
        assertNull(combine(WayFacts(dog = "yes") to 200.0, none to 3_800.0))
    }

    @Test
    fun horsesTakeTheMostOpen() {
        assertEquals("designated", combine(WayFacts(horse = "no") to 900.0, WayFacts(horse = "designated") to 100.0)?.horse)
    }

    @Test
    fun litThresholds() {
        assertEquals("yes", combine(WayFacts(lit = true) to 950.0, none to 50.0)?.lit)
        assertEquals("partly", combine(WayFacts(lit = true) to 500.0, WayFacts(lit = false) to 500.0)?.lit)
        assertEquals("no", combine(WayFacts(lit = false) to 950.0, none to 50.0)?.lit)
        assertEquals("partly", combine(WayFacts(lit = false) to 300.0, none to 700.0, WayFacts(lit = true) to 1.0)?.lit)
    }

    @Test
    fun wheelchairNeedsNinetyPercentAccessible() {
        assertEquals("yes", combine(WayFacts(wheelchair = "yes") to 950.0, none to 50.0)?.wheelchair)
        assertNull(combine(WayFacts(wheelchair = "yes") to 500.0, none to 500.0), "not enough is known accessible")
        assertEquals("no", combine(WayFacts(wheelchair = "yes") to 990.0, WayFacts(wheelchair = "no") to 10.0)?.wheelchair)
        assertEquals("limited", combine(WayFacts(wheelchair = "limited") to 10.0, none to 990.0)?.wheelchair)
    }

    @Test
    fun widthIsTheWeightedMedianAndGradeTheSteepest() {
        val trail =
            combine(
                WayFacts(widthCm = 50, gradePercent = 5) to 300.0,
                WayFacts(widthCm = 100, gradePercent = 20) to 600.0,
                WayFacts(widthCm = 300, gradePercent = 40) to 40.0,
            )
        assertEquals(100, trail?.widthCm)
        assertEquals(20, trail?.gradePercent, "40% runs only 40 m")
    }

    @Test
    fun seasonsAndDatesAreKeptOnlyWhenTheyAgree() {
        val dates = WayFacts(seasonal = true, open = OpenDates("06-15", "10-15"))
        assertEquals(TrailAttributes(openFrom = "06-15", openUntil = "10-15"), combine(dates to 500.0, dates to 500.0))
        assertEquals(
            TrailAttributes(seasonal = true),
            combine(dates to 500.0, WayFacts(open = OpenDates("05-01", "10-15")) to 500.0),
            "dates that disagree are dropped, leaving it seasonal",
        )
        val winter = WayFacts(seasonal = true, seasons = setOf("winter"))
        assertEquals(listOf("winter"), combine(winter to 1_000.0)?.seasons)
        assertEquals(TrailAttributes(seasonal = true), combine(WayFacts(seasonal = true) to 1_000.0))
        assertNull(combine(WayFacts(seasonal = true) to 500.0, WayFacts(seasonal = false) to 500.0))
    }

    @Test
    fun operatorAndWebsiteCoverTheMost() {
        val trail =
            combine(
                WayFacts(operator = "Town", website = "https://a.example") to 300.0,
                WayFacts(operator = "Club", website = "https://b.example") to 700.0,
            )
        assertEquals("Club", trail?.operator)
        assertEquals("https://b.example", trail?.website)
    }

    @Test
    fun nothingKnownIsNull() {
        assertNull(combine(none to 1_000.0))
        assertNull(TrailAttributeCombiner.combine(emptyList()))
    }

    @Test
    fun anOsmPathFillsInOnlyWhatItLacks() {
        val osm = TrailAttributes(surfaces = listOf(TrailSurface("dirt", 100)), seasonal = true)
        val agency =
            TrailAttributes(surfaces = listOf(TrailSurface("gravel", 100)), trailClass = 3, openFrom = "06-01", openUntil = "09-30")
        assertEquals(TrailAttributes(surfaces = listOf(TrailSurface("dirt", 100)), trailClass = 3, seasonal = true), osm.filledFrom(agency))
    }

    @Test
    fun openDatesCountTheirDaysInALeapYear() {
        assertEquals(1, OpenDates("01-01", "01-01").days())
        assertEquals(122, OpenDates("06-01", "09-30").days())
        assertEquals(31, OpenDates("02-01", "03-02").days(), "29 February counts")
        assertEquals(1, OpenDates("02-29", "02-29").days())
        assertEquals(91, OpenDates("12-01", "02-29").days(), "wraps past the new year")
        assertEquals(366, OpenDates("01-01", "12-31").days())
    }
}
