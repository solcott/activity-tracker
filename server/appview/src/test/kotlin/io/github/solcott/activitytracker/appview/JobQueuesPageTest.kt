package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminActionEntry
import io.github.solcott.activitytracker.appview.admin.AdminAudit
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.AuditFilter
import io.github.solcott.activitytracker.appview.admin.JobQueuesPage
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** The admin site's queues page (admin/job-queues): what admins see, and the operator actions with their checks and audit. */
class JobQueuesPageTest : AdminTestBase() {
    private val nowMs = now.toEpochMilliseconds()
    private val trackUri = "at://$carol/${Nsid.FEED_SEGMENT_TRACK}/3kt"

    private fun page(terrain: Boolean = false) = JobQueuesPage(SegmentQueue(db, SegmentJobs(terrain), clock), clock)

    private fun entries(): List<AdminActionEntry> = runBlocking { AdminAudit(db, clock).page(AuditFilter(), null, 100).entries }

    private fun queued(): List<Pair<String, String>> = runBlocking {
        db.tx { SegmentMatchJobs.selectAll().map { it[SegmentMatchJobs.kind] to it[SegmentMatchJobs.subjectUri] } }.sortedBy { it.second }
    }

    private fun failed(): List<String> = runBlocking { db.tx { SegmentJobFailures.selectAll().map { it[SegmentJobFailures.subjectUri] } } }

    private fun retrying(kind: String, subject: String, dueAt: Long = nowMs + 60_000) = runBlocking {
        db.tx {
            SegmentMatchJobs.insert {
                it[SegmentMatchJobs.kind] = kind
                it[subjectUri] = subject
                it[enqueuedAt] = dueAt
                it[attempts] = 3
                it[lastError] = "IllegalStateException: the PDS is down"
                it[firstFailedAt] = nowMs - 600_000
            }
        }
    }

    private fun deadLetter(kind: String, subject: String) = runBlocking {
        db.tx {
            SegmentJobFailures.insert {
                it[SegmentJobFailures.kind] = kind
                it[subjectUri] = subject
                it[attempts] = 9
                it[lastError] = "IOException: refused"
                it[firstFailedAt] = nowMs - 7_200_000
                it[failedAt] = nowMs - 60_000
            }
        }
    }

    /** Carol's segment track, held since her matching is on. */
    private fun heldTrack() = runBlocking {
        db.tx {
            SegmentPrefs.insert {
                it[did] = carol
                it[matching] = true
                it[updatedAt] = 0
            }
            SegmentTracks.insert {
                it[uri] = trackUri
                it[authorDid] = carol
                it[activityUri] = "at://$carol/${Nsid.FEED_ACTIVITY}/3kt"
                it[dataCid] = "bafkdata"
                it[indexedAt] = 0
            }
        }
    }

    /** A segment of carol's near Burlington, Vermont, and one at the equator. */
    private fun segments() = runBlocking {
        val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
        for ((rkey, start) in listOf("3kvt" to GeoPoint(44.47, -73.2), "3keq" to GeoPoint(0.0, 0.0))) {
            val line = SegmentGeometry.encodePolyline(listOf(start, GeoPoint(start.lat + 0.005, start.lng)))
            val json =
                """{"name":"Hill","sport":"run","line":"$line","source":"athlete","license":"CC0-1.0","createdAt":"2026-10-01T00:00:00Z"}"""
            indexer.handle(
                JetstreamEvent.Commit(
                    did = carol,
                    time = "2026-10-14T12:00:00.000000Z",
                    operation = "create",
                    collection = Nsid.FEED_SEGMENT,
                    rkey = rkey,
                    record = AtJson.parseToJsonElement(json).jsonObject,
                    cid = "bafy$rkey",
                ),
            )
        }
        db.tx { SegmentMatchJobs.deleteAll() }
    }

    private fun queuesTest(page: JobQueuesPage = page(), block: suspend (HttpClient, JobQueuesPage) -> Unit) =
        adminTest({ site(pages = listOf(page)) }) { http -> block(http, page) }

    private suspend fun HttpClient.html(path: String, cookie: String) = get(path) { session(cookie) }.bodyAsText()

