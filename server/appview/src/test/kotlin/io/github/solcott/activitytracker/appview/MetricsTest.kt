package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.labels.LabelVerifier
import io.github.solcott.activitytracker.appview.ops.AppViewMetrics
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.metricsRoutes
import io.github.solcott.activitytracker.appview.segments.JobOutcome
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert

/** The AppView's Prometheus metrics (dev/worker-telemetry): what they cover, how often they read the database, and what they never name. */
class MetricsTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Instant.parse("2026-11-18T12:00:00Z"))
    private val time = TestTimeSource()
    private val metrics = AppViewMetrics(db, clock = clock, timeSource = time)

    private fun queue(kind: String, subject: String) = runBlocking {
        db.tx {
            SegmentMatchJobs.insert {
                it[SegmentMatchJobs.kind] = kind
                it[subjectUri] = subject
                it[enqueuedAt] = clock.now.toEpochMilliseconds()
            }
        }
    }

    private fun gaveUp(kind: String, subject: String) = runBlocking {
        db.tx {
            SegmentJobFailures.insert {
                it[SegmentJobFailures.kind] = kind
                it[subjectUri] = subject
                it[attempts] = 8
                it[lastError] = "IOException"
                it[firstFailedAt] = 0
                it[failedAt] = 0
            }
        }
    }

    /** The value of the sample [line] starts with, from a scrape. */
    private fun String.sample(line: String): Double? =
        lineSequence().firstOrNull { it.startsWith("$line ") }?.substringAfterLast(' ')?.toDouble()

    private fun scrape() = runBlocking { metrics.scrape() }

    @Test
    fun queueDepthAndFailedJobsByKind() {
        repeat(3) { queue(SegmentJobs.TRACK, "at://did:plc:alice/app.activitytracker.feed.segmentTrack/3k$it") }
        queue(SegmentJobs.ELEVATION, "at://did:plc:bob/app.activitytracker.feed.segment/3ks")
        gaveUp(SegmentJobs.GROUP, "at://did:plc:alice/app.activitytracker.feed.segmentTrack/3kz")
        val text = scrape()
        assertEquals(3.0, text.sample("""appview_segment_jobs_queued{kind="track"}"""))
        assertEquals(3.0, text.sample("""appview_segment_jobs_due{kind="track"}"""))
        assertEquals(1.0, text.sample("""appview_segment_jobs_queued{kind="elevation"}"""))
        assertEquals(0.0, text.sample("""appview_segment_jobs_queued{kind="group"}"""))
        assertEquals(1.0, text.sample("""appview_segment_job_failures{kind="group"}"""))
        assertFalse("did:plc:alice" in text, "no subject is ever named")
    }

    @Test
    fun theDatabaseIsReadAtMostEveryFifteenSeconds() {
        queue(SegmentJobs.TRACK, "a")
        assertEquals(1.0, scrape().sample("""appview_segment_jobs_queued{kind="track"}"""))
        queue(SegmentJobs.TRACK, "b")
        time += 14.seconds
        assertEquals(1.0, scrape().sample("""appview_segment_jobs_queued{kind="track"}"""), "still the counts read before")
        time += 2.seconds
        assertEquals(2.0, scrape().sample("""appview_segment_jobs_queued{kind="track"}"""))
    }

    @Test
    fun jobOutcomesAreCountedByKind() {
        metrics.segmentJobOutcomes.outcome(SegmentJobs.TRACK, JobOutcome.DONE)
        metrics.segmentJobOutcomes.outcome(SegmentJobs.TRACK, JobOutcome.DONE)
        metrics.segmentJobOutcomes.outcome(SegmentJobs.TIERS, JobOutcome.DEAD)
        val text = scrape()
        assertEquals(2.0, text.sample("""appview_segment_jobs_total{kind="track",outcome="done"}"""))
        assertEquals(1.0, text.sample("""appview_segment_jobs_total{kind="tiers",outcome="dead"}"""))
    }

    @Test
    fun workersStatusRestartsAndRounds() {
        val workers = WorkerRegistry(clock)
        val matcher = workers.handle("segment-matcher", WorkerKind.LOOP)
        workers.disabled("space-syncer", WorkerKind.LOOP, "APPVIEW_SIGNING_KEY is not set")
        metrics.bindWorkers(workers)
        matcher.success()
        matcher.success()
        val text = scrape()
        assertEquals(1.0, text.sample("""appview_worker_up{worker="segment-matcher"}"""))
        assertEquals(2.0, text.sample("""appview_worker_iterations_total{worker="segment-matcher"}"""))
        assertEquals(0.0, text.sample("""appview_worker_restarts_total{worker="segment-matcher"}"""))
        assertEquals(
            clock.now.toEpochMilliseconds() / 1_000.0,
            text.sample("""appview_worker_last_success_timestamp_seconds{worker="segment-matcher"}"""),
        )
        assertFalse("space-syncer" in text, "a disabled worker has no meters")
    }

    @Test
    fun aRestartedWorkerCountsItsRestart() {
        val workers = WorkerRegistry(clock)
        workers.handle("segment-matcher", WorkerKind.LOOP)
        metrics.bindWorkers(workers)
        workers.crashed("segment-matcher", IOException("boom"))
        val text = scrape()
        assertEquals(1.0, text.sample("""appview_worker_restarts_total{worker="segment-matcher"}"""))
        assertEquals(0.0, text.sample("""appview_worker_up{worker="segment-matcher"}"""))
    }

    @Test
    fun jetstreamLagIsSecondsSinceTheLastEvent() {
        val http = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        val jetstream = JetstreamConsumer(http, "ws://jetstream.test", db, Indexer(db, { "x.test" }))
        metrics.bindJetstream(jetstream)
        jetstream.lastEventUs.set((clock.now.toEpochMilliseconds() - 120_000) * 1_000)
        assertEquals(120.0, scrape().sample("appview_jetstream_lag_seconds"))
    }

    @Test
    fun eachLabelersCursorAgeAndConnection() {
        val labeler = "did:plc:labeler"
        val subscriber =
            LabelSubscriber(
                AppViewConfig.Labeler(labeler, "http://labeler.test"),
                LabelStore(db, labeler, null),
                LabelVerifier { _, _ -> null },
                endpoint = { null },
                frames = { _, _, _ -> },
                clock = clock,
            )
        metrics.bindLabelers(listOf(subscriber))
        clock.now += 90.seconds
        val text = scrape()
        assertEquals(90.0, text.sample("""appview_label_cursor_age_seconds{labeler="$labeler"}"""))
        assertEquals(0.0, text.sample("""appview_labeler_connected{labeler="$labeler"}"""))
    }

    @Test
    fun requestsAreTimedByRouteTemplateAndNeverByPath() = testApplication {
        val config = AppViewConfig.fromEnv(emptyMap())
        application { appView(config, db, ServiceAuthVerifier(config.serviceDid, keys = { null }), metrics = metrics) }
        client.get("/img/media/did:plc:alice/bafkreialicephoto")
        repeat(20) { client.get("/scanner/probe-$it") }
        assertEquals(HttpStatusCode.NotFound, client.get("/metrics").status, "never on the public port")
        val text = scrape()
        assertFalse("did:plc:alice" in text)
        assertFalse("bafkreialicephoto" in text)
        assertFalse("probe-" in text)
        assertTrue(text.lineSequence().any { it.startsWith("http_server_requests_seconds_count{") && "{did}" in it }, text)
        val unmatched =
            text.lineSequence().filter { it.startsWith("http_server_requests_seconds_count{") && "route=\"n/a\"" in it }.toList()
        assertEquals(1, unmatched.size, "every unmatched path under one route: $unmatched")
        assertEquals(21.0, unmatched.single().substringAfterLast(' ').toDouble())
    }

    @Test
    fun theMetricsRouteServesPrometheusText() = testApplication {
        application { routing { metricsRoutes(metrics) } }
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers["Content-Type"].orEmpty().startsWith("text/plain"))
        assertTrue("appview_segment_jobs_queued" in response.bodyAsText())
    }
}
