package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminActionEntry
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.AuditFilter
import io.github.solcott.activitytracker.appview.admin.DatabaseSizes
import io.github.solcott.activitytracker.appview.admin.HealthPage
import io.github.solcott.activitytracker.appview.admin.HealthSources
import io.github.solcott.activitytracker.appview.db.LabelCursors
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.labels.LabelVerifier
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerNames
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.WorkerState
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.upsert

/** The health page (admin/ingest-health) over a real supervisor, an H2 database and fake streams. */
class HealthPageTest : AdminTestBase() {
    private val app = CoroutineScope(SupervisorJob())
    private val registry = WorkerRegistry(clock)
    private val supervisor = WorkerSupervisor(app, registry)
    private val connections = CopyOnWriteArrayList<String>()
    private val consumer =
        JetstreamConsumer("ws://jetstream.test", db, Indexer(db, { "x.test" })) { url, _ ->
            connections += url
            awaitCancellation()
        }
    private val ozone = "did:plc:ozone"
    private val labeler =
        LabelSubscriber(
            AppViewConfig.Labeler(ozone, "http://labeler.test"),
            LabelStore(db, ozone, null),
            LabelVerifier { _, _ -> null },
            endpoint = { null },
            frames = { _, _, _ -> awaitCancellation() },
            clock = clock,
        )
    private var sizes: DatabaseSizes = DatabaseSizes.Unavailable
    private var syncing = true
    private var jetstreamOn = true

    @AfterTest fun stop() = app.cancel()

    private fun health() =
        HealthPage(
            HealthSources(
                supervisor,
                StartedWorkers(consumer.takeIf { jetstreamOn }, listOf(labeler)),
                db,
                24.hours,
                syncing,
                sizes,
                clock,
            ),
        )

    private fun healthTest(block: suspend (HttpClient) -> Unit) = adminTest({ site(pages = listOf(health())) }) { block(it) }

    /** A running Jetstream and handle sweep, a backing-off block backfill and a disabled matcher. */
    private fun startWorkers() = runBlocking {
        supervisor.stream(WorkerNames.JETSTREAM) { consumer.run(it) }
        supervisor.loop(WorkerNames.HANDLE_SWEEP) { awaitCancellation() }
        supervisor.loop(WorkerNames.BSKY_BLOCK_BACKFILL) {
            it.failure(IOException("pds down"))
            awaitCancellation()
        }
        registry.disabled(WorkerNames.SEGMENT_MATCHER, WorkerKind.LOOP, "APPVIEW_SIGNING_KEY is not set")
        until { registry.snapshot().count { it.state in setOf(WorkerState.RUNNING, WorkerState.BACKING_OFF) } == 3 }
        until { connections.size == 1 }
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10.seconds) { while (!condition()) delay(5) }

    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get(path) { session(cookie) }

    private suspend fun HttpClient.act(path: String, cookie: String, csrf: String?, form: Map<String, String>) =
        submitForm(path, Parameters.build { form.forEach { (k, v) -> append(k, v) } }) {
            session(cookie)
            csrf?.let { header(AdminSite.CSRF_HEADER, it) }
        }

    /** Signs [who] in and returns their cookie and CSRF token. */
    private suspend fun HttpClient.operator(who: String = "alice.test"): Pair<String, String> {
        val cookie = signIn(who)!!
        return cookie to csrfOf(page("/admin/health", cookie).bodyAsText())
    }

    private fun entries(): List<AdminActionEntry> = runBlocking { site().audit.page(AuditFilter(), null, 100).entries }

    private fun us(at: Instant) = at.epochSeconds * 1_000_000 + at.nanosecondsOfSecond / 1_000

    private fun seedSpace(space: String, failures: Int, lastErrorAt: Long? = null, notFoundSince: Long? = null, nextAttemptAt: Long) =
        runBlocking {
            db.tx {
                SpaceSync.insert {
                    it[SpaceSync.space] = space
                    it[authorityDid] = space.removePrefix("at://").substringBefore('/')
                    it[SpaceSync.failures] = failures
                    it[lastError] = if (failures > 0) "IOException: host unreachable" else null
                    it[SpaceSync.lastErrorAt] = lastErrorAt
                    it[SpaceSync.notFoundSince] = notFoundSince
                    it[SpaceSync.nextAttemptAt] = nextAttemptAt
                    it[createdAt] = 0
                }
            }
        }

    // What it shows

    @Test
    fun aViewerSeesEveryWorkerAndNoActions() {
        startWorkers()
        healthTest { http ->
            val body = http.page("/admin/health", http.signIn("bob.test")!!).bodyAsText()
            for (name in listOf("jetstream", "handle-sweep", "bsky-block-backfill", "segment-matcher")) assertTrue(name in body, name)
            assertTrue("backing off" in body)
            assertTrue("IOException: pds down" in body)
            assertTrue("APPVIEW_SIGNING_KEY is not set" in body)
            assertFalse("workers/restart" in body)
            assertFalse("jetstream/rewind" in body)
            val operator = http.page("/admin/health", http.signIn("alice.test")!!).bodyAsText()
            assertTrue("/admin/health/workers/restart" in operator)
            assertTrue("/admin/health/jetstream/rewind" in operator)
        }
    }

