package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.ops.RestartRefusal
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerNames
import io.github.solcott.activitytracker.appview.ops.WorkerState
import io.github.solcott.activitytracker.appview.ops.WorkerStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.tr

/**
 * How ingest and background work stand (admin/ingest-health): workers, Jetstream, labelers, space syncing and database size, each a
 * fragment the page loads, and the operator actions that restart a worker and rewind Jetstream. It shows identifiers, states, counts,
 * sizes, times and recorded error summaries only. Its summary's tiles follow the fixed rules in [tilesOf].
 */
@Suppress("TooManyFunctions") // The page, its five sections, its two actions and its summary.
class HealthPage(private val sources: HealthSources) : AdminPage {
    override val path = PATH
    override val title = "Health"

    override fun privacyProbes(subject: String) = listOf(path) + SECTIONS.map { "$path/$it" }

    override fun AdminRoutes.install() {
        get(path) {
            val admin = admin
            val now = now
            val workers = workersSection(admin, now)
            val jetstream = jetstreamSection(admin, now)
            val labelers = labelersSection(now)
            page(title) {
                outcome(OUTCOME_ID)
                // Memory only, so drawn at once; the database sections load after, so a slow database never delays the rest.
                healthSection("workers", "Workers", load = false, workers)
                healthSection("jetstream", "Jetstream", load = false, jetstream)
                healthSection("labelers", "Labelers", load = false, labelers)
                healthSection("spaces", "Space syncing", load = true) { p("muted") { +"Loading…" } }
                healthSection("database", "Database", load = true) { p("muted") { +"Loading…" } }
            }
        }
        get("$path/workers") { workersSection(admin, now).let { fragment(it) } }
        get("$path/jetstream") { jetstreamSection(admin, now).let { fragment(it) } }
        get("$path/labelers") { labelersSection(now).let { fragment(it) } }
        get("$path/spaces") { spacesSection(now).let { fragment(it) } }
        get("$path/database") { databaseSection().let { fragment(it) } }
        action("$path/workers/restart", "restart-worker") { restartWorker() }
        action("$path/jetstream/rewind", "rewind-jetstream") { rewindJetstream() }
    }

    override suspend fun summary(now: Instant): List<Tile> = tilesOf(snapshot(), now)

    /** What the summary reads: memory for workers, Jetstream and labelers, one query for spaces, and the cached sizes. */
    private suspend fun snapshot(): HealthSnapshot {
        val workers = sources.supervisor.registry.snapshot()
        val consumer = sources.workers.jetstream
        return HealthSnapshot(
            workers = workers,
            jetstream =
                consumer?.let {
                    JetstreamState(
                        lastEventUs = it.lastEventUs.get(),
                        receivedAtMs = it.lastReceivedAtMs.get(),
                        worker = workers.firstOrNull { w -> w.name == WorkerNames.JETSTREAM }?.state,
                    )
                },
            labelersOpen = sources.workers.labelSubscribers.map { it.connected.get() },
            spaces = if (sources.syncing) sources.spaceCounts() else null,
            databaseBytes = sources.sizes.read()?.databaseBytes,
        )
    }

    private fun workersSection(admin: Admin, now: Instant): FlowContent.() -> Unit {
        val statuses = sources.supervisor.registry.snapshot()
        val headers =
            listOf(
                    "Worker",
                    "Kind",
                    "State",
                    "Run started",
                    "Last success",
                    "Last error",
                    "Error at",
                    "Rounds / events",
                    "Crash restarts",
                    "Operator restarts",
                    "Off because",
                )
                .plus(listOfNotNull("".takeIf { admin.isOperator }))
        return {
            dataTable(headers, numeric = setOf("Rounds / events", "Crash restarts", "Operator restarts")) {
                statuses.forEach { w ->
                    tr {
                        td { +w.name }
                        td("muted") { +w.kind.name.lowercase().replace('_', ' ') }
                        td { badge(w.state.name.lowercase().replace('_', ' '), workerStatus(w, now)) }
                        timeCell(w.startedAt, now)
                        timeCell(w.lastSuccessAt, now)
                        td { +w.lastError.orEmpty() }
                        timeCell(w.lastErrorAt, now)
                        num(w.iterations)
                        num(w.restarts)
                        num(w.manualRestarts)
                        td { +w.disabledReason.orEmpty() }
                        if (admin.isOperator)
                            td {
                                if (w.restartable)
                                    actionForm(
                                        "/admin/$PATH/workers/restart",
                                        "Restart",
                                        admin.csrfToken,
                                        OUTCOME_ID,
                                        target = w.name,
                                        inline = true,
                                    )
                            }
                    }
                }
            }
        }
    }