    private suspend fun HttpClient.act(path: String, cookie: String, csrf: String?, form: Map<String, String>): HttpResponse =
        submitForm("/admin/queues/$path", Parameters.build { form.forEach { (k, v) -> append(k, v) } }) {
            session(cookie)
            csrf?.let { header(AdminSite.CSRF_HEADER, it) }
        }

    /** Signs in as [identifier] and returns its session cookie and CSRF token. */
    private suspend fun HttpClient.admin(identifier: String): Pair<String, String> {
        val cookie = signIn(identifier)!!
        return cookie to csrfOf(html("/admin/queues", cookie))
    }

    // What admins see

    @Test
    fun viewerSeesTheSummaryAndListsWithNoControls() = queuesTest { http, _ ->
        retrying(SegmentJobs.ELEVATION, "at://s1")
        deadLetter(SegmentJobs.TRACK, trackUri)
        val cookie = http.signIn("bob.test")!!
        val html = http.html("/admin/queues", cookie)
        assertTrue("/admin/queues" in html && ">Queues<" in html, "listed in the navigation")
        SegmentJobs.KINDS.forEach { assertTrue(">$it<" in html, it) }
        assertTrue("at://s1" in html && "IllegalStateException: the PDS is down" in html)
        assertTrue(trackUri in html && "IOException: refused" in html)
        for (control in listOf("Retry now", "Requeue", "Drop", "Bulk requeue", "/admin/queues/rematch")) {
            assertFalse(control in html, control)
        }
    }

    @Test
    fun operatorSeesTheControls() = queuesTest { http, _ ->
        retrying(SegmentJobs.ELEVATION, "at://s1")
        deadLetter(SegmentJobs.TRACK, trackUri)
        val html = http.html("/admin/queues", http.signIn("alice.test")!!)
        for (control in listOf("Retry now", "Requeue", "Drop", "Bulk requeue", "Rematch tracks", "Recompute elevation", "Terrain tiles")) {
            assertTrue(control in html, control)
        }
    }

    @Test
    fun listsFilterByKindAndPage() = queuesTest { http, _ ->
        retrying(SegmentJobs.ELEVATION, "at://s1")
        retrying(SegmentJobs.TRACK, trackUri)
        val cookie = http.signIn("bob.test")!!
        val tracks = http.html("/admin/queues/retrying?kind=track", cookie)
        assertTrue(trackUri in tracks && "at://s1" !in tracks)
        assertTrue("Unknown kind" in http.html("/admin/queues/failed?kind=nonsense", cookie))
    }

    // Who may act

    @Test
    fun viewerActionsAreRefusedAndChangeNothing() = queuesTest { http, _ ->
        retrying(SegmentJobs.TRACK, trackUri)
        deadLetter(SegmentJobs.TRACK, trackUri)
        val (cookie, csrf) = http.admin("bob.test")
        val job = mapOf("target" to trackUri, "kind" to SegmentJobs.TRACK)
        val posts =
            listOf(
                "retry" to job,
                "requeue" to job,
                "drop" to job + ("list" to "failed"),
                "requeue-kind" to mapOf("target" to SegmentJobs.TRACK, "confirm" to "on"),
                "rematch" to mapOf("target" to SegmentJobs.TRACK, "confirm" to "on"),
                "elevation" to mapOf("target" to SegmentJobs.ELEVATION, "confirm" to "on"),
            )
        for ((path, form) in posts) assertEquals(HttpStatusCode.Forbidden, http.act(path, cookie, csrf, form).status, path)
        assertEquals(List(posts.size) { "refused: viewer" }, entries().map { it.outcome })
        assertEquals(listOf(SegmentJobs.TRACK to trackUri), queued())
        assertEquals(listOf(trackUri), failed())
    }

    @Test
    fun aPostWithoutTheTokenIsRefused() = queuesTest { http, _ ->
        deadLetter(SegmentJobs.GROUP, "at://g")
        val (cookie, _) = http.admin("alice.test")
        val response = http.act("drop", cookie, null, mapOf("target" to "at://g", "kind" to SegmentJobs.GROUP, "list" to "failed"))
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(listOf("at://g"), failed())
        assertEquals("refused: csrf", entries().single().outcome)
    }

    // Operator actions

