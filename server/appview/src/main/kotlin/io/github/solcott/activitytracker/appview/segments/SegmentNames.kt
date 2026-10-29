@file:Suppress("TooManyFunctions") // The naming rules and the reads the indexer, the naming pass and the views share.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

// Community names for segments (segments/segment-names): what the indexer, the naming pass in SegmentMatcher and SegmentViews share, so
// the flag a viewer sees and the checks a proposal meets can't disagree.

/** The thresholds of segment naming, each configurable (`SEGMENT_NAME_*`, [fromEnv]). */
@Suppress("MagicNumber") // The defaults, as segments/segment-names gives them.
data class SegmentNameRules(
    /** Distinct local dates with efforts a proposer needs. */
    val proposerDays: Int = 5,
    /** The fewest up votes that adopt a proposal, however few riders. */
    val minUp: Int = 5,
    /** The share of the segment's riders in the last 365 days whose up votes adopt a proposal, in percent, rounded up. */
    val riderPercent: Int = 10,
    /** How long a proposal must be open before it can be adopted. */
    val minOpenDays: Int = 7,
    /** The up-minus-down score at or below which a proposal is rejected; negative. */
    val rejectScore: Int = -5,
    /** How long a proposal stays open without being adopted. */
    val expiryDays: Int = 90,
    /** How long after an adoption new proposals are ignored. */
    val cooldownDays: Int = 90,
) {
    /** The up votes that adopt a proposal on a segment with [riders] distinct athletes in the last 365 days. */
    fun upNeeded(riders: Long): Long = maxOf(minUp.toLong(), (riders * riderPercent + PERCENT - 1) / PERCENT)

    companion object {
        private const val PERCENT = 100

        /** The rules from `SEGMENT_NAME_*`, each falling back to its default when unset or invalid. */
        fun fromEnv(env: Map<String, String>): SegmentNameRules {
            val defaults = SegmentNameRules()
            fun positive(key: String, default: Int) = env[key]?.trim()?.toIntOrNull()?.takeIf { it >= 1 } ?: default
            return SegmentNameRules(
                proposerDays = positive("SEGMENT_NAME_PROPOSER_DAYS", defaults.proposerDays),
                minUp = positive("SEGMENT_NAME_MIN_UP", defaults.minUp),
                riderPercent =
                    env["SEGMENT_NAME_RIDER_PERCENT"]?.trim()?.toIntOrNull()?.takeIf { it in 0..PERCENT } ?: defaults.riderPercent,
                minOpenDays = env["SEGMENT_NAME_MIN_OPEN_DAYS"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.minOpenDays,
                rejectScore = env["SEGMENT_NAME_REJECT_SCORE"]?.trim()?.toIntOrNull()?.takeIf { it <= -1 } ?: defaults.rejectScore,
                expiryDays = positive("SEGMENT_NAME_EXPIRY_DAYS", defaults.expiryDays),
                cooldownDays = env["SEGMENT_NAME_COOLDOWN_DAYS"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.cooldownDays,
            )
        }
    }
}

/** `segment_name_proposals.state` values. Withdrawn proposals have no row. */
object ProposalState {
    const val OPEN = "open"
    const val ADOPTED = "adopted"
    /** Closed because another proposal on the segment was adopted. */
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

internal const val DAY_MS = 24L * 60 * 60 * 1000
private const val MM_PER_M = 1_000L

/** The effort's average speed is within its segment's group cap: `elapsed * cap >= distance * 1000`, in integers both databases run. */
internal fun plausibleEffort(): Op<Boolean> =
    SegmentSportGroup.all
        .map { group ->
            (Segments.sport eq group) and
                (SegmentEfforts.elapsedS.times(SegmentSportGroup.maxSpeedMmPerS(group).toLong()) greaterEq
                    Segments.distanceM.times(MM_PER_M))
        }
        .reduce { a, b -> a or b }

/**
 * Proposals with no `!hide` or `!takedown` on their record (at the stored version) or their proposer's account: only these are listed,
 * counted, adopted, or in effect.
 */
internal fun unlabelledProposal(): Op<Boolean> =
    listOf(LabelValues.HIDE, LabelValues.TAKEDOWN)
        .map { value ->
            not(labelled(SegmentNameProposals.uri, value, SegmentNameProposals.cid)) and
                not(labelled(SegmentNameProposals.proposerDid, value))
        }
        .reduce { a, b -> a and b }

/** How many distinct local dates [did] has efforts on [segment] within its sport group's speed cap, of every visibility. */
internal fun JdbcTransaction.proposerDays(segment: String, did: String): Long =
    SegmentEfforts.join(Segments, JoinType.INNER, SegmentEfforts.segmentUri, Segments.uri)
        .select(SegmentEfforts.startedOn)
        .where { (SegmentEfforts.segmentUri eq segment) and (SegmentEfforts.authorDid eq did) and plausibleEffort() }
        .withDistinct()
        .count()

/** Which of [dids] have at least one effort on [segment], of any visibility: the athletes whose votes count. */
internal fun JdbcTransaction.ridersAmong(segment: String, dids: Collection<String>): Set<String> =
    if (dids.isEmpty()) {
        emptySet()
    } else {
        SegmentEfforts.select(SegmentEfforts.authorDid)
            .where { (SegmentEfforts.segmentUri eq segment) and (SegmentEfforts.authorDid inList dids) }
            .withDistinct()
            .map { it[SegmentEfforts.authorDid] }
            .toSet()
    }

/** Whether [segment] adopted a name still in effect within [SegmentNameRules.cooldownDays] before [now]. */
internal fun JdbcTransaction.inCooldown(segment: String, now: Long, rules: SegmentNameRules): Boolean =
    SegmentNameProposals.select(SegmentNameProposals.uri)
        .where {
            (SegmentNameProposals.segmentUri eq segment) and
                (SegmentNameProposals.state eq ProposalState.ADOPTED) and
                (SegmentNameProposals.closedAt greater now - rules.cooldownDays * DAY_MS) and
                unlabelledProposal()
        }
        .any()

/** [did]'s open proposal on [segment], if any. */
internal fun JdbcTransaction.openProposalOf(segment: String, did: String): String? =
    SegmentNameProposals.select(SegmentNameProposals.uri)
        .where {
            (SegmentNameProposals.segmentUri eq segment) and
                (SegmentNameProposals.proposerDid eq did) and
                (SegmentNameProposals.state eq ProposalState.OPEN)
        }
        .firstOrNull()
        ?.get(SegmentNameProposals.uri)

/**
 * Whether [name], trimmed and ignoring case, is [segment]'s current name or the name of one of its open proposals other than [except]: a
 * proposal or edit with such a name is ignored.
 */
internal fun JdbcTransaction.nameTaken(segment: String, name: String, except: String? = null): Boolean {
    val key = name.trim().lowercase()
    val current = Segments.select(Segments.currentName).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.currentName)
    if (current?.trim()?.lowercase() == key) return true
    return SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.name)
        .where { (SegmentNameProposals.segmentUri eq segment) and (SegmentNameProposals.state eq ProposalState.OPEN) }
        .any { it[SegmentNameProposals.uri] != except && it[SegmentNameProposals.name].trim().lowercase() == key }
}

/**
 * Whether [did] may propose a name for [segment] at [now]: it's active and outside a cooldown, they have no open proposal on it, and they
 * have efforts on it on [SegmentNameRules.proposerDays] dates. The indexer and `getSegment`'s flag both ask this.
 */
internal fun JdbcTransaction.mayPropose(segment: String, did: String, now: Long, rules: SegmentNameRules): Boolean {
    val status = Segments.select(Segments.status).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.status)
    return status == SegmentStatus.ACTIVE &&
        !inCooldown(segment, now, rules) &&
        openProposalOf(segment, did) == null &&
        proposerDays(segment, did) >= rules.proposerDays
}

