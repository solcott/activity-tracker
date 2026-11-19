package io.github.solcott.activitytracker.appview.ops

import io.github.solcott.activitytracker.appview.db.ActivityStreamFetchFailures
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.BskyBlockBackfillFailures
import io.github.solcott.activitytracker.appview.db.SegmentJobFailures
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.labels.LabelSubscriber
import io.github.solcott.activitytracker.appview.segments.SegmentJobOutcomes
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.sync.SyncOutcomes
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The AppView's Prometheus metrics (dev/worker-telemetry), served only on the internal metrics port. Every tag is bounded: a job kind, an
 * outcome, a worker name, a configured labeler's DID or a route template; never an athlete's DID or handle, an AT-URI, a CID or a raw path.
 * Counts read from the database are refreshed at most every [refreshEvery], before a scrape, so scraping often costs nothing more.
 */
class AppViewMetrics(
    private val db: Database,
    val registry: PrometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
    private val clock: Clock = Clock.System,
    private val refreshEvery: Duration = 15.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /** Counts each segment job attempt by kind and outcome. */
    val segmentJobOutcomes = SegmentJobOutcomes { kind, outcome ->
        registry.counter("appview.segment.jobs", "kind", kind, "outcome", outcome.tag).increment()
    }

    /** Counts each space sync by outcome. */
    val syncOutcomes = SyncOutcomes { outcome -> registry.counter("appview.space.syncs", "outcome", outcome.tag).increment() }

    @Volatile private var counts = Counts()
    private var refreshedAt: TimeMark? = null
    private val refreshing = Mutex()

    /** Database-backed counts, as of the last refresh. */
    private data class Counts(
        val queued: Map<String, Long> = emptyMap(),
        val due: Map<String, Long> = emptyMap(),
        val failedJobs: Map<String, Long> = emptyMap(),
        val spaces: Long = 0,
        val failingSpaces: Long = 0,
        val fetchFailures: Map<String, Long> = emptyMap(),
        val backfillPending: Long = 0,
        val backfillBackingOff: Long = 0,
    )

    init {
        for (kind in SegmentJobs.KINDS) {
            gauge("appview.segment.jobs.queued", "Segment jobs waiting, by kind", "kind", kind) { counts.queued[kind] ?: 0 }
            gauge("appview.segment.jobs.due", "Segment jobs due now, by kind", "kind", kind) { counts.due[kind] ?: 0 }
            gauge("appview.segment.job.failures", "Segment jobs that gave up, by kind", "kind", kind) { counts.failedJobs[kind] ?: 0 }
        }
        gauge("appview.space.sync.spaces", "Spaces being synced") { counts.spaces }
        gauge("appview.space.sync.failing", "Spaces whose last sync failed") { counts.failingSpaces }
        for (kind in listOf(ActivityStreamFetchFailures.KIND_CONTENT, ActivityStreamFetchFailures.KIND_UNAVAILABLE)) {
            gauge("appview.stream.fetch.failures", "Remembered stream fetch failures, by kind", "kind", kind) {
                counts.fetchFailures[kind] ?: 0
            }
        }
        gauge("appview.bsky.block.backfill.pending", "Actors whose Bluesky blocks aren't backfilled yet") { counts.backfillPending }
        gauge("appview.bsky.block.backfill.backing.off", "Actors whose block backfill failed and is backing off") {
            counts.backfillBackingOff
        }
    }

    /** Each worker in [workers] as it is when called: its up state, restarts, rounds and last success. Disabled workers are left out. */
    fun bindWorkers(workers: WorkerRegistry) {
        for (status in workers.snapshot().filter { it.state != WorkerState.DISABLED }) {
            val name = status.name
            fun current() = workers[name]
            Gauge.builder("appview.worker.up") { if (current()?.state in UP) 1.0 else 0.0 }
                .description("1 while the worker runs, backs off after a failed round, or finished; 0 while restarting or stopped")
                .tag("worker", name)
                .register(registry)
            FunctionCounter.builder("appview.worker.restarts", workers) { current()?.restarts?.toDouble() ?: 0.0 }
                .tag("worker", name)
                .register(registry)
            FunctionCounter.builder("appview.worker.iterations", workers) { current()?.iterations?.toDouble() ?: 0.0 }
                .description("Completed rounds (a loop) or events (a stream)")
                .tag("worker", name)
                .register(registry)
            Gauge.builder("appview.worker.last.success.timestamp") {
                    current()?.lastSuccessAt?.toEpochMilliseconds()?.div(MILLIS_PER_SECOND) ?: Double.NaN
                }
                .baseUnit("seconds")
                .tag("worker", name)
                .register(registry)
        }
    }

    /** Jetstream's lag: now less the time of the last event it indexed. */
    fun bindJetstream(jetstream: JetstreamConsumer) {
        Gauge.builder("appview.jetstream.lag") {
                val last = jetstream.lastEventUs.get()
                if (last == 0L) Double.NaN else (clock.now().toEpochMilliseconds() * MICROS_PER_MILLI - last) / MICROS_PER_SECOND
            }
            .description("Seconds between now and the last Jetstream event indexed")
            .baseUnit("seconds")
            .register(registry)
    }

    /** Each labeler's cursor age and whether its stream is open. */
    fun bindLabelers(subscribers: List<LabelSubscriber>) {
        for (subscriber in subscribers) {
            Gauge.builder("appview.label.cursor.age") {
                    (clock.now() - subscriber.cursorAdvancedAt.get()).inWholeMilliseconds / MILLIS_PER_SECOND
                }
                .description("Seconds since the labeler's cursor last advanced (a quiet labeler ages too)")
                .baseUnit("seconds")
                .tag("labeler", subscriber.did)
                .register(registry)
            Gauge.builder("appview.labeler.connected") { if (subscriber.connected.get()) 1.0 else 0.0 }
                .tag("labeler", subscriber.did)
                .register(registry)
        }
    }

    /** The metrics in Prometheus text format, with the database counts refreshed first if they're older than [refreshEvery]. */
    suspend fun scrape(): String {
        refreshIfStale()
        return registry.scrape()
    }

    /** Reads the database counts again unless they were read within [refreshEvery]. Exposed for tests. */
    suspend fun refreshIfStale() = refreshing.withLock {
        if (refreshedAt?.let { it.elapsedNow() < refreshEvery } == true) return@withLock
        counts = readCounts()
        refreshedAt = timeSource.markNow()
    }

    private suspend fun readCounts(): Counts {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            Counts(
                queued = countBy(SegmentMatchJobs, SegmentMatchJobs.kind),
                due = countBy(SegmentMatchJobs, SegmentMatchJobs.kind) { SegmentMatchJobs.enqueuedAt lessEq now },
                failedJobs = countBy(SegmentJobFailures, SegmentJobFailures.kind),
                spaces = SpaceSync.selectAll().count(),
                failingSpaces = SpaceSync.selectAll().where { SpaceSync.failures greater 0 }.count(),
                fetchFailures = countBy(ActivityStreamFetchFailures, ActivityStreamFetchFailures.kind),
                backfillPending = Actors.selectAll().where { Actors.bskyBlocksSyncedAt.isNull() }.count(),
                backfillBackingOff = BskyBlockBackfillFailures.selectAll().count(),
            )
        }
    }

    private fun JdbcTransaction.countBy(
        table: Table,
        key: Column<String>,
        where: (() -> org.jetbrains.exposed.v1.core.Op<Boolean>)? = null,
    ): Map<String, Long> {
        val count = key.count()
        val query = table.select(key, count)
        where?.let { query.where(it()) }
        return query.groupBy(key).associate { it[key] to it[count] }
    }

    private fun gauge(name: String, description: String, vararg tags: String, value: () -> Long) {
        Gauge.builder(name) { value().toDouble() }.description(description).tags(*tags).register(registry)
    }

    private companion object {
        val UP = setOf(WorkerState.STARTING, WorkerState.RUNNING, WorkerState.BACKING_OFF, WorkerState.FINISHED)
        const val MILLIS_PER_SECOND = 1_000.0
        const val MICROS_PER_MILLI = 1_000L
        const val MICROS_PER_SECOND = 1_000_000.0
    }
}
