package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.SegmentCandidateCells
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.MAX_NAME_LENGTH
import io.github.solcott.activitytracker.appview.index.MAX_TOKEN_LENGTH
import io.github.solcott.activitytracker.appview.index.e7
import io.github.solcott.activitytracker.appview.index.validAttribution
import io.github.solcott.activitytracker.appview.index.validLine
import io.github.solcott.activitytracker.appview.index.validName
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.FinishSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateRejection
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.http.HttpStatusCode
import kotlin.math.roundToLong
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * The segment importer's calls (segments/segment-candidates), refused with `NotImporter` to anyone but [importer], and to everyone when
 * it's null. Candidates are public lines staged here before they're published; their riders, counted by the matcher from usable segment
 * tracks, are private, and only whether a candidate is ridden is ever revealed.
 */
class SegmentCandidateViews(
    private val db: Database,
    private val importer: Did?,
    private val jobs: SegmentJobs,
    private val clock: Clock = Clock.System,
) {
    /**
     * Stores or replaces each valid candidate by its id, returning the rest with why. One whose line and sport are unchanged keeps its
     * riders and ridden; a new or changed one loses them, gets its cells again and is queued to be counted against past tracks.
     */
    suspend fun put(caller: Did, input: PutSegmentCandidates.Input): PutSegmentCandidates.Output {
        requireImporter(caller)
        requireRun(input.region, input.revision)
        if (input.candidates.size > SegmentCandidates.MAX_BATCH) {
            invalid("At most ${SegmentCandidates.MAX_BATCH} candidates per call, not ${input.candidates.size}")
        }
        val now = clock.now().toEpochMilliseconds()
        val rejected = mutableListOf<SegmentCandidateRejection>()
        db.tx {
            for (candidate in input.candidates) {
                try {
                    stage(candidate, input.region, input.revision, now)
                } catch (e: IllegalArgumentException) {
                    rejected += SegmentCandidateRejection(candidate.id.take(SegmentCandidates.MAX_ID_LENGTH), e.message ?: "invalid")
                }
            }
        }
        return PutSegmentCandidates.Output(rejected)
    }

    /** Removes the region's candidates staged in any other revision, with their cells and riders. */
    suspend fun finish(caller: Did, input: FinishSegmentCandidates.Input): FinishSegmentCandidates.Output {
        requireImporter(caller)
        requireRun(input.region, input.revision)
        val removed = db.tx {
            val stale =
                SegmentCandidateRows.select(SegmentCandidateRows.id)
                    .where { (SegmentCandidateRows.region eq input.region) and (SegmentCandidateRows.revision neq input.revision) }
                    .map { it[SegmentCandidateRows.id] }
            if (stale.isNotEmpty()) {
                SegmentCandidateRiders.deleteWhere { candidateId inList stale }
                SegmentCandidateCells.deleteWhere { candidateId inList stale }
                SegmentCandidateRows.deleteWhere { id inList stale }
            }
            stale.size
        }
        return FinishSegmentCandidates.Output(removed)
    }

    /**
     * The region's ridden candidates by id, as staged: no counts, athletes or dates. A candidate the importer already published, as a
     * segment of its own keyed by the candidate's id (segments/segment-import), is left out whatever that segment's status.
     */
    suspend fun ridden(caller: Did, params: GetRiddenSegmentCandidates.Params): GetRiddenSegmentCandidates.Output {
        requireImporter(caller)
        requireRegion(params.region)
        val published = concat(stringLiteral("at://${caller.value}/${Nsid.FEED_SEGMENT}/"), SegmentCandidateRows.id)
        val limit = (params.limit ?: SegmentCandidates.MAX_BATCH).coerceIn(1, SegmentCandidates.MAX_BATCH)
        val page = db.tx {
            SegmentCandidateRows.selectAll()
                .where {
                    (SegmentCandidateRows.region eq params.region) and
                        (SegmentCandidateRows.ridden eq true) and
                        notExists(Segments.select(Segments.uri).where { Segments.uri eq published })
                }
                .apply { params.cursor?.let { cursor -> andWhere { SegmentCandidateRows.id greater cursor } } }
                .orderBy(SegmentCandidateRows.id to SortOrder.ASC)
                .limit(limit)
                .map {
                    SegmentCandidate(
                        id = it[SegmentCandidateRows.id],
                        sport = it[SegmentCandidateRows.sport],
                        name = it[SegmentCandidateRows.name],
                        line = it[SegmentCandidateRows.line],
                        source = it[SegmentCandidateRows.dataSource],
                        license = it[SegmentCandidateRows.license],
                        attribution = it[SegmentCandidateRows.attribution],
                    )
                }
        }
        return GetRiddenSegmentCandidates.Output(page, cursor = page.takeIf { it.size == limit }?.last()?.id)
    }

    /**
     * Validates [candidate] as a segment is validated, and its attribution, then stores it; throws [IllegalArgumentException] naming what's
     * wrong.
     */
    private fun JdbcTransaction.stage(candidate: SegmentCandidate, region: String, revision: String, now: Long) {
        require(SegmentCandidates.ID_PATTERN.matches(candidate.id)) { "an id that isn't 1 to 64 of a-z, 0-9 and -" }
        require(candidate.line.length <= MAX_LINE_LENGTH) { "a line over $MAX_LINE_LENGTH characters" }
        require(candidate.source.length <= MAX_TOKEN_LENGTH && candidate.license.length <= MAX_TOKEN_LENGTH) {
            "a source or licence over $MAX_TOKEN_LENGTH characters"
        }
        val name = validName(candidate.name)
        val line = validLine(candidate.sport, candidate.line)
        val attribution = validAttribution(candidate.attribution)
        val old =
            SegmentCandidateRows.select(SegmentCandidateRows.line, SegmentCandidateRows.sport, SegmentCandidateRows.ridden)
                .where { SegmentCandidateRows.id eq candidate.id }
                .firstOrNull()
        val changed = old == null || old[SegmentCandidateRows.line] != candidate.line || old[SegmentCandidateRows.sport] != candidate.sport
        SegmentCandidateRows.upsert {
            it[id] = candidate.id
            it[SegmentCandidateRows.region] = region
            it[SegmentCandidateRows.revision] = revision
            it[sport] = candidate.sport
            it[SegmentCandidateRows.name] = name.take(MAX_NAME_LENGTH)
            it[SegmentCandidateRows.line] = candidate.line
            it[distanceM] = SegmentGeometry.lengthMeters(line).roundToLong()
            it[minLatE7] = line.minOf { p -> p.lat }.e7()
            it[maxLatE7] = line.maxOf { p -> p.lat }.e7()
            it[minLngE7] = line.minOf { p -> p.lng }.e7()
            it[maxLngE7] = line.maxOf { p -> p.lng }.e7()
            it[dataSource] = candidate.source
            it[license] = candidate.license
            it[SegmentCandidateRows.attribution] = attribution
            it[ridden] = if (changed) false else old[SegmentCandidateRows.ridden]
            it[stagedAt] = now
        }
        if (!changed) return
        SegmentCandidateRiders.deleteWhere { candidateId eq candidate.id }
        replaceCells(candidate.id, line)
        jobs.enqueueCandidate(candidate.id, now)
    }

    private fun JdbcTransaction.replaceCells(id: String, line: List<GeoPoint>) {
        SegmentCandidateCells.deleteWhere { candidateId eq id }
        SegmentCandidateCells.batchInsert(SegmentCells.cellsOf(line)) {
            this[SegmentCandidateCells.cell] = it
            this[SegmentCandidateCells.candidateId] = id
        }
    }

    private fun requireImporter(caller: Did) {
        if (importer == null || caller != importer) {
            throw XrpcError(
                HttpStatusCode.Forbidden.value,
                SegmentCandidates.NOT_IMPORTER,
                "Only the segment importer may use candidate calls",
            )
        }
    }

    private fun requireRun(region: String, revision: String) {
        requireRegion(region)
        if (revision.isEmpty() || revision.length > SegmentCandidates.MAX_REVISION_LENGTH) {
            invalid("A revision must be 1 to ${SegmentCandidates.MAX_REVISION_LENGTH} characters")
        }
    }

    private fun requireRegion(region: String) {
        if (!SegmentCandidates.ID_PATTERN.matches(region)) invalid("A region must be 1 to 64 of a-z, 0-9 and -")
    }

    private fun invalid(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

    private companion object {
        /** `importer.defs#candidate.line`'s maxLength, as `feed.segment#line`'s. */
        const val MAX_LINE_LENGTH = 100_000
    }
}
