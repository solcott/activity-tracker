package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLines
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathRecord
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** `publish-paths` (segments/path-import): only the paths segments lie on are published, and a changed one is refreshed. */
class PublishPathsTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-11-16T12:00:00Z")
        }
    private val repo = mutableMapOf<Pair<String, String>, JsonObject>()

    /** The live segments the fake AppView reports. */
    private val segments = mutableListOf<GetSegmentLines.SegmentLine>()

    private val lat = 44.4
    private val lng = -72.9

    private fun line(east: Double, fromNorth: Double, toNorth: Double) =
        SegmentGeometry.encodePolyline(listOf(GeoPoint(north(lat, fromNorth), lng + east), GeoPoint(north(lat, toNorth), lng + east)))

    private fun path(id: String, name: String, east: Double, kind: String = PathKind.TRAIL) =
        PathEntry(id, kind, name, listOf(line(east, 0.0, 5_000.0)), 5_000, "osm", "ODbL-1.0")

    private val anns = path("anns", "Ann's", east = 0.0)
    private val pine = path("pine", "Pine St", east = 0.05, kind = PathKind.ROAD)
    private val oak = path("oak", "Oak St", east = 0.1, kind = PathKind.ROAD)

    private fun services() {
        fake.on(Nsid.IMPORTER_GET_SEGMENT_LINES) {
            json(AtJson.encodeToString(GetSegmentLines.Output.serializer(), GetSegmentLines.Output(segments.toList())))
        }
        fake.on("com.atproto.repo.listRecords") { call ->
            val records =
                repo
                    .filterKeys { it.first == call.params["collection"] }
                    .entries
                    .joinToString(",") { (key, value) ->
                        """{"uri":"at://${FakeServices.DID}/${key.first}/${key.second}","cid":"bafy${key.second}","value":$value}"""
                    }
            json("""{"records":[$records]}""")
        }
        fake.serveRepo(repo)
    }

    private fun segment(rkey: String, east: Double, sport: String = "ride") =
        GetSegmentLines.SegmentLine(AtUri("at://did:plc:alice/${Nsid.FEED_SEGMENT}/$rkey"), sport, line(east, 1_000.0, 2_500.0))

    @Test
    fun onlyPathsUnderSegmentsArePublished() = runTest {
        services()
        segments += segment("3ka", east = 0.0)
        segments += segment("3kb", east = 0.05)
        segments += segment("3kc", east = 0.3)
        val result = publishPaths(clients, listOf(anns, pine, oak), clock)
        assertNull(result.stoppedBy)
        assertEquals(listOf("anns", "pine"), result.published.sorted(), "Oak St has no segment on it")
        assertEquals(1, result.unpathed, "the segment off every path")
        val record = AtJson.decodeFromJsonElement(PathRecord.serializer(), repo.getValue(Nsid.FEED_PATH to "pine"))
        assertEquals(PathRecord("Pine St", PathKind.ROAD, pine.lines, "osm", "ODbL-1.0", clock.now()), record)
        assertEquals(
            "Bearer svc:${FakeServices.APPVIEW_DID}:${Nsid.IMPORTER_GET_SEGMENT_LINES}",
            fake.calls(Nsid.IMPORTER_GET_SEGMENT_LINES).first().auth,
        )
    }

    @Test
    fun nothingNewPublishesNothing() = runTest {
        services()
        val result = publishPaths(clients, listOf(anns, pine), clock)
        assertEquals(emptyList(), result.published)
        assertEquals(emptyList(), fake.commits())
    }

    @Test
    fun aChangedPathIsRefreshedUnderItsKey() = runTest {
        services()
        repo[Nsid.FEED_PATH to "anns"] = pathRecord(anns.copy(lines = listOf(line(0.0, 0.0, 4_700.0))), clock)
        val result = publishPaths(clients, listOf(anns), clock)
        assertEquals(listOf("anns"), result.refreshed)
        assertEquals(listOf(listOf(Triple("update", Nsid.FEED_PATH, "anns"))), fake.commits())
        assertEquals(anns.lines, AtJson.decodeFromJsonElement(PathRecord.serializer(), repo.getValue(Nsid.FEED_PATH to "anns")).lines)
    }

    @Test
    fun aRideOnAFootPathPublishesNoPathForIt() = runTest {
        services()
        segments += segment("3kr", east = 0.0)
        val result = publishPaths(clients, listOf(anns.copy(sports = listOf("run", "hike"))), clock)
        assertEquals(emptyList(), result.published)
        assertEquals(1, result.unpathed)
    }

    @Test
    fun pathsTheRegionListedBeforeAndNoLongerHoldsAreRemoved() = runTest {
        services()
        repo[Nsid.FEED_PATH to "rush-2112"] = pathRecord(path("rush-2112", "Rush Trail - 2112 Section", east = 0.0), clock)
        // A neighbouring region's path, lying inside this file's area: regions' boxes overlap.
        repo[Nsid.FEED_PATH to "border"] = pathRecord(path("border", "Border Trail", east = 0.02), clock)
        val result = publishPaths(clients, listOf(anns, pine), clock, listedBefore = setOf("anns", "rush-2112"))
        assertEquals(listOf("rush-2112"), result.removed, "the region's own; another region's path stays, wherever it lies")
        assertEquals(setOf("border"), repo.keys.map { it.second }.toSet())
        assertEquals(emptySet(), result.undeleted)
    }

    @Test
    fun withNothingListedBeforeNothingIsRemoved() = runTest {
        services()
        repo[Nsid.FEED_PATH to "rush-2112"] = pathRecord(path("rush-2112", "Rush Trail - 2112 Section", east = 0.0), clock)
        assertEquals(emptyList(), publishPaths(clients, listOf(anns, pine), clock).removed)
        assertEquals(setOf("rush-2112"), repo.keys.map { it.second }.toSet())
    }

    @Test
    fun theFileKeepsWhatItListedForTheNextRun() = runTest {
        services()
        segments += segment("3ka", east = 0.0)
        val dir = createTempDirectory("paths").toFile()
        val file = File(dir, "us-vermont-paths.ndjson")
        file.writer().use { writePaths(it, "us-vermont", listOf(anns, pine)) }
        publishPathsFile(clients, file)
        assertEquals(listOf("anns", "pine"), listedFile(file, "us-vermont").readLines())
        assertEquals(setOf("anns"), repo.keys.map { it.second }.toSet())

        file.writer().use { writePaths(it, "us-vermont", listOf(pine)) }
        publishPathsFile(clients, file)
        assertEquals(emptySet(), repo.keys.map { it.second }.toSet(), "Ann's left the region's file")
        assertEquals(listOf("pine"), listedFile(file, "us-vermont").readLines())
    }

    @Test
    fun aSegmentCrossingTwoTilesIsCountedOnce() = runTest {
        services()
        segments += segment("3kc", east = 0.3)
        // Two paths a degree and more apart, so the region spans two tiles, each answering with the same off-path segment.
        val far = path("far", "Far Trail", east = 1.5)
        val result = publishPaths(clients, listOf(anns, far), clock)
        assertEquals(2, fake.calls(Nsid.IMPORTER_GET_SEGMENT_LINES).size, "one call per tile")
        assertEquals(1, result.unpathed)
    }

    /** A 500 m path along the middle of where [segment]s run, a third of each one's 1.5 km. */
    private val stub = PathEntry("stub", PathKind.TRAIL, "Stub", listOf(line(0.0, 1_500.0, 2_000.0)), 500, "osm", "ODbL-1.0")

    @Test
    fun theImportersOwnSegmentNeedsAPathItMostlyFollows() = runTest {
        services()
        segments +=
            GetSegmentLines.SegmentLine(AtUri("at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/3ki"), "ride", line(0.0, 1_000.0, 2_500.0))
        val result = publishPaths(clients, listOf(stub), clock)
        assertEquals(emptyList(), result.published, "a third of an imported segment isn't enough")
        assertEquals(1, result.unpathed)
    }

    @Test
    fun anAthletesSegmentNeedsOnlyAQuarterOnAPath() = runTest {
        services()
        segments += segment("3ka", east = 0.0)
        assertEquals(listOf("stub"), publishPaths(clients, listOf(stub), clock).published)
    }
}