    private suspend fun jetstreamSection(admin: Admin, now: Instant): FlowContent.() -> Unit {
        val consumer = sources.workers.jetstream ?: return { p { +"Jetstream is off: JETSTREAM_URL is not set." } }
        val cursor = consumer.storedCursorUs()?.let(::instantOfUs)
        val lastUs = consumer.lastEventUs.get()
        val receivedAt = consumer.lastReceivedAtMs.get().takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
        val worker = sources.supervisor.registry[WorkerNames.JETSTREAM]?.state
        val earliest = now - sources.rewindMax
        return {
            stats {
                stat("Stored cursor") { if (cursor == null) +"None yet" else at(cursor, now) }
                if (lastUs == 0L) stat("Last event indexed", "No event indexed yet")
                else {
                    val last = instantOfUs(lastUs)
                    stat("Last event indexed") { at(last, now) }
                    stat("Received") { at(receivedAt, now) }
                    val lag = catchUpLag(last, receivedAt)
                    stat("Catch-up lag", durationText(lag), jetstreamStatus(lag, worker))
                }
            }
            p("muted") {
                +"Catch-up lag is how far behind its own time the last event was when it arrived, so a quiet network doesn't add to it. "
                +"A rewind may reach back to ${exact(earliest)} (JETSTREAM_REWIND_MAX ${sources.rewindMax}), "
                +"and never past the stored cursor."
            }
            if (admin.isOperator) {
                actionForm(
                    "/admin/$PATH/jetstream/rewind",
                    "Rewind",
                    admin.csrfToken,
                    OUTCOME_ID,
                    target = "jetstream",
                    confirm = "Rewind Jetstream? Events after that time are indexed again.",
                ) {
                    label {
                        +"Rewind to (UTC) "
                        textInput(name = "at") { value = exact(now - 1.hours) }
                    }
                }
            }
        }
    }

    private suspend fun labelersSection(now: Instant): FlowContent.() -> Unit {
        val subscribers = sources.workers.labelSubscribers
        if (subscribers.isEmpty()) return { p { +"No labeler is configured (LABELER_DID, BSKY_LABELER_DID)." } }
        val rows = subscribers.map { Triple(it, sources.labelCursor(it.did), it.connected.get()) }
        return {
            dataTable(listOf("Labeler", "Stored cursor", "Stream", "Cursor last advanced"), numeric = setOf("Stored cursor")) {
                rows.forEach { (subscriber, cursor, connected) ->
                    tr {
                        td { +subscriber.did }
                        td("num") { +(cursor?.toString() ?: "None yet") }
                        td { if (connected) badge("open", TileStatus.Ok) else badge("closed", TileStatus.Bad) }
                        timeCell(subscriber.cursorAdvancedAt.get(), now)
                    }
                }
            }
        }
    }

    private suspend fun spacesSection(now: Instant): FlowContent.() -> Unit {
        if (!sources.syncing) return { p { +"Space syncing is off: APPVIEW_SIGNING_KEY is not set." } }
        val counts = sources.spaceCounts()
        val failing = sources.failingSpaces()
        return {
            stats {
                stat("Synced spaces", counts.synced.toString())
                stat("Failing", counts.failing.toString(), spacesStatus(counts))
                stat("Reported missing by their host", counts.notFound.toString())
                stat("Due now", counts.due.toString())
            }
            if (failing.isEmpty()) p { +"No space is failing." }
            else
                dataTable(
                    listOf("Space", "Authority", "Failures", "Last error", "Error at", "Next attempt", "Last success"),
                    numeric = setOf("Failures"),
                ) {
                    failing.forEach { s ->
                        tr {
                            td { +s.space }
                            td { +s.authorityDid }
                            num(s.failures)
                            td { +s.lastError.orEmpty() }
                            timeCell(s.lastErrorAt, now)
                            timeCell(s.nextAttemptAt, now)
                            timeCell(s.syncedAt, now)
                        }
                    }
                }
        }
    }

