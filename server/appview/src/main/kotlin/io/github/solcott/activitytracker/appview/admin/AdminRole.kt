package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.lexicon.Did

/** What an admin may do on the admin site (admin/admin-site): a [Viewer] reads every page, an [Operator] also runs actions. */
enum class AdminRole(val id: String) {
    Viewer("viewer"),
    Operator("operator");

    companion object {
        /**
         * Parses `APPVIEW_ADMINS`: comma-separated `did=role` entries. Blank or missing is no admins. Throws [IllegalArgumentException]
         * naming the first entry that isn't a DID with a known role, so a mistyped list stops start-up instead of locking an operator out
         * later.
         */
        fun parseAdmins(value: String?): Map<Did, AdminRole> =
            value
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associate { entry ->
                    val did = entry.substringBefore('=', "").trim()
                    val role = entries.firstOrNull { it.id == entry.substringAfter('=', "").trim() }
                    require(did.startsWith("did:") && did.length > "did:".length && role != null) {
                        "APPVIEW_ADMINS entry \"$entry\" isn't did=viewer or did=operator"
                    }
                    Did(did) to role
                }
    }
}