/**
 * The counted votes on each of [proposals] (all on [segment]): per voter, their latest vote by first indexing, counted only while it names
 * the proposal's stored version, the voter has an effort on the segment, and the voter isn't the proposer. The naming pass and the views
 * both read totals here.
 */
internal fun JdbcTransaction.tallies(segment: String, proposals: Collection<String>): Map<String, Tally> {
    if (proposals.isEmpty()) return emptyMap()
    return latestVotes(segment, proposals).mapValues { (_, votes) ->
        Tally(up = votes.values.count { it }, down = votes.values.count { !it })
    }
}

/** Per proposal in [proposals], each counted voter's latest vote (true for up), by the rules of [tallies]. */
private fun JdbcTransaction.latestVotes(segment: String, proposals: Collection<String>): Map<String, Map<String, Boolean>> {
    val stored =
        SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.cid, SegmentNameProposals.proposerDid)
            .where { SegmentNameProposals.uri inList proposals }
            .associate { it[SegmentNameProposals.uri] to (it[SegmentNameProposals.cid] to it[SegmentNameProposals.proposerDid]) }
    val latest = mutableMapOf<Pair<String, String>, Pair<String, Boolean>>()
    SegmentNameVotes.select(SegmentNameVotes.proposalUri, SegmentNameVotes.voterDid, SegmentNameVotes.proposalCid, SegmentNameVotes.up)
        .where { SegmentNameVotes.proposalUri inList proposals }
        .orderBy(SegmentNameVotes.indexedAt to SortOrder.ASC, SegmentNameVotes.uri to SortOrder.ASC)
        .forEach { row ->
            latest[row[SegmentNameVotes.proposalUri] to row[SegmentNameVotes.voterDid]] =
                row[SegmentNameVotes.proposalCid] to row[SegmentNameVotes.up]
        }
    val riders = ridersAmong(segment, latest.keys.map { it.second }.toSet())
    return proposals.associateWith { proposal ->
        val (cid, proposer) = stored[proposal] ?: return@associateWith emptyMap()
        latest
            .filter { (key, vote) -> key.first == proposal && vote.first == cid && key.second != proposer && key.second in riders }
            .map { (key, vote) -> key.second to vote.second }
            .toMap()
    }
}