    private suspend fun databaseSection(): FlowContent.() -> Unit {
        val sizes = sources.sizes.read() ?: return { p { +"Sizes aren't available on this database." } }
        return {
            stats { stat("Database", bytesText(sizes.databaseBytes)) }
            dataTable(listOf("Table", "Size with indexes", "Estimated rows"), numeric = setOf("Size with indexes", "Estimated rows")) {
                sizes.tables
                    .sortedByDescending { it.bytes }
                    .forEach { t ->
                        tr {
                            td { +t.name }
                            num(bytesText(t.bytes))
                            num(t.estimatedRows?.toString() ?: "unknown")
                        }
                    }
            }
        }
    }

    private suspend fun ActionCall.restartWorker(): ActionResult {
        val name = target
        val refusal = if (name == null) RestartRefusal.UNKNOWN else sources.supervisor.restart(name)
        if (refusal == null) call.response.headers.append(HX_TRIGGER, "health-workers")
        return refusal?.let { refusedRestart(it, name.orEmpty()) } ?: ActionResult("Restarted $name.")
    }

    private suspend fun ActionCall.rewindJetstream(): ActionResult {
        val consumer = sources.workers.jetstream ?: return ActionResult.refused("off", "Jetstream is off.")
        val previous = consumer.storedCursorUs()
        previous?.let { audit("previous", instantOfUs(it).toString()) }
        val at = params["at"]?.trim()?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val refusal =
            rewindRefusal(at, previous)
                ?: sources.supervisor
                    .restart(WorkerNames.JETSTREAM) { consumer.storeCursor(usOf(checkNotNull(at))) }
                    ?.let { refusedRestart(it, WorkerNames.JETSTREAM) }
        if (refusal == null) call.response.headers.append(HX_TRIGGER, "health-jetstream, health-workers")
        return refusal ?: ActionResult("Rewound Jetstream to ${exact(checkNotNull(at))}.")
    }

    /** Why a rewind to [at] is refused, given the stored cursor [previous] (µs): it must be a time within the bound and not past it. */
    private fun rewindRefusal(at: Instant?, previous: Long?): ActionResult? {
        val now = sources.clock.now()
        val earliest = sources.clock.now() - sources.rewindMax
        return when {
            at == null -> ActionResult.refused("invalid", "Give a time such as ${exact(now - 1.hours)}.")
            at > now -> ActionResult.refused("future", "That time is in the future.")
            at < earliest -> ActionResult.refused("too-far", "A rewind may reach back to ${exact(earliest)} at the earliest.")
            previous != null && usOf(at) > previous ->
                ActionResult.refused(
                    "forward",
                    "That's after the stored cursor (${exact(instantOfUs(previous))}), which would skip events.",
                )
            else -> null
        }
    }

