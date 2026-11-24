@file:Suppress("TooManyFunctions") // The naming rules and the reads the indexer, the naming pass and the views share.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.api.served
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

// Community names for segments (segments/segment-names): what the indexer, the naming pass in SegmentMatcher and SegmentViews share, so
// the flag a viewer sees and the checks a proposal meets can't disagree.

/** The thresholds of segment naming, each configurable (`SEGMENT_NAME_*`, [fromEnv]); the shared ones are [VotingRules]. */
@Suppress("MagicNumber") // The defaults, as segments/segment-names gives them.
data class SegmentNameRules(
    /** Distinct local dates with efforts a proposer needs. */
    val proposerDays: Int = 5,
    override val minUp: Int = 5,
    override val riderPercent: Int = 10,
    override val minOpenDays: Int = 7,
    override val rejectScore: Int = -5,
    override val expiryDays: Int = 90,
    /** How long after an adoption new proposals are ignored. */
    override val cooldownDays: Int = 90,
) : VotingRules {
    companion object {
        /** The rules from `SEGMENT_NAME_*`, each falling back to its default when unset or invalid. */
        fun fromEnv(env: Map<String, String>): SegmentNameRules {
            val defaults = SegmentNameRules()
            val read = RulesEnv(env, "SEGMENT_NAME")
            return SegmentNameRules(
                proposerDays = read.positive("PROPOSER_DAYS", defaults.proposerDays),
                minUp = read.minUp(defaults),
                riderPercent = read.riderPercent(defaults),
                minOpenDays = read.minOpenDays(defaults),
                rejectScore = read.rejectScore(defaults),
                expiryDays = read.expiryDays(defaults),
                cooldownDays = read.cooldownDays(defaults),
            )
        }
    }
}

/**
 * Name proposals and their votes, for the shared voting machinery: every proposal on a segment competes, and a label counts only on the
 * stored version ([notLabelled]).
 */
internal val NAME_PROPOSALS =
    ProposalTable(
        table = SegmentNameProposals,
        uri = SegmentNameProposals.uri,
        cid = SegmentNameProposals.cid,
        subject = SegmentNameProposals.segmentUri,
        author = SegmentNameProposals.proposerDid,
        topic = null,
        state = SegmentNameProposals.state,
        openedAt = SegmentNameProposals.openedAt,
        closedAt = SegmentNameProposals.closedAt,
        finalUp = SegmentNameProposals.finalUp,
        finalDown = SegmentNameProposals.finalDown,
        recordGone = SegmentNameProposals.recordGone,
        notLabelled = ::notLabelled,
        votes =
            ProposalVotes(
                table = SegmentNameVotes,
                uri = SegmentNameVotes.uri,
                voter = SegmentNameVotes.voterDid,
                indexedAt = SegmentNameVotes.indexedAt,
                columns =
                    listOf(
                        SegmentNameVotes.uri,
                        SegmentNameVotes.proposalUri,
                        SegmentNameVotes.voterDid,
                        SegmentNameVotes.proposalCid,
                        SegmentNameVotes.up,
                    ),
                on = { proposals -> SegmentNameVotes.proposalUri inList proposals },
                read = { row ->
                    VoteRow(
                        uri = row[SegmentNameVotes.uri],
                        voter = row[SegmentNameVotes.voterDid],
                        proposal = row[SegmentNameVotes.proposalUri],
                        cid = row[SegmentNameVotes.proposalCid],
                        up = row[SegmentNameVotes.up],
                    )
                },
            ),
    )

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
internal fun unlabelledProposal(): Op<Boolean> = NAME_PROPOSALS.unlabelled()

/**
 * The open proposals a list shows [viewer]: [unlabelledProposal], except that their own stay listed to them under `!hide`, as every hidden
 * subject does for its author (moderation/labels), so they can still see and withdraw the one open proposal [mayPropose] counts. Never
 * under `!takedown`, and never counted or adopted. Anyone else's only while the proposer's account is served ([served]).
 */
internal fun listedProposal(viewer: Did?): Op<Boolean> = listedProposalTo(viewer?.let { stringParam(it.value) })

/** [listedProposal] for a [viewer] that may vary per row, such as a quote's author in the outer query. */
internal fun listedProposalTo(viewer: Expression<String>?): Op<Boolean> = NAME_PROPOSALS.listedTo(viewer)

/** No [value] on the proposal's record (at the stored version) or its proposer's account. */
private fun notLabelled(value: String): Op<Boolean> =
    not(labelled(SegmentNameProposals.uri, value, SegmentNameProposals.cid)) and not(labelled(SegmentNameProposals.proposerDid, value))

/**
 * What a name proposal names (dive-sites/site-names): a segment, whose locals are the athletes with efforts on it, or a dive site, whose
 * locals are the athletes with visits to it. Every naming rule reads its locals through here, so both follow `segments/segment-names`.
 */
