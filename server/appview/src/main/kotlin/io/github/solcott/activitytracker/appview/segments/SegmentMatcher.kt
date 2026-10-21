package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.combinedIntoOf
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.SegmentCandidateCells
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.db.usableSegmentTracks
import io.github.solcott.activitytracker.appview.index.LIVE_STATUSES
import io.github.solcott.activitytracker.appview.index.recheckDuplicatesOf
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TimedPoint
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * Turns usable segment tracks into segment efforts (segments/segment-efforts), off the request path, from the jobs indexing queues
 * ([SegmentJobs]):
 * - `track`: fetches the track's blob from the athlete's `self` space, records its cells, and replaces its activity's efforts with its
 *   passes over the segments of its sport group that share a cell with it;
 * - `segment`: queues a `track` job for each usable track sharing a cell with a new or changed segment;
 * - `candidate`: the same for a new or changed staged candidate (segments/segment-candidates);
 * - `tiers`: recomputes the visibility of an activity's efforts from its indexed record, details and streams.
 *
 * Only proposed and active segments are matched (segments/segment-records). A proposed segment becomes active once efforts on it come from
 * [activeAthletes] distinct athletes, checked as each activity's efforts are written and again by the hourly status pass, which also
 * archives segments still proposed [ARCHIVE_AFTER] after they were first indexed.
 *
 * A `track` job also records which staged candidates of its group the track passes from start to end, by the same test as efforts, and a
 * candidate becomes ridden once [candidateRiders] distinct athletes pass it; ridden is never undone.
 *
 * Nothing is read from a track whose athlete's matching is off ([usableSegmentTracks]), and that is checked again before efforts are
 * written. An activity that isn't indexed yet, or is a valid member of a combined activity, has no efforts. A failed fetch is retried with
 * backoff, then given up.
 */
