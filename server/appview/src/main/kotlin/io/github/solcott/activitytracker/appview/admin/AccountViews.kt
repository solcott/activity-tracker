package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.reindex.ReindexRun
import io.github.solcott.activitytracker.atproto.identity.isResolvableDid
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.encodeURLPathPart
import kotlin.time.Instant
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
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
import kotlinx.html.thead
import kotlinx.html.tr

/**
 * The account page (admin/account-tools): look an account up by DID or verified handle, see what the AppView holds for it (through
 * [AccountSummary] only, so identifiers, statuses, counts and times), and, as an operator, re-verify its handle, backfill its Bluesky
 * blocks, reindex its repo or resync its spaces. "Resync spaces" exists only with a [syncer].
 */

// How the account page (admin/account-tools) draws what AccountsPage read: AccountSummary and reindex runs only.

internal fun FlowContent.lookupForm() {
    form(action = "/admin/accounts", method = FormMethod.get) {
        label {
            +"Handle or DID "
            textInput(name = "q") { required = true }
        }
        button { +"Look up" }
    }
}

internal fun FlowContent.account(s: AccountSummary) {
    h2 { +"Account" }
    dl {
        row("DID", s.did.value)
        if (!s.indexed) {
            row("Indexed", "no: not indexed")
            return@dl
        }
        row("Status", s.status.orEmpty())
        row("Handle", s.handle.orEmpty())
        row("Handle verified", yesNo(s.handleVerified))
        row("Next handle check", s.handleCheckDueAt?.let(::shownTime) ?: "due now")
        row("Segment matching", yesNo(s.segmentMatching))
        row("Grouping", yesNo(s.grouping))
        row("Bluesky blocks backfilled", s.blocks.syncedAt?.let(::shownTime) ?: "never")
        if (s.blocks.failures > 0) {
            row("Backfill failures", s.blocks.failures.toString())
            row("Last backfill error", s.blocks.lastError.orEmpty())
            row("Next backfill attempt", s.blocks.nextAttemptAt?.let(::shownTime).orEmpty())
        }
    }
    h2 { +"Indexed records" }
    if (s.counts.isEmpty()) p { +"None." }
    else {
        val origins = s.origins
        table {
            thead { tr { (listOf("Collection") + origins).forEach { th { +it } } } }
            tbody {
                s.counts.forEach { c ->
                    tr {
                        td { +c.collection }
                        origins.forEach { o -> td { +(c.byOrigin[o] ?: 0).toString() } }
                    }
                }
            }
        }
    }
}

internal fun FlowContent.spaces(did: Did, s: AccountSummary) {
    div {
        attributes["id"] = "spaces"
        attributes["hx-get"] = "${accountUrl(did)}/spaces"
        attributes["hx-trigger"] = "$ACCOUNT_SPACES_CHANGED from:body"
        attributes["hx-select"] = "#spaces"
        attributes["hx-swap"] = "outerHTML"
        if (s.spaces.isEmpty()) p { +"No synced spaces." }
        else
            table {
                thead {
                    tr {
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
                            )
                            .forEach { th { +it } }
                    }
                }
                tbody {
                    s.spaces.forEach { sp ->
                        tr {
                            td { +sp.space.value }
                            td { +sp.repoRev.orEmpty() }
                            td { +sp.syncedAt?.let(::shownTime).orEmpty() }
                            td { +sp.failures.toString() }
                            td { +sp.lastError.orEmpty() }
                            td { +sp.lastErrorAt?.let(::shownTime).orEmpty() }
                            td { +shownTime(sp.nextAttemptAt) }
                            td { +sp.notFoundSince?.let(::shownTime).orEmpty() }
                            td { +sp.registeredUntil?.let(::shownTime).orEmpty() }
                        }
                    }
                }
            }
    }
}

/** The runs list; while one is queued or running it polls itself every 3 s, and stops once the answer has none active. */
internal fun FlowContent.runs(did: Did, runs: List<ReindexRun>) {
    div {
        attributes["id"] = "runs"
        attributes["hx-get"] = "${accountUrl(did)}/runs"
        attributes["hx-trigger"] =
            if (runs.any { it.state.active }) "every 3s, $ACCOUNT_RUNS_CHANGED from:body" else "$ACCOUNT_RUNS_CHANGED from:body"
        attributes["hx-select"] = "#runs"
        attributes["hx-swap"] = "outerHTML"
        if (runs.isEmpty()) p { +"No reindex runs." }
        else
            table {
                thead {
                    tr {
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
                            )
                            .forEach { th { +it } }
                    }
                }
                tbody {
                    runs.forEach { r ->
                        tr {
                            td { +r.id.toString() }
                            td { +r.requestedBy.value }
                            td { +shownTime(r.requestedAt) }
                            td { +r.state.code }
                            td { +r.phase?.code.orEmpty() }
                            td { +r.startedAt?.let(::shownTime).orEmpty() }
                            td { +r.finishedAt?.let(::shownTime).orEmpty() }
                            td { +r.rev.orEmpty() }
                            with(r.counts) { listOf(seen, unchanged, indexed, invalid, removed, live).forEach { td { +it.toString() } } }
                            td { +yesNo(r.resyncSpaces) }
                            td { +r.spacesResynced?.toString().orEmpty() }
                            td { +r.errorCode.orEmpty() }
                            td { +r.errorMessage.orEmpty() }
                        }
                    }
                }
            }
    }
}

internal fun FlowContent.actions(did: Did, spacesSynced: Boolean) {
    h2 { +"Repairs" }
    actionForm("recheck-handle", did, "Re-verify handle now")
    actionForm("backfill-blocks", did, "Backfill Bluesky blocks now")
    actionForm("reindex", did, "Reindex repo") {
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
    if (spacesSynced) actionForm("resync-spaces", did, "Resync spaces")
}

private fun FlowContent.actionForm(name: String, did: Did, label: String, fields: FlowContent.() -> Unit = {}) {
    val slot = "outcome-$name"
    form(action = "/admin/accounts/$name", method = FormMethod.post) {
        attributes["hx-post"] = "/admin/accounts/$name"
        attributes["hx-target"] = "#$slot"
        hiddenInput(name = "target") { value = did.value }
        fields()
        button { +label }
        div {
            attributes["id"] = slot
            attributes["aria-live"] = "polite"
        }
    }
}

internal const val ACCOUNT_HX_TRIGGER = "HX-Trigger"
internal const val ACCOUNT_RUNS_CHANGED = "reindex-requested"
internal const val ACCOUNT_SPACES_CHANGED = "spaces-resynced"

internal fun accountDidOf(value: String?): Did? = value?.trim()?.takeIf(::isResolvableDid)?.let(::Did)

internal fun accountUrl(did: Did) = "/admin/accounts/${did.value.encodeURLPathPart()}"

internal fun shownTime(epochMs: Long?): String = epochMs?.let { Instant.fromEpochMilliseconds(it).toString() }.orEmpty()

private fun yesNo(value: Boolean) = if (value) "yes" else "no"

private fun kotlinx.html.DL.row(term: String, value: String) {
    dt { +term }
    dd { +value }
}
