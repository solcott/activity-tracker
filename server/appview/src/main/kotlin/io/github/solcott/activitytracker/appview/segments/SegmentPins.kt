@file:Suppress("TooManyFunctions") // The pin rules every status and name writer reads, the transitions a pin makes, and the service.

package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentPinRows
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.IndexOrder
import io.github.solcott.activitytracker.appview.index.LIVE_STATUSES
import io.github.solcott.activitytracker.appview.index.duplicatesOf
import io.github.solcott.activitytracker.appview.index.liveStatus
import io.github.solcott.activitytracker.appview.index.recheckDuplicatesOf
import io.github.solcott.activitytracker.appview.index.rederiveGroups
import io.github.solcott.activitytracker.appview.index.regroup
import io.github.solcott.activitytracker.appview.index.validName
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Operators' pins on segments (segments/segment-pins). A pin writes its value into `segments.status`, `duplicate_of` and `current_name`,
// which every reader already keys off; the writers of those columns ask the rules here so no derivation undoes a pin.

/** The kinds of [SegmentPinRows]. */
object SegmentPinKind {
    const val STATUS = "status"
    const val NAME = "name"
}

/** The statuses a status pin may hold: never `proposed`. */
internal val PINNABLE_STATUSES = listOf(SegmentStatus.ACTIVE, SegmentStatus.ARCHIVED, SegmentStatus.DUPLICATE)

/** The longest note a pin keeps. */
internal const val MAX_PIN_NOTE_LENGTH = 500

/** A status pin in effect: its [status], its target when a duplicate, and the derived status unpinning starts from. */
internal data class StatusPin(val status: String, val duplicateOf: String?, val priorStatus: String?)

/**
 * The status pin on [uri] if it applies to a segment of [sport] and [kind]: always for `active` and `archived`, and for `duplicate` only
 * while its target is indexed with the same sport group and kind. A pin that doesn't apply leaves the status derived.
 */
internal fun JdbcTransaction.statusPinFor(uri: String, sport: String, kind: String): StatusPin? {
    val pin =
        SegmentPinRows.selectAll()
            .where { (SegmentPinRows.segmentUri eq uri) and (SegmentPinRows.kind eq SegmentPinKind.STATUS) }
            .firstOrNull()
            ?.let { row ->
                row[SegmentPinRows.status]?.let { status ->
                    StatusPin(
                        status,
                        row[SegmentPinRows.duplicateOf].takeIf { status == SegmentStatus.DUPLICATE },
                        row[SegmentPinRows.priorStatus],
                    )
                }
            }
    return pin?.takeIf { it.status != SegmentStatus.DUPLICATE || duplicateTargetMatches(it.duplicateOf, sport, kind) }
}

/** Whether [target] is indexed with [sport] and [kind]: when a duplicate pin naming it applies. */
private fun JdbcTransaction.duplicateTargetMatches(target: String?, sport: String, kind: String): Boolean =
    target != null &&
        Segments.select(Segments.uri).where { (Segments.uri eq target) and (Segments.sport eq sport) and (Segments.kind eq kind) }.any()

/** [statusPinFor] the indexed segment at [uri]; null when it isn't indexed. */
internal fun JdbcTransaction.statusPinOf(uri: String): StatusPin? {
    val row = Segments.select(Segments.sport, Segments.kind).where { Segments.uri eq uri }.firstOrNull() ?: return null
    return statusPinFor(uri, row[Segments.sport], row[Segments.kind])
}

/**
 * No `active` or `archived` pin on the segment row in the query: what the status pass's archives and
 * [SegmentMatcher.activateImporterSegments] require. Those pins always apply; a duplicate pin that applies holds the row at `duplicate`,
 * which neither step touches, and one that doesn't leaves it derived.
 */
internal fun noFixedStatusPin(): Op<Boolean> =
    notExists(
        SegmentPinRows.select(SegmentPinRows.segmentUri).where {
            (SegmentPinRows.segmentUri eq Segments.uri) and
                (SegmentPinRows.kind eq SegmentPinKind.STATUS) and
                (SegmentPinRows.status inList listOf(SegmentStatus.ACTIVE, SegmentStatus.ARCHIVED))
        },
    )

/** Records [derived], the status a change of [uri]'s line, sport or kind just derived, as where unpinning its status pin starts. */
internal fun JdbcTransaction.recordPriorStatus(uri: String, derived: String) {
    SegmentPinRows.update({ (SegmentPinRows.segmentUri eq uri) and (SegmentPinRows.kind eq SegmentPinKind.STATUS) }) {
        it[priorStatus] = derived
    }
}

