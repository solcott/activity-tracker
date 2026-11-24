@file:Suppress("TooManyFunctions") // The one voting system both proposal kinds run: rules, reads, the adoption pass and deletes.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.notBlocked
import io.github.solcott.activitytracker.appview.api.notMuted
import io.github.solcott.activitytracker.appview.api.served
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.utcDate
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

// Community proposals and their votes: segment names (segments/segment-names) and trail detail suggestions
// (segments/trail-detail-suggestions) run one voting system, which reads each kind's tables through a [ProposalTable]. What differs
// between them (which version a label hides, what proposals compete for, the pin check, how a viewer's own vote is shown, the defaults)
// stays with each kind.

/** The adoption thresholds every proposal kind shares, each configurable under its env prefix ([RulesEnv]). */
interface VotingRules {
    /** The fewest up votes that adopt a proposal, however few riders. */
    val minUp: Int
    /** The share of the subject's riders in the last 365 days whose up votes adopt a proposal, in percent, rounded up. */
    val riderPercent: Int
    /** How long a proposal must be open before it can be adopted. */
    val minOpenDays: Int
    /** The up-minus-down score at or below which a proposal is rejected; negative. */
    val rejectScore: Int
    /** How long a proposal stays open without being adopted. */
    val expiryDays: Int
    /** How long after an adoption new proposals competing with it are ignored. */
    val cooldownDays: Int

    /** The up votes that adopt a proposal on a subject with [riders] distinct athletes in the last 365 days. */
    fun upNeeded(riders: Long): Long = maxOf(minUp.toLong(), (riders * riderPercent + PERCENT - 1) / PERCENT)
}

private const val PERCENT = 100

/** Reads `<prefix>_*` thresholds from [env], each falling back to its default when unset or invalid. */
internal class RulesEnv(private val env: Map<String, String>, private val prefix: String) {
    fun int(name: String, default: Int, valid: (Int) -> Boolean): Int =
        env["${prefix}_$name"]?.trim()?.toIntOrNull()?.takeIf(valid) ?: default

    fun positive(name: String, default: Int): Int = int(name, default) { it >= 1 }

    fun minUp(defaults: VotingRules): Int = positive("MIN_UP", defaults.minUp)

    fun riderPercent(defaults: VotingRules): Int = int("RIDER_PERCENT", defaults.riderPercent) { it in 0..PERCENT }

    fun minOpenDays(defaults: VotingRules): Int = int("MIN_OPEN_DAYS", defaults.minOpenDays) { it >= 0 }

    fun rejectScore(defaults: VotingRules): Int = int("REJECT_SCORE", defaults.rejectScore) { it <= -1 }

    fun expiryDays(defaults: VotingRules): Int = positive("EXPIRY_DAYS", defaults.expiryDays)

    fun cooldownDays(defaults: VotingRules): Int = int("COOLDOWN_DAYS", defaults.cooldownDays) { it >= 0 }
}

/** `segment_name_proposals.state` and `trail_detail_suggestions.suggestion_state` values. Withdrawn proposals have no row. */
object ProposalState {
    const val OPEN = "open"
    const val ADOPTED = "adopted"
    /** Closed because another proposal competing with it was adopted. */
    const val SUPERSEDED = "superseded"
    const val REJECTED = "rejected"
    const val EXPIRED = "expired"
}

/** A proposal's counted votes. */
data class Tally(val up: Int, val down: Int) {
    val score: Int
        get() = up - down

    companion object {
        val NONE = Tally(0, 0)
    }
}

/** A voter's counted vote on a proposal: its record's [uri], and whether it's [up]. */
internal data class CountedVote(val uri: String, val up: Boolean)

/** The totals of these counted votes, by voter. */
internal fun Map<String, CountedVote>.tally(): Tally = Tally(up = values.count { it.up }, down = values.count { !it.up })

internal const val DAY_MS = 24L * 60 * 60 * 1000

/** One vote row as the shared reads see it: the [proposal] it names at version [cid], by [voter]. */
internal data class VoteRow(val uri: String, val voter: String, val proposal: String, val cid: String?, val up: Boolean)

