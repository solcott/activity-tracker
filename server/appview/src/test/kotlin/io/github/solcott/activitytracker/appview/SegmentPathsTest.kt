package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.GetPaths
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLines
import io.github.solcott.activitytracker.lexicon.app.SearchPathNames
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.update

/** Trails and roads that segments lie on (segments/segment-paths). */
class SegmentPathsTest : SegmentTestBase() {
    private val withImporter = config.copy(segmentImporter = Did(importer))

    /** Ann's: 5 km running north from the origin. */
    private val anns = listOf(0.0 to 0.0, 0.0 to 5000.0)

    /** Hogback: 3 km running east from Ann's end. */
    private val hogback = listOf(0.0 to 5000.0, 3000.0 to 5000.0)

    private fun pathUri(rkey: String, did: String = importer) = "at://$did/${Nsid.FEED_PATH}/$rkey"

    /** A `feed.path` record of [pieces], as JSON. */
    private fun pathJson(
        vararg pieces: List<Pair<Double, Double>>,
        name: String = "Ann's",
        kind: String = "trail",
        sports: List<String>? = null,
    ): String {
        val lines = pieces.joinToString(",") { "\"${lineOf(it)}\"" }
        val open = sports?.joinToString(",", prefix = ",\"sports\":[", postfix = "]") { "\"$it\"" }.orEmpty()
        return """{"name":"$name","kind":"$kind","lines":[$lines],"source":"osm","license":"ODbL-1.0"$open,""" +
            """"createdAt":"2026-10-01T00:00:00Z"}"""
    }

    /** Publishes a path of [pieces] from the importer's repo under [rkey]. */
    private fun path(
        rkey: String,
        vararg pieces: List<Pair<Double, Double>>,
        name: String = "Ann's",
        kind: String = "trail",
        sports: List<String>? = null,
    ) = commit(importer, Nsid.FEED_PATH, rkey, pathJson(*pieces, name = name, kind = kind, sports = sports))

