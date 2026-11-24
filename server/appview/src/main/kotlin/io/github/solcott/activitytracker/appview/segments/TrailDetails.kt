@file:Suppress("TooManyFunctions") // The suggestion rules and the reads the indexer, the adoption pass and the views share.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.TrailAttributesSanitizer
import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.api.served
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.TrailDetailVotes
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
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

// Crowd-sourced trail details (segments/trail-detail-suggestions): what the indexer, the adoption pass and SegmentViews share, so the
// flags a viewer sees and the checks a suggestion meets can't disagree.

/** The thresholds of trail detail suggestions, each configurable (`TRAIL_DETAIL_*`, [fromEnv]). */
@Suppress("MagicNumber") // The defaults, as segments/trail-detail-suggestions gives them.
data class TrailDetailRules(
    /** Distinct local dates with efforts a suggester needs. */
    val suggesterDays: Int = 5,
    /** The fewest up votes that adopt a suggestion, however few riders. */
    val minUp: Int = 3,
    /** The share of the segment's riders in the last 365 days whose up votes adopt a suggestion, in percent, rounded up. */
    val riderPercent: Int = 10,
    /** How long a suggestion must be open before it can be adopted. */
    val minOpenDays: Int = 7,
    /** The up-minus-down score at or below which a suggestion is rejected; negative. */
    val rejectScore: Int = -5,
    /** How long a suggestion stays open without being adopted. */
    val expiryDays: Int = 90,
    /** How long after an adoption new suggestions on that detail are ignored. */
    val cooldownDays: Int = 30,
) {
    /** The up votes that adopt a suggestion on a segment with [riders] distinct athletes in the last 365 days. */
    fun upNeeded(riders: Long): Long = maxOf(minUp.toLong(), (riders * riderPercent + PERCENT - 1) / PERCENT)

    companion object {
        private const val PERCENT = 100

        /** The rules from `TRAIL_DETAIL_*`, each falling back to its default when unset or invalid. */
        fun fromEnv(env: Map<String, String>): TrailDetailRules {
            val defaults = TrailDetailRules()
            fun int(key: String, default: Int, valid: (Int) -> Boolean) = env[key]?.trim()?.toIntOrNull()?.takeIf(valid) ?: default
            return TrailDetailRules(
                suggesterDays = int("TRAIL_DETAIL_SUGGESTER_DAYS", defaults.suggesterDays) { it >= 1 },
                minUp = int("TRAIL_DETAIL_MIN_UP", defaults.minUp) { it >= 1 },
                riderPercent = int("TRAIL_DETAIL_RIDER_PERCENT", defaults.riderPercent) { it in 0..PERCENT },
                minOpenDays = int("TRAIL_DETAIL_MIN_OPEN_DAYS", defaults.minOpenDays) { it >= 0 },
                rejectScore = int("TRAIL_DETAIL_REJECT_SCORE", defaults.rejectScore) { it <= -1 },
                expiryDays = int("TRAIL_DETAIL_EXPIRY_DAYS", defaults.expiryDays) { it >= 1 },
                cooldownDays = int("TRAIL_DETAIL_COOLDOWN_DAYS", defaults.cooldownDays) { it >= 0 },
            )
        }
    }
}

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
                if (importer != null && row[Segments.authorDid] == importer.value)
                    runCatching { AtJson.decodeFromString(SegmentRecord.serializer(), row[Segments.record]).trail }
                        .getOrNull()
                        ?.let(TrailAttributesSanitizer::clean)
                else null
            effectiveTrail(imported, communityOf(row[Segments.trailCommunity]))
        }

/** Suggestions with no `!hide` or `!takedown` on their record (at the stored version) or their author's account. */
internal fun unlabelledSuggestion(): Op<Boolean> = notLabelled(LabelValues.HIDE) and notLabelled(LabelValues.TAKEDOWN)

/** The open suggestions a list shows [viewer]: as [listedProposal] for names, their own staying listed to them under `!hide`. */
internal fun listedSuggestion(viewer: Did?): Op<Boolean> {
    val own = viewer?.let { TrailDetailSuggestions.authorDid eq stringParam(it.value) } ?: Op.FALSE
    return notLabelled(LabelValues.TAKEDOWN) and
        (own or notLabelled(LabelValues.HIDE)) and
        served(viewer?.let { stringParam(it.value) }, TrailDetailSuggestions.authorDid, null, null)
}

private fun notLabelled(value: String): Op<Boolean> =
    not(labelled(TrailDetailSuggestions.uri, value, TrailDetailSuggestions.cid)) and not(labelled(TrailDetailSuggestions.authorDid, value))

/** Whether [segment] may take trail detail suggestions: an active run segment, never a lift (as names, [nameable]). */
internal fun JdbcTransaction.suggestable(segment: String): Boolean =
    runCatching { AtUri(segment).collection }.getOrNull() == io.github.solcott.activitytracker.lexicon.Nsid.FEED_SEGMENT &&
        nameable(NamedPlace.Segment(segment))

/** Whether [segment] adopted a value for [detail] still in effect within [TrailDetailRules.cooldownDays] before [now]. */
internal fun JdbcTransaction.inDetailCooldown(segment: String, detail: String, now: Long, rules: TrailDetailRules): Boolean =
    TrailDetailSuggestions.select(TrailDetailSuggestions.uri)
        .where {
            (TrailDetailSuggestions.segmentUri eq segment) and
                (TrailDetailSuggestions.detail eq detail) and
                (TrailDetailSuggestions.state eq ProposalState.ADOPTED) and
                (TrailDetailSuggestions.closedAt greater now - rules.cooldownDays * DAY_MS) and
                unlabelledSuggestion()
        }
        .any()

