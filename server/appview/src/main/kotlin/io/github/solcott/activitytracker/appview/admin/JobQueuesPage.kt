package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.segments.Box
import io.github.solcott.activitytracker.appview.segments.FailedJob
import io.github.solcott.activitytracker.appview.segments.KindSummary
import io.github.solcott.activitytracker.appview.segments.QueuePage
import io.github.solcott.activitytracker.appview.segments.Requeued
import io.github.solcott.activitytracker.appview.segments.RetryingJob
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentQueue
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.jetbrains.exposed.v1.core.VarCharColumnType

/**
 * The segment job queue (admin/job-queues): each kind's depth, the retrying jobs and the ones that gave up, and for operators the actions
 * that retry, requeue, drop and bulk requeue them, all through [SegmentQueue]. Jobs are shown by kind and subject identifier only.
 */
@Suppress("TooManyFunctions") // The page, its fragments, previews and actions, and the tables they draw.
class JobQueuesPage(private val queue: SegmentQueue, private val clock: Clock = Clock.System) : AdminPage {
    override val path = "queues"
    override val title = "Queues"

    /** Held while a bulk requeue runs, so a second is refused rather than racing it. Exposed for tests. */
    internal val bulk = Mutex()

    override fun privacyProbes(subject: String) =
        listOf(path) + SegmentJobs.KINDS.flatMap { listOf("$path/retrying?kind=$it", "$path/failed?kind=$it") }

    override fun AdminRoutes.install() {
        installPages()
        installPreviews()
        installJobActions()
        installBulkActions()
    }

    private fun AdminRoutes.installPages() {
        get(path) {
            val summary = queue.summary()
            val retrying = queue.retrying(null, null)
            val failed = queue.failed(null, null)
            page(title) {
                summaryTable(summary)
                section {
                    h2 { +"Retrying" }
                    kindFilter("retrying")
                    div {
                        attributes["id"] = "retrying"
                        retryingList(null, retrying, admin.isOperator)
                    }
                }
                section {
                    h2 { +"Gave up" }
                    kindFilter("failed")
                    div {
                        attributes["id"] = "failed"
                        failedList(null, failed, admin.isOperator)
                    }
                }
                if (admin.isOperator) bulkForms(admin)
            }
        }
        get("$path/retrying") {
            val kind = kindParam() ?: return@get fragment { p("error") { +"Unknown kind." } }
            val page = queue.retrying(kind.value, call.request.queryParameters["cursor"])
            fragment { retryingList(kind.value, page, admin.isOperator) }
        }
        get("$path/failed") {
            val kind = kindParam() ?: return@get fragment { p("error") { +"Unknown kind." } }
            val page = queue.failed(kind.value, call.request.queryParameters["cursor"])
            fragment { failedList(kind.value, page, admin.isOperator) }
        }
    }

    /** What a bulk requeue would queue, counted with its own predicate. */
    private fun AdminRoutes.installPreviews() {
        get("$path/preview/rematch") {
            val sport = sportOf(call.request.queryParameters["sport"])
            val message = sport.error ?: "This queues ${queue.rematchPreview(sport.value)} track jobs."
            fragment { p("outcome") { +message } }
        }
        get("$path/preview/elevation") {
            val query = call.request.queryParameters
            val box = boxOf(query["box"])
            val message =
                box.error
                    ?: queue.elevationPreview(box.value, query["missingOnly"] != null)?.let { "This queues $it elevation jobs." }
                    ?: NO_TERRAIN
            fragment { p("outcome") { +message } }
        }
        get("$path/preview/requeue-kind") {
            val kind = call.request.queryParameters["target"]
            val count = queue.summary().firstOrNull { it.kind == kind }?.failed
            fragment {
                p("outcome") {
                    +(count?.let { "$kind has $it failed jobs; this requeues up to ${SegmentJobs.REQUEUE_LIMIT}." } ?: UNKNOWN_KIND)
                }
            }
        }
    }

