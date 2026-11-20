package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AccountsPage
import io.github.solcott.activitytracker.appview.admin.AuditPage
import io.github.solcott.activitytracker.appview.admin.HealthPage
import io.github.solcott.activitytracker.appview.admin.HealthSnapshot
import io.github.solcott.activitytracker.appview.admin.HealthSources.SpaceCounts
import io.github.solcott.activitytracker.appview.admin.ImporterRun
import io.github.solcott.activitytracker.appview.admin.JetstreamState
import io.github.solcott.activitytracker.appview.admin.JobQueuesPage
import io.github.solcott.activitytracker.appview.admin.SegmentsPage
import io.github.solcott.activitytracker.appview.admin.Tile
import io.github.solcott.activitytracker.appview.admin.TileStatus
import io.github.solcott.activitytracker.appview.admin.at
import io.github.solcott.activitytracker.appview.admin.bytesText
import io.github.solcott.activitytracker.appview.admin.durationText
import io.github.solcott.activitytracker.appview.admin.relative
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerState
import io.github.solcott.activitytracker.appview.ops.WorkerStatus
import io.github.solcott.activitytracker.appview.reindex.RecentRunCounts
import io.github.solcott.activitytracker.appview.segments.KindSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.html.div
import kotlinx.html.stream.createHTML

