package io.github.solcott.activitytracker.appview.reindex

import io.github.solcott.activitytracker.appview.db.RepoReindexes
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.exposed.v1.core.ResultRow

/** Where a reindex run stands (admin/repo-reindex). Stored by [code]. */
enum class ReindexState(val code: String) {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),

    /** The PDS refused the repo (taken down, suspended, deactivated, not found) or the account didn't resolve: nothing changed. */
    UNAVAILABLE("unavailable");

    val active: Boolean
        get() = this == QUEUED || this == RUNNING

    companion object {
        fun of(code: String) = entries.first { it.code == code }
    }
}

/** The step a running reindex is on. Stored by [code]. */
enum class ReindexPhase(val code: String) {
    RESOLVING("resolving"),
    FETCHING("fetching"),
    APPLYING("applying"),
    REMOVING("removing"),
    SPACES("spaces");

    companion object {
        fun of(code: String) = entries.first { it.code == code }
    }
}

/**
 * Counts of a run's records, all from the account's repo and in collections the AppView indexes: [seen] in the repo, [unchanged] already
 * indexed at the CID the repo has, [indexed] written, [invalid] dropped as undecodable, [removed] deleted as gone from the repo, and [live]
 * left alone because a newer live commit for them arrived during the run.
 */
data class ReindexCounts(
    val seen: Int = 0,
    val unchanged: Int = 0,
    val indexed: Int = 0,
    val invalid: Int = 0,
    val removed: Int = 0,
    val live: Int = 0,
)

/**
 * One reindex of [did], as an admin page shows it: identifiers, the repo revision read, counts, times and a short error, never record
 * content (admin/repo-reindex "Run records hold no record content"). Times are epoch ms.
 */
data class ReindexRun(
    val id: Long,
    val did: Did,
    val requestedBy: Did,
    val requestedAt: Long,
    val resyncSpaces: Boolean,
    val state: ReindexState,
    val phase: ReindexPhase?,
    val startedAt: Long?,
    val finishedAt: Long?,
    val rev: String?,
    val carBytes: Long?,
    val counts: ReindexCounts,
    val spacesResynced: Int?,
    /** A short, stable code (`too_large`, `bad_signature`, `repo_takendown`, `interrupted`…); null unless the run didn't succeed. */
    val errorCode: String?,
    val errorMessage: String?,
) {
    internal companion object {
        fun of(row: ResultRow) =
            ReindexRun(
                id = row[RepoReindexes.id],
                did = Did(row[RepoReindexes.did]),
                requestedBy = Did(row[RepoReindexes.requestedBy]),
                requestedAt = row[RepoReindexes.requestedAt],
                resyncSpaces = row[RepoReindexes.resyncSpaces],
                state = ReindexState.of(row[RepoReindexes.state]),
                phase = row[RepoReindexes.phase]?.let(ReindexPhase::of),
                startedAt = row[RepoReindexes.startedAt],
                finishedAt = row[RepoReindexes.finishedAt],
                rev = row[RepoReindexes.rev],
                carBytes = row[RepoReindexes.carBytes],
                counts =
                    ReindexCounts(
                        seen = row[RepoReindexes.recordsSeen],
                        unchanged = row[RepoReindexes.recordsUnchanged],
                        indexed = row[RepoReindexes.recordsIndexed],
                        invalid = row[RepoReindexes.recordsInvalid],
                        removed = row[RepoReindexes.recordsRemoved],
                        live = row[RepoReindexes.recordsLive],
                    ),
                spacesResynced = row[RepoReindexes.spacesResynced],
                errorCode = row[RepoReindexes.errorCode],
                errorMessage = row[RepoReindexes.errorMessage],
            )
    }
}