    private fun AdminRoutes.installJobActions() {
        action("$path/retry", "queues.retry") {
            job { kind, subject ->
                if (queue.retryNow(kind, subject)) ActionResult("Due now.", detail = "1 due now")
                else ActionResult.refused("not-found", "That job is no longer queued.")
            }
        }
        action("$path/requeue", "queues.requeue") {
            job { kind, subject ->
                when (queue.requeue(kind, subject)) {
                    Requeued.REQUEUED -> ActionResult("Requeued.", detail = "1 requeued")
                    Requeued.NOT_FOUND -> ActionResult.refused("not-found", "That failed job no longer exists.")
                    Requeued.NO_TERRAIN -> ActionResult.refused("no-terrain", NO_TERRAIN)
                }
            }
        }
        action("$path/drop", "queues.drop") {
            job { kind, subject ->
                val dropped =
                    when (params["list"]) {
                        "retrying" -> queue.dropRetrying(kind, subject)
                        "failed" -> queue.dropFailed(kind, subject)
                        else -> return@job ActionResult.refused("invalid", "Say which list to drop it from.")
                    }
                if (dropped) ActionResult("Dropped.", detail = "1 dropped")
                else ActionResult.refused("not-found", "That job no longer exists.")
            }
        }
    }

    private fun AdminRoutes.installBulkActions() {
        action("$path/requeue-kind", "queues.requeueKind") {
            val kind = target?.takeIf { it in SegmentJobs.KINDS } ?: return@action ActionResult.refused("unknown-kind", UNKNOWN_KIND)
            bulk {
                val result = queue.requeueFailed(kind) ?: return@bulk ActionResult.refused("no-terrain", NO_TERRAIN)
                ActionResult(
                    "${result.requeued} requeued, ${result.remaining} remain.",
                    detail = "${result.requeued} requeued, ${result.remaining} remain",
                )
            }
        }
        action("$path/rematch", "queues.rematch") {
            val sport = sportOf(params["sport"])
            sport.error?.let {
                return@action ActionResult.refused("invalid-sport", it)
            }
            bulk {
                val queued = queue.rematch(sport.value)
                ActionResult("$queued track jobs queued.", detail = "$queued queued")
            }
        }
        action("$path/elevation", "queues.elevation") {
            val box = boxOf(params["box"])
            box.error?.let {
                return@action ActionResult.refused("invalid-box", it)
            }
            if (!queue.terrain) return@action ActionResult.refused("no-terrain", NO_TERRAIN)
            bulk {
                val queued =
                    queue.recomputeElevation(box.value, params["missingOnly"] != null)
                        ?: return@bulk ActionResult.refused("no-terrain", NO_TERRAIN)
                ActionResult("$queued elevation jobs queued.", detail = "$queued queued")
            }
        }
    }

