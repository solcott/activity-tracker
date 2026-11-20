package io.github.solcott.activitytracker.appview.admin

import io.ktor.http.encodeURLParameter
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

/** The admin site's home page: what each page is for. */
internal object HomePage : AdminPage {
    override val path = ""
    override val title = "Home"

    override fun AdminRoutes.install() {
        get(path) {
            page(title) {
                p { +"Signed in as ${admin.did.value} (${admin.role.id})." }
                p { +"Reports, labels and takedowns are handled in the labeler, not here." }
            }
        }
    }
}

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
            page(title) {
                form(action = "/admin/$path", method = FormMethod.get) {
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
                    table {
                        thead { tr { listOf("When", "Admin", "Action", "Target", "Settings", "Outcome").forEach { th { +it } } } }
                        tbody {
                            entries.forEach { e ->
                                tr {
                                    td { +e.at.toString() }
                                    td { +e.admin }
                                    td { +e.action }
                                    td { +e.target.orEmpty() }
                                    td { +e.params.entries.joinToString(", ") { "${it.key}=${it.value}" } }
                                    td { +e.outcome }
                                }
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
    }
}
