package io.github.solcott.activitytracker.appview.admin

import io.ktor.http.encodeURLParameter
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.tr

/** The audit log (admin/admin-site): every action and refused attempt, newest first, filterable by admin, action and target. */
internal class AuditPage(private val audit: AdminAudit) : AdminPage {
    override val path = "audit"
    override val title = "Audit log"

    override fun privacyProbes(subject: String) = listOf(path, "$path?target=${subject.encodeURLParameter()}")

    override fun AdminRoutes.install() {
        get(path) {
            val query = call.request.queryParameters
            fun param(name: String) = query[name]?.trim()?.takeIf { it.isNotEmpty() }
            val filter = AuditFilter(admin = param("admin"), action = param("action"), target = param("target"))
            val (entries, next) = audit.page(filter, param("cursor"), PAGE_SIZE)
            val now = now
            page(title) {
                form(action = "/admin/$path", method = FormMethod.get, classes = "filters") {
                    label {
                        +"Admin "
                        textInput(name = "admin") { value = filter.admin.orEmpty() }
                    }
                    label {
                        +"Action "
                        textInput(name = "action") { value = filter.action.orEmpty() }
                    }
                    label {
                        +"Target "
                        textInput(name = "target") { value = filter.target.orEmpty() }
                    }
                    button { +"Filter" }
                }
                if (entries.isEmpty()) p { +"No entries." }
                else
                    dataTable(listOf("When", "Admin", "Action", "Target", "Settings", "Outcome")) {
                        entries.forEach { e ->
                            tr {
                                timeCell(e.at, now)
                                td { +e.admin }
                                td("mono") { +e.action }
                                td { +e.target.orEmpty() }
                                td { +e.params.entries.joinToString(", ") { "${it.key}=${it.value}" } }
                                td { badge(e.outcome, outcomeStatus(e.outcome)) }
                            }
                        }
                    }
                next?.let { cursor ->
                    val keep =
                        listOfNotNull(
                            filter.admin?.let { "admin=${it.encodeURLParameter()}" },
                            filter.action?.let { "action=${it.encodeURLParameter()}" },
                            filter.target?.let { "target=${it.encodeURLParameter()}" },
                        )
                    p { a(href = "/admin/$path?" + (keep + "cursor=${cursor.encodeURLParameter()}").joinToString("&")) { +"Older" } }
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 50

        /** An audit outcome's status: `ok…` fine, `refused…` a warning, `failed…` bad. */
        fun outcomeStatus(outcome: String): TileStatus? =
            when (outcome.substringBefore(':')) {
                "ok" -> TileStatus.Ok
                "refused" -> TileStatus.Warn
                "failed" -> TileStatus.Bad
                else -> null
            }
    }
}