/** Whether [segment] has a name pin, in effect or not: while it does, no proposal is accepted or adopted there. */
internal fun JdbcTransaction.hasNamePin(segment: String): Boolean =
    SegmentPinRows.select(SegmentPinRows.segmentUri)
        .where { (SegmentPinRows.segmentUri eq segment) and (SegmentPinRows.kind eq SegmentPinKind.NAME) }
        .any()

/**
 * [segment]'s pinned name while it's in effect: always for free text, and for one taken from a proposal only while neither the proposal (at
 * any version) nor its proposer carries `!hide` or `!takedown`, read from labels by URI and DID so it holds after the proposal goes.
 */
internal fun JdbcTransaction.namePinInEffect(segment: String): String? {
    val row =
        SegmentPinRows.select(SegmentPinRows.name, SegmentPinRows.proposalUri, SegmentPinRows.proposerDid)
            .where { (SegmentPinRows.segmentUri eq segment) and (SegmentPinRows.kind eq SegmentPinKind.NAME) }
            .firstOrNull()
    val subjects = listOfNotNull(row?.get(SegmentPinRows.proposalUri), row?.get(SegmentPinRows.proposerDid))
    return row?.get(SegmentPinRows.name)?.takeIf { subjects.isEmpty() || unlabelledSubjects(segment, subjects) }
}

/** Whether none of [subjects] (the proposal a name pin on [segment] was taken from, and its proposer) carries `!hide` or `!takedown`. */
private fun JdbcTransaction.unlabelledSubjects(segment: String, subjects: List<String>): Boolean {
    val labelledAway =
        subjects
            .flatMap { subject -> listOf(LabelValues.HIDE, LabelValues.TAKEDOWN).map { labelled(stringParam(subject), it) } }
            .reduce<Op<Boolean>, Op<Boolean>> { a, b -> a or b }
    return SegmentPinRows.select(SegmentPinRows.segmentUri)
        .where { (SegmentPinRows.segmentUri eq segment) and (SegmentPinRows.kind eq SegmentPinKind.NAME) and not(labelledAway) }
        .any()
}

/** The segments whose name pin comes from a proposal by or among [subjects] (record URIs and account DIDs), or from any proposal. */
internal fun JdbcTransaction.segmentsWithProposalNamePins(subjects: Collection<String>? = null): List<String> =
    SegmentPinRows.select(SegmentPinRows.segmentUri)
        .where {
            val fromProposal = (SegmentPinRows.kind eq SegmentPinKind.NAME) and SegmentPinRows.proposalUri.isNotNull()
            if (subjects == null) {
                fromProposal
            } else {
                fromProposal and ((SegmentPinRows.proposalUri inList subjects) or (SegmentPinRows.proposerDid inList subjects))
            }
        }
        .map { it[SegmentPinRows.segmentUri] }

/**
 * What a segment's status moving from [old] to [new] entails, as the derived moves do: becoming `duplicate` drops its efforts; leaving the
 * live statuses re-checks the segments that duplicated it and derives its segment group again, as an archive does; becoming live queues it
 * to be matched against past tracks and gives it its segment group.
 */
@Suppress("LongParameterList") // The move, and what its side effects queue and recheck with.
internal fun JdbcTransaction.applyStatusMove(uri: String, old: String, new: String, jobs: SegmentJobs, now: Long, importer: Did?) {
    val wasLive = old in LIVE_STATUSES
    val isLive = new in LIVE_STATUSES
    if (new == SegmentStatus.DUPLICATE) deleteEfforts { segmentUri eq uri }
    if (wasLive && !isLive) {
        recheckDuplicatesOf(listOf(uri), jobs, now, importer)
        rederiveGroups(listOf(uri))
    }
    if (!wasLive && isLive) {
        jobs.enqueueSegment(uri, now)
        regroup(uri)
    }
}

/**
 * After [target] was indexed: makes `duplicate` of it each segment whose duplicate pin names it and now applies (its target is back, or
 * matches its sport group and kind again).
 */