    @Test
    fun retryRequeueAndDropOneJob() = queuesTest { http, _ ->
        retrying(SegmentJobs.ELEVATION, "at://s1")
        retrying(SegmentJobs.SITE, "at://site")
        deadLetter(SegmentJobs.TRACK, trackUri)
        deadLetter(SegmentJobs.CANDIDATE, "c1")
        val (cookie, csrf) = http.admin("alice.test")
        fun job(kind: String, subject: String) = mapOf("target" to subject, "kind" to kind)
        assertEquals(HttpStatusCode.OK, http.act("retry", cookie, csrf, job(SegmentJobs.ELEVATION, "at://s1")).status)
        assertEquals(HttpStatusCode.OK, http.act("requeue", cookie, csrf, job(SegmentJobs.TRACK, trackUri)).status)
        val dropFailed = job(SegmentJobs.CANDIDATE, "c1") + ("list" to "failed")
        assertEquals(HttpStatusCode.OK, http.act("drop", cookie, csrf, dropFailed).status)
        val dropRetrying = job(SegmentJobs.SITE, "at://site") + ("list" to "retrying")
        assertEquals(HttpStatusCode.OK, http.act("drop", cookie, csrf, dropRetrying).status)
        val again = http.act("drop", cookie, csrf, dropFailed)
        assertEquals(HttpStatusCode.UnprocessableEntity, again.status)
        assertTrue("no longer exists" in again.bodyAsText())

        assertEquals(listOf(SegmentJobs.TRACK to trackUri, SegmentJobs.ELEVATION to "at://s1"), queued())
        assertEquals(emptyList(), failed())
        val due = runBlocking {
            db.tx { SegmentMatchJobs.selectAll().associate { it[SegmentMatchJobs.subjectUri] to it[SegmentMatchJobs.enqueuedAt] } }
        }
        assertEquals(nowMs, due["at://s1"])
        val audit = entries().reversed()
        assertEquals(
            listOf(
                Triple("queues.retry", "at://s1", "ok: 1 due now"),
                Triple("queues.requeue", trackUri, "ok: 1 requeued"),
                Triple("queues.drop", "c1", "ok: 1 dropped"),
                Triple("queues.drop", "at://site", "ok: 1 dropped"),
                Triple("queues.drop", "c1", "refused: not-found"),
            ),
            audit.map { Triple(it.action, it.target, it.outcome) },
        )
        assertEquals(mapOf("kind" to SegmentJobs.ELEVATION), audit.first().params)
    }