    companion object {
        val SECTIONS = listOf("workers", "jetstream", "labelers", "spaces", "database")

        /** A worker starting for longer than this is `warn` (admin/ingest-health, "Workers tile thresholds"). */
        val WORKER_STARTING_WARN = 1.minutes

        /** Catch-up lag past these is `warn` and `bad` (admin/ingest-health, "Jetstream tile thresholds"). */
        val JETSTREAM_WARN = 1.minutes
        val JETSTREAM_BAD = 15.minutes

        /** The Health page's tiles under the fixed rules of admin/ingest-health. */
        fun tilesOf(s: HealthSnapshot, now: Instant): List<Tile> = buildList {
            val down = s.workers.filter { it.state !in QUIET_STATES }
            val worst = s.workers.mapNotNull { workerStatus(it, now) }.filter { it != TileStatus.Ok }.maxOrNull()
            add(Tile("Workers not running", down.size.toString(), worst ?: TileStatus.Ok, "/admin/$PATH#workers"))
            add(jetstreamTile(s.jetstream))
            if (s.labelersOpen.isNotEmpty()) {
                val open = s.labelersOpen.count { it }
                val status = if (open == s.labelersOpen.size) TileStatus.Ok else TileStatus.Bad
                add(Tile("Labeler streams open", "$open of ${s.labelersOpen.size}", status, "/admin/$PATH#labelers"))
            }
            add(
                s.spaces?.let { Tile("Failing spaces", it.failing.toString(), spacesStatus(it), "/admin/$PATH#spaces") }
                    ?: Tile("Failing spaces", "Syncing off", null, "/admin/$PATH#spaces"),
            )
            s.databaseBytes?.let { add(Tile("Database size", bytesText(it), null, "/admin/$PATH#database")) }
        }

        private fun jetstreamTile(j: JetstreamState?): Tile {
            val (value, status) =
                when {
                    j == null -> "Off" to null
                    // Before the first event there is no lag: the worker's state alone decides.
                    j.lastEventUs == 0L -> "No event yet" to jetstreamStatus(Duration.ZERO, j.worker)
                    else -> {
                        val receivedAt = j.receivedAtMs.takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
                        val lag = catchUpLag(instantOfUs(j.lastEventUs), receivedAt)
                        durationText(lag) to jetstreamStatus(lag, j.worker)
                    }
                }
            return Tile("Jetstream catch-up lag", value, status, "/admin/$PATH#jetstream")
        }

        /** How far the event stamped [event] was behind its own time when it arrived at [receivedAt]; never negative, for clock skew. */
        internal fun catchUpLag(event: Instant, receivedAt: Instant?): Duration =
            receivedAt?.let { (it - event).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO

        private fun jetstreamStatus(lag: Duration, worker: WorkerState?): TileStatus =
            when {
                lag > JETSTREAM_BAD || worker == WorkerState.RESTARTING -> TileStatus.Bad
                lag > JETSTREAM_WARN || worker == WorkerState.BACKING_OFF -> TileStatus.Warn
                else -> TileStatus.Ok
            }

        private fun spacesStatus(c: HealthSources.SpaceCounts): TileStatus =
            when {
                c.failingOther > 0 -> TileStatus.Bad
                c.failing > 0 -> TileStatus.Warn
                else -> TileStatus.Ok
            }

        /** A worker's own status: `bad` restarting, `warn` backing off or slow to start, `ok` running, none otherwise. */
        internal fun workerStatus(w: WorkerStatus, now: Instant): TileStatus? =
            when (w.state) {
                WorkerState.RESTARTING -> TileStatus.Bad
                WorkerState.BACKING_OFF -> TileStatus.Warn
                WorkerState.STARTING -> if (w.startedAt != null && now - w.startedAt > WORKER_STARTING_WARN) TileStatus.Warn else null
                WorkerState.RUNNING -> TileStatus.Ok
                else -> null
            }

        /** States that aren't a worker being down: running, done, or switched off on purpose. */
        private val QUIET_STATES = setOf(WorkerState.RUNNING, WorkerState.FINISHED, WorkerState.DISABLED, WorkerState.STOPPED)
    }
}

/** What the Health page's summary is drawn from. [jetstream] and [spaces] are null when switched off. */
data class HealthSnapshot(
    val workers: List<WorkerStatus>,
    val jetstream: JetstreamState?,
    val labelersOpen: List<Boolean>,
    val spaces: HealthSources.SpaceCounts?,
    val databaseBytes: Long?,
)

/**
 * The Jetstream consumer as the summary sees it: the last event's time (µs, 0 before the first), when it arrived (ms), its worker's state.
 */
data class JetstreamState(val lastEventUs: Long, val receivedAtMs: Long, val worker: WorkerState?)

private const val PATH = "health"
private const val OUTCOME_ID = "health-outcome"
private const val HX_TRIGGER = "HX-Trigger"
private const val US_PER_SECOND = 1_000_000L
private const val NANOS_PER_US = 1_000

/** A section that redraws from its fragment when an action triggers `health-<name>`, and on load when [load] is set. */
private fun FlowContent.healthSection(name: String, heading: String, load: Boolean, body: FlowContent.() -> Unit) {
    section {
        attributes["id"] = name
        h2 { +heading }
        div {
            attributes["id"] = "health-$name"
            attributes["hx-get"] = "/admin/$PATH/$name"
            attributes["hx-trigger"] = listOfNotNull("load".takeIf { load }, "health-$name from:body").joinToString(", ")
            body()
        }
    }
}

private fun refusedRestart(refusal: RestartRefusal, name: String) =
    ActionResult.refused(refusal.name.lowercase().replace('_', '-'), "${refusal.message}: $name")

private val WorkerStatus.restartable: Boolean
    get() = kind != WorkerKind.ONE_OFF && state !in setOf(WorkerState.DISABLED, WorkerState.STOPPED, WorkerState.FINISHED)

private fun instantOfUs(us: Long) = Instant.fromEpochSeconds(us / US_PER_SECOND, (us % US_PER_SECOND) * NANOS_PER_US)

private fun usOf(at: Instant) = at.epochSeconds * US_PER_SECOND + at.nanosecondsOfSecond / NANOS_PER_US