    /** The `kind` query parameter: null when it names no kind, else a holder of the kind or of null for none given. */
    private fun AdminGet.kindParam(): KindFilter? {
        val kind = call.request.queryParameters["kind"]?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            kind == null -> KindFilter(null)
            kind in SegmentJobs.KINDS -> KindFilter(kind)
            else -> null
        }
    }

    private class KindFilter(val value: String?)

    /** A single-job action: [target] is the subject and `kind` its kind, both checked before [body] touches the queue. */
    @Suppress("ReturnCount") // An unknown kind, a bad subject, or the job's own outcome.
    private suspend fun ActionCall.job(body: suspend (kind: String, subject: String) -> ActionResult): ActionResult {
        val kind = params["kind"]?.takeIf { it in SegmentJobs.KINDS } ?: return ActionResult.refused("unknown-kind", UNKNOWN_KIND)
        val subject = target?.takeIf { it.length <= MAX_SUBJECT } ?: return ActionResult.refused("invalid", "Name a job's subject.")
        return body(kind, subject)
    }

    /** A bulk action: refused unless confirmed, and while another runs. */
    @Suppress("ReturnCount") // Unconfirmed, already running, or the requeue's own outcome.
    private suspend fun ActionCall.bulk(body: suspend () -> ActionResult): ActionResult {
        if (params["confirm"] == null) return ActionResult.refused("unconfirmed", "Confirm the requeue first.")
        if (!bulk.tryLock()) return ActionResult.refused("running", "Another bulk requeue is running.", HttpStatusCode.Conflict)
        return try {
            body()
        } finally {
            bulk.unlock()
        }
    }

    private fun FlowContent.summaryTable(summary: List<KindSummary>) {
        val now = clock.now().toEpochMilliseconds()
        table {
            thead { tr { listOf("Kind", "Queued", "Due now", "Oldest due", "Retrying", "Gave up").forEach { th { +it } } } }
            tbody {
                summary.forEach { s ->
                    tr {
                        td { +s.kind }
                        td { +s.queued.toString() }
                        td { +s.due.toString() }
                        td { +(s.oldestDueAt?.let { ageOf(now - it) } ?: "–") }
                        td { +s.retrying.toString() }
                        td { +s.failed.toString() }
                    }
                }
            }
        }
    }

    private fun FlowContent.kindFilter(list: String) {
        form(action = "/admin/$path/$list", method = FormMethod.get) {
            attributes["hx-get"] = "/admin/$path/$list"
            attributes["hx-target"] = "#$list"
            label {
                +"Kind "
                select {
                    name = "kind"
                    option {
                        value = ""
                        +"All"
                    }
                    SegmentJobs.KINDS.forEach { option { +it } }
                }
            }
            button { +"Filter" }
        }
    }

    private fun FlowContent.retryingList(kind: String?, page: QueuePage<RetryingJob>, operator: Boolean) {
        if (page.items.isEmpty()) return p { +"No retrying jobs." }
        table {
            thead {
                tr {
                    (listOf("Kind", "Subject", "Attempts", "Last error", "Failing since", "Next due") +
                            if (operator) listOf("") else emptyList())
                        .forEach { th { +it } }
                }
            }
            tbody {
                page.items.forEach { job ->
                    tr {
                        td { +job.kind }
                        td { +job.subject }
                        td { +job.attempts.toString() }
                        td { +job.lastError.orEmpty() }
                        td { +timeOf(job.firstFailedAt) }
                        td { +timeOf(job.dueAt) }
                        if (operator)
                            td {
                                jobButton("retry", "Retry now", job.kind, job.subject)
                                jobButton("drop", "Drop", job.kind, job.subject, list = "retrying")
                            }
                    }
                }
            }
        }
        olderLink("retrying", kind, page.cursor)
    }

    private fun FlowContent.failedList(kind: String?, page: QueuePage<FailedJob>, operator: Boolean) {
        if (page.items.isEmpty()) return p { +"No jobs gave up." }
        table {
            thead {
                tr {
                    (listOf("Kind", "Subject", "Attempts", "Last error", "First failure", "Gave up") +
                            if (operator) listOf("") else emptyList())
                        .forEach { th { +it } }
                }
            }
            tbody {
                page.items.forEach { job ->
                    tr {
                        td { +job.kind }
                        td { +job.subject }
                        td { +job.attempts.toString() }
                        td { +job.lastError }
                        td { +timeOf(job.firstFailedAt) }
                        td { +timeOf(job.failedAt) }
                        if (operator)
                            td {
                                jobButton("requeue", "Requeue", job.kind, job.subject)
                                jobButton("drop", "Drop", job.kind, job.subject, list = "failed")
                            }
                    }
                }
            }
        }
        olderLink("failed", kind, page.cursor)
    }

    /** A button posting [action] for one job; htmx puts the outcome in its place. */
    private fun FlowContent.jobButton(action: String, label: String, kind: String, subject: String, list: String? = null) {
        form(action = "/admin/$path/$action", method = FormMethod.post, classes = "inline") {
            attributes["hx-post"] = "/admin/$path/$action"
            attributes["hx-target"] = "this"
            attributes["hx-swap"] = "outerHTML"
            hiddenInput(name = "target") { value = subject }
            hiddenInput(name = "kind") { value = kind }
            list?.let { hiddenInput(name = "list") { value = it } }
            button { +label }
        }
    }

    private fun FlowContent.olderLink(list: String, kind: String?, cursor: String?) {
        cursor ?: return
        val href =
            "/admin/$path/$list?" + listOfNotNull(kind?.let { "kind=$it" }, "cursor=${cursor.encodeURLParameter()}").joinToString("&")
        p {
            a(href = href) {
                attributes["hx-get"] = href
                attributes["hx-target"] = "#$list"
                +"Older"
            }
        }
    }

    private fun FlowContent.bulkForms(admin: Admin) {
        section {
            h2 { +"Bulk requeue" }
            p { +"Each queues work for the matcher behind what is already due. Preview the count, then confirm." }
            bulkForm(admin, "rematch", "Rematch tracks", target = SegmentJobs.TRACK) {
                label {
                    +"Only sport "
                    textInput(name = "sport") { placeholder = "all" }
                }
            }
            if (!queue.terrain) p { +NO_TERRAIN }
            bulkForm(admin, "elevation", "Recompute elevation", target = SegmentJobs.ELEVATION) {
                label {
                    +"Box (west,south,east,north) "
                    textInput(name = "box") { placeholder = "all segments" }
                }
                label {
                    checkBoxInput(name = "missingOnly")
                    +" Only missing"
                }
            }
            bulkForm(admin, "requeue-kind", "Requeue a kind's failed jobs", target = null) {
                label {
                    +"Kind "
                    select {
                        name = "target"
                        SegmentJobs.KINDS.forEach { option { +it } }
                    }
                }
            }
        }
    }

    /** A bulk action's form, with its [fields], a preview of what it would queue and the confirmation it needs. */
    private fun FlowContent.bulkForm(admin: Admin, action: String, label: String, target: String?, fields: FlowContent.() -> Unit) {
        val outcome = "bulk-$action"
        form(action = "/admin/$path/$action", method = FormMethod.post) {
            attributes["hx-post"] = "/admin/$path/$action"
            attributes["hx-target"] = "#$outcome"
            hiddenInput(name = AdminSite.CSRF_FIELD) { value = admin.csrfToken }
            target?.let { hiddenInput(name = "target") { value = it } }
            p {
                +"$label: "
                fields()
            }
            p {
                button(type = ButtonType.button) {
                    attributes["hx-get"] = "/admin/$path/preview/$action"
                    attributes["hx-include"] = "closest form"
                    attributes["hx-target"] = "#$outcome"
                    +"Preview"
                }
                +" "
                label {
                    checkBoxInput(name = "confirm")
                    +" Confirm"
                }
                +" "
                button { +label }
            }
            span { attributes["id"] = outcome }
        }
    }

    private fun timeOf(epochMillis: Long) = Instant.fromEpochMilliseconds(epochMillis).toString()

    /** An age to the second, as `1h 2m 3s`. */
    private fun ageOf(millis: Long) = millis.coerceAtLeast(0).milliseconds.inWholeSeconds.seconds.toString()

    /** A form field read as [value], or the [error] to refuse it with. */
    private class Parsed<T>(val value: T?, val error: String? = null)

    private companion object {
        const val NO_TERRAIN = "Terrain tiles aren't configured."
        const val UNKNOWN_KIND = "Unknown job kind."

        /** A job subject's column width. */
        val MAX_SUBJECT = (SegmentMatchJobs.subjectUri.columnType as VarCharColumnType).colLength

        private val SPORT = Regex("[A-Za-z]{1,64}")

        /** A sport id, matched exactly; none given is every sport. */
        fun sportOf(text: String?): Parsed<String> {
            val sport = text?.trim()?.takeIf { it.isNotEmpty() } ?: return Parsed(null)
            return if (SPORT.matches(sport)) Parsed(sport) else Parsed(null, "That isn't a sport id.")
        }

        /** A box of degrees; none given is every segment. */
        fun boxOf(text: String?): Parsed<Box> {
            val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return Parsed(null)
            return Box.parse(raw)?.takeIf { it.isValid }?.let { Parsed(it) }
                ?: Parsed(null, "The box is invalid: give west,south,east,north degrees, west before east.")
        }
    }
}
