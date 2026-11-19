package io.github.solcott.activitytracker.appview.index

import java.util.concurrent.ConcurrentHashMap

/**
 * What a reindex of [did] (admin/repo-reindex) and Jetstream's commits for that account need to know of each other, by AT-URI, so neither
 * puts back an older version of a record over a newer one. Revisions are TIDs, which sort as strings. Every method is called under the
 * record's URI lock in [Indexer], so a check and the write it guards are one step.
 *
 * Registered before the repo is fetched: a commit Jetstream delivered before then was committed before the fetch, so its revision is at
 * most the snapshot's and the snapshot covers it.
 */
class ReindexWatch internal constructor(val did: String) {
    /** The fetched repo's revision, once its commit is verified; until then nothing the reindex does is guarded by it. */
    @Volatile internal var snapshotRev: String? = null

    /** When the watch stops applying, in epoch ms ([Indexer.unwatch] sets it); [Long.MAX_VALUE] while the reindex runs. */
    @Volatile internal var expiresAt: Long = Long.MAX_VALUE

    /** The newest revision Jetstream delivered for each URI since the watch began; [UNKNOWN] for a commit that carried none. */
    private val live = ConcurrentHashMap<String, String>()

    /** URIs the reindex wrote or deleted from the snapshot. */
    private val applied = ConcurrentHashMap.newKeySet<String>()

    /**
     * A Jetstream commit for [uri] at [rev]: false when it must be dropped (the reindex already applied [uri] from a snapshot that includes
     * this commit), else true, remembering it so the reindex leaves [uri] alone if it's newer than the snapshot.
     */
    internal fun liveCommit(uri: String, rev: String?): Boolean {
        val snapshot = snapshotRev
        val covered = rev != null && snapshot != null && rev <= snapshot
        if (covered && uri in applied) return false
        live.merge(uri, rev ?: UNKNOWN) { old, new -> if (old == UNKNOWN || new == UNKNOWN) UNKNOWN else maxOf(old, new) }
        return true
    }

    /**
     * The reindex is about to write or delete [uri] from the snapshot: false when a newer (or unordered) live commit already did, so the
     * snapshot's older view must not replace it; else true, remembering [uri] as applied.
     */
    internal fun snapshotWrite(uri: String): Boolean {
        val snapshot = checkNotNull(snapshotRev) { "The snapshot's revision isn't known yet" }
        val rev = live[uri]
        if (rev != null && (rev == UNKNOWN || rev > snapshot)) return false
        applied += uri
        return true
    }

    internal fun expired(now: Long) = now >= expiresAt

    private companion object {
        /** Sorts after every TID, so a commit without a revision always counts as newer. */
        const val UNKNOWN = "~"
    }
}
