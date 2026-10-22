package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.PADDLE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.SKI
import io.github.solcott.activitytracker.location.GeoPoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Agency datasets read into the trail network (segments/trail-candidates), on the `usfs-trails`, `nps-trails` and `blm-trails` fixtures.
 */
class AgencyReaderTest {
    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/$name")).toURI())

    private val agency =
        AgencyReader.read(listOf(AgencyRules.USFS to fixture("usfs-trails.geojsonl"), AgencyRules.NPS to fixture("nps-trails.geojsonl")))
    private val data = OsmData(emptyList(), emptyList(), NodeCoords(LongArray(0))) + agency

    private fun ways(name: String) = agency.ways.filter { it.name == name }

    private fun trail(name: String, group: String) = TrailBuilder.build(data).single { it.name == name && it.group == group }

    @Test
    fun milepostFeaturesJoinIntoOneTrail() {
        assertEquals(3, ways("Long Trail").size, "three features, title-cased")
        val trail = trail("Long Trail", HIKE)
        assertEquals(ways("Long Trail").map { it.id }.toSet(), trail.ways)
        assertEquals(7, trail.nodes.size, "joined end to end, each shared end one point")
        assertEquals(setOf(RIDE, RUN, HIKE), TrailBuilder.build(data).filter { it.name == "Long Trail" }.map { it.group }.toSet())
    }

    @Test
    fun aForestServiceTrailEndingOnAParkTrailIsAJunction() {
        val beaver = ways("Beaver Pond").single()
        val valley = ways("Valley Trail").single()
        assertEquals(valley.nodes[1], beaver.nodes.last(), "half a metre apart, one point")
        assertEquals(ways("Long Trail")[1].nodes[1], beaver.nodes.first())

        val cutter = CandidateCutter(data)
        val valleyParts = cutter.cut(trail("Valley Trail", HIKE)).map { it.name }
        assertTrue(valleyParts.any { "(part 1)" in it } && valleyParts.any { "(part 2)" in it }, "$valleyParts")
        val longParts = cutter.cut(trail("Long Trail", HIKE)).map { it.name }
        assertTrue(longParts.any { "(part 1)" in it }, "$longParts")
        val rideParts = cutter.cut(trail("Long Trail", RIDE)).map { it.name }
        assertTrue(
            rideParts.any { "(part 1)" in it },
            "Beaver Pond isn't a ride trail, but marks a junction as any named trail does: $rideParts",
        )
    }

