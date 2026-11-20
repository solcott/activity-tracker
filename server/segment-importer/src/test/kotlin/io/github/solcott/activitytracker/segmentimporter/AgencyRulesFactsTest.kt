package io.github.solcott.activitytracker.segmentimporter

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What agency features say about their trail (segments/trail-attributes, "Agency fields map to trail attributes"), read through
 * [AgencyReader] as a run reads them, with values seen in the live datasets.
 */
class AgencyRulesFactsTest {
    /** One feature of [rules]' dataset with [properties], as the way the reader makes of it. */
    private fun way(rules: AgencyRules, properties: Map<String, String>): OsmWay {
        val ids =
            when (rules) {
                AgencyRules.USFS -> mapOf("TRAIL_NAME" to "Ridge", "TRAIL_CN" to "1", "HIKER_PEDESTRIAN_MANAGED" to "01/01-12/31")
                AgencyRules.NPS ->
                    mapOf(
                        "TRLNAME" to "Ridge",
                        "GEOMETRYID" to "{1}",
                        "TRLSTATUS" to "Existing",
                        "PUBLICDISPLAY" to "Public Map Display",
                        "TRLUSE" to "Hiker",
                    )
                AgencyRules.BLM -> mapOf("ROUTE_PRMRY_NM" to "Ridge", "ORIGINAL_GLOBALID" to "{1}", "PLAN_ALLOW_MODE_TRNSPRT" to "HIK_ONLY")
            }
        val json = JsonObject((ids + properties).mapValues { JsonPrimitive(it.value) })
        val file = createTempFile("agency", ".geojsonl").toFile()
        file.writeText(
            """{"type":"Feature","properties":$json,"geometry":{"type":"LineString","coordinates":[[-72.9,44.4],[-72.9,44.41]]}}""" + "\n",
        )
        return AgencyReader.read(listOf(AgencyDataset(rules, file))).ways.single()
    }

    private fun facts(rules: AgencyRules, group: String? = "hike", vararg properties: Pair<String, String>) =
        rules.facts(way(rules, mapOf(*properties)).tags, group)

    @Test
    fun aForestServiceNativeTrail() {
        val facts =
            facts(
                AgencyRules.USFS,
                "hike",
                "TRAIL_SURFACE" to "NAT - NATIVE MATERIAL",
                "TRAIL_CLASS" to "3",
                "TYPICAL_TREAD_WIDTH" to "TW03 - 18-24 INCHES",
                "TYPICAL_TRAIL_GRADE" to "TG05 - +12-20%",
                "ACCESSIBILITY_STATUS" to "NOT ACCESSIBLE",
            )
        assertEquals("dirt", facts.surface)
        assertEquals(3, facts.trailClass)
        assertEquals(53, facts.widthCm, "21 inches")
        assertEquals(20, facts.gradePercent)
        assertEquals("no", facts.wheelchair)
    }

    @Test
    fun forestServiceSurfaces() {
        val cases =
            mapOf(
                "AC - ASPHALT" to "paved",
                "PC - PORTLAND CEMENT CONCRETE" to "paved",
                "AGG - CRUSHED AGGREGATE OR GRAVEL" to "gravel",
                "IMP - IMPORTED COMPACTED MATERIAL" to "compacted",
                "IMP - IMPORTED LOOSE MATERIAL" to "dirt",
                "CW - CHUNK WOOD" to "wood",
                "SNOW" to "snow",
                "OTHER" to "other",
            )
        for ((value, word) in cases) assertEquals(word, facts(AgencyRules.USFS, "hike", "TRAIL_SURFACE" to value).surface, value)
        assertEquals(null, facts(AgencyRules.USFS, "hike", "TRAIL_SURFACE" to "N/A").surface)
        assertEquals("yes", facts(AgencyRules.USFS, "hike", "ACCESSIBILITY_STATUS" to "ACCESSIBLE").wheelchair)
        assertEquals(305, facts(AgencyRules.USFS, "hike", "TYPICAL_TREAD_WIDTH" to "TW08 - > 120 INCHES").widthCm, "open-ended")
    }

    @Test
    fun aSeasonalForestServiceBikeUse() {
        val properties = arrayOf("BICYCLE_MANAGED" to "06/15-10/15")
        assertEquals(OpenDates("06-15", "10-15"), facts(AgencyRules.USFS, "ride", *properties).open)
        assertEquals(null, facts(AgencyRules.USFS, "hike", *properties).open, "hiking's season is the whole year")
        assertEquals(null, facts(AgencyRules.USFS, null, *properties).open)
        assertEquals(null, facts(AgencyRules.USFS, "ride", "BICYCLE_MANAGED" to "N/A").open)
    }

    @Test
    fun forestServiceHorses() {
        assertEquals("designated", facts(AgencyRules.USFS, "hike", "PACK_SADDLE_MANAGED" to "01/01-12/31").horse)
        assertEquals("yes", facts(AgencyRules.USFS, "hike", "PACK_SADDLE_ACCPT_DISC" to "06/01-09/30").horse)
        assertEquals("no", facts(AgencyRules.USFS, "hike", "PACK_SADDLE_RESTRICTED" to "01/01-12/31").horse)
        assertEquals(null, facts(AgencyRules.USFS, "hike", "PACK_SADDLE_MANAGED" to "N/A").horse)
    }

    @Test
    fun aParkServiceClassLabelAndSurfaces() {
        assertEquals(4, facts(AgencyRules.NPS, "hike", "TRLCLASS" to "Class 4: Highly Developed").trailClass)
        val cases =
            mapOf(
                "Asphalt" to "paved",
                "Masonry/Stone" to "paved",
                "Gravel Road" to "gravel",
                "Native Material" to "dirt",
                "Earth/Gravel" to "dirt",
                "Earth/Grass" to "grass",
                "Lava" to "rock",
                "Wood Chips" to "wood",
                "Boardwalk" to "other",
            )
        for ((value, word) in cases) assertEquals(word, facts(AgencyRules.NPS, "hike", "TRLSURFACE" to value).surface, value)
        for (nothing in listOf("12", "Unknown", "None", "N/A", "Not Applicable", " ")) {
            assertEquals(null, facts(AgencyRules.NPS, "hike", "TRLSURFACE" to nothing).surface, nothing)
        }
    }

    @Test
    fun aParkServiceSeasonAndMaintainer() {
        val facts = facts(AgencyRules.NPS, "hike", "SEASONAL" to "Yes", "MAINTAINER" to "NPS")
        assertEquals(true, facts.seasonal)
        assertEquals("NPS", facts.operator)
        assertEquals(null, facts(AgencyRules.NPS, "hike", "MAINTAINER" to "Unknown").operator)
    }

    @Test
    fun blmSurfaces() {
        val cases =
            mapOf("Solid Surface" to "paved", "Aggregate" to "gravel", "Natural" to "dirt", "Natural Improved" to "dirt", "Snow" to "snow")
        for ((value, word) in cases) assertEquals(word, facts(AgencyRules.BLM, "hike", "OBSRVE_SRFCE_TYPE" to value).surface, value)
        assertEquals(null, facts(AgencyRules.BLM, "hike", "OBSRVE_SRFCE_TYPE" to "N").surface)
    }
}