/** A proposal kind's votes table. */
@Suppress("LongParameterList") // One column per shared field, named at each kind's single declaration.
internal class ProposalVotes(
    val table: Table,
    val uri: Column<String>,
    val voter: Column<String>,
    /** Of a voter's votes on one proposal, the latest by this (then [uri]) counts. */
    val indexedAt: Column<Long>,
    /** The columns [read] reads. */
    val columns: List<Expression<*>>,
    /** Votes naming any of these proposals. */
    val on: (proposals: Collection<String>) -> Op<Boolean>,
    /** A row of [columns] as a vote, or null when it names no proposal. */
    val read: (ResultRow) -> VoteRow?,
)

/** A proposal kind's tables, as the shared machinery reads them. */
@Suppress("LongParameterList") // One column per shared field, named at each kind's single declaration.
internal class ProposalTable(
    val table: Table,
    val uri: Column<String>,
    /** The version votes count for. */
    val cid: Column<String>,
    /** The segment (or dive site) it proposes for. */
    val subject: Column<String>,
    val author: Column<String>,
    /** Within a subject, what proposals compete for (a trail detail); null when every proposal on a subject competes (names). */
    val topic: Column<String>?,
    val state: Column<String>,
    val openedAt: Column<Long>,
    val closedAt: Column<Long?>,
    val finalUp: Column<Int?>,
    val finalDown: Column<Int?>,
    val recordGone: Column<Boolean>,
    /** No label [value] on the proposal (at whichever versions this kind checks) or on its author. */
    val notLabelled: (value: String) -> Op<Boolean>,
    val votes: ProposalVotes,
) {
    /** Proposals with no `!hide` or `!takedown` on them or their author: only these are counted, adopted, or in effect. */
    fun unlabelled(): Op<Boolean> = notLabelled(LabelValues.HIDE) and notLabelled(LabelValues.TAKEDOWN)

    /**
     * The proposals a list shows [viewer] (a viewer that may vary per row): [unlabelled], except that their own stay listed to them under
     * `!hide`, as every hidden subject does for its author (moderation/labels). Never under `!takedown`. Anyone else's only while the
     * author's account is served ([served]).
     */
    fun listedTo(viewer: Expression<String>?): Op<Boolean> {
        val own = viewer?.let { author eq it } ?: Op.FALSE
        return notLabelled(LabelValues.TAKEDOWN) and (own or notLabelled(LabelValues.HIDE)) and served(viewer, author, null, null)
    }

    /**
     * The proposals a list shows a known [viewer]: [listedTo], and not by an athlete the viewer blocks, mutes or is blocked by.
     * `getSegment`'s name proposals and trail detail suggestions both list by it.
     */
    fun shownTo(viewer: Did?): Op<Boolean> =
        listedTo(viewer?.let { stringParam(it.value) }) and notBlocked(viewer, author) and notMuted(viewer, author)

    /** Proposals on [subject], and on [topic] too when this kind has topics and it's given. */
    fun on(subject: String, topic: String? = null): Op<Boolean> {
        val onSubject = this.subject eq subject
        val column = this.topic
        return if (column == null || topic == null) onSubject else onSubject and (column eq topic)
    }
}

/**
 * The counted votes on each of [proposals] (all on [subject]), by voter: per voter, their latest vote by first indexing, counted only while
 * it names the proposal's stored version, the voter has an effort on (a visit to) the subject, and the voter isn't the author. Who voted
 * never leaves the AppView: views serve only totals and the viewer's own vote.
 */
internal fun JdbcTransaction.countedVotes(
    kind: ProposalTable,
    subject: String,
    proposals: Collection<String>,
): Map<String, Map<String, CountedVote>> {
    if (proposals.isEmpty()) return emptyMap()
    val stored =
        kind.table
            .select(kind.uri, kind.cid, kind.author)
            .where { kind.uri inList proposals }
            .associate { it[kind.uri] to (it[kind.cid] to it[kind.author]) }
    // (proposal, voter) to the voter's latest vote on it.
    val latest = mutableMapOf<Pair<String, String>, VoteRow>()
    kind.votes.table
        .select(kind.votes.columns)
        .where { kind.votes.on(proposals) }
        .orderBy(kind.votes.indexedAt to SortOrder.ASC, kind.votes.uri to SortOrder.ASC)
        .forEach { row -> kind.votes.read(row)?.let { latest[it.proposal to it.voter] = it } }
    val riders = ridersAmong(subject, latest.keys.map { it.second }.toSet())
    return proposals.associateWith { proposal ->
        val (cid, author) = stored[proposal] ?: return@associateWith emptyMap()
        latest.values
            .filter { it.proposal == proposal && it.cid == cid && it.voter != author && it.voter in riders }
            .associate { it.voter to CountedVote(it.uri, it.up) }
    }
}

