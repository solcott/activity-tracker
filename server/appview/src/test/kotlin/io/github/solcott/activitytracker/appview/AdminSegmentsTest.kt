package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.AuditFilter
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentPinRows
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll

/** The Segments admin page and its operator actions (admin/segment-tools), through the admin site on a Ktor test host. */
class AdminSegmentsTest : AdminTestBase() {
    private var terrain = false
    private val f by lazy { SegmentAdminFixtures(db, clock, terrain = terrain) }
    private val actions =
        listOf("status-pass", "naming-pass", "elevation", "rematch", "pin-status", "unpin-status", "pin-name", "unpin-name")

    @AfterTest fun forget() = f.forgetLabels()

    private fun segmentsTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(HttpClient) -> Unit) =
        adminTest({ site(pages = listOf(f.page)) }, block)

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get(path) { session(cookie) }

    private suspend fun HttpClient.segmentPage(uri: String, cookie: String) =
        page("/admin/segments/segment?uri=${uri.encodeURLParameter()}", cookie).bodyAsText()

    private suspend fun HttpClient.act(
        action: String,
        cookie: String,
        csrf: String?,
        form: Map<String, String> = emptyMap(),
        origin: String? = null,
    ) =
        submitForm("/admin/segments/$action", Parameters.build { form.forEach { (k, v) -> append(k, v) } }) {
            session(cookie)
            csrf?.let { header(AdminSite.CSRF_HEADER, it) }
            origin?.let { header(HttpHeaders.Origin, it) }
        }

    /** Signs in as alice, the operator; returns her cookie and CSRF token. */
    private suspend fun HttpClient.operator(): Pair<String, String> {
        val cookie = signIn("alice.test")!!
        return cookie to csrfOf(page("/admin", cookie).bodyAsText())
    }

    private fun entries() = runBlocking { site().audit.page(AuditFilter(), null, 100).entries }.reversed()

    private fun jobs() = runBlocking {
        db.tx { SegmentMatchJobs.selectAll().map { it[SegmentMatchJobs.kind] to it[SegmentMatchJobs.subjectUri] } }
    }

    private fun clearJobs() = runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }

    private fun riders(segment: String, count: Int) = repeat(count) { f.efforts("did:plc:rider$it", segment) }

    // ---------------------------------------------------------------- Pages

    @Test
    fun viewersSeeTheSegmentPageWithoutControls() = segmentsTest { http ->
        val a = f.segment("3ka")
        val cookie = http.signIn("bob.test")!!
        val body = http.segmentPage(a, cookie)
        assertTrue(a in body)
        assertTrue("/admin/audit?target=${a.encodeURLParameter()}" in body)
        assertFalse("<form action=\"/admin/segments/" in body)
        assertTrue("Segments" in http.page("/admin/segments", cookie).bodyAsText())
    }

    @Test
    fun operatorsSeeTheControls() = segmentsTest { http ->
        val a = f.segment("3ka")
        val (cookie, _) = http.operator()
        val body = http.segmentPage(a, cookie)
        actions.filter { !it.startsWith("unpin") }.forEach { assertTrue("/admin/segments/$it" in body, it) }
    }

    @Test
    fun searchSaysWhenNothingIsFound() = segmentsTest { http ->
        val (cookie, _) = http.operator()
        val body = http.page("/admin/segments?name=nowhere", cookie).bodyAsText()
        assertTrue("No segment found." in body)
        assertTrue("No candidates are staged." in body)
    }

    @Test
    fun aTakenDownSegmentsPageWithholdsItsName() = segmentsTest { http ->
        val a = f.segment("3ka", name = "Secretname")
        f.label(a, "!takedown")
        val (cookie, _) = http.operator()
        val body = http.segmentPage(a, cookie)
        assertFalse("Secretname" in body)
        assertTrue("withheld" in body)
        assertTrue(SegmentStatus.PROPOSED in body)
    }

    // ---------------------------------------------------------------- Passes, elevation and rematch

    @Test
    fun statusPassPromotesNow() = segmentsTest { http ->
        val a = f.segment("3ka")
        riders(a, 3)
        val (cookie, csrf) = http.operator()
        val response = http.act("status-pass", cookie, csrf, mapOf("target" to a))
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("Status proposed → active." in response.bodyAsText())
        assertEquals(SegmentStatus.ACTIVE, f.status(a))
    }

    @Test
    fun statusPassLeavesAPinAlone() = segmentsTest { http ->
        val a = f.segment("3ka")
        runBlocking { f.indexer.segmentPins.pinStatus(a, SegmentStatus.ARCHIVED, null, Did(alice)) }
        riders(a, 3)
        val (cookie, csrf) = http.operator()
        http.act("status-pass", cookie, csrf, mapOf("target" to a))
        assertEquals(SegmentStatus.ARCHIVED, f.status(a))
    }

    @Test
    fun namingPassAdoptsNow() = segmentsTest { http ->
        val a = f.segment("3ka")
        f.setStatus(a, SegmentStatus.ACTIVE)
        val adopted = f.proposal("3kp", a, "did:plc:proposer", "Wiggly Woods", now.toEpochMilliseconds())
        val other = f.proposal("3kq", a, "did:plc:other", "Plain", now.toEpochMilliseconds())
        repeat(6) {
            f.efforts("did:plc:up$it", a)
            f.vote(adopted, "did:plc:up$it")
        }
        now += 8.days
        val (cookie, csrf) = http.operator()
        val body = http.act("naming-pass", cookie, csrf, mapOf("target" to a)).bodyAsText()
        assertTrue("$adopted ${ProposalState.ADOPTED}" in body, body)
        assertTrue("$other ${ProposalState.SUPERSEDED}" in body, body)
        assertTrue("Current name: Wiggly Woods." in body, body)
    }

    @Test
    fun elevationWithoutTerrain() = segmentsTest { http ->
        val a = f.segment("3ka")
        val (cookie, csrf) = http.operator()
        val response = http.act("elevation", cookie, csrf, mapOf("target" to a))
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("terrain not configured" in response.bodyAsText())
    }

    @Test
    fun elevationWithTerrainQueuesIt() {
        terrain = true
        segmentsTest { http ->
            val a = f.segment("3ka")
            clearJobs()
            val (cookie, csrf) = http.operator()
            assertTrue("terrain queued" in http.act("elevation", cookie, csrf, mapOf("target" to a)).bodyAsText())
            assertEquals(listOf("elevation" to a), jobs())
        }
    }

    @Test
    fun rematchQueuesOnlyALiveSegment() = segmentsTest { http ->
        val live = f.segment("3ka")
        val archived = f.segment("3kb", east = 5000.0)
        f.setStatus(live, SegmentStatus.ACTIVE)
        f.setStatus(archived, SegmentStatus.ARCHIVED)
        clearJobs()
        val (cookie, csrf) = http.operator()
        assertEquals(HttpStatusCode.OK, http.act("rematch", cookie, csrf, mapOf("target" to live)).status)
        val refused = http.act("rematch", cookie, csrf, mapOf("target" to archived))
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertTrue("Not live (archived)" in refused.bodyAsText())
        assertEquals(listOf("segment" to live), jobs())
        assertEquals(listOf("ok", "refused: not-live"), entries().map { it.outcome })
    }

    // ---------------------------------------------------------------- Pins

    @Test
    fun pinArchivedWithANote() = segmentsTest { http ->
        val a = f.segment("3ka")
        f.setStatus(a, SegmentStatus.ACTIVE)
        val (cookie, csrf) = http.operator()
        val response = http.act("pin-status", cookie, csrf, mapOf("target" to a, "status" to "archived", "note" to "closed trail"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(SegmentStatus.ARCHIVED, f.status(a))
        val body = http.segmentPage(a, cookie)
        assertTrue("closed trail" in body)
        assertTrue(alice in body)
        val entry = entries().single()
        assertEquals(listOf("segments.pin-status", a, "ok"), listOf(entry.action, entry.target, entry.outcome))
        assertEquals(mapOf("status" to "archived", "note" to "closed trail"), entry.params)
    }

    @Test
    fun anInvalidPinIsRefusedAndChangesNothing() = segmentsTest { http ->
        val run = f.segment("3ka")
        val ride = f.segment("3kb", east = 5000.0, sport = "ride")
        val (cookie, csrf) = http.operator()
        val form = mapOf("target" to run, "status" to "duplicate", "duplicateOf" to ride, "confirm" to "yes")
        val response = http.act("pin-status", cookie, csrf, form)
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("another sport group or kind" in response.bodyAsText())
        assertEquals(SegmentStatus.PROPOSED, f.status(run))
        val entry = entries().single()
        assertEquals(listOf(run, "refused: invalid"), listOf(entry.target, entry.outcome))
        assertEquals(form - "target", entry.params)
    }

    @Test
    fun anUnconfirmedDuplicatePinIsRefused() = segmentsTest { http ->
        val a = f.segment("3ka")
        val b = f.segment("3kb", east = 5000.0)
        val (cookie, csrf) = http.operator()
        val response = http.act("pin-status", cookie, csrf, mapOf("target" to a, "status" to "duplicate", "duplicateOf" to b))
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("confirmation" in response.bodyAsText())
        assertEquals(SegmentStatus.PROPOSED, f.status(a))
        assertEquals("refused: unconfirmed", entries().single().outcome)
    }

    @Test
    fun pinAndUnpinAName() = segmentsTest { http ->
        val a = f.segment("3ka")
        val (cookie, csrf) = http.operator()
        assertEquals(HttpStatusCode.OK, http.act("pin-name", cookie, csrf, mapOf("target" to a, "name" to "Pinned Path")).status)
        assertTrue("Pinned Path" in http.segmentPage(a, cookie))
        assertEquals(HttpStatusCode.OK, http.act("unpin-name", cookie, csrf, mapOf("target" to a)).status)
        val nothing = http.act("unpin-name", cookie, csrf, mapOf("target" to a))
        assertEquals(HttpStatusCode.UnprocessableEntity, nothing.status)
        assertTrue("There was no pin to remove." in nothing.bodyAsText())
        assertEquals(listOf("ok", "ok", "refused: no-pin"), entries().map { it.outcome })
    }

    @Test
    fun pinsOnADeletedSegmentAreListedAndUnpinned() = segmentsTest { http ->
        val a = f.segment("3ka")
        runBlocking { f.indexer.segmentPins.pinStatus(a, SegmentStatus.ARCHIVED, null, Did(alice)) }
        f.commit("did:plc:alice", Nsid.FEED_SEGMENT, "3ka", null)
        val (cookie, csrf) = http.operator()
        val body = http.page("/admin/segments", cookie).bodyAsText()
        assertTrue("Pins on missing segments" in body)
        assertTrue(a in body.substringAfter("Pins on missing segments"))
        assertEquals(HttpStatusCode.OK, http.act("unpin-status", cookie, csrf, mapOf("target" to a)).status)
        assertEquals(0, runBlocking { db.tx { SegmentPinRows.selectAll().count() } })
    }

    // ---------------------------------------------------------------- Gating and auditing

    @Test
    fun viewersCantActAndEachAttemptIsAudited() = segmentsTest { http ->
        val a = f.segment("3ka")
        riders(a, 3)
        val cookie = http.signIn("bob.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        for (action in actions) {
            val response = http.act(action, cookie, csrf, mapOf("target" to a, "status" to "archived", "name" to "X"))
            assertEquals(HttpStatusCode.Forbidden, response.status, action)
        }
        assertEquals(SegmentStatus.PROPOSED, f.status(a))
        assertEquals(actions.map { "segments.$it" }, entries().map { it.action })
        assertTrue(entries().all { it.target == a && it.outcome == "refused: viewer" && it.params["status"] == "archived" })
    }

    @Test
    fun forgedRequestsAreRefused() = segmentsTest { http ->
        val a = f.segment("3ka")
        val (cookie, csrf) = http.operator()
        val form = mapOf("target" to a, "status" to "archived")
        assertEquals(HttpStatusCode.Forbidden, http.act("pin-status", cookie, null, form).status)
        assertEquals(HttpStatusCode.Forbidden, http.act("pin-status", cookie, csrf, form, origin = "https://evil.example").status)
        assertEquals(SegmentStatus.PROPOSED, f.status(a))
        assertEquals(listOf("refused: csrf", "refused: cross-origin"), entries().map { it.outcome })
    }
}
