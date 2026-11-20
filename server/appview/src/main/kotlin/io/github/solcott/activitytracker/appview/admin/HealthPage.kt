package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.ops.RestartRefusal
import io.github.solcott.activitytracker.appview.ops.WorkerKind
import io.github.solcott.activitytracker.appview.ops.WorkerNames
import io.github.solcott.activitytracker.appview.ops.WorkerState
import io.github.solcott.activitytracker.appview.ops.WorkerStatus
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.html.FORM
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

/**
 * How ingest and background work stand (admin/ingest-health): workers, Jetstream, labelers, space syncing and database size, each a
 * fragment the page loads, and the operator actions that restart a worker and rewind Jetstream. It shows identifiers, states, counts,
 * sizes, times and recorded error summaries only.
 */
class HealthPage(private val sources: HealthSources) : AdminPage {
    override val path = PATH
    override val title = "Health"

    override fun privacyProbes(subject: String) = listOf(path) + SECTIONS.map { "$path/$it" }

    override fun AdminRoutes.install() {
        get(path) {
            val admin = admin
            val workers = workersSection(admin)
            val jetstream = jetstreamSection(admin)
            val labelers = labelersSection()
            page(title) {
                p { attributes["id"] = OUTCOME_ID }
                // Memory only, so drawn at once; the database sections load after, so a slow database never delays the rest.
                healthSection("workers", "Workers", load = false, workers)
                healthSection("jetstream", "Jetstream", load = false, jetstream)
                healthSection("labelers", "Labelers", load = false, labelers)
                healthSection("spaces", "Space syncing", load = true) { p { +"Loading…" } }
                healthSection("database", "Database", load = true) { p { +"Loading…" } }
            }
        }
        get("$path/workers") { workersSection(admin).let { fragment(it) } }
        get("$path/jetstream") { jetstreamSection(admin).let { fragment(it) } }
        get("$path/labelers") { labelersSection().let { fragment(it) } }
        get("$path/spaces") { spacesSection().let { fragment(it) } }
        get("$path/database") { databaseSection().let { fragment(it) } }
        action("$path/workers/restart", "restart-worker") { restartWorker() }
        action("$path/jetstream/rewind", "rewind-jetstream") { rewindJetstream() }
    }

    private fun workersSection(admin: Admin): FlowContent.() -> Unit {
        val statuses = sources.supervisor.registry.snapshot()
        return {
            table {
                thead {
                    tr {
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
                            .forEach { th { +it } }
                    }
                }
                tbody {
                    statuses.forEach { w ->
                        tr {
                            td { +w.name }
                            td { +w.kind.name.lowercase() }
                            td { +w.state.name.lowercase().replace('_', ' ') }
                            td { +time(w.startedAt) }
                            td { +time(w.lastSuccessAt) }
                            td { +w.lastError.orEmpty() }
                            td { +time(w.lastErrorAt) }
                            td { +w.iterations.toString() }
                            td { +w.restarts.toString() }
                            td { +w.manualRestarts.toString() }
                            td { +w.disabledReason.orEmpty() }
                            if (admin.isOperator)
                                td { if (w.restartable) actionForm(admin, "workers/restart", w.name) { button { +"Restart" } } }
                        }
                    }
                }
            }
        }
    }

    private suspend fun jetstreamSection(admin: Admin): FlowContent.() -> Unit {
        val consumer = sources.workers.jetstream ?: return { p { +"Jetstream is off: JETSTREAM_URL is not set." } }
        val cursor = consumer.storedCursorUs()?.let(::instantOfUs)
        val lastUs = consumer.lastEventUs.get()
        val now = sources.clock.now()
        val earliest = sources.clock.now() - sources.rewindMax
        return {
            dl {
                dt { +"Stored cursor" }
                dd { +(cursor?.let(::time) ?: "None yet") }
                dt { +"Last event indexed" }
                if (lastUs == 0L) dd { +"No event indexed yet since this process started" }
                else {
                    val last = instantOfUs(lastUs)
                    dd { +time(last) }
                    dt { +"Lag" }
                    dd { +(now - last).inWholeSeconds.seconds.toString() }
                }
            }
            p {
                +"A rewind may reach back to ${time(earliest)} (JETSTREAM_REWIND_MAX ${sources.rewindMax}), "
                +"and never past the stored cursor."
            }
            if (admin.isOperator) {
                actionForm(admin, "jetstream/rewind", "jetstream") {
                    label {
                        +"Rewind to (UTC) "
                        textInput(name = "at") { value = time(now - 1.hours) }
                    }
                    button { +"Rewind" }
                }
            }
        }
    }

