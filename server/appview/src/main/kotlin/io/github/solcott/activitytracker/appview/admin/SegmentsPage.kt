package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.appview.segments.PinResult
import io.github.solcott.activitytracker.appview.segments.Rematch
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentPasses
import io.github.solcott.activitytracker.appview.segments.SegmentPin
import io.github.solcott.activitytracker.appview.segments.SegmentPinKind
import io.github.solcott.activitytracker.appview.segments.SegmentPins
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.http.encodeURLParameter
import kotlin.time.Instant
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * The Segments page (admin/segment-tools): segment search, importer runs, pins on missing segments, each segment's page, and the operator
 * actions on one segment. Reads go through [queries]; pins only through [pins] (`Indexer.segmentPins`); passes through [passes], the code
 * the hourly passes run.
 */
@Suppress("TooManyFunctions") // The page's routes, its sections and its actions, each drawn by one small function.
class SegmentsPage(
    private val queries: SegmentAdminQueries,
    private val passes: SegmentPasses,
    private val pins: SegmentPins,
    private val jobs: SegmentJobs,
) : AdminPage {
    override val path = "segments"
    override val title = "Segments"

    override fun privacyProbes(subject: String): List<String> {
        val q = subject.encodeURLParameter()
        return listOf(path, "$path?uri=$q", "$path?name=$q", "$path/segment?uri=$q")
    }

    override fun AdminRoutes.install() {
        get(path) {
            val query = call.request.queryParameters
            fun param(name: String) = query[name]?.trim()?.takeIf { it.isNotEmpty() }
            val search =
                SegmentSearch(
                    uri = param("uri"),
                    name = param("name"),
                    status = param("status"),
                    sport = param("sport"),
                    kind = param("kind"),
                    region = param("region"),
                )
            val results = queries.search(search, param("cursor"))
            val runs = queries.importerRuns()
            val orphans = pins.orphanedPins(PINS_PAGE + 1, param("pinsAfter"))
            page(title) {
                searchForm(search)
                results(search, results)
                importer(runs)
                orphanedPins(admin, orphans)
            }
        }
        get("$path/segment") {
            val uri = call.request.queryParameters["uri"]?.trim().orEmpty()
            val detail = uri.takeIf { it.isNotEmpty() }?.let { queries.detail(it) }
            page("Segment") {
                if (detail == null) p { +"No indexed segment at ${uri.ifEmpty { "(none given)" }}." } else segmentPage(admin, detail)
            }
        }
        installActions()
        installPins()
    }

    private fun AdminRoutes.installActions() {
        action("$path/status-pass", "segments.status-pass") {
            val uri = target ?: return@action notFound(null)
            val change = passes.updateStatusOf(uri) ?: return@action notFound(uri)
            ActionResult("Status ${change.before} → ${change.after}.")
        }
        action("$path/naming-pass", "segments.naming-pass") {
            val uri = target ?: return@action notFound(null)
            if (queries.detail(uri) == null) return@action notFound(uri)
            val change = passes.updateNameOf(uri)
            val name = queries.detail(uri)?.currentName ?: "withheld"
            val changed =
                if (change.changed.isEmpty()) "No proposal changed state."
                else change.changed.entries.joinToString("; ", postfix = ".") { (proposal, state) -> "$proposal $state" }
            ActionResult("$changed Current name: $name.")
        }
        action("$path/elevation", "segments.elevation") {
            val uri = target ?: return@action notFound(null)
            if (!passes.recomputeElevation(uri)) return@action notFound(uri)
            val terrain = if (jobs.terrainConfigured) "terrain queued" else "terrain not configured"
            ActionResult("Effort elevation recomputed; $terrain.")
        }
        action("$path/rematch", "segments.rematch") {
            val uri = target ?: return@action notFound(null)
            when (val result = passes.rematch(uri)) {
                Rematch.Queued -> ActionResult("Match job queued.")
                Rematch.NotFound -> notFound(uri)
                is Rematch.NotLive -> ActionResult("Not live (${result.status}); nothing queued.", refused = "not-live")
            }
        }
    }

    private fun AdminRoutes.installPins() {
        action("$path/pin-status", "segments.pin-status") {
            val uri = target ?: return@action notFound(null)
            val status = params["status"]?.trim().orEmpty()
            if (status == SegmentStatus.DUPLICATE && params["confirm"] != "yes") {
                return@action ActionResult(
                    "A duplicate pin removes this segment's efforts: tick the confirmation to pin it.",
                    refused = "unconfirmed",
                )
            }
            val duplicateOf = params["duplicateOf"]?.trim()?.takeIf { it.isNotEmpty() }
            outcomeOf(pins.pinStatus(uri, status, duplicateOf, admin.did, note()), "Status pinned $status.")
        }
        action("$path/unpin-status", "segments.unpin-status") {
            val uri = target ?: return@action notFound(null)
            outcomeOf(pins.unpinStatus(uri), "Status unpinned.")
        }
        action("$path/pin-name", "segments.pin-name") {
            val uri = target ?: return@action notFound(null)
            val name = params["name"]?.trim()?.takeIf { it.isNotEmpty() }
            val proposal = params["proposal"]?.trim()?.takeIf { it.isNotEmpty() }
            outcomeOf(pins.pinName(uri, name, proposal, admin.did, note()), "Name pinned.")
        }
        action("$path/unpin-name", "segments.unpin-name") {
            val uri = target ?: return@action notFound(null)
            outcomeOf(pins.unpinName(uri), "Name unpinned.")
        }
    }

    private fun ActionCall.note(): String? = params["note"]?.trim()?.takeIf { it.isNotEmpty() }

    private fun notFound(uri: String?) =
        ActionResult(uri?.let { "No indexed segment at $it." } ?: "No segment given.", refused = "not-found")

    private fun outcomeOf(result: PinResult, done: String): ActionResult =
        when (result) {
            PinResult.Pinned,
            PinResult.Unpinned -> ActionResult(done)
            PinResult.NoPin -> ActionResult("There was no pin to remove.", refused = "no-pin")
            is PinResult.NotFound -> ActionResult("No indexed segment at ${result.uri}.", refused = "not-found")
            is PinResult.Invalid -> ActionResult("Refused: ${result.reason}.", refused = "invalid")
        }

    // ---------------------------------------------------------------- The search page

    private fun FlowContent.searchForm(search: SegmentSearch) {
        form(action = "/admin/$path", method = FormMethod.get) {
            field("AT-URI or rkey", "uri", search.uri)
            field("Name", "name", search.name)
            label {
                +"Status "
                select {
                    name = "status"
                    option {
                        value = ""
                        +"any"
                    }
                    STATUSES.forEach { s ->
                        option {
                            value = s
                            selected = search.status == s
                            +s
                        }
                    }
                }
            }
            field("Sport group", "sport", search.sport)
            field("Kind", "kind", search.kind)
            field("Importer region", "region", search.region)
            button { +"Search" }
        }
        p { +"Region search finds segments whose candidates are still staged; search by URI finds the rest." }
    }

    private fun FlowContent.results(search: SegmentSearch, results: SegmentResults) {
        if (results.rows.isEmpty()) {
            p { +"No segment found." }
            return
        }
        table {
            thead { tr { listOf("Segment", "Name", "Status", "Sport", "Kind", "Author").forEach { th { +it } } } }
            tbody {
                results.rows.forEach { row ->
                    tr {
                        td { segmentLink(row.uri) }
                        td { +(row.name ?: WITHHELD) }
                        td { +row.status }
                        td { +row.sport }
                        td { +row.kind }
                        td { +row.authorDid }
                    }
                }
            }
        }
        results.cursor?.let { cursor ->
            val keep =
                listOf("uri" to search.uri, "name" to search.name, "status" to search.status, "sport" to search.sport)
                    .plus(listOf("kind" to search.kind, "region" to search.region))
                    .mapNotNull { (k, v) -> v?.let { "$k=${it.encodeURLParameter()}" } }
            p { a(href = "/admin/$path?" + (keep + "cursor=${cursor.encodeURLParameter()}").joinToString("&")) { +"More" } }
        }
    }

    private fun FlowContent.importer(runs: List<ImporterRun>?) {
        h2 { +"Importer" }
        when {
            runs == null -> p { +"No segment importer is configured." }
            runs.isEmpty() -> p { +"No candidates are staged." }
            else ->
                table {
                    thead {
                        tr {
                            listOf("Region", "Revision", "Candidates", "Ridden, unpublished", "Published", "Last staged").forEach {
                                th { +it }
                            }
                        }
                    }
                    tbody {
                        runs.forEach { run ->
                            tr {
                                td { +run.region }
                                td { +run.revision }
                                td { +"${run.candidates}" }
                                td { +"${run.riddenUnpublished}" }
                                td { +"${run.published}" }
                                td { +time(run.lastStagedAt) }
                            }
                        }
                    }
                }
        }
    }

    private fun FlowContent.orphanedPins(admin: Admin, fetched: List<SegmentPin>) {
        h2 { +"Pins on missing segments" }
        // A page ends between segments, since the cursor is a segment URI and a segment has up to two pins.
        val more = fetched.size > PINS_PAGE
        val page =
            if (more) fetched.take(PINS_PAGE).let { p -> p.dropLastWhile { it.segmentUri == p.last().segmentUri }.ifEmpty { p } }
            else fetched
        if (page.isEmpty()) {
            p { +"None." }
            return
        }
        pinsTable(admin, page, showSegment = true)
        if (more) p { a(href = "/admin/$path?pinsAfter=${page.last().segmentUri.encodeURLParameter()}") { +"More pins" } }
    }

    // ---------------------------------------------------------------- A segment's page

    private fun FlowContent.segmentPage(admin: Admin, d: SegmentDetail) {
        table {
            tbody {
                row("URI", d.uri)
                row("Author", d.authorDid)
                row("Sport group", d.sport)
                row("Kind", d.kind)
                row("Distance", "${d.distanceM} m")
                row("First indexed", time(d.firstIndexedAt))
                row("Indexed", time(d.indexedAt))
                row("Original name", d.originalName ?: WITHHELD)
                row("Current name", d.currentName ?: WITHHELD)
                row("Status", d.status)
                row("Duplicate of", d.duplicateOf ?: "—")
                row("Segment group", d.groupUri)
                row("Efforts", "${d.efforts} from ${d.athletes} athletes, every visibility; ${d.activeAthletes} athletes make it active")
                row("Queued jobs", d.queuedJobs.joinToString().ifEmpty { "none" })
            }
        }
        p { a(href = "/admin/audit?target=${d.uri.encodeURLParameter()}") { +"Audit log for this segment" } }
        elevation(d)
        h2 { +"Pins" }
        if (d.pins.isEmpty()) p { +"None." } else pinsTable(admin, d.pins, showSegment = false)
        proposals(d)
        if (admin.isOperator) actions(admin, d)
    }

    private fun FlowContent.elevation(d: SegmentDetail) {
        val e = d.elevation
        h2 { +"Elevation" }
        table {
            tbody {
                row("Served", e.served?.let { "+${it.gain} m / -${it.loss} m from ${it.source}" } ?: "none")
                row(
                    "Terrain",
                    if (e.terrainGainM == null) "not covered or not sampled"
                    else "+${e.terrainGainM} m / -${e.terrainLossM} m, max grade ${e.terrainMaxGrade?.let { "${it / TENTHS}%" } ?: "—"}",
                )
                row("Athletes with effort elevation", "${e.effortAthletes} (served from ${d.activeAthletes})")
                row("From efforts", e.effortGainM?.let { "+$it m / -${e.effortLossM} m" } ?: "not served")
            }
        }
    }

    private fun FlowContent.proposals(d: SegmentDetail) {
        h2 { +"Name proposals" }
        if (d.proposals.isEmpty()) {
            p { +"None." }
            return
        }
        table {
            thead { tr { listOf("Proposal", "Name", "Proposer", "State", "Opened", "Closed", "Up", "Down").forEach { th { +it } } } }
            tbody {
                d.proposals.forEach { proposal ->
                    tr {
                        td { +proposal.uri }
                        td { +(proposal.name ?: WITHHELD) }
                        td { +proposal.proposerDid }
                        td { +proposal.state }
                        td { +time(proposal.openedAt) }
                        td { +(proposal.closedAt?.let(::time) ?: "—") }
                        td { +"${proposal.up}" }
                        td { +"${proposal.down}" }
                    }
                }
            }
        }
    }

    private fun FlowContent.pinsTable(admin: Admin, list: List<SegmentPin>, showSegment: Boolean) {
        table {
            thead {
                tr {
                    if (showSegment) th { +"Segment" }
                    listOf("Pin", "Value", "In effect", "By", "When", "Note").forEach { th { +it } }
                    if (admin.isOperator) th {}
                }
            }
            tbody {
                list.forEach { pin ->
                    tr {
                        if (showSegment) td { +pin.segmentUri }
                        td { +pin.kind }
                        td { +pinValue(pin) }
                        td { +(if (pin.inEffect) "yes" else "no") }
                        td { +pin.pinnedBy }
                        td { +time(pin.pinnedAt) }
                        td { +pin.note.orEmpty() }
                        if (admin.isOperator) {
                            td {
                                val action = if (pin.kind == SegmentPinKind.STATUS) "unpin-status" else "unpin-name"
                                actionForm(admin, action, pin.segmentUri, "Unpin")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pinValue(pin: SegmentPin): String =
        when {
            pin.kind == SegmentPinKind.STATUS && pin.duplicateOf != null -> "${pin.status} of ${pin.duplicateOf}"
            pin.kind == SegmentPinKind.STATUS -> pin.status.orEmpty()
            pin.proposalUri != null -> "${pin.name} (from ${pin.proposalUri})"
            else -> pin.name.orEmpty()
        }

    private fun FlowContent.actions(admin: Admin, d: SegmentDetail) {
        h2 { +"Actions" }
        div {
            attributes["id"] = OUTCOME
            attributes["aria-live"] = "polite"
        }
        actionForm(admin, "status-pass", d.uri, "Run status pass")
        actionForm(admin, "naming-pass", d.uri, "Run naming pass")
        actionForm(admin, "elevation", d.uri, "Recompute elevation")
        actionForm(admin, "rematch", d.uri, "Rematch")
        actionForm(admin, "pin-status", d.uri, "Pin status") {
            label {
                +"Status "
                select {
                    name = "status"
                    PIN_STATUSES.forEach { s ->
                        option {
                            value = s
                            +s
                        }
                    }
                }
            }
            field("Duplicate of", "duplicateOf", null)
            label {
                checkBoxInput(name = "confirm") { value = "yes" }
                +" A duplicate pin removes this segment's efforts"
            }
            noteField()
        }
        actionForm(admin, "pin-name", d.uri, "Pin name") {
            field("Name", "name", null)
            val named = d.proposals.filter { it.name != null }
            if (named.isNotEmpty()) {
                label {
                    +"or proposal "
                    select {
                        name = "proposal"
                        option {
                            value = ""
                            +"—"
                        }
                        named.forEach { proposal ->
                            option {
                                value = proposal.uri
                                +"${proposal.name}"
                            }
                        }
                    }
                }
            }
            noteField()
        }
    }

    private fun kotlinx.html.FORM.noteField() {
        label {
            +"Note "
            input(InputType.text, name = "note") { maxLength = "$MAX_NOTE" }
        }
    }

    /** A form posting [action] on [uri], with htmx into the outcome slot and the CSRF field so it works without htmx too. */
    private fun FlowContent.actionForm(
        admin: Admin,
        action: String,
        uri: String,
        label: String,
        fields: kotlinx.html.FORM.() -> Unit = {},
    ) {
        form(action = "/admin/$path/$action", method = FormMethod.post) {
            attributes["hx-post"] = "/admin/$path/$action"
            attributes["hx-target"] = "#$OUTCOME"
            hiddenInput(name = AdminSite.CSRF_FIELD) { value = admin.csrfToken }
            hiddenInput(name = "target") { value = uri }
            fields()
            button { +label }
        }
    }

    private fun FlowContent.field(text: String, name: String, value: String?) {
        label {
            +"$text "
            textInput(name = name) { this.value = value.orEmpty() }
        }
    }

    private fun FlowContent.segmentLink(uri: String) {
        a(href = "/admin/$path/segment?uri=${uri.encodeURLParameter()}") { +uri }
    }

    private fun kotlinx.html.TBODY.row(name: String, value: String) {
        tr {
            th { +name }
            td { +value }
        }
    }

    private fun time(epochMillis: Long) = Instant.fromEpochMilliseconds(epochMillis).toString()

    companion object {
        const val PINS_PAGE = 50

        /** The page `main` mounts: reads and passes built from [config], over the indexer's [pins] and [jobs]. */
        fun of(config: AppViewConfig, db: Database, pins: SegmentPins, jobs: SegmentJobs) =
            SegmentsPage(
                SegmentAdminQueries(db, pins, config.segmentActiveAthletes, config.segmentImporter),
                SegmentPasses(
                    db,
                    jobs,
                    activeAthletes = config.segmentActiveAthletes,
                    names = config.segmentNames,
                    importer = config.segmentImporter,
                ),
                pins,
                jobs,
            )

        private const val MAX_NOTE = 500

        /** Grades are stored in tenths of a percent. */
        private const val TENTHS = 10.0
        private const val OUTCOME = "segment-outcome"
        private const val WITHHELD = "withheld"
        private val STATUSES = listOf(SegmentStatus.PROPOSED, SegmentStatus.ACTIVE, SegmentStatus.DUPLICATE, SegmentStatus.ARCHIVED)
        private val PIN_STATUSES = listOf(SegmentStatus.ACTIVE, SegmentStatus.ARCHIVED, SegmentStatus.DUPLICATE)
    }
}
