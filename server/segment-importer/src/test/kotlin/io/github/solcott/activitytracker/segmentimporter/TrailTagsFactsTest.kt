package io.github.solcott.activitytracker.segmentimporter

import kotlin.test.Test
import kotlin.test.assertEquals

/** What an OSM way's tags say about its trail (segments/trail-attributes, "OSM tags map to trail attributes"). */
class TrailTagsFactsTest {
    private fun facts(vararg tags: Pair<String, String>) = TrailTags.facts(mapOf(*tags), group = null)

    @Test
    fun surfacesMapToTheirWord() {
        val cases =
            mapOf(
                "asphalt" to "paved",
                "concrete" to "paved",
                "concrete:plates" to "paved",
                "paving_stones" to "paved",
                "sett" to "paved",
                "chipseal" to "paved",
                "metal" to "paved",
                "bricks" to "paved",
                "compacted" to "compacted",
                "fine_gravel" to "compacted",
                "gravel" to "gravel",
                "pebblestone" to "gravel",
                "dirt" to "dirt",
                "earth" to "dirt",
                "ground" to "dirt",
                "soil" to "dirt",
                "mud" to "dirt",
                "unpaved" to "dirt",
                "grass" to "grass",
                "grass_paver" to "grass",
                "rock" to "rock",
                "stone" to "rock",
                "sand" to "sand",
                "wood" to "wood",
                "woodchips" to "wood",
                "snow" to "snow",
                "ice" to "snow",
                "artificial_turf" to "other",
                "gravel;dirt" to "gravel",
            )
        for ((tag, word) in cases) assertEquals(word, facts("surface" to tag).surface, tag)
    }

    @Test
    fun aTrackWithoutASurfaceTakesItsTrackType() {
        assertEquals("paved", facts("tracktype" to "grade1").surface)
        assertEquals("gravel", facts("tracktype" to "grade2").surface)
        for (grade in 3..5) assertEquals("dirt", facts("highway" to "track", "tracktype" to "grade$grade").surface)
        assertEquals("sand", facts("surface" to "sand", "tracktype" to "grade1").surface, "surface wins")
    }

    @Test
    fun difficultiesAreReadInTheirOwnVocabulary() {
        val way =
            facts(
                "smoothness" to "very_bad",
                "sac_scale" to "demanding_mountain_hiking",
                "mtb:scale" to "2+",
                "mtb:scale:imba" to "3",
                "mtb:scale:uphill" to "1-",
                "piste:difficulty" to "intermediate",
                "piste:grooming" to "classic+skating",
                "trail_visibility" to "bad",
            )
        assertEquals("very_bad", way.smoothness)
        assertEquals("demanding_mountain_hiking", way.sacScale)
        assertEquals(2, way.mtbScale, "2+ counts as 2")
        assertEquals(3, way.mtbScaleImba)
        assertEquals(1, way.mtbScaleUphill)
        assertEquals("intermediate", way.pisteDifficulty)
        assertEquals("classic_skating", way.pisteGrooming)
        assertEquals("bad", way.visibility)
    }

    @Test
    fun valuesOutsideAVocabularyAreIgnored() {
        val way =
            facts(
                "sac_scale" to "T3",
                "smoothness" to "rough",
                "mtb:scale" to "7",
                "mtb:scale:imba" to "5",
                "piste:difficulty" to "hard",
                "trail_visibility" to "poor",
                "dog" to "maybe",
                "horse" to "unknown",
                "wheelchair" to "partly",
            )
        assertEquals(WayFacts.NONE, way)
    }

    @Test
    fun widthsAreReadInTheirUnits() {
        val cases =
            mapOf(
                "1.5" to 150,
                "2 m" to 200,
                "1,2" to 120,
                "60 cm" to 60,
                "3 ft" to 91,
                "3'" to 91,
                "3'6\"" to 107,
                "18\"" to 46,
            )
        for ((tag, cm) in cases) assertEquals(cm, facts("width" to tag).widthCm, tag)
        assertEquals(null, facts("width" to "narrow").widthCm)
        assertEquals(null, facts("width" to "0").widthCm)
    }

    @Test
    fun accessIsReadAsTheSpecSays() {
        assertEquals(true, facts("lit" to "yes").lit)
        assertEquals(true, facts("lit" to "sunset-sunrise").lit, "any value but no is lit")
        assertEquals(false, facts("lit" to "no").lit)
        assertEquals("yes", facts("dog" to "designated").dog)
        assertEquals("yes", facts("dog" to "permissive").dog)
        assertEquals("leashed", facts("dog" to "leashed").dog)
        assertEquals("no", facts("dog" to "no").dog)
        assertEquals("yes", facts("horse" to "permissive").horse)
        assertEquals("designated", facts("horse" to "designated").horse)
        assertEquals("limited", facts("wheelchair" to "limited").wheelchair)
    }

    @Test
    fun seasonsAndOpenDates() {
        assertEquals(true, facts("seasonal" to "yes").seasonal)
        assertEquals(null, facts("seasonal" to "yes").seasons)
        assertEquals(false, facts("seasonal" to "no").seasonal)
        assertEquals(setOf("summer", "autumn"), facts("seasonal" to "summer;autumn").seasons)
        assertEquals(OpenDates("04-01", "11-30"), facts("opening_hours" to "Apr-Nov").open)
        assertEquals(OpenDates("05-15", "10-31"), facts("opening_hours" to "May 15-Oct 31").open)
        assertEquals(OpenDates("12-01", "02-28"), facts("opening_hours" to "Dec-Feb").open)
        assertEquals(null, facts("opening_hours" to "Mo-Fr 08:00-18:00").open, "a complex form is ignored")
        assertEquals(null, facts("opening_hours" to "Jan-Dec").open, "the whole year isn't seasonal")
        assertEquals(null, facts("opening_hours" to "Feb 30-Mar 3").open)
    }

    @Test
    fun operatorAndWebsite() {
        assertEquals("Green Mountain Club", facts("operator" to " Green Mountain Club ").operator)
        assertEquals("https://kingdomtrails.org", facts("contact:website" to "https://kingdomtrails.org").website)
        assertEquals("http://a.example", facts("website" to "http://a.example", "contact:website" to "https://b.example").website)
        assertEquals(null, facts("website" to "kingdomtrails.org").website, "only an absolute http(s) URL")
        assertEquals(null, facts("website" to "javascript:alert(1)").website)
        assertEquals(null, facts("operator" to "x".repeat(101)).operator, "over 100 graphemes")
    }
}