internal fun JdbcTransaction.applyPinsNaming(target: String, jobs: SegmentJobs, now: Long, importer: Did?) {
    val pinned =
        SegmentPinRows.select(SegmentPinRows.segmentUri)
            .where { (SegmentPinRows.kind eq SegmentPinKind.STATUS) and (SegmentPinRows.duplicateOf eq target) }
            .map { it[SegmentPinRows.segmentUri] }
    for (uri in pinned.filter { statusPinOf(it) != null }) {
        val row = Segments.select(Segments.status, Segments.duplicateOf).where { Segments.uri eq uri }.single()
        if (row[Segments.status] == SegmentStatus.DUPLICATE && row[Segments.duplicateOf] == target) continue
        Segments.update({ Segments.uri eq uri }) {
            it[status] = SegmentStatus.DUPLICATE
            it[duplicateOf] = target
        }
        applyStatusMove(uri, row[Segments.status], SegmentStatus.DUPLICATE, jobs, now, importer)
    }
}

/** Locks held around a pin's transaction: the indexer's per-URI locks, so a pin never interleaves with indexing the segments it names. */
interface UriLocks {
    suspend fun <T> on(uris: Collection<String>, block: suspend () -> T): T
}

/** A pin as stored, for the admin site: [inEffect] says whether it beats the derived value now. */
data class SegmentPin(
    val segmentUri: String,
    val kind: String,
    val status: String?,
    val duplicateOf: String?,
    val name: String?,
    val proposalUri: String?,
    val pinnedBy: String,
    val pinnedAt: Long,
    val note: String?,
    val inEffect: Boolean,
)

/** What a pin or unpin did. */
sealed interface PinResult {
    data object Pinned : PinResult

    data object Unpinned : PinResult

    /** There was no pin of that kind to remove. */
    data object NoPin : PinResult

    /** The segment (or a duplicate pin's target) isn't indexed. */
    data class NotFound(val uri: String) : PinResult

    data class Invalid(val reason: String) : PinResult
}

/**
 * Pins and unpins segments' statuses and names for operators (segments/segment-pins): what the admin site's operator actions call, which
 * check the operator's role and record each call in their audit log. Each call is one transaction under the indexer's locks for the URIs it
 * names, and never writes to a repo or space.
 */