    @Test
    fun jetstreamShowsItsCursorLastEventAndLag() = healthTest { http ->
        val cookie = http.signIn("bob.test")!!
        consumer.storeCursor(us(Instant.parse("2026-11-18T11:50:00Z")))
        val before = http.page("/admin/health/jetstream", cookie).bodyAsText()
        assertTrue("2026-11-18T11:50:00Z" in before)
        assertTrue("No event indexed yet" in before)
        consumer.lastEventUs.set(us(now - 2.minutes))
        val after = http.page("/admin/health/jetstream", cookie).bodyAsText()
        assertTrue("2026-11-18T11:58:00Z" in after)
        assertTrue("2m" in after, after)
        assertTrue("2026-11-17T12:00:00Z" in after, "the earliest rewind")
    }

    @Test
    fun jetstreamOffSaysSo() {
        jetstreamOn = false
        healthTest { http ->
            assertTrue("Jetstream is off" in http.page("/admin/health/jetstream", http.signIn("bob.test")!!).bodyAsText())
        }
    }

    @Test
    fun labelersShowTheirCursorAndStream() = healthTest { http ->
        db.tx {
            LabelCursors.upsert {
                it[src] = ozone
                it[seq] = 4242
            }
        }
        val body = http.page("/admin/health/labelers", http.signIn("bob.test")!!).bodyAsText()
        assertTrue(ozone in body)
        assertTrue("4242" in body)
        assertTrue("closed" in body)
        assertTrue("2026-11-18T12:00:00Z" in body)
    }

    @Test
    fun spacesListTheFailingOnesAndSayWhenSyncingIsOff() {
        seedSpace(
            "at://did:plc:carol/app.activitytracker.space.athlete/self",
            failures = 3,
            lastErrorAt = 1,
            nextAttemptAt = Long.MAX_VALUE,
        )
        seedSpace("at://did:plc:dave/app.activitytracker.space.athlete/self", failures = 0, nextAttemptAt = Long.MAX_VALUE)
        healthTest { http ->
            val body = http.page("/admin/health/spaces", http.signIn("bob.test")!!).bodyAsText()
            assertTrue("at://did:plc:carol/app.activitytracker.space.athlete/self" in body)
            assertTrue("IOException: host unreachable" in body)
            assertFalse("did:plc:dave" in body, "a healthy space isn't listed")
        }
        syncing = false
        healthTest { http ->
            assertTrue("Space syncing is off" in http.page("/admin/health/spaces", http.signIn("bob.test")!!).bodyAsText())
        }
    }

    @Test
    fun databaseSizesAreUnavailableOnH2AndDrawnLargestFirst() {
        assertEquals(DatabaseSizes.Unavailable, DatabaseSizes.of(db))
        healthTest { http -> assertTrue("aren't available" in http.page("/admin/health/database", http.signIn("bob.test")!!).bodyAsText()) }
        sizes = DatabaseSizes {
            DatabaseSizes.Sizes(
                5L * 1024 * 1024,
                listOf(DatabaseSizes.Table("small_table", 2048, 10), DatabaseSizes.Table("big_table", 4L * 1024 * 1024, null)),
            )
        }
        healthTest { http ->
            val body = http.page("/admin/health/database", http.signIn("bob.test")!!).bodyAsText()
            assertTrue("5.0 MiB" in body)
            assertTrue(body.indexOf("big_table") < body.indexOf("small_table"))
            assertTrue("unknown" in body)
        }
    }

    // Restarting a worker

    @Test
    fun anOperatorRestartsALoop() {
        startWorkers()
        healthTest { http ->
            val (cookie, csrf) = http.operator()
            val response = http.act("/admin/health/workers/restart", cookie, csrf, mapOf("target" to WorkerNames.HANDLE_SWEEP))
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("health-workers", response.headers["HX-Trigger"])
            until { registry[WorkerNames.HANDLE_SWEEP]!!.state == WorkerState.RUNNING }
            assertEquals(1, registry[WorkerNames.HANDLE_SWEEP]!!.manualRestarts)
            assertEquals(0, registry[WorkerNames.HANDLE_SWEEP]!!.restarts)
            assertEquals(listOf(WorkerNames.HANDLE_SWEEP to "ok"), entries().map { it.target to it.outcome })
        }
    }