/** [did]'s open suggestion on [segment]'s [detail], if any. */
internal fun JdbcTransaction.openSuggestionOf(segment: String, detail: String, did: String): String? =
    TrailDetailSuggestions.select(TrailDetailSuggestions.uri)
        .where {
            (TrailDetailSuggestions.segmentUri eq segment) and
                (TrailDetailSuggestions.detail eq detail) and
                (TrailDetailSuggestions.authorDid eq did) and
                (TrailDetailSuggestions.state eq ProposalState.OPEN)
        }
        .firstOrNull()
        ?.get(TrailDetailSuggestions.uri)

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
 * Per suggestion in [suggestions] (all on [segment]), each counted voter's latest vote (true for up): by first indexing, only while it
 * names the suggestion's stored version, the voter has an effort on the segment, and isn't its author.
 */
internal fun JdbcTransaction.suggestionVotes(
    segment: String,
    suggestions: Collection<String>,
): Map<String, Map<String, CountedVote>> {
    if (suggestions.isEmpty()) return emptyMap()
    val stored =
        TrailDetailSuggestions.select(TrailDetailSuggestions.uri, TrailDetailSuggestions.cid, TrailDetailSuggestions.authorDid)
            .where { TrailDetailSuggestions.uri inList suggestions }
            .associate { it[TrailDetailSuggestions.uri] to (it[TrailDetailSuggestions.cid] to it[TrailDetailSuggestions.authorDid]) }
    // (suggestion, voter) to the vote's cid and the vote.
    val latest = mutableMapOf<Pair<String, String>, Triple<String?, String, Boolean>>()
    TrailDetailVotes.select(
            TrailDetailVotes.uri,
            TrailDetailVotes.suggestionUri,
            TrailDetailVotes.voterDid,
            TrailDetailVotes.suggestionCid,
            TrailDetailVotes.direction,
        )
        .where { TrailDetailVotes.suggestionUri inList suggestions }
        .orderBy(TrailDetailVotes.indexedAt to SortOrder.ASC, TrailDetailVotes.uri to SortOrder.ASC)
        .forEach { row ->
            val suggestion = row[TrailDetailVotes.suggestionUri] ?: return@forEach
            latest[suggestion to row[TrailDetailVotes.voterDid]] =
                Triple(
                    row[TrailDetailVotes.suggestionCid],
                    row[TrailDetailVotes.uri],
                    row[TrailDetailVotes.direction] == TrailDetailVoteDirection.UP,
                )
        }
    val riders = ridersAmong(segment, latest.keys.map { it.second }.toSet())
    return suggestions.associateWith { suggestion ->
        val (cid, author) = stored[suggestion] ?: return@associateWith emptyMap()
        latest
            .filter { (key, vote) -> key.first == suggestion && vote.first == cid && key.second != author && key.second in riders }
            .map { (key, vote) -> key.second to CountedVote(vote.second, vote.third) }
            .toMap()
    }
}

/** The counted totals of each of [suggestions] (all on [segment]), by [suggestionVotes]. */
internal fun JdbcTransaction.suggestionTallies(segment: String, suggestions: Collection<String>): Map<String, Tally> =
    suggestionVotes(segment, suggestions).mapValues { (_, votes) ->
        Tally(up = votes.values.count { it.up }, down = votes.values.count { !it.up })
    }

/** A voter's counted vote on a suggestion: its record's [uri], and whether it's [up]. */
internal data class CountedVote(val uri: String, val up: Boolean)

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
            confirmedMonth =
                stills
                    .takeIf { it.size >= MONTH_AFTER }
                    ?.maxOf { it.at }
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString().take(MONTH_TEXT) },
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
    Segments.update({ Segments.uri eq segment }) { it[trailCommunity] = column }
}

/** After labels changed on [subjects]: recomputes the community values of every segment with an adopted suggestion among or by them. */
internal fun JdbcTransaction.recomputeTrailsLabelled(subjects: Collection<String>) {
    if (subjects.isEmpty()) return
    TrailDetailSuggestions.select(TrailDetailSuggestions.segmentUri)
        .where {
            (TrailDetailSuggestions.state eq ProposalState.ADOPTED) and
                ((TrailDetailSuggestions.uri inList subjects) or (TrailDetailSuggestions.authorDid inList subjects))
        }
        .withDistinct()
        .map { it[TrailDetailSuggestions.segmentUri] }
        .forEach { recomputeTrailCommunity(it) }
}

/** Recomputes the community values of every segment with an adopted suggestion: the pass's backstop, which also catches expired labels. */
internal fun JdbcTransaction.recomputeAllTrailCommunities() {
    TrailDetailSuggestions.select(TrailDetailSuggestions.segmentUri)
        .where { TrailDetailSuggestions.state eq ProposalState.ADOPTED }
        .withDistinct()
        .map { it[TrailDetailSuggestions.segmentUri] }
        .forEach { recomputeTrailCommunity(it) }
}

/** The suggestions on [segments] and every vote and confirmation on them, for deleted segments. */
internal fun JdbcTransaction.deleteTrailDetails(segments: Collection<String>) {
    if (segments.isEmpty()) return
    val suggestions =
        TrailDetailSuggestions.select(TrailDetailSuggestions.uri)
            .where { TrailDetailSuggestions.segmentUri inList segments }
            .map { it[TrailDetailSuggestions.uri] }
    if (suggestions.isNotEmpty()) TrailDetailVotes.deleteWhere { suggestionUri inList suggestions }
    TrailDetailVotes.deleteWhere { segmentUri inList segments }
    TrailDetailSuggestions.deleteWhere { segmentUri inList segments }
}