@Suppress("LongParameterList") // Its collaborators and the configuration unpinning derives with.
class SegmentPins
internal constructor(
    private val db: Database,
    private val jobs: SegmentJobs,
    private val locks: UriLocks,
    private val clock: Clock = Clock.System,
    private val importer: Did? = null,
    private val activeAthletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
) {
    /** Pins [segment]'s status: `active`, `archived`, or `duplicate` of [duplicateOf], replacing any status pin it had. */
    suspend fun pinStatus(segment: String, status: String, duplicateOf: String?, by: Did, note: String? = null): PinResult =
        locks.on(listOfNotNull(segment, duplicateOf)) {
            db.tx {
                invalidStatusPin(segment, status, duplicateOf, note)?.let {
                    return@tx it
                }
                val now = clock.now().toEpochMilliseconds()
                val row = Segments.select(Segments.status).where { Segments.uri eq segment }.single()
                val old = row[Segments.status]
                val earlier = statusPinOf(segment)
                val prior =
                    SegmentPinRows.select(SegmentPinRows.priorStatus)
                        .where { (SegmentPinRows.segmentUri eq segment) and (SegmentPinRows.kind eq SegmentPinKind.STATUS) }
                        .firstOrNull()
                        ?.get(SegmentPinRows.priorStatus)
                SegmentPinRows.upsert {
                    it[segmentUri] = segment
                    it[kind] = SegmentPinKind.STATUS
                    it[SegmentPinRows.status] = status
                    it[SegmentPinRows.duplicateOf] = duplicateOf.takeIf { status == SegmentStatus.DUPLICATE }
                    it[name] = null
                    it[proposalUri] = null
                    it[proposerDid] = null
                    // The derived status: the row's own unless an earlier pin in effect holds it, which kept what it derived.
                    it[priorStatus] = if (earlier != null) prior else old
                    it[pinnedBy] = by.value
                    it[pinnedAt] = now
                    it[SegmentPinRows.note] = note?.trim()?.takeIf(String::isNotEmpty)
                }
                Segments.update({ Segments.uri eq segment }) {
                    it[Segments.status] = status
                    it[Segments.duplicateOf] = duplicateOf.takeIf { status == SegmentStatus.DUPLICATE }
                }
                applyStatusMove(segment, old, status, jobs, now, importer)
                PinResult.Pinned
            }
        }

    /**
     * Removes [segment]'s status pin and derives its status again: `duplicate` of the earliest earlier live segment it duplicates, else
     * `active` if it was active when pinned (and not re-lined since), else as a segment that duplicates nothing, promoted at once with
     * enough riders. A pin that didn't apply (a duplicate pin whose target is gone) left the status derived, so it's only removed.
     */
    suspend fun unpinStatus(segment: String): PinResult =
        locks.on(listOf(segment)) {
            db.tx {
                val pin = statusPinOf(segment)
                val deleted = SegmentPinRows.deleteWhere { (segmentUri eq segment) and (kind eq SegmentPinKind.STATUS) }
                if (deleted == 0) return@tx PinResult.NoPin
                if (pin == null) return@tx PinResult.Unpinned
                val row = Segments.selectAll().where { Segments.uri eq segment }.single()
                val line = SegmentGeometry.decodePolyline(row[Segments.line]) ?: return@tx PinResult.Unpinned
                val original =
                    duplicatesOf(row[Segments.sport], row[Segments.kind], line, IndexOrder(row[Segments.firstIndexedAt], segment))
                        .firstOrNull()
                val derived =
                    when {
                        original != null -> SegmentStatus.DUPLICATE
                        pin.priorStatus == SegmentStatus.ACTIVE -> SegmentStatus.ACTIVE
                        else -> liveStatus(row[Segments.authorDid], importer)
                    }
                val new = if (derived == SegmentStatus.PROPOSED && riders(segment) >= activeAthletes) SegmentStatus.ACTIVE else derived
                Segments.update({ Segments.uri eq segment }) {
                    it[status] = new
                    it[duplicateOf] = original
                }
                applyStatusMove(segment, row[Segments.status], new, jobs, clock.now().toEpochMilliseconds(), importer)
                PinResult.Unpinned
            }
        }

    /**
     * Pins [segment]'s name: [name], trimmed, or, with [proposal], that proposal's name, in effect only while it and its proposer are
     * unlabelled. Exactly one of the two is given.
     */
    suspend fun pinName(segment: String, name: String?, proposal: String?, by: Did, note: String? = null): PinResult =
        locks.on(listOf(segment)) {
            db.tx {
                if (!Segments.select(Segments.uri).where { Segments.uri eq segment }.any()) return@tx PinResult.NotFound(segment)
                if ((name == null) == (proposal == null)) return@tx PinResult.Invalid("give either a name or a proposal")
                if ((note?.length ?: 0) > MAX_PIN_NOTE_LENGTH) return@tx PinResult.Invalid("a note over $MAX_PIN_NOTE_LENGTH characters")
                val (pinned, proposer) =
                    if (proposal != null) {
                        val row =
                            SegmentNameProposals.select(SegmentNameProposals.name, SegmentNameProposals.proposerDid)
                                .where { (SegmentNameProposals.uri eq proposal) and (SegmentNameProposals.segmentUri eq segment) }
                                .firstOrNull() ?: return@tx PinResult.Invalid("$proposal isn't a proposal on this segment")
                        row[SegmentNameProposals.name] to row[SegmentNameProposals.proposerDid]
                    } else {
                        val valid = runCatching {
                            validName(checkNotNull(name))
                        }
                            .getOrElse {
                                return@tx PinResult.Invalid("invalid name: ${it.message}")
                            }
                        valid to null
                    }
                SegmentPinRows.upsert {
                    it[segmentUri] = segment
                    it[kind] = SegmentPinKind.NAME
                    it[status] = null
                    it[duplicateOf] = null
                    it[SegmentPinRows.name] = pinned
                    it[proposalUri] = proposal
                    it[proposerDid] = proposer
                    it[priorStatus] = null
                    it[pinnedBy] = by.value
                    it[pinnedAt] = clock.now().toEpochMilliseconds()
                    it[SegmentPinRows.note] = note?.trim()?.takeIf(String::isNotEmpty)
                }
                recomputeCurrentName(segment)
                PinResult.Pinned
            }
        }

    /** Removes [segment]'s name pin: its current name is its latest adopted name in effect again, else its original. */
    suspend fun unpinName(segment: String): PinResult =
        locks.on(listOf(segment)) {
            db.tx {
                val deleted = SegmentPinRows.deleteWhere { (segmentUri eq segment) and (kind eq SegmentPinKind.NAME) }
                if (deleted == 0) return@tx PinResult.NoPin
                recomputeCurrentName(segment)
                PinResult.Unpinned
            }
        }

    /** [segment]'s pins, status first. */
    suspend fun pinsOf(segment: String): List<SegmentPin> = db.tx {
        SegmentPinRows.selectAll()
            .where { SegmentPinRows.segmentUri eq segment }
            .orderBy(SegmentPinRows.kind to SortOrder.DESC)
            .map { pinOf(it) }
    }

    /** Up to [limit] pins on segments that aren't indexed, by segment URI after [after]: kept in case the URI is indexed again. */
    suspend fun orphanedPins(limit: Int, after: String? = null): List<SegmentPin> = db.tx {
        SegmentPinRows.selectAll()
            .where {
                val orphaned = notExists(Segments.select(Segments.uri).where { Segments.uri eq SegmentPinRows.segmentUri })
                if (after == null) orphaned else orphaned and (SegmentPinRows.segmentUri greater after)
            }
            .orderBy(SegmentPinRows.segmentUri to SortOrder.ASC, SegmentPinRows.kind to SortOrder.DESC)
            .limit(limit)
            .map { pinOf(it) }
    }

    private fun JdbcTransaction.pinOf(row: ResultRow): SegmentPin {
        val uri = row[SegmentPinRows.segmentUri]
        val kind = row[SegmentPinRows.kind]
        return SegmentPin(
            segmentUri = uri,
            kind = kind,
            status = row[SegmentPinRows.status],
            duplicateOf = row[SegmentPinRows.duplicateOf],
            name = row[SegmentPinRows.name],
            proposalUri = row[SegmentPinRows.proposalUri],
            pinnedBy = row[SegmentPinRows.pinnedBy],
            pinnedAt = row[SegmentPinRows.pinnedAt],
            note = row[SegmentPinRows.note],
            inEffect = if (kind == SegmentPinKind.STATUS) statusPinOf(uri) != null else namePinInEffect(uri) != null,
        )
    }

    /** Why a status pin of [segment] would be refused, or null when it's valid. */
    private fun JdbcTransaction.invalidStatusPin(segment: String, status: String, duplicateOf: String?, note: String?): PinResult? {
        val row = Segments.select(Segments.sport, Segments.kind).where { Segments.uri eq segment }.firstOrNull()
        return when {
            row == null -> PinResult.NotFound(segment)
            status !in PINNABLE_STATUSES -> PinResult.Invalid("a status pin is active, archived or duplicate, not $status")
            (note?.length ?: 0) > MAX_PIN_NOTE_LENGTH -> PinResult.Invalid("a note over $MAX_PIN_NOTE_LENGTH characters")
            status != SegmentStatus.DUPLICATE ->
                if (duplicateOf != null) PinResult.Invalid("only a duplicate pin names a segment it duplicates") else null
            duplicateOf == null -> PinResult.Invalid("a duplicate pin names the segment it duplicates")
            duplicateOf == segment -> PinResult.Invalid("a segment can't duplicate itself")
            else -> invalidTarget(segment, duplicateOf, row[Segments.sport], row[Segments.kind])
        }
    }

    private fun JdbcTransaction.invalidTarget(segment: String, target: String, sport: String, kind: String): PinResult? {
        val row = Segments.select(Segments.sport, Segments.kind, Segments.status).where { Segments.uri eq target }.firstOrNull()
        val pinnedOnto =
            SegmentPinRows.select(SegmentPinRows.segmentUri)
                .where { (SegmentPinRows.kind eq SegmentPinKind.STATUS) and (SegmentPinRows.duplicateOf eq segment) }
                .map { it[SegmentPinRows.segmentUri] }
        return when {
            row == null -> PinResult.NotFound(target)
            row[Segments.sport] != sport || row[Segments.kind] != kind -> PinResult.Invalid("$target is of another sport group or kind")
            row[Segments.status] == SegmentStatus.DUPLICATE -> PinResult.Invalid("$target is itself a duplicate")
            pinnedOnto.isNotEmpty() -> PinResult.Invalid("pinned as duplicates of this segment: ${pinnedOnto.joinToString()}")
            else -> null
        }
    }

    /** How many distinct athletes have efforts on [segment], of every visibility: what promotes a proposed segment. */
    private fun JdbcTransaction.riders(segment: String): Long =
        SegmentEfforts.select(SegmentEfforts.authorDid).where { SegmentEfforts.segmentUri eq segment }.withDistinct().count()
}