@Suppress("TooManyFunctions") // The loop, the four jobs, the status pass, and the reads they share.
class SegmentMatcher(
    private val db: Database,
    private val jobs: SegmentJobs,
    private val blobs: SpaceBlobs,
    private val clock: Clock = Clock.System,
    private val activeAthletes: Int = DEFAULT_ACTIVE_ATHLETES,
    private val candidateRiders: Int = DEFAULT_ACTIVE_ATHLETES,
) {
    private val log = LoggerFactory.getLogger(SegmentMatcher::class.java)

    /**
     * Runs until [scope] is cancelled: the one-off backfill, then the due jobs, waking on each enqueue or every [POLL_INTERVAL], and the
     * status pass on start and every [STATUS_INTERVAL].
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        guarded("Backfilling segment tracks") { backfill() }
        var statusesAt: Long? = null
        while (true) {
            val now = clock.now().toEpochMilliseconds()
            if (statusesAt == null || now - statusesAt >= STATUS_INTERVAL.inWholeMilliseconds) {
                guarded("Updating segment statuses") { updateStatuses() }
                statusesAt = now
            }
            guarded("Running segment jobs") { runDue() }
            withTimeoutOrNull(POLL_INTERVAL) { jobs.wakeups.receive() }
        }
    }

    /**
     * The status pass: promotes every proposed segment that already has enough athletes (those from before statuses, or after the threshold
     * was lowered), then archives those still proposed [ARCHIVE_AFTER] after they were first indexed and checks again the segments that
     * duplicated them. Archived segments keep their efforts. Exposed for tests.
     */
    suspend fun updateStatuses() {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            promote(Segments.select(Segments.uri).where { Segments.status eq SegmentStatus.PROPOSED }.map { it[Segments.uri] })
            val stale =
                Segments.select(Segments.uri)
                    .where {
                        (Segments.status eq SegmentStatus.PROPOSED) and
                            (Segments.firstIndexedAt less now - ARCHIVE_AFTER.inWholeMilliseconds)
                    }
                    .map { it[Segments.uri] }
            if (stale.isEmpty()) return@tx
            Segments.update({ Segments.uri inList stale }) { it[status] = SegmentStatus.ARCHIVED }
            recheckDuplicatesOf(stale, jobs, now)
        }
    }

    /** Makes each of [uris] that is proposed active once efforts on it come from [activeAthletes] distinct athletes. */
    private fun JdbcTransaction.promote(uris: Collection<String>) {
        for (uri in uris) {
            val athletes = SegmentEfforts.select(SegmentEfforts.authorDid).where { SegmentEfforts.segmentUri eq uri }.withDistinct().count()
            if (athletes < activeAthletes) continue
            Segments.update({ (Segments.uri eq uri) and (Segments.status eq SegmentStatus.PROPOSED) }) { it[status] = SegmentStatus.ACTIVE }
        }
    }

    /** Queues a `track` job for each usable track that was never matched (has no cells), such as those from before matching existed. */
    suspend fun backfill() {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            usableSegmentTracks()
                .andWhere {
                    notExists(SegmentTrackCells.select(SegmentTrackCells.cell).where { SegmentTrackCells.trackUri eq SegmentTracks.uri })
                }
                .map { it[SegmentTracks.uri] }
                .forEach { jobs.enqueueTrack(it, now) }
        }
    }

    /** Runs every due job, [BATCH] at a time, oldest first, and returns how many ran. Exposed for tests. */
    suspend fun runDue(): Int {
        var ran = 0
        while (true) {
            val now = clock.now().toEpochMilliseconds()
            val batch = db.tx {
                SegmentMatchJobs.selectAll()
                    .where { SegmentMatchJobs.enqueuedAt lessEq now }
                    .orderBy(SegmentMatchJobs.enqueuedAt to SortOrder.ASC)
                    .limit(BATCH)
                    .map {
                        Queued(
                            it[SegmentMatchJobs.kind],
                            it[SegmentMatchJobs.subjectUri],
                            it[SegmentMatchJobs.enqueuedAt],
                            it[SegmentMatchJobs.attempts],
                        )
                    }
            }
            if (batch.isEmpty()) return ran
            for (job in batch) {
                run(job)
                ran++
            }
            yield()
        }
    }

    private data class Queued(val kind: String, val subject: String, val enqueuedAt: Long, val attempts: Int)

    private suspend fun run(job: Queued) {
        try {
            when (job.kind) {
                SegmentJobs.TRACK -> matchTrack(job.subject)
                SegmentJobs.SEGMENT -> matchSegment(job.subject)
                SegmentJobs.CANDIDATE -> matchCandidate(job.subject)
                SegmentJobs.TIERS -> db.tx { retier(job.subject) }
            }
            done(job)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failed(job, e)
        }
    }

    /** Removes [job], unless it was enqueued again while it ran. */
    private suspend fun done(job: Queued) {
        db.tx { SegmentMatchJobs.deleteWhere { (kind eq job.kind) and (subjectUri eq job.subject) and (enqueuedAt eq job.enqueuedAt) } }
    }

    private suspend fun failed(job: Queued, e: Exception) {
        val attempts = job.attempts + 1
        val delay = BACKOFF_BASE * (1 shl minOf(attempts - 1, BACKOFF_DOUBLINGS))
        if (delay > BACKOFF_MAX) {
            log.warn("Segment job {} {} failed {} times; giving up: {}", job.kind, job.subject, attempts, e.message)
            done(job)
            return
        }
        log.info("Segment job {} {} failed, retrying in {}: {}", job.kind, job.subject, delay, e.message)
        db.tx {
            SegmentMatchJobs.update({
                (SegmentMatchJobs.kind eq job.kind) and
                    (SegmentMatchJobs.subjectUri eq job.subject) and
                    (SegmentMatchJobs.enqueuedAt eq job.enqueuedAt)
            }) {
                it[SegmentMatchJobs.attempts] = attempts
                it[enqueuedAt] = (clock.now() + delay).toEpochMilliseconds()
            }
        }
    }

    /** What a `track` job needs before fetching: the usable track and the sport group its activity can match. */
    private data class TrackToMatch(val author: Did, val activity: String, val dataCid: String, val group: SegmentSport.Grouped)

    private suspend fun matchTrack(trackUri: String) {
        val track = db.tx { trackToMatch(trackUri) } ?: return
        val space = SpaceUri.of(track.author, Nsid.SPACE_ATHLETE, AthleteSpace.SELF)
        val blob = checkNotNull(blobs.fetch(space, track.author, track.dataCid)) { "the space doesn't have blob ${track.dataCid}" }
        val pieces =
            AtJson.decodeFromString(SegmentTrackData.serializer(), blob.bytes.decodeToString()).pieces.map { piece ->
                require(piece.lat.size == piece.t.size && piece.lng.size == piece.t.size) { "a piece's arrays don't line up" }
                piece.t.indices.map { TimedPoint(piece.lat[it] / E7, piece.lng[it] / E7, piece.t[it].toDouble()) }
            }
        val cells = pieces.flatMapTo(mutableSetOf()) { piece -> SegmentCells.cellsOf(piece.map { it.point }) }
        db.tx {
            // Matching may have gone off, or the track changed, while the blob was fetched.
            if (trackToMatch(trackUri) != track) return@tx
            SegmentTrackCells.deleteWhere { SegmentTrackCells.trackUri eq trackUri }
            SegmentTrackCells.batchInsert(cells) {
                this[SegmentTrackCells.cell] = it
                this[SegmentTrackCells.trackUri] = trackUri
            }
            replaceEfforts(trackUri, track, pieces, cells)
            replaceCandidateRiders(trackUri, track, pieces, cells)
        }
    }

    /**
     * The track at [trackUri] if it's usable and its activity can have efforts; otherwise null, after deleting the efforts and candidate
     * riders it no longer gives.
     */
    @Suppress("ReturnCount") // Each reason a track gives no efforts.
    private fun JdbcTransaction.trackToMatch(trackUri: String): TrackToMatch? {
        fun none(): TrackToMatch? {
            SegmentEfforts.deleteWhere { SegmentEfforts.trackUri eq trackUri }
            SegmentCandidateRiders.deleteWhere { SegmentCandidateRiders.trackUri eq trackUri }
            return null
        }
        val track = usableSegmentTracks().andWhere { SegmentTracks.uri eq trackUri }.firstOrNull() ?: return none()
        val activity = track[SegmentTracks.activityUri]
        val row =
            Activities.select(Activities.sport, Activities.subSport).where { Activities.uri eq activity }.firstOrNull() ?: return none()
        if (combinedIntoOf(listOf(activity)).isNotEmpty()) return none()
        val group = SegmentSport.of(row[Activities.sport], row[Activities.subSport]) ?: return none()
        return TrackToMatch(Did(track[SegmentTracks.authorDid]), activity, track[SegmentTracks.dataCid], group)
    }

    /**
     * Replaces [track]'s activity's efforts with its passes over the live segments of its group in [cells], so its efforts on a segment
     * archived since go, and promotes the segments it now has efforts on.
     */
    private fun JdbcTransaction.replaceEfforts(trackUri: String, track: TrackToMatch, pieces: List<List<TimedPoint>>, cells: Set<Long>) {
        val segments =
            if (cells.isEmpty()) emptyList()
            else
                Segments.join(SegmentCellIndex, JoinType.INNER, Segments.uri, SegmentCellIndex.segmentUri)
                    .select(Segments.uri, Segments.line)
                    .where {
                        (SegmentCellIndex.cell inList cells) and
                            (Segments.sport eq track.group.group) and
                            (Segments.status inList LIVE_STATUSES)
                    }
                    .withDistinct()
                    .map { it[Segments.uri] to it[Segments.line] }
        val activity = Activities.selectAll().where { Activities.uri eq track.activity }.first()
        val visibility = effortVisibility(activity)
        val startedOn = startedOn(activity)
        val efforts = segments.flatMap { (uri, line) ->
            val ref = SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty())
            pieces.flatMap { SegmentGeometry.passes(it, ref) }.map { pass -> uri to pass }
        }
        SegmentEfforts.deleteWhere { activityUri eq track.activity }
        SegmentEfforts.batchInsert(efforts.distinctBy { (uri, pass) -> uri to pass.start.roundToLong() }) { (uri, pass) ->
            val offset = pass.start.roundToLong().coerceAtLeast(0)
            this[SegmentEfforts.id] = "${track.activity} $uri $offset"
            this[SegmentEfforts.segmentUri] = uri
            this[SegmentEfforts.activityUri] = track.activity
            this[SegmentEfforts.trackUri] = trackUri
            this[SegmentEfforts.authorDid] = track.author.value
            this[SegmentEfforts.sport] = track.group.group
            this[SegmentEfforts.eBike] = track.group.eBike
            this[SegmentEfforts.startOffsetS] = offset
            this[SegmentEfforts.elapsedS] = (pass.end - pass.start).roundToLong()
            this[SegmentEfforts.visibility] = visibility
            this[SegmentEfforts.startedOn] = startedOn
        }
        promote(efforts.map { (uri, _) -> uri }.toSet())
    }

    /**
     * Replaces [trackUri]'s candidate riders with the staged candidates of [track]'s group in [cells] that one of its [pieces] passes from
     * start to end, and makes each of those ridden once [candidateRiders] distinct athletes pass it.
     */
    private fun JdbcTransaction.replaceCandidateRiders(
        trackUri: String,
        track: TrackToMatch,
        pieces: List<List<TimedPoint>>,
        cells: Set<Long>,
    ) {
        val candidates =
            if (cells.isEmpty()) emptyList()
            else
                SegmentCandidateRows.join(SegmentCandidateCells, JoinType.INNER, SegmentCandidateRows.id, SegmentCandidateCells.candidateId)
                    .select(SegmentCandidateRows.id, SegmentCandidateRows.line)
                    .where { (SegmentCandidateCells.cell inList cells) and (SegmentCandidateRows.sport eq track.group.group) }
                    .withDistinct()
                    .map { it[SegmentCandidateRows.id] to it[SegmentCandidateRows.line] }
        val passed = candidates.filter { (_, line) ->
            val ref = SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty())
            pieces.any { SegmentGeometry.passes(it, ref).isNotEmpty() }
        }
        SegmentCandidateRiders.deleteWhere { SegmentCandidateRiders.trackUri eq trackUri }
        SegmentCandidateRiders.batchInsert(passed) { (id, _) ->
            this[SegmentCandidateRiders.candidateId] = id
            this[SegmentCandidateRiders.trackUri] = trackUri
            this[SegmentCandidateRiders.authorDid] = track.author.value
        }
        for ((id, _) in passed) {
            val riders =
                SegmentCandidateRiders.select(SegmentCandidateRiders.authorDid)
                    .where { SegmentCandidateRiders.candidateId eq id }
                    .withDistinct()
                    .count()
            if (riders >= candidateRiders) SegmentCandidateRows.update({ SegmentCandidateRows.id eq id }) { it[ridden] = true }
        }
    }

    /** Queues a `track` job for each usable track sharing a cell with the segment at [segmentUri], unless it's no longer live. */
    private suspend fun matchSegment(segmentUri: String) {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            val live = Segments.select(Segments.uri).where { (Segments.uri eq segmentUri) and (Segments.status inList LIVE_STATUSES) }.any()
            if (!live) return@tx
            enqueueTracksIn(
                SegmentCellIndex.select(SegmentCellIndex.cell)
                    .where { SegmentCellIndex.segmentUri eq segmentUri }
                    .map { it[SegmentCellIndex.cell] },
                now,
            )
        }
    }

    /** Queues a `track` job for each usable track sharing a cell with the staged candidate [candidateId], unless it's gone. */
    private suspend fun matchCandidate(candidateId: String) {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            enqueueTracksIn(
                SegmentCandidateCells.select(SegmentCandidateCells.cell)
                    .where { SegmentCandidateCells.candidateId eq candidateId }
                    .map { it[SegmentCandidateCells.cell] },
                now,
            )
        }
    }

    /** Queues a `track` job for each usable track with a cell in [cells]. */
    private fun JdbcTransaction.enqueueTracksIn(cells: List<Long>, now: Long) {
        if (cells.isEmpty()) return
        usableSegmentTracks()
            .andWhere {
                exists(
                    SegmentTrackCells.select(SegmentTrackCells.cell).where {
                        (SegmentTrackCells.trackUri eq SegmentTracks.uri) and (SegmentTrackCells.cell inList cells)
                    }
                )
            }
            .map { it[SegmentTracks.uri] }
            .forEach { jobs.enqueueTrack(it, now) }
    }

    /** Re-tiers [activity]'s efforts; an activity gone or now a valid member loses them. */
    private fun JdbcTransaction.retier(activity: String) {
        val row = Activities.selectAll().where { Activities.uri eq activity }.firstOrNull()
        if (row == null || combinedIntoOf(listOf(activity)).isNotEmpty()) {
            SegmentEfforts.deleteWhere { activityUri eq activity }
            SegmentCandidateRiders.deleteWhere {
                trackUri inSubQuery SegmentTracks.select(SegmentTracks.uri).where { SegmentTracks.activityUri eq activity }
            }
            return
        }
        val visibility = effortVisibility(row)
        SegmentEfforts.update({ SegmentEfforts.activityUri eq activity }) { it[SegmentEfforts.visibility] = visibility }
    }

    /** The narrowest of the activity's visibility and its Map and Pace/Speed categories', from the indexed rows. */
    private fun JdbcTransaction.effortVisibility(activity: ResultRow): String {
        val uri = activity[Activities.uri]
        val tier = activity[Activities.visibility]
        val record = AtJson.decodeFromString(ActivityRecord.serializer(), activity[Activities.record])
        val details =
            ActivityDetails.select(ActivityDetails.visibility, ActivityDetails.record)
                .where { ActivityDetails.activityUri eq uri }
                .map {
                    DetailsView(
                        it[ActivityDetails.visibility],
                        AtJson.decodeFromString(ActivityDetailsRecord.serializer(), it[ActivityDetails.record]),
                    )
                }
        val parts =
            ActivityStreams.select(ActivityStreams.visibility, ActivityStreams.channels)
                .where { ActivityStreams.activityUri eq uri }
                .map {
                    it[ActivityStreams.visibility] to
                        AtJson.decodeFromString(ListSerializer(String.serializer()), it[ActivityStreams.channels])
                }
        val categories = DetailCategory.currentTiersOfChannels(tier, record, details, parts)
        return listOf(tier, categories[DetailCategory.MAP] ?: tier, categories[DetailCategory.SPEED] ?: tier).reduce(Visibility::narrower)
    }

    /** The activity's local date, from its start and UTC offset. */
    private fun startedOn(activity: ResultRow): String {
        val record = AtJson.decodeFromString(ActivityRecord.serializer(), activity[Activities.record])
        val offset = ZoneOffset.ofTotalSeconds((record.utcOffset ?: 0) * SECONDS_PER_MINUTE)
        return java.time.Instant.ofEpochMilli(record.startedAt.toEpochMilliseconds()).atOffset(offset).toLocalDate().toString()
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.warn("{} failed; retrying: {}", what, e.message)
        }
    }

    private val TimedPoint.point
        get() = GeoPoint(lat, lng)

    companion object {
        /** How many distinct athletes' efforts make a proposed segment active, unless configured otherwise. */
        const val DEFAULT_ACTIVE_ATHLETES = 3

        /** How long a segment may stay proposed before it's archived. */
        val ARCHIVE_AFTER = 180.days

        private val STATUS_INTERVAL = 1.hours
        private const val BATCH = 20
        private const val E7 = 1e7
        private const val SECONDS_PER_MINUTE = 60
        private val POLL_INTERVAL = 2.seconds
        private val BACKOFF_BASE = 30.seconds
        private val BACKOFF_MAX = 1.hours
        private const val BACKOFF_DOUBLINGS = 8
    }
}
