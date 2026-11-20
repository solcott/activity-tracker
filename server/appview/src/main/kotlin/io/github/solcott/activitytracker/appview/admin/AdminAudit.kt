package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.AdminActions
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** One audited action or refused attempt. */
data class AdminActionEntry(
    val id: Long,
    val admin: String,
    val action: String,
    val target: String?,
    val params: Map<String, String>,
    val outcome: String,
    val at: Instant,
)

/** A page of audit entries and the cursor to the next, null at the end. */
data class AuditPageOf(val entries: List<AdminActionEntry>, val cursor: String?)

/** Which entries an audit page lists; null fields don't filter. */
data class AuditFilter(val admin: String? = null, val action: String? = null, val target: String? = null)

/**
 * The audit log of admin site actions (admin/admin-site): who, what, on what, with which settings, the outcome and when. Kept for
 * [RETENTION], at least the year the spec asks for. Its values come from action forms, which take identifiers and settings only.
 */
class AdminAudit(private val db: Database, private val clock: Clock = Clock.System) {
    suspend fun record(admin: String, action: String, target: String?, params: Map<String, String>, outcome: String) = db.tx {
        AdminActions.insert {
            it[adminDid] = admin.take(MAX_ID)
            it[AdminActions.action] = action.take(MAX_ACTION)
            it[AdminActions.target] = target?.take(MAX_TARGET)
            it[AdminActions.params] = AtJson.encodeToString(JsonObject.serializer(), paramsJson(params))
            it[AdminActions.outcome] = outcome.take(MAX_OUTCOME)
            it[at] = clock.now().toEpochMilliseconds()
        }
    }

    /** Up to [limit] entries matching [filter], newest first, before the [cursor] an earlier page returned. */
    suspend fun page(filter: AuditFilter, cursor: String?, limit: Int): AuditPageOf = db.tx {
        var where: Op<Boolean> = Op.TRUE
        filter.admin?.let { where = where and (AdminActions.adminDid eq it) }
        filter.action?.let { where = where and (AdminActions.action eq it) }
        filter.target?.let { where = where and (AdminActions.target eq it) }
        cursorOf(cursor)?.let { (at, id) ->
            where = where and ((AdminActions.at less at) or ((AdminActions.at eq at) and (AdminActions.id less id)))
        }
        val rows =
            AdminActions.selectAll()
                .where(where)
                .orderBy(AdminActions.at to SortOrder.DESC, AdminActions.id to SortOrder.DESC)
                .limit(limit + 1)
                .map {
                    AdminActionEntry(
                        id = it[AdminActions.id],
                        admin = it[AdminActions.adminDid],
                        action = it[AdminActions.action],
                        target = it[AdminActions.target],
                        params = paramsOf(it[AdminActions.params]),
                        outcome = it[AdminActions.outcome],
                        at = Instant.fromEpochMilliseconds(it[AdminActions.at]),
                    )
                }
        val page = rows.take(limit)
        AuditPageOf(page, page.lastOrNull()?.takeIf { rows.size > limit }?.let { "${it.at.toEpochMilliseconds()}:${it.id}" })
    }

    /** Deletes entries older than [RETENTION]; returns how many. */
    suspend fun prune(): Int = db.tx {
        val cutoff = (clock.now() - RETENTION).toEpochMilliseconds()
        AdminActions.deleteWhere { at less cutoff }
    }

    /** `<acted_at>:<id>` as those two numbers, or null for anything else. */
    private fun cursorOf(cursor: String?): Pair<Long, Long>? =
        CURSOR.matchEntire(cursor.orEmpty())?.destructured?.let { (at, id) ->
            at.toLongOrNull()?.let { a -> id.toLongOrNull()?.let { a to it } }
        }

    private fun paramsJson(params: Map<String, String>) =
        JsonObject(params.entries.take(MAX_PARAMS).associate { (k, v) -> k.take(MAX_ACTION) to JsonPrimitive(v.take(MAX_PARAM)) })

    private fun paramsOf(json: String): Map<String, String> = runCatching {
        AtJson.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }
        .getOrDefault(emptyMap())

    companion object {
        /** About 13 months. */
        val RETENTION = 395.days
        private val CURSOR = Regex("""(\d+):(\d+)""")
        private const val MAX_ID = 255
        private const val MAX_ACTION = 128
        private const val MAX_TARGET = 512
        private const val MAX_OUTCOME = 255
        private const val MAX_PARAMS = 20
        private const val MAX_PARAM = 200
    }
}
