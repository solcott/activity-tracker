@file:Suppress("TooManyFunctions") // The suggestion rules and the reads the indexer, the adoption pass and the views share.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.TrailAttributesSanitizer
import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.TrailDetailVotes
import io.github.solcott.activitytracker.appview.utcDate
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailViewerVote
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

// Crowd-sourced trail details (segments/trail-detail-suggestions): what the indexer, the adoption pass and SegmentViews share, so the
// flags a viewer sees and the checks a suggestion meets can't disagree.

/** The thresholds of trail detail suggestions, each configurable (`TRAIL_DETAIL_*`, [fromEnv]); the shared ones are [VotingRules]. */
@Suppress("MagicNumber") // The defaults, as segments/trail-detail-suggestions gives them.
data class TrailDetailRules(
    /** Distinct local dates with efforts a suggester needs. */
    val suggesterDays: Int = 5,
    override val minUp: Int = 3,
    override val riderPercent: Int = 10,
    override val minOpenDays: Int = 7,
    override val rejectScore: Int = -5,
    override val expiryDays: Int = 90,
    /** How long after an adoption new suggestions on that detail are ignored. */
    override val cooldownDays: Int = 30,
) : VotingRules {
    companion object {
        /** The rules from `TRAIL_DETAIL_*`, each falling back to its default when unset or invalid. */
        fun fromEnv(env: Map<String, String>): TrailDetailRules {
            val defaults = TrailDetailRules()
            val read = RulesEnv(env, "TRAIL_DETAIL")
            return TrailDetailRules(
                suggesterDays = read.positive("SUGGESTER_DAYS", defaults.suggesterDays),
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
 * Trail detail suggestions and their votes (confirmations aside), for the shared voting machinery: suggestions compete per detail, and a
 * label counts at any version ([notLabelled]).
 */
internal val TRAIL_SUGGESTIONS =
    ProposalTable(
        table = TrailDetailSuggestions,
        uri = TrailDetailSuggestions.uri,
        cid = TrailDetailSuggestions.cid,
        subject = TrailDetailSuggestions.segmentUri,
        author = TrailDetailSuggestions.authorDid,
        topic = TrailDetailSuggestions.detail,
        state = TrailDetailSuggestions.state,
        openedAt = TrailDetailSuggestions.openedAt,
        closedAt = TrailDetailSuggestions.closedAt,
        finalUp = TrailDetailSuggestions.finalUp,
        finalDown = TrailDetailSuggestions.finalDown,
        recordGone = TrailDetailSuggestions.recordGone,
        notLabelled = ::notLabelled,
        votes =
            ProposalVotes(
                table = TrailDetailVotes,
                uri = TrailDetailVotes.uri,
                voter = TrailDetailVotes.voterDid,
                indexedAt = TrailDetailVotes.indexedAt,
                columns =
                    listOf(
                        TrailDetailVotes.uri,
                        TrailDetailVotes.suggestionUri,
                        TrailDetailVotes.voterDid,
                        TrailDetailVotes.suggestionCid,
                        TrailDetailVotes.direction,
                    ),
                on = { suggestions -> TrailDetailVotes.suggestionUri inList suggestions },
                read = { row ->
                    row[TrailDetailVotes.suggestionUri]?.let { suggestion ->
                        VoteRow(
                            uri = row[TrailDetailVotes.uri],
                            voter = row[TrailDetailVotes.voterDid],
                            proposal = suggestion,
                            cid = row[TrailDetailVotes.suggestionCid],
                            up = row[TrailDetailVotes.direction] == TrailDetailVoteDirection.UP,
                        )
                    }
                },
            ),
    )

/** One detail's adopted community value in effect, as [Segments.trailCommunity] holds it: [riders] are the up votes that adopted it. */
@Serializable internal data class CommunityValue(val detail: String, val value: TrailAttributes, val riders: Int)

private val COMMUNITY = ListSerializer(CommunityValue.serializer())

/** [value]'s fields for [detail] alone, width as its kind, encoded: what "the same value" means when comparing. Null when it has none. */
internal fun normalizedValue(detail: String, value: TrailAttributes): String? =
    TrailDetail.valueOf(value, detail)?.let { AtJson.encodeToString(TrailAttributes.serializer(), it) }

/** A [Segments.trailCommunity] column decoded; empty for none or one that no longer decodes. */
internal fun communityOf(column: String?): List<CommunityValue> =
    column?.let { runCatching { AtJson.decodeFromString(COMMUNITY, it) }.getOrNull() }.orEmpty()

/** What a segment view serves: its effective details, and where each came from. */
internal data class EffectiveTrail(val trail: TrailAttributes?, val details: List<TrailDetailInfo>)

/**
 * A segment's effective trail details: each [community] value over [imported] (the importer's, cleaned, or null for anyone else's segment),
 * each detail with its source: [TrailSource.COMMUNITY] with its riders, else the importer's recorded source, else [TrailSource.IMPORTED].
 * The served object carries no sources of its own: [EffectiveTrail.details] has them.
 */
internal fun effectiveTrail(imported: TrailAttributes?, community: List<CommunityValue>): EffectiveTrail {
    var trail = imported?.copy(sources = null) ?: TrailAttributes()
    val riders = mutableMapOf<String, Int>()
    for (value in community) {
        val clean = TrailAttributesSanitizer.clean(value.value)?.takeIf { TrailDetail.isSuggestion(value.detail, it) } ?: continue
        trail = TrailDetail.replace(trail, value.detail, clean)
        riders[value.detail] = value.riders
    }
    val served = trail.takeUnless { it.isEmpty }
    val details =
        served?.let(TrailDetail::detailsOf).orEmpty().map { detail ->
            riders[detail]?.let { TrailDetailInfo(detail, TrailSource.COMMUNITY, riders = it) }
                ?: TrailDetailInfo(detail, imported?.sources?.of(detail) ?: TrailSource.IMPORTED)
        }
    return EffectiveTrail(served, details)
}

/** [segment]'s effective details, read from its row: the importer's (only for [importer]'s segment) with its community values over them. */
internal fun JdbcTransaction.effectiveTrailOf(segment: String, importer: Did?): EffectiveTrail? =
    Segments.select(Segments.authorDid, Segments.record, Segments.trailCommunity)
        .where { Segments.uri eq segment }
        .firstOrNull()
        ?.let { row ->
            val imported =
                if (importer.isImporter(row[Segments.authorDid]))
                    runCatching { AtJson.decodeFromString(SegmentRecord.serializer(), row[Segments.record]).trail }
                        .getOrNull()
                        ?.let(TrailAttributesSanitizer::clean)
                else null
            effectiveTrail(imported, communityOf(row[Segments.trailCommunity]))
        }

/** Suggestions with no `!hide` or `!takedown` on their record (at any version) or their author's account. */
internal fun unlabelledSuggestion(): Op<Boolean> = TRAIL_SUGGESTIONS.unlabelled()

/**
 * No [value] label on the suggestion, at any version (a same-value rewrite keeps the stored CID, so a label on the new one must still
 * count), nor on its author.
 */
private fun notLabelled(value: String): Op<Boolean> =
    not(labelled(TrailDetailSuggestions.uri, value)) and not(labelled(TrailDetailSuggestions.authorDid, value))

/** Whether [segment] may take trail detail suggestions: an active run segment, never a lift (as names, [nameable]). */
internal fun JdbcTransaction.suggestable(segment: String): Boolean =
    runCatching { AtUri(segment).collection }.getOrNull() == io.github.solcott.activitytracker.lexicon.Nsid.FEED_SEGMENT &&
        nameable(NamedPlace.Segment(segment))

/** Whether [segment] adopted a value for [detail] still in effect within [TrailDetailRules.cooldownDays] before [now]. */
internal fun JdbcTransaction.inDetailCooldown(segment: String, detail: String, now: Long, rules: TrailDetailRules): Boolean =
    adoptedWithin(TRAIL_SUGGESTIONS, segment, detail, now, rules.cooldownDays)

/** [did]'s open suggestion on [segment]'s [detail], if any. */
internal fun JdbcTransaction.openSuggestionOf(segment: String, detail: String, did: String): String? =
    openProposalBy(TRAIL_SUGGESTIONS, segment, detail, did)

/** Whether [did] is a regular of [segment]: efforts on it on [TrailDetailRules.suggesterDays] distinct dates, within the speed cap. */
internal fun JdbcTransaction.isRegular(segment: String, did: String, rules: TrailDetailRules): Boolean =
    proposerDays(segment, did) >= rules.suggesterDays

/**
 * Whether [did] may suggest a value for [segment]'s [detail] at [now]: a suggestable detail of an active run segment, outside the detail's
 * cooldown, with no open suggestion of theirs on it, and they're a regular. The indexer and `getSegment`'s flags both ask this.
 */
internal fun JdbcTransaction.maySuggest(segment: String, detail: String, did: String, now: Long, rules: TrailDetailRules): Boolean =
    detail in TrailDetail.suggestable &&
        suggestable(segment) &&
        !inDetailCooldown(segment, detail, now, rules) &&
        openSuggestionOf(segment, detail, did) == null &&
        isRegular(segment, did, rules)

/** The suggestable details [did] may not suggest for [segment] now: in a cooldown, or holding their own open suggestion. */
internal fun JdbcTransaction.closedDetailsFor(segment: String, did: String, now: Long, rules: TrailDetailRules): List<String> =
    TrailDetail.suggestable.filter { inDetailCooldown(segment, it, now, rules) || openSuggestionOf(segment, it, did) != null }

/**
 * Whether [value] (normalized, [normalizedValue]) is [segment]'s [detail] in effect or the value of one of its open suggestions on it other
 * than [except]: a suggestion or edit with such a value is ignored.
 */
internal fun JdbcTransaction.valueTaken(segment: String, detail: String, value: String, importer: Did?, except: String? = null): Boolean {
    val current = effectiveTrailOf(segment, importer)?.trail?.let { normalizedValue(detail, it) }
    if (current == value) return true
    return TrailDetailSuggestions.select(TrailDetailSuggestions.uri, TrailDetailSuggestions.value)
        .where {
            (TrailDetailSuggestions.segmentUri eq segment) and
                (TrailDetailSuggestions.detail eq detail) and
                (TrailDetailSuggestions.state eq ProposalState.OPEN)
        }
        .any { it[TrailDetailSuggestions.uri] != except && it[TrailDetailSuggestions.value] == value }
}

/**
 * Per suggestion in [suggestions] (all on [segment]), each counted voter's vote, by [countedVotes]: their latest by first indexing, only
 * while it names the suggestion's stored version, the voter has an effort on the segment, and isn't its author.
 */
internal fun JdbcTransaction.suggestionVotes(segment: String, suggestions: Collection<String>): Map<String, Map<String, CountedVote>> =
    countedVotes(TRAIL_SUGGESTIONS, segment, suggestions)

/** The counted totals of each of [suggestions] (all on [segment]), by [suggestionVotes]. */
internal fun JdbcTransaction.suggestionTallies(segment: String, suggestions: Collection<String>): Map<String, Tally> =
    countedTallies(TRAIL_SUGGESTIONS, segment, suggestions)

/** How riders see one detail in effect: [TrailDetailInfo]'s getSegment fields. */
internal data class DetailConfirmations(
    val confirmations: Int,
    val confirmedMonth: String?,
    val mayHaveChanged: Boolean,
    val viewerConfirmation: TrailDetailViewerVote?,
)

private const val CONFIRM_WINDOW_DAYS = 365L
private const val DOUBT_WINDOW_DAYS = 90L
private const val MONTH_AFTER = 2
private const val DOUBTERS = 3

/**
 * For each detail [trail] holds on [segment]: the distinct riders whose latest confirmation says it's still right in the last 365 days, the
 * month of the latest once 2 have, whether riders recently say it changed, and [viewer]'s own. A confirmation counts only while it names
 * the value in effect, and only from a rider (segments/trail-detail-suggestions).
 */
internal fun JdbcTransaction.confirmationsOf(
    segment: String,
    trail: TrailAttributes?,
    viewer: Did?,
    now: Long,
): Map<String, DetailConfirmations> {
    if (trail == null) return emptyMap()
    // (detail, voter) to the voter's latest confirmation of it.
    val latest = mutableMapOf<Pair<String, String>, ConfirmationRow>()
    TrailDetailVotes.select(
            TrailDetailVotes.uri,
            TrailDetailVotes.voterDid,
            TrailDetailVotes.detail,
            TrailDetailVotes.value,
            TrailDetailVotes.direction,
            TrailDetailVotes.indexedAt,
        )
        .where { (TrailDetailVotes.segmentUri eq segment) and TrailDetailVotes.suggestionUri.isNull() }
        .orderBy(TrailDetailVotes.indexedAt to SortOrder.ASC, TrailDetailVotes.uri to SortOrder.ASC)
        .forEach { row ->
            val detail = row[TrailDetailVotes.detail] ?: return@forEach
            latest[detail to row[TrailDetailVotes.voterDid]] =
                ConfirmationRow(
                    row[TrailDetailVotes.uri],
                    row[TrailDetailVotes.value],
                    row[TrailDetailVotes.direction] == TrailDetailVoteDirection.STILL,
                    row[TrailDetailVotes.indexedAt],
                )
        }
    val riders = ridersAmong(segment, latest.keys.map { it.second }.toSet())
    return TrailDetail.detailsOf(trail).associateWith { detail ->
        val current = normalizedValue(detail, trail)
        val counted = latest.filter { (key, row) -> key.first == detail && key.second in riders && row.value == current }
        val stills = counted.values.filter { it.still && it.at > now - CONFIRM_WINDOW_DAYS * DAY_MS }
        val recent = counted.values.filter { it.at > now - DOUBT_WINDOW_DAYS * DAY_MS }
        val changed = recent.count { !it.still }
        val own = viewer?.let { counted[detail to it.value] }
        DetailConfirmations(
            confirmations = stills.size,
            confirmedMonth = stills.takeIf { it.size >= MONTH_AFTER }?.maxOf { it.at }?.let { utcDate(it).toString().take(MONTH_TEXT) },
            mayHaveChanged = changed >= DOUBTERS && changed > recent.count { it.still },
            viewerConfirmation =
                own?.let {
                    TrailDetailViewerVote(
                        AtUri(it.uri),
                        if (it.still) TrailDetailVoteDirection.STILL else TrailDetailVoteDirection.CHANGED,
                    )
                },
        )
    }
}

private const val MONTH_TEXT = 7

private data class ConfirmationRow(val uri: String, val value: String?, val still: Boolean, val at: Long)

/**
 * Sets [segment]'s [Segments.trailCommunity]: for each detail, its latest adopted suggestion in effect ([unlabelledSuggestion]) with the up
 * votes that adopted it; null when none.
 */
internal fun JdbcTransaction.recomputeTrailCommunity(segment: String) {
    val adopted =
        TrailDetailSuggestions.select(
                TrailDetailSuggestions.detail,
                TrailDetailSuggestions.value,
                TrailDetailSuggestions.finalUp,
            )
            .where {
                (TrailDetailSuggestions.segmentUri eq segment) and
                    (TrailDetailSuggestions.state eq ProposalState.ADOPTED) and
                    unlabelledSuggestion()
            }
            .orderBy(TrailDetailSuggestions.closedAt to SortOrder.DESC, TrailDetailSuggestions.uri to SortOrder.DESC)
            .mapNotNull { row ->
                val value = runCatching {
                    AtJson.decodeFromString(TrailAttributes.serializer(), row[TrailDetailSuggestions.value])
                }
                    .getOrNull()
                value?.let { CommunityValue(row[TrailDetailSuggestions.detail], it, row[TrailDetailSuggestions.finalUp] ?: 0) }
            }
            .distinctBy { it.detail }
            .sortedBy { TrailDetail.all.indexOf(it.detail) }
    val column = adopted.ifEmpty { null }?.let { AtJson.encodeToString(COMMUNITY, it) }
    // Written only when it changed, so the hourly backstop rewrites nothing that stayed the same.
    val stored = Segments.select(Segments.trailCommunity).where { Segments.uri eq segment }.firstOrNull() ?: return
    if (stored[Segments.trailCommunity] == column) return
    Segments.update({ Segments.uri eq segment }) { it[trailCommunity] = column }
}

/** After labels changed on [subjects]: recomputes the community values of every segment with an adopted suggestion among or by them. */
internal fun JdbcTransaction.recomputeTrailsLabelled(subjects: Collection<String>) {
    if (subjects.isEmpty()) return
    adoptedSubjects(TRAIL_SUGGESTIONS, among = subjects).forEach { recomputeTrailCommunity(it) }
}

/** Recomputes the community values of every segment with an adopted suggestion: the pass's backstop, which also catches expired labels. */
internal fun JdbcTransaction.recomputeAllTrailCommunities() {
    adoptedSubjects(TRAIL_SUGGESTIONS).forEach { recomputeTrailCommunity(it) }
}

/** The suggestions on [segments] and every vote and confirmation on them, for deleted segments. */
internal fun JdbcTransaction.deleteTrailDetails(segments: Collection<String>) {
    if (segments.isEmpty()) return
    deleteProposalsOn(TRAIL_SUGGESTIONS, segments)
    TrailDetailVotes.deleteWhere { segmentUri inList segments }
}