/** The view kit's formatters and every page's tile rules (admin/admin-site and each page's summary requirement), without a database. */
class AdminTilesTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    // The view kit

    @Test
    fun durationsShowTheirTwoLargestUnits() {
        assertEquals("45 s", durationText(45.seconds))
        assertEquals("12 min", durationText(12.minutes + 30.seconds))
        assertEquals("3 h 41 min", durationText(3.hours + 41.minutes + 11.seconds))
        assertEquals("3 h", durationText(3.hours))
        assertEquals("2 d 4 h", durationText(2.days + 4.hours + 5.minutes))
        assertEquals("0 s", durationText(-(5.seconds)))
    }

    @Test
    fun timesAreRelativeWithTheExactTimeOnTheElement() {
        assertEquals("just now", relative(now - 2.seconds, now))
        assertEquals("3 min ago", relative(now - 3.minutes, now))
        assertEquals("in 2 h", relative(now + 2.hours, now))
        val html = createHTML().div { at(now - 3.minutes, now) }
        assertTrue("""datetime="2026-11-18T11:57:00Z"""" in html && """title="2026-11-18T11:57:00Z"""" in html, html)
        assertTrue(">3 min ago<" in html, html)
        assertEquals("<div>—</div>", createHTML(prettyPrint = false).div { at(null, now) })
    }

    @Test
    fun sizesUseBinaryUnits() {
        assertEquals("512 B", bytesText(512))
        assertEquals("1.5 KiB", bytesText(1536))
        assertEquals("5.0 MiB", bytesText(5L * 1024 * 1024))
        assertEquals("1.0 GiB", bytesText(1024L * 1024 * 1024))
    }

    // Health (admin/ingest-health)

    private fun worker(name: String, state: WorkerState, startedAt: Instant? = now) =
        WorkerStatus(name, WorkerKind.LOOP, state, startedAt = startedAt)

    private fun health(
        workers: List<WorkerStatus> = listOf(worker("a", WorkerState.RUNNING)),
        jetstream: JetstreamState? = JetstreamState(us(now - 1.seconds), now.toEpochMilliseconds(), WorkerState.RUNNING),
        labelers: List<Boolean> = listOf(true),
        spaces: SpaceCounts? = SpaceCounts(4, 0, 0, 0),
        bytes: Long? = 1024,
    ) = HealthPage.tilesOf(HealthSnapshot(workers, jetstream, labelers, spaces, bytes), now).associateBy { it.label }

    private fun us(at: Instant) = at.epochSeconds * 1_000_000 + at.nanosecondsOfSecond / 1_000

    private fun jetstream(lag: Duration, received: Instant = now, worker: WorkerState = WorkerState.RUNNING) =
        JetstreamState(us(received - lag), received.toEpochMilliseconds(), worker)

    @Test
    fun aHealthyAppViewIsAllOkButTheDatabaseSize() {
        val tiles = health()
        assertEquals(
            mapOf(
                "Workers not running" to TileStatus.Ok,
                "Jetstream catch-up lag" to TileStatus.Ok,
                "Labeler streams open" to TileStatus.Ok,
                "Failing spaces" to TileStatus.Ok,
                "Database size" to null,
            ),
            tiles.mapValues { it.value.status },
        )
        assertEquals("1.0 KiB", tiles.getValue("Database size").value)
    }

    @Test
    fun workersTileThresholds() {
        val restarting = health(workers = listOf(worker("a", WorkerState.RUNNING), worker("b", WorkerState.RESTARTING)))
        assertEquals(TileStatus.Bad, restarting.getValue("Workers not running").status)
        assertEquals("1", restarting.getValue("Workers not running").value)
        assertEquals(TileStatus.Warn, health(workers = listOf(worker("b", WorkerState.BACKING_OFF))).getValue("Workers not running").status)
        val slow = worker("c", WorkerState.STARTING, startedAt = now - 2.minutes)
        assertEquals(TileStatus.Warn, health(workers = listOf(slow)).getValue("Workers not running").status)
        val fresh = worker("c", WorkerState.STARTING, startedAt = now - 10.seconds)
        assertEquals(TileStatus.Ok, health(workers = listOf(fresh)).getValue("Workers not running").status)
        val disabled = health(workers = listOf(worker("a", WorkerState.RUNNING), worker("s", WorkerState.DISABLED, null)))
        assertEquals(TileStatus.Ok, disabled.getValue("Workers not running").status)
        assertEquals("0", disabled.getValue("Workers not running").value)
    }

    @Test
    fun jetstreamTileThresholds() {
        fun status(j: JetstreamState?) = health(jetstream = j).getValue("Jetstream catch-up lag")
        assertEquals(TileStatus.Warn, status(jetstream(5.minutes)).status)
        assertEquals("5 min", status(jetstream(5.minutes)).value)
        assertEquals(TileStatus.Bad, status(jetstream(20.minutes)).status)
        // Three quiet hours after an event that arrived a second late: keeping up.
        assertEquals(TileStatus.Ok, status(jetstream(1.seconds, received = now - 3.hours)).status)
        assertEquals(TileStatus.Bad, status(jetstream(1.seconds, worker = WorkerState.RESTARTING)).status)
        assertEquals(TileStatus.Warn, status(jetstream(1.seconds, worker = WorkerState.BACKING_OFF)).status)
        // Clock skew never makes the lag negative.
        assertEquals("0 s", status(JetstreamState(us(now + 3.seconds), now.toEpochMilliseconds(), WorkerState.RUNNING)).value)
        val off = status(null)
        assertEquals("Off" to null, off.value to off.status)
        val first = status(JetstreamState(0, 0, WorkerState.RUNNING))
        assertEquals("No event yet" to TileStatus.Ok, first.value to first.status)
        assertEquals(TileStatus.Warn, status(JetstreamState(0, 0, WorkerState.BACKING_OFF)).status)
    }

    @Test
    fun labelersTileThresholds() {
        val tile = health(labelers = listOf(true, false)).getValue("Labeler streams open")
        assertEquals("1 of 2" to TileStatus.Bad, tile.value to tile.status)
        assertNull(health(labelers = emptyList())["Labeler streams open"], "no labeler configured, no tile")
    }

    @Test
    fun failingSpacesTileThresholds() {
        val missing = health(spaces = SpaceCounts(6, 3, 3, 0, failingOther = 0)).getValue("Failing spaces")
        assertEquals("3" to TileStatus.Warn, missing.value to missing.status)
        assertEquals(TileStatus.Bad, health(spaces = SpaceCounts(6, 1, 0, 0, failingOther = 1)).getValue("Failing spaces").status)
        val off = health(spaces = null).getValue("Failing spaces")
        assertEquals("Syncing off" to null, off.value to off.status)
        assertNull(health(bytes = null)["Database size"], "sizes unavailable, no tile")
    }

    // Queues (admin/job-queues)

    private fun kind(kind: String, oldestDueAt: Duration? = null, retrying: Long = 0, failed: Long = 0) =
        KindSummary(kind, queued = 1, due = 1, oldestDueAt = oldestDueAt?.let { (now - it).toEpochMilliseconds() }, retrying, failed)

    private fun queues(vararg kinds: KindSummary) = JobQueuesPage.tilesOf(kinds.toList(), now).associateBy { it.label }

    @Test
    fun queueTileThresholds() {
        val building = queues(kind("track", oldestDueAt = 20.minutes), kind("group"))
        assertEquals("20 min" to TileStatus.Warn, building.getValue("Oldest due job").let { it.value to it.status })
        assertEquals(TileStatus.Ok, building.getValue("Retrying jobs").status)
        assertEquals(TileStatus.Ok, building.getValue("Jobs that gave up").status)
        assertEquals(TileStatus.Bad, queues(kind("track", oldestDueAt = 2.hours)).getValue("Oldest due job").status)
        assertEquals(TileStatus.Ok, queues(kind("track", oldestDueAt = 1.minutes)).getValue("Oldest due job").status)
        val gaveUp = queues(kind("group", failed = 1)).getValue("Jobs that gave up")
        assertEquals("1" to TileStatus.Bad, gaveUp.value to gaveUp.status)
        assertTrue(gaveUp.href.endsWith("#gave-up"))
        assertEquals(TileStatus.Warn, queues(kind("track", retrying = 2)).getValue("Retrying jobs").status)
        val idle = queues(kind("track"))
        assertEquals(listOf(TileStatus.Ok, TileStatus.Ok, TileStatus.Ok), idle.values.map(Tile::status))
        assertEquals("None due", idle.getValue("Oldest due job").value)
    }

    // Segments (admin/segment-tools)

    @Test
    fun segmentTileRules() {
        val run =
            ImporterRun(
                "us-vermont",
                "r1",
                candidates = 3,
                riddenUnpublished = 1,
                published = 0,
                lastStagedAt = (now - 2.hours).toEpochMilliseconds(),
            )
        val tiles = SegmentsPage.tilesOf(1, listOf(run), now).associateBy { it.label }
        assertEquals("1" to TileStatus.Warn, tiles.getValue("Pins on missing segments").let { it.value to it.status })
        assertEquals("2 h ago" to null, tiles.getValue("Latest import").let { it.value to it.status })
        assertEquals(TileStatus.Ok, SegmentsPage.tilesOf(0, emptyList(), now).first().status)
        assertEquals("Nothing staged", SegmentsPage.tilesOf(0, emptyList(), now)[1].value)
        assertEquals(listOf("Pins on missing segments"), SegmentsPage.tilesOf(0, null, now).map { it.label }, "no importer, no tile")
        assertTrue(tiles.values.none { "1" == it.value && it.label.contains("rid", ignoreCase = true) }, "no rider count")
    }

    // Accounts (admin/account-tools)

    @Test
    fun accountTileRules() {
        val tiles = AccountsPage.tilesOf(RecentRunCounts(inFlight = 2, failed = 1)).associateBy { it.label }
        assertEquals("2" to null, tiles.getValue("Reindex runs in flight").let { it.value to it.status })
        assertEquals("1" to TileStatus.Warn, tiles.getValue("Reindex runs failed (24 h)").let { it.value to it.status })
        assertEquals(TileStatus.Ok, AccountsPage.tilesOf(RecentRunCounts(0, 0))[1].status)
    }

    // Audit log outcomes

    @Test
    fun auditOutcomesMapToStatuses() {
        assertEquals(TileStatus.Ok, AuditPage.outcomeStatus("ok: 3 queued"))
        assertEquals(TileStatus.Warn, AuditPage.outcomeStatus("refused: viewer"))
        assertEquals(TileStatus.Bad, AuditPage.outcomeStatus("failed: IOException"))
        assertNull(AuditPage.outcomeStatus("something else"))
    }
}
