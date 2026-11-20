@file:Suppress("TooManyFunctions") // The account page's sections and the small helpers they share.

package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.reindex.ReindexRun
import io.github.solcott.activitytracker.appview.reindex.ReindexState
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.encodeURLPathPart
import kotlin.time.Instant
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.hiddenInput
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.tr

// How the account page (admin/account-tools) draws what AccountsPage read: AccountSummary and reindex runs only.

internal fun FlowContent.lookupForm() {
    form(action = "/admin/accounts", method = FormMethod.get, classes = "filters") {
        label {
            +"Handle or DID "
            textInput(name = "q") { required = true }
        }
        button { +"Look up" }
    }
}

internal fun FlowContent.account(s: AccountSummary, now: Instant) {
    h2 { +"Account" }
    facts {
        row("DID", s.did.value)
        if (!s.indexed) {
            row("Indexed") { badge("not indexed", null) }
            return@facts
        }
        row("Status") { badge(s.status.orEmpty(), if (s.status == "active") TileStatus.Ok else TileStatus.Warn) }
        row("Handle", s.handle.orEmpty())
        row("Handle verified") { yesNo(s.handleVerified) }
        row("Next handle check") { if (s.handleCheckDueAt == null) +"due now" else at(instantOfMs(s.handleCheckDueAt), now) }
        row("Segment matching") { yesNo(s.segmentMatching) }
        row("Grouping") { yesNo(s.grouping) }
        row("Bluesky blocks backfilled") { if (s.blocks.syncedAt == null) +"never" else at(instantOfMs(s.blocks.syncedAt), now) }
        if (s.blocks.failures > 0) {
            row("Backfill failures") { badge(s.blocks.failures.toString(), TileStatus.Warn) }
            row("Last backfill error", s.blocks.lastError.orEmpty())
            row("Next backfill attempt") { at(instantOfMs(s.blocks.nextAttemptAt), now) }
        }
    }
    h2 { +"Indexed records" }
    if (s.counts.isEmpty()) p { +"None." }
    else {
        val origins = s.origins
        dataTable(listOf("Collection") + origins, numeric = origins.toSet()) {
            s.counts.forEach { c ->
                tr {
                    td { +c.collection }
                    origins.forEach { o -> num(c.byOrigin[o] ?: 0) }
                }
            }
        }
    }
}

internal fun FlowContent.spaces(did: Did, s: AccountSummary, now: Instant) {
    div {
        attributes["id"] = "spaces"
        attributes["hx-get"] = "${accountUrl(did)}/spaces"
        attributes["hx-trigger"] = "$ACCOUNT_SPACES_CHANGED from:body"
        attributes["hx-select"] = "#spaces"
        attributes["hx-swap"] = "outerHTML"
        if (s.spaces.isEmpty()) p { +"No synced spaces." }
        else
            dataTable(
                listOf(
                    "Space",
                    "Revision",
                    "Last synced",
                    "Failures",
                    "Last error",
                    "Error at",
                    "Next attempt",
                    "Not found since",
                    "Registered until",
                ),
                numeric = setOf("Failures"),
            ) {
                s.spaces.forEach { sp ->
                    tr {
                        td { +sp.space.value }
                        td("mono") { +sp.repoRev.orEmpty() }
                        timeCell(instantOfMs(sp.syncedAt), now)
                        td("num") { if (sp.failures > 0) badge(sp.failures.toString(), TileStatus.Warn) else +"0" }
                        td { +sp.lastError.orEmpty() }
                        timeCell(instantOfMs(sp.lastErrorAt), now)
                        timeCell(instantOfMs(sp.nextAttemptAt), now)
                        timeCell(instantOfMs(sp.notFoundSince), now)
                        timeCell(instantOfMs(sp.registeredUntil), now)
                    }
                }
            }
    }
}