    private suspend fun labelersSection(): FlowContent.() -> Unit {
        val subscribers = sources.workers.labelSubscribers
        if (subscribers.isEmpty()) return { p { +"No labeler is configured (LABELER_DID, BSKY_LABELER_DID)." } }
        val rows = subscribers.map { Triple(it, sources.labelCursor(it.did), it.connected.get()) }
        return {
            table {
                thead { tr { listOf("Labeler", "Stored cursor", "Stream", "Cursor last advanced").forEach { th { +it } } } }
                tbody {
                    rows.forEach { (subscriber, cursor, connected) ->
                        tr {
                            td { +subscriber.did }
                            td { +(cursor?.toString() ?: "None yet") }
                            td { +(if (connected) "open" else "closed") }
                            td { +time(subscriber.cursorAdvancedAt.get()) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun spacesSection(): FlowContent.() -> Unit {
        if (!sources.syncing) return { p { +"Space syncing is off: APPVIEW_SIGNING_KEY is not set." } }
        val counts = sources.spaceCounts()
        val failing = sources.failingSpaces()
        return {
            dl {
                dt { +"Synced spaces" }
                dd { +counts.synced.toString() }
                dt { +"Failing" }
                dd { +counts.failing.toString() }
                dt { +"Reported missing by their host" }
                dd { +counts.notFound.toString() }
                dt { +"Due now" }
                dd { +counts.due.toString() }
            }
            if (failing.isEmpty()) p { +"No space is failing." }
            else
                table {
                    thead {
                        tr {
                            listOf("Space", "Authority", "Failures", "Last error", "Error at", "Next attempt", "Last success").forEach {
                                th { +it }
                            }
                        }
                    }
                    tbody {
                        failing.forEach { s ->
                            tr {
                                td { +s.space }
                                td { +s.authorityDid }
                                td { +s.failures.toString() }
                                td { +s.lastError.orEmpty() }
                                td { +time(s.lastErrorAt) }
                                td { +time(s.nextAttemptAt) }
                                td { +time(s.syncedAt) }
                            }
                        }
                    }
                }
        }
    }

    private suspend fun databaseSection(): FlowContent.() -> Unit {
        val sizes = sources.sizes.read() ?: return { p { +"Sizes aren't available on this database." } }
        return {
            p { +"Database: ${bytes(sizes.databaseBytes)}" }
            table {
                thead { tr { listOf("Table", "Size with indexes", "Estimated rows").forEach { th { +it } } } }
                tbody {
                    sizes.tables
                        .sortedByDescending { it.bytes }
                        .forEach { t ->
                            tr {
                                td { +t.name }
                                td { +bytes(t.bytes) }
                                td { +(t.estimatedRows?.toString() ?: "unknown") }
                            }
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
        return refusal ?: ActionResult("Rewound Jetstream to ${time(at)}.")
    }

    /** Why a rewind to [at] is refused, given the stored cursor [previous] (µs): it must be a time within the bound and not past it. */
    private fun rewindRefusal(at: Instant?, previous: Long?): ActionResult? {
        val now = sources.clock.now()
        val earliest = sources.clock.now() - sources.rewindMax
        return when {
            at == null -> ActionResult.refused("invalid", "Give a time such as ${time(now - 1.hours)}.")
            at > now -> ActionResult.refused("future", "That time is in the future.")
            at < earliest -> ActionResult.refused("too-far", "A rewind may reach back to ${time(earliest)} at the earliest.")
            previous != null && usOf(at) > previous ->
                ActionResult.refused("forward", "That's after the stored cursor (${time(instantOfUs(previous))}), which would skip events.")
            else -> null
        }
    }

    companion object {
        val SECTIONS = listOf("workers", "jetstream", "labelers", "spaces", "database")
    }
}

private const val PATH = "health"
private const val OUTCOME_ID = "health-outcome"
private const val HX_TRIGGER = "HX-Trigger"
private const val US_PER_SECOND = 1_000_000L
private const val NANOS_PER_US = 1_000
private const val KIB = 1024.0

/** A section that redraws from its fragment when an action triggers `health-<name>`, and on load when [load] is set. */
private fun FlowContent.healthSection(name: String, heading: String, load: Boolean, body: FlowContent.() -> Unit) {
    section {
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

/** A form posting to the action at [action] under the page, by htmx (outcome shown at the top) or plainly, with the CSRF token. */
private fun FlowContent.actionForm(admin: Admin, action: String, target: String, body: FORM.() -> Unit) {
    form(action = "/admin/$PATH/$action", method = FormMethod.post) {
        attributes["hx-post"] = "/admin/$PATH/$action"
        attributes["hx-target"] = "#$OUTCOME_ID"
        hiddenInput(name = AdminSite.CSRF_FIELD) { value = admin.csrfToken }
        hiddenInput(name = "target") { value = target }
        body()
    }
}

private val WorkerStatus.restartable: Boolean
    get() = kind != WorkerKind.ONE_OFF && state !in setOf(WorkerState.DISABLED, WorkerState.STOPPED, WorkerState.FINISHED)

private fun time(at: Instant?): String = at?.let { Instant.fromEpochSeconds(it.epochSeconds).toString() } ?: "—"

private fun instantOfUs(us: Long) = Instant.fromEpochSeconds(us / US_PER_SECOND, (us % US_PER_SECOND) * NANOS_PER_US)

private fun usOf(at: Instant) = at.epochSeconds * US_PER_SECOND + at.nanosecondsOfSecond / NANOS_PER_US

private fun bytes(n: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = n.toDouble()
    var unit = 0
    while (value >= KIB && unit < units.lastIndex) {
        value /= KIB
        unit++
    }
    return if (unit == 0) "$n B" else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
}