    @Test
    fun restartsOfDisabledAndUnknownWorkersAndForbiddenPostsAreRefused() {
        startWorkers()
        healthTest { http ->
            val (cookie, csrf) = http.operator()
            val before = registry.snapshot()
            val restart = "/admin/health/workers/restart"
            val disabled = http.act(restart, cookie, csrf, mapOf("target" to WorkerNames.SEGMENT_MATCHER))
            assertEquals(HttpStatusCode.UnprocessableEntity, disabled.status)
            assertEquals(HttpStatusCode.UnprocessableEntity, http.act(restart, cookie, csrf, mapOf("target" to "nope")).status)
            assertEquals(HttpStatusCode.Forbidden, http.act(restart, cookie, null, mapOf("target" to WorkerNames.HANDLE_SWEEP)).status)
            val (viewer, viewerCsrf) = http.operator("bob.test")
            assertEquals(
                HttpStatusCode.Forbidden,
                http.act(restart, viewer, viewerCsrf, mapOf("target" to WorkerNames.HANDLE_SWEEP)).status,
            )
            delay(100)
            assertEquals(before, registry.snapshot())
            assertEquals(
                listOf(
                    WorkerNames.SEGMENT_MATCHER to "refused: not-running",
                    "nope" to "refused: unknown",
                    WorkerNames.HANDLE_SWEEP to "refused: csrf",
                    WorkerNames.HANDLE_SWEEP to "refused: viewer",
                ),
                entries().reversed().map { it.target to it.outcome },
            )
        }
    }

    // Rewinding Jetstream

    @Test
    fun aRewindWithinTheBoundStoresTheCursorAndRestartsJetstream() {
        startWorkers()
        val previous = Instant.parse("2026-11-18T11:50:00Z")
        runBlocking { consumer.storeCursor(us(previous)) }
        healthTest { http ->
            val (cookie, csrf) = http.operator()
            val response =
                http.act("/admin/health/jetstream/rewind", cookie, csrf, mapOf("target" to "jetstream", "at" to "2026-11-18T09:00:00Z"))
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(us(Instant.parse("2026-11-18T09:00:00Z")), consumer.storedCursorUs())
            until { connections.size == 2 }
            assertTrue(connections[1].endsWith("cursor=${us(Instant.parse("2026-11-18T09:00:00Z")) - JetstreamConsumer.REWIND_US}"))
            assertEquals(1, registry[WorkerNames.JETSTREAM]!!.manualRestarts)
            val entry = entries().single()
            assertEquals(
                listOf(alice, "rewind-jetstream", "jetstream", "ok"),
                listOf(entry.admin, entry.action, entry.target, entry.outcome),
            )
            assertEquals(mapOf("at" to "2026-11-18T09:00:00Z", "previous" to "2026-11-18T11:50:00Z"), entry.params)
        }
    }

    @Test
    fun rewindsOutOfBoundsAreRefusedWithTheCursorUnchanged() {
        startWorkers()
        val previous = us(Instant.parse("2026-11-18T11:50:00Z"))
        runBlocking { consumer.storeCursor(previous) }
        healthTest { http ->
            val (cookie, csrf) = http.operator()
            val rewind = "/admin/health/jetstream/rewind"
            val attempts =
                mapOf(
                    "2026-11-15T12:00:00Z" to "refused: too-far",
                    "2026-11-18T11:55:00Z" to "refused: forward",
                    "2026-11-18T13:00:00Z" to "refused: future",
                    "soon" to "refused: invalid",
                )
            for ((at, _) in attempts) {
                val response = http.act(rewind, cookie, csrf, mapOf("target" to "jetstream", "at" to at))
                assertEquals(HttpStatusCode.UnprocessableEntity, response.status, at)
            }
            assertTrue("2026-11-17T12:00:00Z" in http.act(rewind, cookie, csrf, mapOf("at" to "2026-11-15T12:00:00Z")).bodyAsText())
            val (viewer, viewerCsrf) = http.operator("bob.test")
            assertEquals(HttpStatusCode.Forbidden, http.act(rewind, viewer, viewerCsrf, mapOf("at" to "2026-11-18T11:00:00Z")).status)
            assertEquals(previous, consumer.storedCursorUs())
            assertEquals(0, registry[WorkerNames.JETSTREAM]!!.manualRestarts)
            val outcomes = entries().reversed().map { it.outcome }
            assertEquals(attempts.values.toList() + listOf("refused: too-far", "refused: viewer"), outcomes)
        }
    }

    @Test
    fun aRewindWithJetstreamOffIsRefused() {
        jetstreamOn = false
        healthTest { http ->
            val (cookie, csrf) = http.operator()
            val response =
                http.act("/admin/health/jetstream/rewind", cookie, csrf, mapOf("target" to "jetstream", "at" to "2026-11-18T11:00:00Z"))
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(null, consumer.storedCursorUs())
            assertEquals("refused: off", entries().single().outcome)
        }
    }
}
