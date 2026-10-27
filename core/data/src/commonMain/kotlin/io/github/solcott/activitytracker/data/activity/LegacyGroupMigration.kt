package io.github.solcott.activitytracker.data.activity

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.Paging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns each of the signed-in athlete's legacy groups (a primary and its `recordingOf` recordings) into a combined activity, once per
 * account (`feed/import-duplicates`): the primary and its recordings become members, starting from the primary's title, description and
 * visibility, and then each recording's `recordingOf` is cleared. Activities merged in the past stay as they are.
 *
 * The combined activity is written first, so a run interrupted before the clears still hides the recordings, by both rules. Each group is
 * noted in Room before it starts and forgotten once its clears are queued, so the next run finishes a noted one: combining it unless the
 * AppView or the outbox shows it combined already, then clearing. A finished group is remembered too, so one the AppView still lists as a
 * group (it hasn't indexed the combined activity yet) isn't combined twice. A group that fails is logged and left, and the account is
 * marked finished in Room only when every group succeeded, so the next sign-in retries.
 */
@SingleIn(SessionScope::class)
@Inject
class LegacyGroupMigration(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val manager: ActivityManager,
    logger: Logger,
) {
    private val log = logger.withTag("LegacyGroupMigration")
    private val mutex = Mutex()
    private var startedThisSession = false

    private val did: String
        get() = clients.did.value

    /** Runs [run] the first time it's called in the session; later calls (a recomposed session) do nothing. */
    suspend fun runOncePerSession() {
        mutex.withLock {
            if (startedThisSession) return
            startedThisSession = true
        }
        run()
    }

    /** Migrates every group not migrated yet; true when the account has none left. Never throws but for cancellation. */
    suspend fun run(): Boolean {
        if (db.keyValue().get(doneKey) != null) return true
        val ok = listGroups()?.let { migrateAll(it) } ?: false
        if (ok) {
            db.keyValue().put(KeyValueEntity(doneKey, "1"))
            db.keyValue().delete(finishedKey)
        }
        return ok
    }

    /** Migrates the [listed] groups not finished already, then those an earlier run left; true when all succeeded. */
    private suspend fun migrateAll(listed: List<Pair<ActivityView, Group>>): Boolean {
        val finished = finished()
        var ok = true
        for ((primary, group) in listed.filter { it.first.uri !in finished }) {
            remember(group)
            ok = finish(group, primary) && ok
        }
        // A group an earlier run started that the AppView no longer lists as one: its primary is a member by now, or about to be.
        val listedPrimaries = listed.map { it.first.uri }.toSet()
        for (group in pending().filter { it.primary !in listedPrimaries }) ok = finish(group) && ok
        return ok
    }

    /** The athlete's groups as the AppView lists them, every page: each primary and its group; null when they can't be listed. */
    private suspend fun listGroups(): List<Pair<ActivityView, Group>>? {
        val groups = mutableListOf<Pair<ActivityView, Group>>()
        var cursor: String? = null
        do {
            val page =
                runSuspendCatching {
                    clients.appView.query(GetAuthorActivities.method, GetAuthorActivities.Params(did, Paging.MAX_LIMIT, cursor))
                }
                    .onFailure { log.w(it) { "Couldn't list the athlete's activities" } }
                    .getOrNull() ?: return null
            for (primary in page.activities.filter { !it.recordings.isNullOrEmpty() && it.combinedInto == null }) {
                val recordings = primary.recordings.orEmpty().map { it.uri }.distinct() - primary.uri
                if (recordings.isNotEmpty()) groups += primary to Group(primary.uri, primary.visibilityOrPublic, recordings)
            }
            // An empty page, or a cursor that doesn't move, would page forever.
            cursor = page.cursor?.takeIf { page.activities.isNotEmpty() && it != cursor }
        } while (cursor != null)
        return groups
    }

    /**
     * Combines [group] unless it's combined already ([view] or the AppView says so, or its combined activity waits in the outbox), then
     * clears its recordings' `recordingOf` and forgets it.
     */
    private suspend fun finish(group: Group, view: ActivityView? = null): Boolean = runSuspendCatching {
        val primary = view ?: clients.appView.query(GetActivity.method, GetActivity.Params(group.primary)).activity
        val combined = primary.combinedInto != null || primary.record.combined != null || queuedCombination(group.primary)
        if (!combined) manager.combineGroup(primary, group.recordings.take(CombinedActivity.MAX_MEMBERS - 1))
        group.recordings.forEach { manager.clearRecordingOf(it, group.visibility) }
        forget(group)
    }
        .onFailure { log.w(it) { "Couldn't turn the group of ${group.primary} into a combined activity" } }
        .isSuccess

    /** Whether a combined activity with [primary] as a member is still waiting in the outbox. */
    private suspend fun queuedCombination(primary: AtUri): Boolean =
        db.outbox().pending(did).mapNotNull { it.pendingActivity(clients.did) }.any { primary in it.record.combined?.members.orEmpty() }

    /** A legacy group: its [primary], where it's stored ([visibility], which its recordings share), and its [recordings]. */
    private data class Group(val primary: AtUri, val visibility: String, val recordings: List<AtUri>) {
        fun encode(): String = (listOf(visibility, primary.value) + recordings.map { it.value }).joinToString(" ")

        companion object {
            fun decode(line: String): Group? {
                val parts = line.split(' ').filter { it.isNotBlank() }
                if (parts.size <= 2) return null
                return Group(AtUri(parts[1]), parts[0], parts.drop(2).map(::AtUri))
            }
        }
    }

    private suspend fun pending(): List<Group> = db.keyValue().get(pendingKey)?.lines()?.mapNotNull(Group::decode).orEmpty()

    private suspend fun remember(group: Group) = save(pending().filter { it.primary != group.primary } + group)

    private suspend fun forget(group: Group) {
        db.keyValue().put(KeyValueEntity(finishedKey, (finished() + group.primary).joinToString("\n") { it.value }))
        save(pending().filter { it.primary != group.primary })
    }

    /** The primaries of groups finished in a run that didn't finish them all. */
    private suspend fun finished(): Set<AtUri> =
        db.keyValue().get(finishedKey)?.lines()?.filter { it.isNotBlank() }?.mapTo(mutableSetOf(), ::AtUri).orEmpty()

    private suspend fun save(groups: List<Group>) {
        if (groups.isEmpty()) db.keyValue().delete(pendingKey)
        else db.keyValue().put(KeyValueEntity(pendingKey, groups.joinToString("\n") { it.encode() }))
    }

    private val doneKey: String
        get() = "legacyGroups.done.$did"

    private val pendingKey: String
        get() = "legacyGroups.pending.$did"

    private val finishedKey: String
        get() = "legacyGroups.finished.$did"
}