/** The counted totals of each of [proposals] (all on [subject]), by [countedVotes]. */
internal fun JdbcTransaction.countedTallies(kind: ProposalTable, subject: String, proposals: Collection<String>): Map<String, Tally> =
    countedVotes(kind, subject, proposals).mapValues { (_, votes) -> votes.tally() }

/** Whether [subject] (its [topic]) adopted a proposal still in effect within [cooldownDays] before [now]. */
internal fun JdbcTransaction.adoptedWithin(kind: ProposalTable, subject: String, topic: String?, now: Long, cooldownDays: Int): Boolean =
    kind.table
        .select(kind.uri)
        .where {
            kind.on(subject, topic) and
                (kind.state eq ProposalState.ADOPTED) and
                (kind.closedAt greater now - cooldownDays * DAY_MS) and
                kind.unlabelled()
        }
        .any()

/** [did]'s open proposal on [subject] (its [topic]), if any. */
internal fun JdbcTransaction.openProposalBy(kind: ProposalTable, subject: String, topic: String?, did: String): String? =
    kind.table
        .select(kind.uri)
        .where { kind.on(subject, topic) and (kind.author eq did) and (kind.state eq ProposalState.OPEN) }
        .firstOrNull()
        ?.get(kind.uri)

/** [subject]'s open proposals passing [listed], oldest first, at most [limit]: what a view lists. */
internal fun JdbcTransaction.openProposalRows(kind: ProposalTable, subject: String, listed: Op<Boolean>, limit: Int): List<ResultRow> =
    kind.table
        .selectAll()
        .where { kind.on(subject) and (kind.state eq ProposalState.OPEN) and listed }
        .orderBy(kind.openedAt to SortOrder.ASC, kind.uri to SortOrder.ASC)
        .limit(limit)
        .toList()

/** The subjects with an adopted proposal, or only those with one among or by [among] (record URIs and account DIDs) when given. */
internal fun JdbcTransaction.adoptedSubjects(kind: ProposalTable, among: Collection<String>? = null): List<String> =
    kind.table
        .select(kind.subject)
        .where {
            val adopted = kind.state eq ProposalState.ADOPTED
            if (among == null) adopted else adopted and ((kind.uri inList among) or (kind.author inList among))
        }
        .withDistinct()
        .map { it[kind.subject] }

/**
 * Deleted proposals: an adopted one keeps its row without its author ([ProposalTable.recordGone]), so the subject keeps what it adopted;
 * any other goes with its votes (an open one is withdrawn).
 */
internal fun JdbcTransaction.deleteProposals(kind: ProposalTable, uris: Collection<String>) {
    if (uris.isEmpty()) return
    kind.table.update({ (kind.uri inList uris) and (kind.state eq ProposalState.ADOPTED) }) { it[kind.recordGone] = true }
    val gone =
        kind.table.select(kind.uri).where { (kind.uri inList uris) and not(kind.state eq ProposalState.ADOPTED) }.map { it[kind.uri] }
    if (gone.isEmpty()) return
    kind.votes.table.deleteWhere { kind.votes.on(gone) }
    kind.table.deleteWhere { kind.uri inList gone }
}

/** [author]'s proposals ([deleteProposals]) and every vote of theirs, for a deleted account. */
internal fun JdbcTransaction.deleteProposalsBy(kind: ProposalTable, author: String) {
    deleteProposals(kind, kind.table.select(kind.uri).where { kind.author eq author }.map { it[kind.uri] })
    kind.votes.table.deleteWhere { kind.votes.voter eq author }
}

/** Every proposal on [subjects] and its votes, for deleted subjects. */
internal fun JdbcTransaction.deleteProposalsOn(kind: ProposalTable, subjects: Collection<String>) {
    if (subjects.isEmpty()) return
    val proposals = kind.table.select(kind.uri).where { kind.subject inList subjects }.map { it[kind.uri] }
    if (proposals.isNotEmpty()) kind.votes.table.deleteWhere { kind.votes.on(proposals) }
    kind.table.deleteWhere { kind.subject inList subjects }
}

