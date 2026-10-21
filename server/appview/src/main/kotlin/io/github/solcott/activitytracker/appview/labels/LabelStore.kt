package io.github.solcott.activitytracker.appview.labels

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.LabelCursors
import io.github.solcott.activitytracker.appview.db.Labels
import io.github.solcott.activitytracker.appview.db.tx
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Applies verified labels (`moderation/labels`). Only the configured labelers count: the app's own for any subject, Bluesky's only for an
 * indexed athlete's account DID. Per source, subject and value the newest `cts` wins; a negation removes the label, and an `exp` is kept so
 * reads skip it once past.
 */
class LabelStore(
    private val db: Database,
    /** The app's labeler DID, trusted for every subject. */
    private val own: String?,
    /** Bluesky's labeler DID, trusted for athletes' accounts only. */
    private val bsky: String?,
    private val clock: Clock = Clock.System,
) {
    /** The labels of one `#labels` frame from [source], then its [seq] as that stream's cursor, in one transaction. */
    suspend fun apply(source: String, seq: Long, labels: List<Label>) {
        // Bluesky labels everything on the network: drop what can't apply before touching the database.
        val candidates = labels.filter { trusted(source, it) }
        db.tx {
            val athletes =
                if (source == bsky && candidates.isNotEmpty()) {
                    Actors.select(Actors.did)
                        .where { Actors.did inList candidates.map { it.uri }.distinct() }
                        .map { it[Actors.did] }
                        .toSet()
                } else {
                    emptySet()
                }
            candidates.filter { source == own || it.uri in athletes }.forEach(::store)
            LabelCursors.upsert {
                it[src] = source
                it[LabelCursors.seq] = seq
            }
        }
    }

    /** The stored cursor of [source]'s stream; null before its first frame. */
    suspend fun cursor(source: String): Long? = db.tx {
        LabelCursors.selectAll().where { LabelCursors.src eq source }.singleOrNull()?.get(LabelCursors.seq)
    }

    /** Deletes labels whose `exp` has passed; reads skip them already, so this only keeps the table small. */
    suspend fun sweepExpired() = db.tx {
        val now = clock.now().toEpochMilliseconds()
        Labels.deleteWhere { exp.isNotNull() and (exp lessEq now) }
    }

    private fun trusted(source: String, label: Label): Boolean =
        label.src == source &&
            when (source) {
                own -> true
                bsky -> label.uri.startsWith("did:")
                else -> false
            }

    private fun store(label: Label) {
        val cts = runCatching { label.cts.toEpochMilliseconds() }.getOrNull() ?: return
        val key = Labels.src eq label.src and (Labels.uri eq label.uri) and (Labels.value eq label.value)
        val current = Labels.select(Labels.cts).where { key }.singleOrNull()?.get(Labels.cts)
        when {
            // An older label (replayed, or out of order) never undoes a newer one.
            current != null && current > cts -> Unit
            label.neg -> Labels.deleteWhere { key }
            else ->
                Labels.upsert {
                    it[src] = label.src
                    it[uri] = label.uri.take(URI_LENGTH)
                    it[value] = label.value.take(VALUE_LENGTH)
                    it[cid] = label.cid?.take(CID_LENGTH)
                    it[Labels.cts] = cts
                    it[exp] = runCatching { label.exp?.toEpochMilliseconds() }.getOrNull()
                    it[indexedAt] = clock.now().toEpochMilliseconds()
                }
        }
    }

    private companion object {
        const val URI_LENGTH = 512
        const val VALUE_LENGTH = 128
        const val CID_LENGTH = 255
    }
}
