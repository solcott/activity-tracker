package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A region's trails and named roads as paths (segments/path-import). */
class PathsTest {
    private val lat = 44.4
    private val lng = -72.9

    /** A trail or road of [ways] ways of [name], each 1 km, laid out north from the origin, sharing end nodes. */
    private fun OsmBuilder.chain(name: String, first: Long, ways: Int, tags: (String) -> Map<String, String>, east: Double = 0.0) {
        for (i in 0..ways) node(first + i, north(lat, i * 1_000.0), lng + east)
        for (i in 0 until ways) way(first * 10 + i, first + i, first + i + 1, tags = tags(name))
    }

    @Test
    fun aMultiUseTrailIsOnePath() {
        val data = osm { chain("Ann's Trail", 100, 3, { path(it, "bicycle" to "yes") }) }
        val paths = pathsOf(data)
        assertEquals(1, paths.size, "one path for ride, run and hike")
        val anns = paths.single()
        assertEquals(PathKind.TRAIL, anns.kind)
        assertEquals("Ann's Trail", anns.name, "named before generic words are dropped")
        assertEquals("osm", anns.source)
        assertEquals(listOf("ride", "run", "hike"), anns.sports)
        assertTrue(anns.lengthMeters in 2_990L..3_010L, "${anns.lengthMeters}")
    }

    @Test
    fun idsAreStableAndIgnoreOtherTrails() {
        val alone = pathsOf(osm { chain("Ann's Trail", 100, 3, { path(it) }) }).single().id
        val withOther =
            pathsOf(
                osm {
                    chain("Ann's Trail", 100, 3, { path(it) })
                    chain("Oak Trail", 200, 2, { path(it) }, east = 0.05)
                },
            )
        assertEquals(alone, withOther.single { it.name == "Ann's Trail" }.id)
    }

    @Test
    fun namedRoadsBecomeRoadPathsButNeverCandidates() {
        val data = osm {
            chain("Pine St", 300, 4, { highway("residential", it) })
            chain("Interstate 89", 400, 2, { highway("motorway", it) }, east = 0.05)
            chain("Dog Lane", 500, 2, { highway("residential", it, "bicycle" to "no") }, east = 0.1)
            chain("Closed Rd", 600, 2, { highway("residential", it, "bicycle" to "no", "foot" to "no") }, east = 0.15)
        }
        val roads = pathsOf(data).filter { it.kind == PathKind.ROAD }.map { it.name }.sorted()
        assertEquals(listOf("Dog Lane", "Pine St"), roads, "joined, motorways and roads closed to bikes and feet left out")
        assertEquals(emptyList(), candidatesOf(data, ClosureFilter(emptyList())), "no candidate is cut from a road")
    }

    @Test
    fun aLongTrailSplitsIntoPiecesThatFit() {
        val points = (0..4_400).map { GeoPoint(north(lat, it * 100.0), lng + (it % 2) * 0.0003) }
        val pieces = PathPieces.of(points)
        assertTrue(pieces.size in 2..PathRecord.MAX_PIECES, "${pieces.size} pieces")
        assertTrue(pieces.all { it.length <= PathRecord.MAX_PIECE_CHARS })
        val length = pieces.sumOf { SegmentGeometry.lengthMeters(SegmentGeometry.decodePolyline(it)!!) }
        assertTrue(length > 440_000, "$length")
    }

    @Test
    fun aPathsFileReadsBack() {
        val paths = pathsOf(osm { chain("Ann's Trail", 100, 3, { path(it) }) })
        val out = StringWriter()
        writePaths(out, "us-vermont", paths)
        val read = readPaths(out.toString().lineSequence())
        assertEquals("us-vermont", read.region)
        assertEquals(paths, read.paths)
    }

    @Test
    fun aSectionedTrailIsOnePathNamedAfterItsFamily() {
        val data = osm {
            for (i in 0..4) node(600L + i, north(lat, i * 800.0), lng)
            way(6000, 600, 601, 602, tags = path("Rush Trail - 2112 Section", "bicycle" to "yes"))
            way(6001, 602, 603, tags = path("Rush Trail - Mystic Rhythms Section", "bicycle" to "yes"))
            way(6002, 603, 604, tags = path("Rush Trail - Exit Stage Left Section", "bicycle" to "yes"))
        }
        val trails = pathsOf(data).filter { it.kind == PathKind.TRAIL }
        assertEquals(listOf("Rush Trail"), trails.map { it.name }, "one path for the family")
        assertTrue(trails.single().lengthMeters in 3_150L..3_250L, "each way once: ${trails.single().lengthMeters}")
    }

