package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import java.io.StringWriter
import kotlin.io.path.createTempFile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json

/**
 * The dive-site pass and `extract-sites` (dive-sites/site-import): on [OsmBuilder] data, and end to end on `dive-sites.osm.pbf`. After
 * editing `dive-sites.osm`, rebuild its PBF and review the diff:
 * ```bash
 * cd server/segment-importer/src/test/resources
 * osmium cat dive-sites.osm -o dive-sites.osm.pbf -O --output-header="osmosis_replication_timestamp=2026-10-01T00:00:00Z"
 * ```
 */
class ExtractSitesTest {
    @Test
    fun aNamedWreckNodeIsKeptAtItsPosition() {
        val data = osm { node(1, 27.8136, 33.9214, scuba("SS Thistlegorm", "historic" to "wreck")) }
        val entry = entryOf(data.diveSites().single())
        assertEquals(SiteEntry("osm-node-1", "SS Thistlegorm", 278_136_000, 339_214_000, 150, "osm", "ODbL-1.0"), entry)
    }

    @Test
    fun shopsAmenitiesClubsBuildingsAndUnnamedFeaturesGiveNoSite() {
        val data = osm {
            node(1, 27.0, 33.0, scuba("Red Sea Divers", "shop" to "scuba_diving"))
            node(2, 27.0, 33.0, scuba("Dive Centre", "amenity" to "dive_centre"))
            node(3, 27.0, 33.0, scuba("Sub Aqua Club", "club" to "sport"))
            node(4, 27.0, 33.0, scuba("Club House", "building" to "yes"))
            node(5, 27.0, 33.0, mapOf("sport" to "scuba_diving"))
            node(6, 27.0, 33.0, mapOf("name" to "A Reef", "natural" to "reef"))
            node(7, 27.0, 33.0, scuba("  "))
        }
        assertEquals(emptyList(), data.diveSites())
    }

    @Test
    fun freeDivingAndSportListsCount() {
        val data = osm {
            node(1, 27.0, 33.0, mapOf("name" to "Apnea Point", "sport" to "free_diving"))
            node(2, 27.0, 33.0, mapOf("name" to "Shared Reef", "sport" to "swimming; scuba_diving"))
        }
        assertEquals(listOf("Apnea Point", "Shared Reef"), data.diveSites().map { it.name })
    }

    @Test
    fun aWayGivesItsCentroid() {
        val data = osm {
            node(10, 28.570, 34.530)
            node(11, 28.570, 34.532)
            node(12, 28.572, 34.532)
            node(13, 28.572, 34.530)
            way(100, 10, 11, 12, 13, 10, tags = scuba("Blue Hole"))
        }
        val entry = entryOf(data.diveSites().single())
        assertEquals("osm-way-100", entry.id)
        assertEquals(285_710_000, entry.lat, "the closing node counts once")
        assertEquals(345_310_000, entry.lon)
    }

    @Test
    fun aRelationGivesItsMemberWaysCentroid() {
        val data = osm {
            node(20, 23.600, -75.900)
            node(21, 23.600, -75.898)
            node(22, 23.602, -75.898)
            node(23, 23.602, -75.900)
            way(200, 20, 21, 22, 23, 20, tags = emptyMap())
            relation(300, 200, tags = mapOf("name" to "Dean's Blue Hole", "sport" to "free_diving", "type" to "multipolygon"))
        }
        val entry = entryOf(data.diveSites().single())
        assertEquals("osm-relation-300", entry.id)
        assertEquals(236_010_000, entry.lat)
        assertEquals(-758_990_000, entry.lon)
    }