internal sealed interface NamedPlace {
    val uri: String

    data class Segment(override val uri: String) : NamedPlace

    data class DiveSite(override val uri: String) : NamedPlace

    companion object {
        fun of(uri: String): NamedPlace = if (isDiveSiteUri(uri)) DiveSite(uri) else Segment(uri)
    }
}

/** Whether [place] may adopt and take names: an active run segment (never a lift, segments/segment-lifts), or an active dive site. */
internal fun JdbcTransaction.nameable(place: NamedPlace): Boolean =
    when (place) {
        is NamedPlace.Segment ->
            Segments.select(Segments.status, Segments.kind)
                .where { Segments.uri eq place.uri }
                .firstOrNull()
                ?.let { it[Segments.status] == SegmentStatus.ACTIVE && SegmentKind.isRanked(it[Segments.kind]) } == true
        is NamedPlace.DiveSite ->
            DiveSites.select(DiveSites.status).where { DiveSites.uri eq place.uri }.firstOrNull()?.get(DiveSites.status) ==
                SegmentStatus.ACTIVE
    }

/** How many distinct athletes have efforts on (visits to) [place] dated [since] or later, of every visibility: adoption's denominator. */
internal fun JdbcTransaction.recentLocals(place: NamedPlace, since: String): Long =
    when (place) {
        is NamedPlace.Segment ->
            SegmentEfforts.select(SegmentEfforts.authorDid)
                .where { (SegmentEfforts.segmentUri eq place.uri) and (SegmentEfforts.startedOn greaterEq since) }
                .withDistinct()
                .count()
        is NamedPlace.DiveSite ->
            DiveSiteVisits.select(DiveSiteVisits.authorDid)
                .where { (DiveSiteVisits.siteUri eq place.uri) and (DiveSiteVisits.startedOn greaterEq since) }
                .withDistinct()
                .count()
    }

/**
 * How many distinct local dates [did] has efforts on [segment] within its sport group's speed cap, or visits to it for a dive site, of
 * every visibility.
 */
internal fun JdbcTransaction.proposerDays(segment: String, did: String): Long =
    if (isDiveSiteUri(segment))
        DiveSiteVisits.select(DiveSiteVisits.startedOn)
            .where { (DiveSiteVisits.siteUri eq segment) and (DiveSiteVisits.authorDid eq did) }
            .withDistinct()
            .count()
    else
        SegmentEfforts.join(Segments, JoinType.INNER, SegmentEfforts.segmentUri, Segments.uri)
            .select(SegmentEfforts.startedOn)
            .where { (SegmentEfforts.segmentUri eq segment) and (SegmentEfforts.authorDid eq did) and plausibleEffort() }
            .withDistinct()
            .count()

/** Which of [dids] have at least one effort on [segment] (a visit, for a dive site), of any visibility: the athletes whose votes count. */
internal fun JdbcTransaction.ridersAmong(segment: String, dids: Collection<String>): Set<String> =
    if (dids.isEmpty()) {
        emptySet()
    } else if (isDiveSiteUri(segment)) {
        DiveSiteVisits.select(DiveSiteVisits.authorDid)
            .where { (DiveSiteVisits.siteUri eq segment) and (DiveSiteVisits.authorDid inList dids) }
            .withDistinct()
            .map { it[DiveSiteVisits.authorDid] }
            .toSet()
    } else {
        SegmentEfforts.select(SegmentEfforts.authorDid)
            .where { (SegmentEfforts.segmentUri eq segment) and (SegmentEfforts.authorDid inList dids) }
            .withDistinct()
            .map { it[SegmentEfforts.authorDid] }
            .toSet()
    }

/** Of [subjects] (segments and dive sites), those [did] has an effort or visit on, of any visibility: [ridersAmong] for one athlete. */
internal fun JdbcTransaction.riddenBy(did: String, subjects: Collection<String>): Set<String> {
    val (sites, segments) = subjects.partition(::isDiveSiteUri)
    val visited =
        if (sites.isEmpty()) emptyList()
        else
            DiveSiteVisits.select(DiveSiteVisits.siteUri)
                .where { (DiveSiteVisits.authorDid eq did) and (DiveSiteVisits.siteUri inList sites) }
                .withDistinct()
                .map { it[DiveSiteVisits.siteUri] }
    val ridden =
        if (segments.isEmpty()) emptyList()
        else
            SegmentEfforts.select(SegmentEfforts.segmentUri)
                .where { (SegmentEfforts.authorDid eq did) and (SegmentEfforts.segmentUri inList segments) }
                .withDistinct()
                .map { it[SegmentEfforts.segmentUri] }
    return (visited + ridden).toSet()
}