    private fun activate(vararg rkeys: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri inList rkeys.map { segmentUri(it) } }) { it[Segments.status] = SegmentStatus.ACTIVE } }
    }

    private suspend fun io.ktor.client.HttpClient.pathsOf(rkey: String, viewer: String = alice, did: String = alice) =
        xrpcOk(GetSegment.method, mapOf("uri" to segmentUri(rkey, did)), viewer).segment.paths.map { it.value }

    @Test
    fun stretchesAndCombinationsLieOnTheirTrails() = testApplication {
        application { appView(config, db, verifier) }
        path("anns", anns)
        path("hogback", hogback, name = "Hogback")
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        segment("3kb", path = listOf(0.0 to 3500.0, 0.0 to 5000.0, 2500.0 to 5000.0), sport = "ride")
        segment("3kc", path = listOf(-1500.0 to 2000.0, 1500.0 to 2000.0), sport = "ride")
        segment("3kd", path = listOf(0.0 to 2200.0, 0.0 to 1000.0))
        assertEquals(listOf(pathUri("anns")), client.pathsOf("3ka"))
        assertEquals(listOf(pathUri("anns"), pathUri("hogback")), client.pathsOf("3kb"), "a combination lies on both")
        assertEquals(emptyList(), client.pathsOf("3kc"), "a road across Ann's only clips it")
        assertEquals(listOf(pathUri("anns")), client.pathsOf("3kd"), "the other way too")
    }

    @Test
    fun aPathIndexedLaterTakesInTheSegmentsUnderIt() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        assertEquals(emptyList(), client.pathsOf("3ka"))
        path("anns", anns)
        assertEquals(listOf(pathUri("anns")), client.pathsOf("3ka"))
        commit(alice, Nsid.FEED_SEGMENT, "3ka", segmentJson(path = listOf(500.0 to 5000.0, 2500.0 to 5000.0)))
        path("hogback", hogback, name = "Hogback")
        assertEquals(listOf(pathUri("hogback")), client.pathsOf("3ka"), "re-lined onto Hogback")
        commit(importer, Nsid.FEED_PATH, "hogback", null)
        assertEquals(emptyList(), client.pathsOf("3ka"), "the path deleted")
    }

    @Test
    fun onlyTheImportersKnownPathsAreIndexed() = testApplication {
        application { appView(config, db, verifier) }
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        commit(bob, Nsid.FEED_PATH, "anns", pathJson(anns))
        path("river", anns, kind = "river")
        assertEquals(emptyList(), client.pathsOf("3ka"))
        assertEquals(emptyList(), client.xrpcOk(GetPaths.method, mapOf("uris" to pathUri("anns", bob))).paths)
        assertEquals("NotFound", client.xrpcError(GetPath.method, mapOf("uri" to pathUri("river")), viewer = carol))
    }

    @Test
    fun aPathListsWhatTheViewerMayFind() = testApplication {
        application { appView(config, db, verifier) }
        path("anns", anns)
        path("long", listOf(0.0 to 0.0, 0.0 to 30000.0), listOf(0.0 to 31000.0, 0.0 to 60000.0), name = "Long Trail")
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0), sport = "ride")
        segment("3kb", path = listOf(0.0 to 2200.0, 0.0 to 1000.0), sport = "run")
        segment("3kc", path = listOf(0.0 to 3000.0, 0.0 to 4000.0), sport = "hike", did = bob)
        activate("3ka", "3kb")
        val carols = client.xrpcOk(GetPath.method, mapOf("uri" to pathUri("anns")), viewer = carol)
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kb")), carols.segments.map { it.uri.value }, "Bob's proposal isn't Carol's")
        assertEquals(listOf("ride", "run"), carols.path.sports)
        assertEquals(1, carols.path.groupCount, "one line both ways")
        assertEquals("trail", carols.path.kind)
        val bobs = client.xrpcOk(GetPath.method, mapOf("uri" to pathUri("anns")), viewer = bob).path
        assertEquals(listOf("ride", "run", "hike"), bobs.sports)
        assertEquals(2, bobs.groupCount)
        val long = client.xrpcOk(GetPaths.method, mapOf("uris" to pathUri("long")), viewer = carol).paths.single()
        assertEquals(2, long.lines.size)
        assertTrue(long.lengthMeters in 58_900L..59_100L, "past the segment limit, still one path: ${long.lengthMeters}")
        assertEquals(listOf("ride", "run"), long.sports, "Ann's segments lie on it too")
        assertEquals("NotFound", client.xrpcError(GetPath.method, mapOf("uri" to pathUri("nothing")), viewer = carol))
    }

    @Test
    fun namesFindTrailsAndLooseSegments() = testApplication {
        application { appView(config, db, verifier) }
        path("anns", anns)
        path("hogback", hogback, name = "Hogback")
        path("bighog", listOf(20000.0 to 0.0, 20000.0 to 5000.0), name = "Big Hog Loop")
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0), name = "Ann's: Lot → Ridge")
        segment("3kb", path = listOf(500.0 to 5000.0, 2500.0 to 5000.0), name = "Wiggly Woods")
        segment("3kc", path = listOf(20000.0 to 1000.0, 20000.0 to 2000.0), name = "Loop climb")
        segment("3kd", path = listOf(9000.0 to 0.0, 9000.0 to 1500.0), name = "Pine St sprint")
        activate("3ka", "3kb", "3kc", "3kd")
        suspend fun names(q: String, extra: Map<String, String> = emptyMap()) =
            client.xrpcOk(SearchPathNames.method, mapOf("q" to q) + extra, viewer = carol)
        assertEquals(listOf(pathUri("anns")), names("ann").results.map { it.path?.uri?.value }, "Ann's once, as a path")
        assertEquals(listOf(pathUri("hogback")), names("wiggly").results.map { it.path?.uri?.value }, "a renamed stretch finds its trail")
        val sprint = names("sprint").results.single()
        assertNull(sprint.path)
        assertEquals(listOf(segmentUri("3kd")), sprint.group?.map { it.uri.value }, "a segment on no path, as its group")
        assertEquals(listOf(pathUri("hogback"), pathUri("bighog")), names("hog").results.map { it.path?.uri?.value }, "a prefix first")
        assertEquals(emptyList(), names("ann", mapOf("sport" to "ride")).results, "Ann's has no ride segment")
        path("empty", listOf(40000.0 to 0.0, 40000.0 to 2000.0), name = "Annex Road", kind = "road")
        assertEquals(listOf(pathUri("anns")), names("ann").results.map { it.path?.uri?.value }, "no segments, not listed")
        val first = names("o", mapOf("limit" to "2"))
        val second = names("o", mapOf("limit" to "2", "cursor" to first.cursor!!))
        val third = second.cursor?.let { names("o", mapOf("limit" to "2", "cursor" to it)) }
        val all = (first.results + second.results + third?.results.orEmpty()).map { it.path?.uri?.value ?: it.group!!.first().uri.value }
        assertEquals(all.distinct(), all, "each result once")
        assertEquals(3, all.size, "Ann's (Lot), Hogback and Big Hog Loop; Annex Road has no segments")
    }

    @Test
    fun aBoxTooLargeIsRefused() = testApplication {
        application { appView(config, db, verifier) }
        val wide = mapOf("q" to "ann", "minLat" to "0", "minLng" to "0", "maxLat" to "30000000", "maxLng" to "1000")
        assertEquals("InvalidRequest", client.xrpcError(SearchPathNames.method, wide))
    }

    @Test
    fun theImporterLearnsTheSegmentsInAnArea() = testApplication {
        application { appView(withImporter, db, verifier) }
        path("anns", anns)
        segment("3ka", path = listOf(0.0 to 1000.0, 0.0 to 2200.0))
        segment("3kd", path = listOf(900.0 to 0.0, 900.0 to 1500.0), name = "Pine St sprint")
        val sw = at(-2000.0, -1000.0)
        val ne = at(2000.0, 3000.0)
        val box =
            mapOf(
                "minLat" to "${(sw.lat * 1e7).roundToInt()}",
                "minLng" to "${(sw.lng * 1e7).roundToInt()}",
                "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
                "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
            )
        val lines = client.xrpcOk(GetSegmentLines.method, box, viewer = importer)
        assertEquals(listOf(segmentUri("3ka"), segmentUri("3kd")), lines.segments.map { it.uri.value }.sorted())
        assertEquals(setOf("run"), lines.segments.map { it.sport }.toSet())
        val refused = client.xrpc(GetSegmentLines.method, box, viewer = alice)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("\"NotImporter\"", AtJson.parseToJsonElement(refused.bodyAsText()).jsonObject["error"].toString())
    }

    @Test
    fun aParallelTrailDoesntTakeANeighboursSegments() = testApplication {
        application { appView(config, db, verifier) }
        path("anns", anns, name = "Achtung Baby")
        path("hog", listOf(20.0 to 0.0, 20.0 to 5000.0), name = "Hog Hollow Road")
        segment("3ka", path = listOf(20.0 to 1000.0, 20.0 to 2200.0), name = "Hog Hollow Road: Two Hollows → Canyon Pointe")
        assertEquals(listOf(pathUri("hog")), client.pathsOf("3ka"), "on its own road, not Achtung Baby 20 m away")
        commit(importer, Nsid.FEED_PATH, "hog", null)
        assertEquals(listOf(pathUri("anns")), client.pathsOf("3ka"), "with Hog Hollow Road gone, Achtung Baby is nearest")
    }

    @Test
    fun aFootPathHoldsNoRides() = testApplication {
        application { appView(config, db, verifier) }
        path("hoof", anns, name = "Hoof N Boot Path", sports = listOf("run", "hike"))
        segment("3kr", path = listOf(0.0 to 1000.0, 0.0 to 2200.0), sport = "ride")
        segment("3kh", path = listOf(0.0 to 2200.0, 0.0 to 1000.0), sport = "hike")
        assertEquals(emptyList(), client.pathsOf("3kr"))
        assertEquals(listOf(pathUri("hoof")), client.pathsOf("3kh"))
        val view = client.xrpcOk(GetPath.method, mapOf("uri" to pathUri("hoof")), viewer = alice).path
        assertEquals(listOf("hike"), view.sports)
    }

    @Test
    fun anImportedSegmentLiesOnlyOnTrailsItMostlyFollows() = testApplication {
        application { appView(config, db, verifier) }
        // Clarks climbs 2 km; the 5 km Bonneville Shoreline runs over it and on.
        path("clarks", listOf(0.0 to 0.0, 0.0 to 2000.0), name = "Clarks Trail")
        path("bst", listOf(0.0 to -1500.0, 0.0 to 3500.0), name = "Bonneville Shoreline Trail")
        val bst = listOf(0.0 to -1500.0, 0.0 to 3500.0)
        segment("3ki", path = bst, sport = "ride", did = importer, name = "Bonneville Shoreline")
        segment("3ka", path = bst, sport = "ride", name = "Shoreline climb")
        segment("3kc", path = listOf(0.0 to 0.0, 0.0 to 2000.0), sport = "ride", did = importer, name = "Clarks")
        val onBoth = listOf(pathUri("bst"), pathUri("clarks"))
        assertEquals(listOf(pathUri("bst")), client.pathsOf("3ki", did = importer), "40% on Clarks isn't enough for an imported segment")
        assertEquals(onBoth, client.pathsOf("3ka"), "an athlete's is on both")
        assertEquals(onBoth, client.pathsOf("3kc", did = importer), "Clarks itself is on both trails, which share its tread")
    }
}