    @Test
    fun aReExtractKeepsTheIds() {
        val before = osm {
            node(1, 27.8136, 33.9214, scuba("SS Thistlegorm"))
            node(10, 28.570, 34.530)
            node(11, 28.572, 34.532)
            way(100, 10, 11, tags = scuba("Blue Hole"))
        }
        // A newer extract: the wreck renamed and nudged, the way's nodes moved, and a new site mapped.
        val after = osm {
            node(1, 27.8137, 33.9215, scuba("Thistlegorm Wreck"))
            node(2, 27.0, 33.0, scuba("New Reef"))
            node(10, 28.571, 34.531)
            node(11, 28.573, 34.533)
            way(100, 10, 11, tags = scuba("Blue Hole"))
        }
        val ids = before.diveSites().map { entryOf(it).id }
        assertEquals(listOf("osm-node-1", "osm-way-100"), ids)
        assertEquals(listOf("osm-node-1", "osm-node-2", "osm-way-100"), after.diveSites().map { entryOf(it).id })
    }

    @Test
    fun idsAreRecordKeys() {
        val rkey = Regex("[A-Za-z0-9._:~-]{1,512}")
        for (type in OsmMember.Type.entries) assertTrue(rkey.matches(siteIdOf(type, Long.MAX_VALUE)), siteIdOf(type, Long.MAX_VALUE))
    }

    @Test
    fun theSitesFileHasAVersionedHeader() {
        val out = StringWriter()
        writeSites(out, "eg-red-sea", Instant.parse("2026-10-01T00:00:00Z"), emptyList())
        assertEquals(
            """{"region":"eg-red-sea","extractTimestamp":"2026-10-01T00:00:00Z","sitesVersion":$SITES_VERSION}""",
            out.toString().trim(),
        )
    }

    @Test
    fun extractsTheFixturesSites() {
        val out = createTempFile("sites", ".ndjson").toFile()
        extractSites(resource("dive-sites.osm.pbf"), "test-region", out)
        val lines = out.readLines()
        assertEquals(
            SitesHeader("test-region", Instant.parse("2026-10-01T00:00:00Z"), SITES_VERSION),
            Json.decodeFromString(SitesHeader.serializer(), lines.first()),
        )
        val entries = lines.drop(1).map { Json.decodeFromString(SiteEntry.serializer(), it) }
        assertEquals(listOf("osm-node-1", "osm-way-100", "osm-relation-300"), entries.map { it.id })
        assertEquals(listOf("SS Thistlegorm", "Blue Hole", "Dean's Blue Hole"), entries.map { it.name })
        assertEquals(SiteEntry("osm-node-1", "SS Thistlegorm", 278_136_000, 339_214_000, 150, "osm", "ODbL-1.0"), entries[0])
        assertEquals(285_710_000 to 345_310_000, entries[1].lat to entries[1].lon)
        val relation = entries[2]
        assertTrue(abs(relation.lat - 236_006_667) <= 1 && abs(relation.lon - -758_990_000) <= 1, "$relation")
    }

    @Test
    fun extractSitesTakesAnExtractRegionAndOut() {
        fun extractSites(vararg args: String) = parse(listOf("extract-sites", *args))
        val expected = Command.ExtractSites(File("region.osm.pbf"), "eg-red-sea", File("sites.ndjson"))
        assertEquals(expected, extractSites("region.osm.pbf", "--region", "eg-red-sea", "--out", "sites.ndjson"))
        assertEquals(expected, extractSites("region.osm.pbf", "--out", "sites.ndjson", "--region", "eg-red-sea"))
        assertNull(extractSites("--region", "eg-red-sea", "--out", "sites.ndjson"), "no extract")
        assertNull(extractSites("region.osm.pbf", "--region", "eg-red-sea"), "no out")
        assertNull(extractSites("region.osm.pbf", "--region", "Red Sea", "--out", "sites.ndjson"), "bad region")
        assertNull(extractSites("region.osm.pbf", "--region", "eg-red-sea", "--out", "sites.ndjson", "--lang", "en"), "unknown option")
        assertNull(extractSites("region.osm.pbf", "--region", "eg-red-sea", "--region", "eg-sinai", "--out", "s.ndjson"), "two regions")
    }

    private fun scuba(name: String, vararg more: Pair<String, String>): Map<String, String> =
        mapOf("name" to name, "sport" to "scuba_diving", *more)

    private fun resource(name: String) = File(requireNotNull(javaClass.getResource("/$name")).toURI())
}