/** Whether [segment] adopted a name still in effect within [SegmentNameRules.cooldownDays] before [now]. */
internal fun JdbcTransaction.inCooldown(segment: String, now: Long, rules: SegmentNameRules): Boolean =
    adoptedWithin(NAME_PROPOSALS, segment, null, now, rules.cooldownDays)

/** [did]'s open proposal on [segment], if any. */
internal fun JdbcTransaction.openProposalOf(segment: String, did: String): String? = openProposalBy(NAME_PROPOSALS, segment, null, did)

/**
 * Whether [name], trimmed and ignoring case, is [segment]'s current name or the name of one of its open proposals other than [except]: a
 * proposal or edit with such a name is ignored.
 */
internal fun JdbcTransaction.nameTaken(segment: String, name: String, except: String? = null): Boolean {
    val key = name.trim().lowercase()
    val current =
        if (isDiveSiteUri(segment))
            DiveSites.select(DiveSites.currentName).where { DiveSites.uri eq segment }.firstOrNull()?.get(DiveSites.currentName)
        else Segments.select(Segments.currentName).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.currentName)
    if (current?.trim()?.lowercase() == key) return true
    return SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.name)
        .where { (SegmentNameProposals.segmentUri eq segment) and (SegmentNameProposals.state eq ProposalState.OPEN) }
        .any { it[SegmentNameProposals.uri] != except && it[SegmentNameProposals.name].trim().lowercase() == key }
}

/**
 * Whether [did] may propose a name for [segment] at [now]: it's an active run (never a lift, segments/segment-lifts) with no pinned name
 * (segments/segment-pins), outside a cooldown, they have no open proposal on it, and they have efforts on it on
 * [SegmentNameRules.proposerDays] dates. The indexer and `getSegment`'s flag both ask this.
 */
internal fun JdbcTransaction.mayPropose(segment: String, did: String, now: Long, rules: SegmentNameRules): Boolean =
    nameable(NamedPlace.of(segment)) &&
        !hasNamePin(segment) &&
        !inCooldown(segment, now, rules) &&
        openProposalOf(segment, did) == null &&
        proposerDays(segment, did) >= rules.proposerDays

/**
 * The counted votes on each of [proposals] (all on [segment]), by [countedVotes]: per voter, their latest vote by first indexing, counted
 * only while it names the proposal's stored version, the voter has an effort on the segment, and the voter isn't the proposer. The naming
 * pass and the views both read totals here.
 */
internal fun JdbcTransaction.tallies(segment: String, proposals: Collection<String>): Map<String, Tally> =
    countedTallies(NAME_PROPOSALS, segment, proposals)

/**
 * Sets [segment]'s current name: its pinned name in effect ([namePinInEffect]), else its latest adopted proposal in effect
 * ([unlabelledProposal]), else its record's name.
 */
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
    if (isDiveSiteUri(segment)) {
        val name = adopted ?: DiveSites.select(DiveSites.name).where { DiveSites.uri eq segment }.firstOrNull()?.get(DiveSites.name)
        if (name != null) DiveSites.update({ (DiveSites.uri eq segment) and not(DiveSites.currentName eq name) }) { it[currentName] = name }
    } else {
        // An operator's pinned name in effect beats every adopted one (segments/segment-pins).
        val name =
            namePinInEffect(segment)
                ?: adopted
                ?: Segments.select(Segments.name).where { Segments.uri eq segment }.firstOrNull()?.get(Segments.name)
        if (name != null) Segments.update({ (Segments.uri eq segment) and not(Segments.currentName eq name) }) { it[currentName] = name }
    }
}

/** Whether [uri] names a `feed.diveSite`, which name proposals may name as well as a segment (dive-sites/site-names). */
internal fun isDiveSiteUri(uri: String): Boolean = runCatching { AtUri(uri).collection == Nsid.FEED_DIVE_SITE }.getOrDefault(false)

/**
 * After labels changed on [subjects] (record URIs and account DIDs): recomputes the current name of every segment with an adopted proposal
 * among them or by one of them.
 */
internal fun JdbcTransaction.recomputeNamesLabelled(subjects: Collection<String>) {
    if (subjects.isEmpty()) return
    adoptedSubjects(NAME_PROPOSALS, among = subjects).plus(segmentsWithProposalNamePins(subjects)).toSet().forEach {
        recomputeCurrentName(it)
    }
}

/**
 * Recomputes the current name of every segment with an adopted proposal or a name pinned from a proposal: the naming pass's backstop, which
 * also catches expired labels.
 */
internal fun JdbcTransaction.recomputeAllAdoptedNames() {
    adoptedSubjects(NAME_PROPOSALS).plus(segmentsWithProposalNamePins()).toSet().forEach { recomputeCurrentName(it) }
}

/** The proposals on [segments] and their votes, for deleted segments. */
internal fun JdbcTransaction.deleteSegmentNames(segments: Collection<String>) = deleteProposalsOn(NAME_PROPOSALS, segments)
