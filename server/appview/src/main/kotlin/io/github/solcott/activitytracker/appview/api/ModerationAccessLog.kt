package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.ModerationAccess
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert

/**
 * The audit log of moderator views of non-public subjects (`moderation/private-subjects`): who, which subject, the open report it relied
 * on, and when, never the content. Entries are kept for [RETENTION], at least the year the spec asks for, then pruned.
 */
class ModerationAccessLog(private val db: Database, private val clock: Clock = Clock.System) {
    suspend fun record(moderator: String, subject: String, reportRef: String) = db.tx {
        ModerationAccess.insert {
            it[moderatorDid] = moderator
            it[subjectUri] = subject
            it[ModerationAccess.reportRef] = reportRef
            it[at] = clock.now().toEpochMilliseconds()
        }
    }

    /** Deletes entries older than [RETENTION]; returns how many. */
    suspend fun prune(): Int = db.tx {
        val cutoff = (clock.now() - RETENTION).toEpochMilliseconds()
        ModerationAccess.deleteWhere { at less cutoff }
    }

    /** Prunes now and every [interval] until [scope] ends. */
    fun start(scope: CoroutineScope, interval: Duration = 24.hours) {
        scope.launch {
            while (true) {
                runSuspendCatching { prune() }
                delay(interval)
            }
        }
    }

    companion object {
        val RETENTION = 400.days
    }
}