/**
 * An adoption pass over one proposal kind, run by [SegmentMatcher] after each status pass. For every subject (and topic) with open
 * proposals it adopts at most one, where [mayAdopt] allows: open at least [VotingRules.minOpenDays], with up votes reaching
 * [VotingRules.upNeeded] for the subject's locals in the last 365 days and at least two-thirds of its votes up, the best score first and
 * then the earliest opened. Adopting closes the competing open proposals as superseded. Otherwise a proposal is rejected at
 * [VotingRules.rejectScore] or expires after [VotingRules.expiryDays]. Each closed proposal keeps its final totals. Then [recompute]
 * refreshes what's in effect.
 */
@Suppress("LongParameterList") // The kind, its rules, and the three places the kinds differ.
internal class ProposalAdoption(
    private val db: Database,
    private val clock: Clock,
    private val kind: ProposalTable,
    private val rules: VotingRules,
    /** Whether [subject] may adopt a proposal now. */
    private val mayAdopt: JdbcTransaction.(subject: String) -> Boolean,
    /** After a proposal on [subject] was adopted. */
    private val adopted: JdbcTransaction.(subject: String) -> Unit,
    /** After the pass: refreshes what's in effect on [only], or on every subject when it's null. */
    private val recompute: JdbcTransaction.(only: String?) -> Unit,
) {
    /** Runs the pass over every subject with open proposals, or only [only]. */
    suspend fun update(only: String?) {
        val now = clock.now().toEpochMilliseconds()
        val since = utcDate(now).minusDays(RIDER_WINDOW_DAYS - 1).toString()
        val topic = kind.topic
        db.tx {
            kind.table
                .select(listOfNotNull(kind.subject, topic))
                .where {
                    val open = kind.state eq ProposalState.OPEN
                    if (only == null) open else open and (kind.subject eq only)
                }
                .withDistinct()
                .map { it[kind.subject] to topic?.let { column -> it[column] } }
                .forEach { (subject, topicValue) -> updateGroup(subject, topicValue, now, since) }
            recompute(only)
        }
    }

    private fun JdbcTransaction.updateGroup(subject: String, topic: String?, now: Long, since: String) {
        val openHere = kind.on(subject, topic) and (kind.state eq ProposalState.OPEN)
        val open = kind.table.select(kind.uri, kind.openedAt).where { openHere }.associate { it[kind.uri] to it[kind.openedAt] }
        val counted = kind.table.select(kind.uri).where { openHere and kind.unlabelled() }.map { it[kind.uri] }.toSet()
        val tallies = countedTallies(kind, subject, counted)
        val adoptedUri = if (mayAdopt(subject)) adoptable(subject, open, tallies, now, since) else null
        for ((uri, openedAt) in open) {
            val tally = tallies[uri] ?: Tally.NONE
            val state =
                when {
                    uri == adoptedUri -> ProposalState.ADOPTED
                    adoptedUri != null -> ProposalState.SUPERSEDED
                    uri in counted && tally.score <= rules.rejectScore -> ProposalState.REJECTED
                    now - openedAt >= rules.expiryDays * DAY_MS -> ProposalState.EXPIRED
                    else -> continue
                }
            kind.table.update({ kind.uri eq uri }) {
                it[kind.state] = state
                it[kind.closedAt] = now
                it[kind.finalUp] = tally.up
                it[kind.finalDown] = tally.down
            }
        }
        if (adoptedUri != null) adopted(subject)
    }

    /** The proposal to adopt among [open] (each with when it opened), if any qualifies by [tallies]. */
    private fun JdbcTransaction.adoptable(
        subject: String,
        open: Map<String, Long>,
        tallies: Map<String, Tally>,
        now: Long,
        since: String,
    ): String? {
        val needed = rules.upNeeded(recentLocals(NamedPlace.of(subject), since))
        return tallies
            .filter { (uri, tally) ->
                val openedAt = open[uri] ?: return@filter false
                now - openedAt >= rules.minOpenDays * DAY_MS && tally.up >= needed && THIRDS * tally.up >= 2 * (tally.up + tally.down)
            }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Tally>> { it.value.score }.thenBy { open[it.key] }.thenBy { it.key })
            .firstOrNull()
            ?.key
    }

    private companion object {
        const val RIDER_WINDOW_DAYS = 365L
        const val THIRDS = 3
    }
}