/** The runs list; while one is queued or running it polls itself every 3 s, and stops once the answer has none active. */
internal fun FlowContent.runs(did: Did, runs: List<ReindexRun>, now: Instant) {
    div {
        attributes["id"] = "runs"
        attributes["hx-get"] = "${accountUrl(did)}/runs"
        attributes["hx-trigger"] =
            if (runs.any { it.state.active }) "every 3s, $ACCOUNT_RUNS_CHANGED from:body" else "$ACCOUNT_RUNS_CHANGED from:body"
        attributes["hx-select"] = "#runs"
        attributes["hx-swap"] = "outerHTML"
        if (runs.isEmpty()) p { +"No reindex runs." }
        else
            dataTable(
                listOf(
                    "Run",
                    "Requester",
                    "Requested",
                    "State",
                    "Phase",
                    "Started",
                    "Finished",
                    "Revision",
                    "Seen",
                    "Unchanged",
                    "Indexed",
                    "Invalid",
                    "Removed",
                    "Live",
                    "Resync spaces",
                    "Spaces queued",
                    "Error",
                    "Message",
                ),
                numeric = setOf("Run", "Seen", "Unchanged", "Indexed", "Invalid", "Removed", "Live", "Spaces queued"),
            ) {
                runs.forEach { r ->
                    tr {
                        num(r.id)
                        td { +r.requestedBy.value }
                        timeCell(instantOfMs(r.requestedAt), now)
                        td { runState(r.state) }
                        td { +r.phase?.code.orEmpty() }
                        timeCell(instantOfMs(r.startedAt), now)
                        timeCell(instantOfMs(r.finishedAt), now)
                        td("mono") { +r.rev.orEmpty() }
                        with(r.counts) { listOf(seen, unchanged, indexed, invalid, removed, live).forEach { num(it) } }
                        td { yesNo(r.resyncSpaces) }
                        num(r.spacesResynced?.toString().orEmpty())
                        td { +r.errorCode.orEmpty() }
                        td { +r.errorMessage.orEmpty() }
                    }
                }
            }
    }
}

/** Runs across every account, queued or running now or failed in the last day, each linking to its account (admin/account-tools). */
internal fun FlowContent.recentRuns(runs: List<ReindexRun>, now: Instant) {
    h2 {
        attributes["id"] = RECENT_RUNS_SECTION
        +"Recent reindex runs"
    }
    if (runs.isEmpty()) {
        p { +"No reindex run is in flight, and none failed in the last 24 hours." }
        return
    }
    dataTable(listOf("Account", "Requester", "State", "Phase", "Requested", "Started", "Finished", "Error")) {
        runs.forEach { r ->
            tr {
                td { a(href = accountUrl(r.did)) { +r.did.value } }
                td { +r.requestedBy.value }
                td { runState(r.state) }
                td { +r.phase?.code.orEmpty() }
                timeCell(instantOfMs(r.requestedAt), now)
                timeCell(instantOfMs(r.startedAt), now)
                timeCell(instantOfMs(r.finishedAt), now)
                td { +r.errorCode.orEmpty() }
            }
        }
    }
}

internal fun FlowContent.actions(did: Did, spacesSynced: Boolean, csrf: String) {
    h2 { +"Repairs" }
    actionForm("recheck-handle", did, "Re-verify handle now", csrf)
    actionForm("backfill-blocks", did, "Backfill Bluesky blocks now", csrf)
    actionForm("reindex", did, "Reindex repo", csrf) {
        if (spacesSynced) {
            label {
                checkBoxInput(name = "resyncSpaces") {
                    value = "true"
                    checked = true
                }
                +" Also resync spaces "
            }
        }
        // After the checkbox: an unchecked box still sends (and audits) the setting, a checked one's value comes first.
        hiddenInput(name = "resyncSpaces") { value = "false" }
    }
    if (spacesSynced) actionForm("resync-spaces", did, "Resync spaces", csrf)
}

/** One repair's form, with its own outcome slot under it. */
private fun FlowContent.actionForm(name: String, did: Did, label: String, csrf: String, fields: kotlinx.html.FORM.() -> Unit = {}) {
    val slot = "outcome-$name"
    actionForm("/admin/accounts/$name", label, csrf, slot, target = did.value, fields = fields)
    outcome(slot)
}

private fun FlowContent.runState(state: ReindexState) {
    badge(
        state.code,
        when (state) {
            ReindexState.SUCCEEDED -> TileStatus.Ok
            ReindexState.FAILED -> TileStatus.Bad
            ReindexState.UNAVAILABLE -> TileStatus.Warn
            else -> null
        },
    )
}

internal const val ACCOUNT_HX_TRIGGER = "HX-Trigger"
internal const val ACCOUNT_RUNS_CHANGED = "reindex-requested"
internal const val ACCOUNT_SPACES_CHANGED = "spaces-resynced"
internal const val RECENT_RUNS_SECTION = "recent-runs"

internal fun accountDidOf(value: String?): Did? = value?.trim()?.takeIf(::isResolvableDid)?.let(::Did)

internal fun accountUrl(did: Did) = "/admin/accounts/${did.value.encodeURLPathPart()}"

internal fun shownTime(epochMs: Long?): String = epochMs?.let { Instant.fromEpochMilliseconds(it).toString() }.orEmpty()

private fun FlowContent.yesNo(value: Boolean) = badge(if (value) "yes" else "no", if (value) TileStatus.Ok else null)

/** A list of facts, one per row: the name in a header cell, the value beside it. */
private fun FlowContent.facts(body: TBODY.() -> Unit) {
    div("table-wrap") { table { tbody { body() } } }
}

private fun TBODY.row(name: String, value: String) = row(name) { +value }

private fun TBODY.row(name: String, value: FlowContent.() -> Unit) {
    tr {
        th { +name }
        td { value() }
    }
}
