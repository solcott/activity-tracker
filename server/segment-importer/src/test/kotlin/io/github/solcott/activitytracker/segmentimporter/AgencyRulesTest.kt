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

    private fun blm(mode: String?, vararg more: Pair<String, String?>) =
        AgencyRules.BLM.groups(
            "route_prmry_nm" to "Porcupine Rim",
            "plan_allow_mode_trnsprt" to mode,
            "plan_mode_trnsprt" to "Motorized",
            "plan_access_rstrct" to "None",
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
    fun blmTrailsQualifyByTheirAllowedModes() {
        assertEquals(setOf(RIDE, RUN, HIKE), blm("NON_MOTO_SHARED"), "shared non-motorized")
        assertEquals(setOf(RUN, HIKE), blm("EQU_HIK_ONLY"), "hiking and horses only")
        assertEquals(setOf(RIDE, RUN, HIKE), blm("MTC_ATV_SHARED"), "shared motorized")
        assertEquals(setOf(RIDE, RUN, HIKE), blm("TECH_VEH_SHARED "), "trailing space")
        assertEquals(setOf(RIDE), blm("bike_only"))
        assertEquals(emptySet(), blm("MTC_ONLY"), "motorcycles only")
        assertEquals(emptySet(), blm("MTC_SHARED"), "motorcycles shared with no one else")
        assertEquals(emptySet(), blm("EQU_ONLY"))
        assertEquals(setOf(SKI), blm("SNOW_NON_MECH_SHARED"), "nordic")
        assertEquals(setOf(SKI), blm("SNOW_NON_MOTO_SHARED"), "fat bikes on snow aren't rides")
        assertEquals(emptySet(), blm("NON_MOTO_SHARED", "route_prmry_nm" to null))
    }

    @Test
    fun anUnknownBlmModeFallsBackToThePlannedMode() {
        assertEquals(setOf(RUN, HIKE), blm("UNK", "plan_mode_trnsprt" to "Non-Mechanized"))
        assertEquals(setOf(RIDE, RUN, HIKE), blm(null, "plan_mode_trnsprt" to "NON-MOTORIZED"))
        assertEquals(setOf(RIDE, RUN, HIKE), blm(" ", "plan_mode_trnsprt" to "non-motorized "))
        assertEquals(emptySet(), blm("UNK"), "motorized")
        assertEquals(emptySet(), blm("UNK", "plan_mode_trnsprt" to null))
        assertEquals(emptySet(), blm("MTC_ONLY", "plan_mode_trnsprt" to "Non-Motorized"), "a known code wins")
    }

    @Test
    fun restrictedBlmTrailsAreLeftOut() {
        assertEquals(emptySet(), blm("NON_MOTO_SHARED", "plan_access_rstrct" to "Authorized/Permitted User Only"))
        assertEquals(emptySet(), blm("NON_MOTO_SHARED", "plan_access_rstrct" to "admin only "))
        assertEquals(emptySet(), blm("NON_MOTO_SHARED", "plan_access_rstrct" to "All"))
        assertEquals(setOf(RIDE, RUN, HIKE), blm("NON_MOTO_SHARED", "plan_access_rstrct" to "Unknown"))
        assertEquals(setOf(RIDE, RUN, HIKE), blm("NON_MOTO_SHARED", "plan_access_rstrct" to null))
    }

    private fun blmClosed(mode: String?, vararg more: Pair<String, String?>) =
        AgencyRules.BLM.closedTo(
            mapOf(
                "route_prmry_nm" to "Porcupine Rim",
                "plan_allow_mode_trnsprt" to mode,
                "plan_mode_trnsprt" to "Non-Motorized",
                "plan_access_rstrct" to "None",
            ) + more
        )

    @Test
    fun aWildernessTrailIsClosedToBikes() {
        val closed =
            AgencyRules.USFS.closedTo(
                mapOf("hiker_pedestrian_managed" to "01/01-12/31", "bicycle_restricted" to "01/01-12/31", "bicycle_disc" to "01/01-12/31")
            )
        assertEquals(setOf(RIDE), closed)
    }

    @Test
    fun aForestServiceRestrictionClosesOnlyAllYearAndUnallowed() {
        assertEquals(
            emptySet(),
            AgencyRules.USFS.closedTo(mapOf("bicycle_restricted" to "11/01-04/30", "bicycle_managed" to "05/01-10/31")),
            "seasonal",
        )
        assertEquals(
            emptySet(),
            AgencyRules.USFS.closedTo(mapOf("bicycle_restricted" to "01/01-12/31", "bicycle_accpt" to "12/15-04/15")),
            "fat bikes accepted in winter",
        )
        assertEquals(
            setOf(RUN, HIKE, SKI),
            AgencyRules.USFS.closedTo(
                mapOf(
                    "hiker_pedestrian_restricted" to " 01/01-12/31 ",
                    "xcountry_ski_restricted" to "01/01-12/31",
                    "xcountry_ski_managed" to "N/A",
                    "motorcycle_restricted" to "01/01-12/31",
                )
            ),
        )
        assertEquals(emptySet(), AgencyRules.USFS.closedTo(mapOf("bicycle_managed" to null)), "blank isn't closed")
    }

    @Test
    fun blmModesCloseWhatTheyLeaveOut() {
        assertEquals(setOf(RIDE), blmClosed("EQU_HIK_ONLY"), "hiking and horses only")
        assertEquals(setOf(RIDE, RUN, HIKE), blmClosed("MTC_ONLY"), "motorcycles only")
        assertEquals(setOf(RUN, HIKE), blmClosed("BIKE_ONLY"))
        assertEquals(emptySet(), blmClosed("NON_MOTO_SHARED"))
        assertEquals(emptySet(), blmClosed("SNOW_MOTO_ONLY"), "snow codes describe winter travel")
        assertEquals(emptySet(), blmClosed("SNOW_NON_MECH_SHARED"))
    }

    @Test
    fun anUnknownBlmModeClosesNothing() {
        assertEquals(emptySet(), blmClosed("UNK", "plan_mode_trnsprt" to "Non-Mechanized"))
        assertEquals(emptySet(), blmClosed(null, "plan_mode_trnsprt" to "Motorized"))
        assertEquals(emptySet(), blmClosed("NEW_CODE"))
    }

    @Test
    fun restrictedBlmAccessClosesEveryGroup() {
        val all = setOf(RIDE, RUN, HIKE, SKI, PADDLE)
        assertEquals(all, blmClosed("NON_MOTO_SHARED", "plan_access_rstrct" to "Authorized/Permitted User Only"))
        assertEquals(all, blmClosed("UNK", "plan_access_rstrct" to "admin only "))
        assertEquals(all, blmClosed(null, "plan_access_rstrct" to "All"))
        assertEquals(emptySet(), blmClosed("NON_MOTO_SHARED", "plan_access_rstrct" to "Unknown"))
    }

    @Test
    fun parkTrailsCloseNothing() {
        val properties =
            mapOf(
                "trlname" to "Ridge Trail",
                "trlstatus" to "Existing",
                "publicdisplay" to "Public Map Display",
                "trluse" to "Hiker/Pedestrian",
            )
        assertEquals(setOf(RUN, HIKE), AgencyRules.NPS.groupsOf(properties))
        assertEquals(emptySet(), AgencyRules.NPS.closedTo(properties))
        assertEquals(emptySet(), AgencyRules.NPS.closedTo(properties + ("opentopublic" to "No")))
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
        assertEquals("24b7ad0a-7203", AgencyRules.BLM.featureKey(mapOf("original_globalid" to " 24b7ad0a-7203", "globalid" to "df23")))
        assertNull(AgencyRules.BLM.featureKey(mapOf("globalid" to "df23")))
        assertEquals(AgencyRules.BLM, AgencyRules.ofKind("blm"))
        assertNull(AgencyRules.ofKind("fws"))
        assertFalse(AgencyRules.USFS.excluded(emptyMap()))
        assertEquals(true to true, AgencyRules.USFS.directions(mapOf("oneway" to "yes"), RIDE))
    }
}