/** Sets [segment]'s current name: its latest adopted proposal in effect ([unlabelledProposal]), else its record's name. */
internal fun JdbcTransaction.recomputeCurrentName(segment: String) {
    val adopted =
        SegmentNameProposals.select(SegmentNameProposals.name)
            .where {
                (SegmentNameProposals.segmentUri eq segment) and
                    (SegmentNameProposals.state eq ProposalState.ADOPTED) and
                    unlabelledProposal()
            }
            .orderBy(SegmentNameProposals.closedAt to SortOrder.DESC, SegmentNameProposals.uri to SortOrder.DESC)
            .firstOrNull()
            ?.get(SegmentNameProposals.name)
    val name = adopted ?: Segments.select(Segments.name).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.name) ?: return
    Segments.update({ (Segments.uri eq segment) and not(Segments.currentName eq name) }) { it[currentName] = name }
}

/**
 * After labels changed on [subjects] (record URIs and account DIDs): recomputes the current name of every segment with an adopted proposal
 * among them or by one of them.
 */
internal fun JdbcTransaction.recomputeNamesLabelled(subjects: Collection<String>) {
    if (subjects.isEmpty()) return
    SegmentNameProposals.select(SegmentNameProposals.segmentUri)
        .where {
            (SegmentNameProposals.state eq ProposalState.ADOPTED) and
                ((SegmentNameProposals.uri inList subjects) or (SegmentNameProposals.proposerDid inList subjects))
        }
        .withDistinct()
        .map { it[SegmentNameProposals.segmentUri] }
        .forEach { recomputeCurrentName(it) }
}

/** Recomputes the current name of every segment with an adopted proposal: the naming pass's backstop, which also catches expired labels. */
internal fun JdbcTransaction.recomputeAllAdoptedNames() {
    SegmentNameProposals.select(SegmentNameProposals.segmentUri)
        .where { SegmentNameProposals.state eq ProposalState.ADOPTED }
        .withDistinct()
        .map { it[SegmentNameProposals.segmentUri] }
        .forEach { recomputeCurrentName(it) }
}

/** The proposals on [segments] and their votes, for deleted segments. */
internal fun JdbcTransaction.deleteSegmentNames(segments: Collection<String>) {
    if (segments.isEmpty()) return
    val proposals =
        SegmentNameProposals.select(SegmentNameProposals.uri)
            .where { SegmentNameProposals.segmentUri inList segments }
            .map { it[SegmentNameProposals.uri] }
    if (proposals.isNotEmpty()) SegmentNameVotes.deleteWhere { proposalUri inList proposals }
    SegmentNameProposals.deleteWhere { segmentUri inList segments }
}