    @Test
    fun theSameTrailsWinterFeaturesDontCutItsSummerOne() {
        // One trail published twice, as the Forest Service does: a summer feature for hikers and a winter one for skiers, split mid-way.
        fun feature(bmp: Double, uses: String, from: Int, to: Int) =
            """{"type":"Feature","properties":{"TRAIL_NAME":"WILKINSON TRAIL","TRAIL_CN":"5044","BMP":$bmp,$uses},""" +
                """"geometry":{"type":"LineString","coordinates":[${(from..to).joinToString(",") { "[-72.7,${north(44.4, it * 250.0)}]" }}]}}"""
        val file = kotlin.io.path.createTempFile("usfs", ".geojsonl").toFile()
        file.writeText(
            listOf(
                    feature(0.0, """"TRAIL_TYPE":"TERRA","HIKER_PEDESTRIAN_MANAGED":"05/01-11/01"""", 0, 4),
                    feature(10.0, """"TRAIL_TYPE":"SNOW","XCOUNTRY_SKI_MANAGED":"11/01-04/15"""", 0, 2),
                    feature(10.6, """"TRAIL_TYPE":"SNOW","XCOUNTRY_SKI_MANAGED":"11/01-04/15"""", 2, 4),
                )
                .joinToString("\n")
        )
        val data = OsmData(emptyList(), emptyList(), NodeCoords(LongArray(0))) + AgencyReader.read(listOf(AgencyRules.USFS to file))
        val cutter = CandidateCutter(data)
        val hikes = TrailBuilder.build(data).filter { it.group == HIKE }.flatMap(cutter::cut)
        assertEquals(listOf("Wilkinson Trail (northbound)", "Wilkinson Trail (southbound)"), hikes.map { it.name }.sorted())
    }

    @Test
    fun aMultiLineStringGivesALinePerPart() {
        val ridge = ways("Ridge Path")
        assertEquals(
            listOf("nps:{0A1B2C3D-0000-4000-8000-000000000002}", "nps:{0A1B2C3D-0000-4000-8000-000000000002}#1"),
            ridge.map { it.tags.getValue(AgencyRules.KEY) },
        )
        assertTrue(ridge.all { AgencyRules.NPS.wayGroups(it.tags) == setOf(RUN, HIKE, SKI) })
        assertTrue(ridge[0].nodes.toSet().intersect(ridge[1].nodes.toSet()).isEmpty())
        assertEquals(
            "usfs:1002.007841@0",
            ways("Beaver Pond").single().tags[AgencyRules.KEY],
            "a single-part MultiLineString keeps its key",
        )
    }

    @Test
    fun featuresWithoutAnIdAreSkippedAndClosedOnesLeftOut() {
        assertEquals(mapOf(AgencyRules.USFS to 1), agency.skipped)
        assertEquals(emptyList(), ways("Orphan") + ways("Service Path") + ways("Future Loop"))
        assertEquals(
            setOf("Beaver Pond", "Long Trail", "Oak Trail", "Ridge Path", "Valley Trail"),
            agency.ways.mapNotNull { it.name }.toSet(),
        )
    }

    @Test
    fun everyKeyedLineKeepsItsOpenAndClosedGroups() {
        val beaver = agency.lines.single { it.points.first() == GeoPoint(44.4134898, -72.64) }
        assertEquals(setOf(RUN, HIKE), beaver.open, "the wilderness trail")
        assertEquals(setOf(RIDE), beaver.closed)

        val unnamed = agency.lines.single { it.points.first() == GeoPoint(44.4, -72.56) }
        assertEquals(emptySet(), unnamed.open, "no name and no use, so no way, but its line still closes")
        assertEquals(setOf(RIDE), unnamed.closed)
        assertEquals(3, unnamed.points.size, "raw points")

        assertEquals(2, agency.lines.count { it.points.first().lng == -72.6 }, "a line per part")
        assertTrue(agency.lines.none { it.points.first().lng in listOf(-72.57, -72.58, -72.59) }, "keyless, planned and hidden")
        assertEquals(3 + 1 + 1 + 1 + 1 + 2, agency.lines.size)
    }

    @Test
    fun blmLinesCloseWhatTheirModesAndAccessLeaveOut() {
        val blm = AgencyReader.read(listOf(AgencyRules.BLM to fixture("blm-trails.geojsonl")))
        fun at(lng: Double) = blm.lines.single { it.points.first().lng == lng }
        assertEquals(setOf(RIDE), at(-72.54).closed, "Mill Creek Rim, hiking and horses only")
        assertEquals(setOf(RIDE, RUN, HIKE), at(-72.52).closed, "Wipeout Hill, motorcycles only")
        assertEquals(setOf(RIDE, RUN, HIKE, SKI, PADDLE), at(-72.49).closed, "Permit Canyon")
        assertEquals(setOf(RIDE, RUN, HIKE), at(-72.48).open, "unnamed, still open")
        assertEquals(emptySet(), at(-72.5).closed, "a snow code")
    }

    @Test
    fun blmFeaturesByTheirModesAndAccess() {
        val blm = AgencyReader.read(listOf(AgencyRules.BLM to fixture("blm-trails.geojsonl")))
        assertEquals(mapOf(AgencyRules.BLM to 1), blm.skipped, "Lost Spur has no Original_GlobalID")
        assertEquals(
            listOf("Hidden Valley", "La Sal Nordic", "Mill Creek Rim", "Sand Flats Loop", "Slickrock Connector"),
            blm.ways.mapNotNull { it.name }.sorted(),
            "not the unnamed, motorcycle-only (Wipeout Hill) or permitted-users-only (Permit Canyon) features; SAND FLATS LOOP title-cased",
        )
        assertEquals(
            setOf(RUN, HIKE),
            AgencyRules.BLM.wayGroups(blm.ways.single { it.name == "Hidden Valley" }.tags),
            "an UNK code on a non-mechanized trail",
        )
        assertEquals(
            "blm:{0B1C2D3E-0000-4000-8000-000000000001}",
            blm.ways.single { it.name == "Slickrock Connector" }.tags[AgencyRules.KEY],
        )
    }

    @Test
    fun pointsAndWaysHaveNegativeIdsAndTheSameDataTheSameIds() {
        assertTrue(agency.points.keys.all { it < 0 } && agency.ways.all { it.id < 0 })
        val again =
            AgencyReader.read(
                listOf(AgencyRules.NPS to fixture("nps-trails.geojsonl"), AgencyRules.USFS to fixture("usfs-trails.geojsonl"))
            )
        assertEquals(agency.ways.map { it.tags to it.nodes.toList() }, again.ways.map { it.tags to it.nodes.toList() })
    }

    @Test
    fun theSnapperJoinsPointsWithinAMetre() {
        val snapper = PointSnapper()
        val a = snapper.idOf(GeoPoint(44.4, -72.6))
        assertEquals(a, snapper.idOf(GeoPoint(44.4 + 0.9 / 111_195.0, -72.6)))
        assertNotEquals(a, snapper.idOf(GeoPoint(44.4 + 1.2 / 111_195.0, -72.6)))
        assertEquals(a, snapper.idOf(GeoPoint(44.4, -72.6 + 0.000011)), "about 0.9 m east")
    }
}