    @Test
    fun aForkedTrailIsOnePath() {
        val data = osm {
            node(700, lat, lng)
            node(701, north(lat, 1_000.0), lng)
            node(702, north(lat, 2_000.0), lng)
            node(703, north(lat, 2_000.0), lng + 0.01)
            way(7000, 700, 701, tags = highway("footway", "Hoof N Boot Path"))
            way(7001, 701, 702, tags = highway("footway", "Hoof N Boot Path"))
            way(7002, 701, 703, tags = highway("footway", "Hoof N Boot Path"))
        }
        val hoof = pathsOf(data).filter { it.name == "Hoof N Boot Path" }
        assertEquals(1, hoof.size, "the three chains a fork makes touch, so they're one path")
        assertEquals(listOf("run", "hike"), hoof.single().sports, "a footpath is open to run and hike only")
    }

    @Test
    fun piecesOfATrailBrokenByShortConnectorsAreOnePath() {
        val data = osm {
            chain("Hoof N Boot Path", 300, 2, { highway("footway", it) })
            chain("Hoof N Boot Path", 400, 1, { highway("footway", it) }, east = 0.002)
        }
        assertEquals(1, pathsOf(data).count { it.name == "Hoof N Boot Path" }, "some 160 m apart, so one trail")
    }

    @Test
    fun separateTrailsOfOneNameStayApart() {
        val data = osm {
            chain("Ridge Trail", 100, 2, { path(it) })
            chain("Ridge Trail", 200, 2, { path(it) }, east = 0.5)
        }
        assertEquals(2, pathsOf(data).count { it.name == "Ridge Trail" }, "they don't touch")
    }

    /** A Forest Service dataset of one feature, `TRAIL_CN` [cn] named [name], 3 km north from the origin [east] m east, open to [uses]. */
    private fun agency(name: String, east: Double = 0.0, cn: String = "1001", uses: String = "HIKER_PEDESTRIAN_MANAGED"): AgencyData {
        val dLng = east / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        val coordinates = (0..30).joinToString(",") { "[${lng + dLng},${north(lat, it * 100.0)}]" }
        val file = kotlin.io.path.createTempFile("usfs", ".geojsonl").toFile()
        file.writeText(
            """{"type":"Feature","properties":{"TRAIL_NAME":"$name","TRAIL_CN":"$cn","BMP":0,"$uses":"01/01-12/31"},""" +
                """"geometry":{"type":"LineString","coordinates":[$coordinates]}}""" +
                "\n",
        )
        return AgencyReader.read(listOf(AgencyDataset(AgencyRules.USFS, file)))
    }

    @Test
    fun anAgencyTrailOsmAlsoMapsGivesOnlyOsmsPath() {
        val osmData = osm { chain("Little Cottonwood Creek Trail", 100, 3, { path(it, "foot" to "designated") }) }
        val alone = pathsOf(osmData).single()
        val paths = pathsOf(osmData + agency("Little Cottonwood Creek", east = 8.0))
        val kept = paths.single()
        assertEquals(alone.id, kept.id)
        assertEquals("Little Cottonwood Creek Trail", kept.name)
        assertEquals("osm", kept.source)
    }

    @Test
    fun theOsmPathOpensToTheAgencysSportGroups() {
        val osmData = osm { chain("Creek Trail", 100, 3, { path(it, "bicycle" to "no") }) }
        assertEquals(listOf("run", "hike"), pathsOf(osmData).single().sports)
        val paths = pathsOf(osmData + agency("Creek", uses = "BICYCLE_MANAGED"))
        assertEquals(listOf("ride", "run", "hike"), paths.single().sports)
    }

    @Test
    fun anAgencyTrailOsmLacksKeepsItsPath() {
        val osmData = osm { chain("Creek Trail", 100, 3, { path(it) }) }
        val paths = pathsOf(osmData + agency("Ridge", east = 2_000.0))
        assertEquals(setOf("osm", "agency"), paths.map { it.source }.toSet())
    }
}