    @Test
    fun requeueAKindsFailedJobs() = queuesTest { http, _ ->
        deadLetter(SegmentJobs.TRACK, "at://t1")
        deadLetter(SegmentJobs.TRACK, "at://t2")
        deadLetter(SegmentJobs.GROUP, "at://g")
        val (cookie, csrf) = http.admin("alice.test")
        val response = http.act("requeue-kind", cookie, csrf, mapOf("target" to SegmentJobs.TRACK, "confirm" to "on"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("2 requeued, 0 remain" in response.bodyAsText())
        assertEquals(listOf(SegmentJobs.TRACK to "at://t1", SegmentJobs.TRACK to "at://t2"), queued())
        assertEquals(listOf("at://g"), failed())
        assertEquals("ok: 2 requeued, 0 remain", entries().single().outcome)
    }

    @Test
    fun rematchQueuesHeldTracksOnce() = queuesTest { http, _ ->
        heldTrack()
        val (cookie, csrf) = http.admin("alice.test")
        assertTrue("This queues 1 track jobs." in http.html("/admin/queues/preview/rematch", cookie))
        val form = mapOf("target" to SegmentJobs.TRACK, "sport" to "", "confirm" to "on")
        assertTrue("1 track jobs queued" in http.act("rematch", cookie, csrf, form).bodyAsText())
        assertTrue("0 track jobs queued" in http.act("rematch", cookie, csrf, form).bodyAsText(), "repeating queues nothing more")
        assertEquals(listOf(SegmentJobs.TRACK to trackUri), queued())
        assertEquals(listOf("ok: 0 queued", "ok: 1 queued"), entries().map { it.outcome })
        assertEquals(SegmentJobs.TRACK, entries().first().target)
    }

    @Test
    fun elevationForABoxIsAuditedWithItsScope() =
        queuesTest(page(terrain = true)) { http, _ ->
            segments()
            val (cookie, csrf) = http.admin("alice.test")
            val box = "-73.5,42.7,-71.4,45.0"
            assertTrue("This queues 1 elevation jobs." in http.html("/admin/queues/preview/elevation?box=$box&missingOnly=on", cookie))
            val form = mapOf("target" to SegmentJobs.ELEVATION, "box" to box, "missingOnly" to "on", "confirm" to "on")
            assertEquals(HttpStatusCode.OK, http.act("elevation", cookie, csrf, form).status)
            assertEquals(listOf(SegmentJobs.ELEVATION to "at://$carol/${Nsid.FEED_SEGMENT}/3kvt"), queued())
            val entry = entries().single()
            assertEquals(
                listOf(alice, "queues.elevation", SegmentJobs.ELEVATION, "ok: 1 queued"),
                listOf(entry.admin, entry.action, entry.target, entry.outcome),
            )
            assertEquals(mapOf("box" to box, "missingOnly" to "on", "confirm" to "on"), entry.params)
        }

    @Test
    fun elevationWithoutTerrainTilesIsRefused() = queuesTest { http, _ ->
        segments()
        deadLetter(SegmentJobs.ELEVATION, "at://s")
        val (cookie, csrf) = http.admin("alice.test")
        val bulk = http.act("elevation", cookie, csrf, mapOf("target" to SegmentJobs.ELEVATION, "confirm" to "on"))
        assertEquals(HttpStatusCode.UnprocessableEntity, bulk.status)
        assertTrue("Terrain tiles aren" in bulk.bodyAsText())
        val one = http.act("requeue", cookie, csrf, mapOf("target" to "at://s", "kind" to SegmentJobs.ELEVATION))
        assertEquals(HttpStatusCode.UnprocessableEntity, one.status)
        assertEquals(emptyList(), queued())
        assertEquals(listOf("at://s"), failed())
        assertEquals(listOf("refused: no-terrain", "refused: no-terrain"), entries().map { it.outcome })
    }

    @Test
    fun invalidAndUnconfirmedActionsChangeNothing() =
        queuesTest(page(terrain = true)) { http, _ ->
            heldTrack()
            segments()
            deadLetter(SegmentJobs.TRACK, trackUri)
            val (cookie, csrf) = http.admin("alice.test")
            val posts =
                listOf(
                    "rematch" to mapOf("target" to SegmentJobs.TRACK),
                    "elevation" to mapOf("target" to SegmentJobs.ELEVATION, "box" to "-71.4,42.7,-73.5,45.0", "confirm" to "on"),
                    "elevation" to mapOf("target" to SegmentJobs.ELEVATION, "box" to "-73.5,42.7,-71.4,91", "confirm" to "on"),
                    "rematch" to mapOf("target" to SegmentJobs.TRACK, "sport" to "run'; drop", "confirm" to "on"),
                    "requeue" to mapOf("target" to trackUri, "kind" to "nonsense"),
                    "requeue-kind" to mapOf("target" to "nonsense", "confirm" to "on"),
                    "retry" to mapOf("target" to "x".repeat(513), "kind" to SegmentJobs.TRACK),
                )
            for ((path, form) in posts) assertEquals(
                HttpStatusCode.UnprocessableEntity,
                http.act(path, cookie, csrf, form).status,
                "$path $form",
            )
            assertEquals(emptyList(), queued())
            assertEquals(listOf(trackUri), failed())
            assertEquals(
                listOf("unconfirmed", "invalid-box", "invalid-box", "invalid-sport", "unknown-kind", "unknown-kind", "invalid").map {
                    "refused: $it"
                },
                entries().reversed().map { it.outcome },
            )
        }

    @Test
    fun aSecondBulkRequeueWhileOneRunsIsRefused() = queuesTest { http, page ->
        heldTrack()
        val (cookie, csrf) = http.admin("alice.test")
        assertTrue(page.bulk.tryLock(), "another bulk requeue is running")
        try {
            val response = http.act("rematch", cookie, csrf, mapOf("target" to SegmentJobs.TRACK, "confirm" to "on"))
            assertEquals(HttpStatusCode.Conflict, response.status)
        } finally {
            page.bulk.unlock()
        }
        assertEquals(emptyList(), queued())
        assertEquals("refused: running", entries().single().outcome)
    }
}
