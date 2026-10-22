package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.PADDLE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.SKI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Which agency features are trails for which groups (segments/trail-candidates), as `AgencyReader` reads them. */
class AgencyRulesTest {
    /** The groups a feature with [properties] gives a way, as the reader builds its tags: none without a name. */
    private fun AgencyRules.groups(vararg properties: Pair<String, String?>): Set<String> {
        val props = properties.toMap()
        val tags = buildMap {
            nameFrom(props)?.let { put(AgencyRules.NAME, it) }
            put(AgencyRules.GROUPS, groupsOf(props).sorted().joinToString(","))
        }
        return wayGroups(tags)
    }

    private fun park(uses: String, vararg more: Pair<String, String?>) =
        AgencyRules.NPS.groups(
            "trlname" to "Ridge Trail",
            "trlstatus" to "Existing",
            "publicdisplay" to "Public Map Display",
            "opentopublic" to null,
            "trluse" to uses,
            *more,
        )

    @Test
    fun aForestServiceTrailOpenToBikes() {
        val groups =
            AgencyRules.USFS.groups(
                "trail_name" to "Long",
                "bicycle_managed" to "06/01-11/01",
                "hiker_pedestrian_managed" to "05/01-11/01",
            )
        assertEquals(setOf(RIDE, RUN, HIKE), groups)
    }

    @Test
    fun aWildernessTrailIsNotForRides() {
        val groups =
            AgencyRules.USFS.groups(
                "trail_name" to "Long",
                "hiker_pedestrian_managed" to "01/01-12/31",
                "bicycle_restricted" to "01/01-12/31",
                "bicycle_disc" to "01/01-12/31",
            )
        assertEquals(setOf(RUN, HIKE), groups)
    }

    @Test
    fun acceptedUsesCountButNotApplicableDoesNot() {
        assertEquals(setOf(RIDE), AgencyRules.USFS.groups("trail_name" to "Bowl Mill", "bicycle_accpt" to "12/15-04/15"))
        assertEquals(setOf(RIDE), AgencyRules.USFS.groups("trail_name" to "Widow's Clearing", "bicycle_accpt_disc" to "01/01-12/31"))
        assertEquals(
            setOf(SKI, PADDLE),
            AgencyRules.USFS.groups(
                "trail_name" to "Lake Loop",
                "xcountry_ski_managed" to "11/01-04/15",
                "nonmotor_watercraft_managed" to "05/01-10/31",
                "hiker_pedestrian_managed" to "N/A",
                "bicycle_managed" to " ",
            ),
        )
    }

    @Test
    fun aForestServiceFeatureWithNoUseOrNoNameGivesNothing() {
        assertEquals(emptySet(), AgencyRules.USFS.groups("trail_name" to "Snowmobile Route", "snowmobile_managed" to "12/01-03/31"))
        assertEquals(emptySet(), AgencyRules.USFS.groups("trail_name" to "  ", "hiker_pedestrian_managed" to "01/01-12/31"))
    }

    @Test
    fun aParkTrailForSkiing() {
        assertEquals(setOf(RUN, HIKE, SKI), park("Hiker/Pedestrian|Cross-Country Ski"))
    }

    @Test
    fun parkUsesAreReadInEverySpellingSeen() {
        assertEquals(setOf(RIDE, RUN, HIKE), park("Bike | Hike | PackOrSaddle"))
        assertEquals(setOf(RIDE, RUN, HIKE), park("Hiker / Pedestrian | Bicycle"))
        assertEquals(setOf(RUN, HIKE, SKI), park("hiker/pedestrian, Cross Country Skiing"))
        assertEquals(setOf(SKI), park("XCSki"))
        assertEquals(setOf(PADDLE), park("Canoe/Kayak"))
        assertEquals(setOf(PADDLE), park("Non-Motorized Watercraft|Paddling"))
        assertEquals(emptySet(), park("Unknown"))
        assertEquals(emptySet(), park("Non-Motorized"))
    }

    @Test
    fun closedAndHiddenParkTrailsAreLeftOut() {
        assertEquals(emptySet(), park("Hiker/Pedestrian", "publicdisplay" to "No Public Map Display"))
        assertEquals(emptySet(), park("Hiker/Pedestrian", "trlstatus" to "Planned"))
        assertEquals(emptySet(), park("Hiker/Pedestrian", "trlstatus" to "Temporarily Closed"))
        assertEquals(emptySet(), park("Hiker/Pedestrian", "opentopublic" to "No"))
        assertEquals(setOf(RUN, HIKE), park("Hiker/Pedestrian", "trlstatus" to " existing ", "opentopublic" to "Yes"))
        assertEquals(
            emptySet(),
            AgencyRules.NPS.groups("trlstatus" to "Existing", "publicdisplay" to "Public Map Display", "trluse" to "Hike"),
        )
    }

    @Test
    fun agencyNamesInCapitalsAreTitleCased() {
        assertEquals("Beaver Pond Trail", AgencyRules.USFS.nameFrom(mapOf("trail_name" to "BEAVER POND TRAIL")))
        assertEquals("Widow's Clearing", AgencyRules.USFS.nameFrom(mapOf("trail_name" to "WIDOW'S CLEARING")))
        assertEquals("Silver-Lake (North)", AgencyRules.USFS.nameFrom(mapOf("trail_name" to " SILVER-LAKE (NORTH) ")))
        assertEquals("McKinley Bar Trail", AgencyRules.NPS.nameFrom(mapOf("trlname" to "McKinley Bar Trail")))
        assertEquals("CCC Road", AgencyRules.NPS.nameFrom(mapOf("trlname" to "CCC Road")))
        assertEquals("101", AgencyRules.USFS.nameFrom(mapOf("trail_name" to "101")))
        assertNull(AgencyRules.USFS.nameFrom(mapOf("trail_name" to null)))
    }

    @Test
    fun featureKeysAndKinds() {
        assertEquals("5293.007841@0.69", AgencyRules.USFS.featureKey(mapOf("trail_cn" to "5293.007841", "bmp" to "0.6900000000000001")))
        assertEquals("5293.007841@0", AgencyRules.USFS.featureKey(mapOf("trail_cn" to "5293.007841", "bmp" to null)))
        assertNull(AgencyRules.USFS.featureKey(mapOf("trail_cn" to " ", "bmp" to "1")))
        assertEquals("{2CF9}", AgencyRules.NPS.featureKey(mapOf("geometryid" to "{2CF9}")))
        assertNull(AgencyRules.NPS.featureKey(emptyMap()))
        assertEquals(AgencyRules.NPS, AgencyRules.ofKind("nps"))
        assertNull(AgencyRules.ofKind("blm"))
        assertFalse(AgencyRules.USFS.excluded(emptyMap()))
        assertEquals(true to true, AgencyRules.USFS.directions(mapOf("oneway" to "yes"), RIDE))
    }
}
