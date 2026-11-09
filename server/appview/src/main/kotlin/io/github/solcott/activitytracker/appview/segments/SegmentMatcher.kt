package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.combinedIntoOf
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.DiveSiteCells
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.SegmentCandidateCells
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.groupableTracks
import io.github.solcott.activitytracker.appview.db.heldSegmentTracks
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.db.usableSegmentTracks
import io.github.solcott.activitytracker.appview.index.E7
import io.github.solcott.activitytracker.appview.index.LIVE_STATUSES
import io.github.solcott.activitytracker.appview.index.dropPairsOfActivities
import io.github.solcott.activitytracker.appview.index.dropSegmentUseOfActivity
import io.github.solcott.activitytracker.appview.index.dropSegmentUseOfTracks
import io.github.solcott.activitytracker.appview.index.recheckDiveSiteDuplicatesOf
import io.github.solcott.activitytracker.appview.index.recheckDuplicatesOf
import io.github.solcott.activitytracker.appview.index.siteCentre
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentTrackData
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.location.GainLoss
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentPass
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
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
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
 * - `tiers`: recomputes the visibility of an activity's efforts and pairs from its indexed record, details and streams;
 * - `group`: pairs a groupable track's activity with other athletes' done together with it (feed/group-activities, [GroupPairing]).
 *
 * Only proposed and active segments are matched (segments/segment-records). A proposed segment becomes active once efforts on it come from
 * [activeAthletes] distinct athletes, checked as each activity's efforts are written and again by the hourly status pass, which also
 * archives segments still proposed [ARCHIVE_AFTER] after they were first indexed.
 *
 * A `track` job also records which staged candidates of its group the track passes from start to end, by the same test as efforts, and a
 * candidate becomes ridden once [candidateRiders] distinct athletes pass it; ridden is never undone.
 *
 * A track is read only while its athlete's matching ([usableSegmentTracks]) or grouping ([groupableTracks]) is on, and used for each
 * purpose only under its own consent, checked again before anything is written: its cells are kept for either, efforts and candidate riders
 * only while matching is on, and a `group` job is queued only while it's groupable. An activity that isn't indexed yet, or is a valid
 * member of a combined activity, has no efforts. A failed fetch is retried with backoff, then given up. A blob that isn't a valid track
 * ([piecesOf]) isn't retried: its activity's efforts, its cells and its candidate riders are dropped.
 */
@Suppress("TooManyFunctions", "LongParameterList") // The loop, the four jobs, the status and naming passes, the reads they share; config.
class SegmentMatcher(
    private val db: Database,
    private val jobs: SegmentJobs,
    private val blobs: SpaceBlobs,
    private val clock: Clock = Clock.System,
    private val activeAthletes: Int = DEFAULT_ACTIVE_ATHLETES,
    private val candidateRiders: Int = DEFAULT_ACTIVE_ATHLETES,
    names: SegmentNameRules = SegmentNameRules(),
    /** The segment importer's account (`SEGMENT_IMPORTER_DID`), whose segments are active without riders (segments/segment-records). */
    private val segmentImporter: Did? = null,
    /** Terrain tiles (`TERRAIN_TILES_DIR`), which give segments terrain elevation (segments/segment-elevation); null for none. */
    private val terrain: TerrainTiles? = null,
) {
    private val log = LoggerFactory.getLogger(SegmentMatcher::class.java)
    private val naming = SegmentNaming(db, clock, names)
    private val pairing = GroupPairing(db, blobs, clock)

    /**
     * Runs until [scope] is cancelled: the one-off backfills, then the due jobs, waking on each enqueue or every [POLL_INTERVAL], and the
     * status pass, then the naming pass, on start and every [STATUS_INTERVAL].
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        guarded("Backfilling segment tracks") { backfill() }
        guarded("Activating the importer's segments") { activateImporterSegments() }
        guarded("Backfilling terrain elevation") { backfillTerrain() }
        var statusesAt: Long? = null
        while (true) {
            val now = clock.now().toEpochMilliseconds()
            if (statusesAt == null || now - statusesAt >= STATUS_INTERVAL.inWholeMilliseconds) {
                guarded("Updating segment statuses") { updateStatuses() }
                guarded("Updating segment names") { updateNames() }
                statusesAt = now
            }
            guarded("Running segment jobs") { runDue() }
            withTimeoutOrNull(POLL_INTERVAL) { jobs.wakeups.receive() }
        }
    }

    /**
     * The one-off backfill of segment statuses, run on start before the first status pass can archive any: makes the segment importer's
     * proposed segments active, those indexed before its segments started active. Indexing makes its new ones active ([liveStatus]), so
     * later passes find none. Exposed for tests.
     */
    suspend fun activateImporterSegments() {
        val importer = segmentImporter ?: return
        db.tx {
            Segments.update({ (Segments.authorDid eq importer.value) and (Segments.status eq SegmentStatus.PROPOSED) }) {
                it[status] = SegmentStatus.ACTIVE
            }
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
            if (stale.isNotEmpty()) {
                Segments.update({ Segments.uri inList stale }) { it[status] = SegmentStatus.ARCHIVED }
                recheckDuplicatesOf(stale, jobs, now, segmentImporter)
            }
            updateSiteStatuses(now)
        }
    }

    /**
     * The status pass's dive-site step (dive-sites/site-records): promotes every proposed site already visited by enough athletes, then
     * archives those still proposed [ARCHIVE_AFTER] after they were first indexed, which keep their visits but get no new ones, and checks
     * again the sites that duplicated them.
     */
    private fun JdbcTransaction.updateSiteStatuses(now: Long) {
        promoteSites(DiveSites.select(DiveSites.uri).where { DiveSites.status eq SegmentStatus.PROPOSED }.map { it[DiveSites.uri] })
        val stale =
            DiveSites.select(DiveSites.uri)
                .where {
                    (DiveSites.status eq SegmentStatus.PROPOSED) and (DiveSites.firstIndexedAt less now - ARCHIVE_AFTER.inWholeMilliseconds)
                }
                .map { it[DiveSites.uri] }
        if (stale.isEmpty()) return
        DiveSites.update({ DiveSites.uri inList stale }) { it[status] = SegmentStatus.ARCHIVED }
        recheckDiveSiteDuplicatesOf(stale, jobs, now)
    }

    /** The naming pass ([SegmentNaming]), run after each status pass. Exposed for tests. */
    suspend fun updateNames() = naming.update()

    /** Makes each of [uris] that is proposed active once efforts on it come from [activeAthletes] distinct athletes. */
    private fun JdbcTransaction.promote(uris: Collection<String>) {
        for (uri in uris) {
            val athletes = SegmentEfforts.select(SegmentEfforts.authorDid).where { SegmentEfforts.segmentUri eq uri }.withDistinct().count()
            if (athletes < activeAthletes) continue
            Segments.update({ (Segments.uri eq uri) and (Segments.status eq SegmentStatus.PROPOSED) }) { it[status] = SegmentStatus.ACTIVE }
        }
    }

    /** Queues a `track` job for each held track that was never matched (has no cells), such as those from before matching existed. */
    suspend fun backfill() {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            heldSegmentTracks()
                .andWhere {
                    notExists(SegmentTrackCells.select(SegmentTrackCells.cell).where { SegmentTrackCells.trackUri eq SegmentTracks.uri })
                }
                .map { it[SegmentTracks.uri] }
                .forEach { jobs.enqueueTrack(it, now) }
        }
    }

    /**
     * Queues an `elevation` job for every segment with no terrain elevation, or no maximum grade, whose box touches the tiles'
     * [TerrainTiles.coverage], when terrain tiles are configured: those indexed before them, and those they didn't cover, in case tiles
     * were added since. Segments the tiles don't reach aren't queued, so a large table outside them costs one statement. Exposed for tests.
     */
    suspend fun backfillTerrain() {
        val tiles = terrain ?: return
        val areas = tiles.coverage
        if (areas.isEmpty()) return
        val now = clock.now().toEpochMilliseconds()
        val touchesTiles =
            areas
                .map { area ->
                    (Segments.maxLatE7 greaterEq area.minLatE7) and
                        (Segments.minLatE7 lessEq area.maxLatE7) and
                        (Segments.maxLngE7 greaterEq area.minLngE7) and
                        (Segments.minLngE7 lessEq area.maxLngE7)
                }
                .compoundOr()
        // A covered segment sampled before maximum grades were has terrain but none; sampling it again gives it one.
        val unsampled = Segments.terrainGainM.isNull() or Segments.terrainMaxGrade.isNull()
        val queued = db.tx { jobs.enqueueElevations(unsampled and touchesTiles, now) }
        log.info("Queued {} segments for terrain elevation across {} tiled areas", queued, areas.size)
    }

    /**
     * Samples the terrain along the segment at [segmentUri]'s line and stores its gain, loss and profile, all null when the tiles don't
     * cover it. A segment whose box doesn't touch the tiles at all is cleared without decoding its line.
     */
    private suspend fun sampleTerrain(segmentUri: String) {
        val tiles = terrain ?: return
        val row =
            db.tx {
                Segments.select(Segments.line, Segments.minLatE7, Segments.maxLatE7, Segments.minLngE7, Segments.maxLngE7)
                    .where { Segments.uri eq segmentUri }
                    .firstOrNull()
            } ?: return
        val line = row[Segments.line]
        val touches = tiles.touches(row[Segments.minLatE7], row[Segments.maxLatE7], row[Segments.minLngE7], row[Segments.maxLngE7])
        val elevation = if (touches) SegmentGeometry.decodePolyline(line)?.let(tiles::terrainOf) else null
        db.tx {
            // Only if the line is still the one sampled: a re-line queues another job.
            Segments.update({ (Segments.uri eq segmentUri) and (Segments.line eq line) }) {
                it[terrainGainM] = elevation?.gain
                it[terrainLossM] = elevation?.loss
                it[terrainProfile] = elevation?.profileDm?.joinToString(",")
                it[terrainMaxGrade] = elevation?.maxGradeTenths
            }
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
                SegmentJobs.GROUP -> pairing.match(job.subject)
                SegmentJobs.SITE -> matchSite(job.subject)
                SegmentJobs.ELEVATION -> sampleTerrain(job.subject)
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
            SegmentMatchJobs.update(
                {
                    (SegmentMatchJobs.kind eq job.kind) and
                        (SegmentMatchJobs.subjectUri eq job.subject) and
                        (SegmentMatchJobs.enqueuedAt eq job.enqueuedAt)
                },
            ) {
                it[SegmentMatchJobs.attempts] = attempts
                it[enqueuedAt] = (clock.now() + delay).toEpochMilliseconds()
            }
        }
    }

    /**
     * What a `track` job needs before fetching: the held track, the sport group its activity can match, and which purposes may use it now.
     * Compared again before writing, so a consent change while the blob was fetched writes nothing.
     */
    private data class TrackToMatch(
        val author: Did,
        val activity: String,
        val dataCid: String,
        /** The sport group its activity can match segments of; null for a dive, which matches only dive sites. */
        val group: SegmentSport.Grouped?,
        /** Its activity is a dive that can visit dive sites (dive-sites/site-visits). */
        val dive: Boolean,
        val segments: Boolean,
        val grouping: Boolean,
    )

    /** A pass over [segment], starting [offset] whole seconds into the activity, and its [elevation] by the track's altitude. */
    private data class Pass(val segment: String, val offset: Long, val pass: SegmentPass, val elevation: GainLoss?)

    private suspend fun matchTrack(trackUri: String) {
        val track = db.tx { trackToMatch(trackUri) } ?: return
        val space = SpaceUri.of(track.author, Nsid.SPACE_ATHLETE, AthleteSpace.SELF)
        val blob = checkNotNull(blobs.fetch(space, track.author, track.dataCid)) { "the space doesn't have blob ${track.dataCid}" }
        val pieces =
            try {
                segmentTrackPieces(blob.bytes)
            } catch (e: IllegalArgumentException) {
                // Never valid however often it's fetched, so it gives nothing, and nothing an earlier version gave stays.
                log.info("Segment track {} isn't a valid track, so it gives no efforts or pairs: {}", trackUri, e.message)
                db.tx {
                    if (trackToMatch(trackUri) != track) return@tx
                    dropSegmentUseOfActivity(track.activity)
                    SegmentTrackCells.deleteWhere { SegmentTrackCells.trackUri eq trackUri }
                    dropPairsOfActivities(listOf(track.activity))
                }
                return
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
            if (track.segments) {
                val group = track.group
                if (group != null) {
                    replaceEfforts(trackUri, track, group, pieces, cells)
                    replaceCandidateRiders(trackUri, track, group, pieces, cells)
                }
                replaceVisit(trackUri, track, pieces)
            } else {
                dropSegmentUseOfActivity(track.activity)
            }
            // Pairing fetches other athletes' tracks too, so it's a job of its own.
            if (track.grouping) jobs.enqueueGroup(trackUri, clock.now().toEpochMilliseconds())
            else dropPairsOfActivities(listOf(track.activity))
        }
    }

    /**
     * The track at [trackUri] if some purpose may read it now and its activity can have efforts or pairs; otherwise null, after deleting
     * the efforts, candidate riders and pairs it no longer gives.
     */
    @Suppress("ReturnCount") // Each reason a track gives nothing.
    private fun JdbcTransaction.trackToMatch(trackUri: String): TrackToMatch? {
        fun none(activity: String? = null): TrackToMatch? {
            dropSegmentUseOfTracks(listOf(trackUri))
            val pairsOf =
                activity
                    ?: SegmentTracks.select(SegmentTracks.activityUri)
                        .where { SegmentTracks.uri eq trackUri }
                        .firstOrNull()
                        ?.get(SegmentTracks.activityUri)
            if (pairsOf != null) dropPairsOfActivities(listOf(pairsOf))
            return null
        }
        val track = heldSegmentTracks().andWhere { SegmentTracks.uri eq trackUri }.firstOrNull() ?: return none()
        val activity = track[SegmentTracks.activityUri]
        val row =
            Activities.select(Activities.sport, Activities.subSport).where { Activities.uri eq activity }.firstOrNull()
                ?: return none(activity)
        if (combinedIntoOf(listOf(activity)).isNotEmpty()) return none(activity)
        val group = SegmentSport.of(row[Activities.sport], row[Activities.subSport])
        val dive = visitsSites(row[Activities.sport], row[Activities.subSport])
        if (group == null && !dive) return none(activity)
        val segments = !usableSegmentTracks().andWhere { SegmentTracks.uri eq trackUri }.empty()
        // A dive has no sport group, so nothing to pair; it's read only for dive sites, under segment matching.
        val grouping = group != null && !groupableTracks().andWhere { SegmentTracks.uri eq trackUri }.empty()
        if (group == null && !segments) return none(activity)
        return TrackToMatch(Did(track[SegmentTracks.authorDid]), activity, track[SegmentTracks.dataCid], group, dive, segments, grouping)
    }

    /**
     * Replaces [track]'s activity's efforts with its passes over the live segments of its group in [cells], so its efforts on a segment
     * archived since go, and promotes the segments it now has efforts on.
     */
    private fun JdbcTransaction.replaceEfforts(
        trackUri: String,
        track: TrackToMatch,
        grouped: SegmentSport.Grouped,
        pieces: List<List<TimedPoint>>,
        cells: Set<Long>,
    ) {
        val segments =
            if (cells.isEmpty()) emptyList()
            else
                Segments.join(SegmentCellIndex, JoinType.INNER, Segments.uri, SegmentCellIndex.segmentUri)
                    .select(Segments.uri, Segments.line, Segments.kind)
                    .where {
                        (SegmentCellIndex.cell inList cells) and
                            (Segments.sport eq grouped.group) and
                            (Segments.status inList LIVE_STATUSES)
                    }
                    .withDistinct()
                    .map { Triple(it[Segments.uri], it[Segments.line], it[Segments.kind]) }
        val activity = Activities.selectAll().where { Activities.uri eq track.activity }.first()
        val visibility = effortVisibility(activity)
        val startedOn = startedOn(activity)
        // Passes starting in the same second on one segment are one effort, and that second is part of its id.
        val efforts =
            segments
                .flatMap { (uri, line, kind) ->
                    val ref = SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty())
                    pieces.flatMap { piece ->
                        passes(piece, ref, grouped.group, kind).map { pass ->
                            Pass(uri, pass.start.roundToLong(), pass, passElevation(piece, pass))
                        }
                    }
                }
                .distinctBy { it.segment to it.offset }
        rewriteEfforts({ activityUri eq track.activity }) { insertEfforts(efforts, trackUri, track, grouped, visibility, startedOn) }
        promote(efforts.map { it.segment }.toSet())
    }

    /** Writes [efforts] of [track]'s activity, returning the segments they're on. */
    @Suppress("LongParameterList") // The efforts and what each row repeats.
    private fun insertEfforts(
        efforts: List<Pass>,
        trackUri: String,
        track: TrackToMatch,
        grouped: SegmentSport.Grouped,
        visibility: String,
        startedOn: String,
    ): List<String> {
        SegmentEfforts.batchInsert(efforts) { effort ->
            val (uri, offset, pass) = effort
            this[SegmentEfforts.id] = "${track.activity} $uri $offset"
            this[SegmentEfforts.segmentUri] = uri
            this[SegmentEfforts.activityUri] = track.activity
            this[SegmentEfforts.trackUri] = trackUri
            this[SegmentEfforts.authorDid] = track.author.value
            this[SegmentEfforts.sport] = grouped.group
            this[SegmentEfforts.eBike] = grouped.eBike
            this[SegmentEfforts.startOffsetS] = offset
            this[SegmentEfforts.elapsedS] = (pass.end - pass.start).roundToLong()
            this[SegmentEfforts.visibility] = visibility
            this[SegmentEfforts.startedOn] = startedOn
            this[SegmentEfforts.elevationGainM] = effort.elevation?.gain
            this[SegmentEfforts.elevationLossM] = effort.elevation?.loss
        }
        return efforts.map { it.segment }
    }

    /**
     * Replaces [trackUri]'s candidate riders with the staged candidates of [track]'s group in [cells] that one of its [pieces] passes from
     * start to end, and makes each of those ridden once [candidateRiders] distinct athletes pass it.
     */
    private fun JdbcTransaction.replaceCandidateRiders(
        trackUri: String,
        track: TrackToMatch,
        grouped: SegmentSport.Grouped,
        pieces: List<List<TimedPoint>>,
        cells: Set<Long>,
    ) {
        val candidates =
            if (cells.isEmpty()) emptyList()
            else
                SegmentCandidateRows.join(SegmentCandidateCells, JoinType.INNER, SegmentCandidateRows.id, SegmentCandidateCells.candidateId)
                    .select(SegmentCandidateRows.id, SegmentCandidateRows.line, SegmentCandidateRows.kind)
                    .where { (SegmentCandidateCells.cell inList cells) and (SegmentCandidateRows.sport eq grouped.group) }
                    .withDistinct()
                    .map { Triple(it[SegmentCandidateRows.id], it[SegmentCandidateRows.line], it[SegmentCandidateRows.kind]) }
        val passed = candidates.filter { (_, line, kind) ->
            val ref = SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty())
            pieces.any { passes(it, ref, grouped.group, kind).isNotEmpty() }
        }
        SegmentCandidateRiders.deleteWhere { SegmentCandidateRiders.trackUri eq trackUri }
        SegmentCandidateRiders.batchInsert(passed) { (id, _, _) ->
            this[SegmentCandidateRiders.candidateId] = id
            this[SegmentCandidateRiders.trackUri] = trackUri
            this[SegmentCandidateRiders.authorDid] = track.author.value
        }
        for ((id, _, _) in passed) {
            val riders =
                SegmentCandidateRiders.select(SegmentCandidateRiders.authorDid)
                    .where { SegmentCandidateRiders.candidateId eq id }
                    .withDistinct()
                    .count()
            if (riders >= candidateRiders) SegmentCandidateRows.update({ SegmentCandidateRows.id eq id }) { it[ridden] = true }
        }
    }

    /**
     * [piece]'s passes over [ref], a segment or candidate of [group] and [kind], within that segment's tolerance
     * (segments/segment-efforts).
     */
    private fun passes(piece: List<TimedPoint>, ref: List<GeoPoint>, group: String, kind: String): List<SegmentPass> {
        val tolerance = SegmentSportGroup.passTolerance(group, kind)
        return SegmentGeometry.passes(
            piece,
            ref,
            startRadius = tolerance.startM.toDouble(),
            endRadius = tolerance.endM.toDouble(),
            corridor = tolerance.corridorM.toDouble(),
        )
    }

    /**
     * Replaces [track]'s activity's visit (dive-sites/site-visits) with one to the live site whose centre is nearest its entry, the first
     * position of its first piece, among those whose radius contains it; none when no site does, or [track] isn't a dive. The visit keeps
     * the dive's local date, its tier (the narrowest of the activity's and its Map category's) and its conditions with its Dive category's
     * tier. Promotes the site it now visits.
     */
    private fun JdbcTransaction.replaceVisit(trackUri: String, track: TrackToMatch, pieces: List<List<TimedPoint>>) {
        DiveSiteVisits.deleteWhere { activityUri eq track.activity }
        val entry = pieces.firstOrNull { it.isNotEmpty() }?.first()?.point
        if (!track.dive || entry == null) return
        val site =
            DiveSites.join(DiveSiteCells, JoinType.INNER, DiveSites.uri, DiveSiteCells.siteUri)
                .select(DiveSites.uri, DiveSites.latE7, DiveSites.lngE7, DiveSites.radiusM, DiveSites.firstIndexedAt)
                .where { (DiveSiteCells.cell eq SegmentCells.cellOf(entry.lat, entry.lng)) and (DiveSites.status inList LIVE_STATUSES) }
                .map { it to SegmentGeometry.distanceMeters(it.siteCentre(), entry) }
                .filter { (row, distance) -> distance <= row[DiveSites.radiusM] }
                .minWithOrNull(compareBy({ it.second }, { it.first[DiveSites.firstIndexedAt] }, { it.first[DiveSites.uri] }))
                ?.first
                ?.get(DiveSites.uri) ?: return
        val activity = Activities.selectAll().where { Activities.uri eq track.activity }.first()
        val conditions = visitConditions(activity)
        DiveSiteVisits.insert {
            it[activityUri] = track.activity
            it[siteUri] = site
            it[DiveSiteVisits.trackUri] = trackUri
            it[authorDid] = track.author.value
            it[visibility] = visitVisibility(activity)
            it[startedOn] = startedOn(activity)
            it[minTemperature] = conditions.minTemperature
            it[visibilityCm] = conditions.visibility
            it[maxDepthCm] = conditions.maxDepth
            it[diveTier] = narrowestOf(activity, DetailCategory.DIVE)
        }
        promoteSites(listOf(site))
    }

    /** The dive's summary from every held tier, whose fields a visit copies; read only for the conditions served at its Dive tier. */
    private fun JdbcTransaction.visitConditions(activity: ResultRow): ActivityDive = exactRecord(activity).dive ?: ActivityDive()

    /** A visit's tier: the narrowest of the activity's and its Map category's. */
    private fun JdbcTransaction.visitVisibility(activity: ResultRow): String = narrowestOf(activity, DetailCategory.MAP)

    /** Queues a `track` job for each usable dive track sharing a cell with the dive site at [siteUri], unless it's no longer live. */
    private suspend fun matchSite(siteUri: String) {
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            val live =
                DiveSites.select(DiveSites.uri).where { (DiveSites.uri eq siteUri) and (DiveSites.status inList LIVE_STATUSES) }.any()
            if (!live) return@tx
            val cells = DiveSiteCells.select(DiveSiteCells.cell).where { DiveSiteCells.siteUri eq siteUri }.map { it[DiveSiteCells.cell] }
            if (cells.isEmpty()) return@tx
            usableSegmentTracks()
                .andWhere {
                    exists(
                        SegmentTrackCells.select(SegmentTrackCells.cell).where {
                            (SegmentTrackCells.trackUri eq SegmentTracks.uri) and (SegmentTrackCells.cell inList cells)
                        },
                    ) and
                        exists(
                            Activities.select(Activities.uri).where {
                                (Activities.uri eq SegmentTracks.activityUri) and (Activities.sport eq Sport.DIVE)
                            },
                        )
                }
                .map { it[SegmentTracks.uri] }
                .forEach { jobs.enqueueTrack(it, now) }
        }
    }

    /** Makes each of [uris] that is a proposed dive site active once visits to it come from [activeAthletes] distinct athletes. */
    private fun JdbcTransaction.promoteSites(uris: Collection<String>) {
        for (uri in uris) {
            val athletes = DiveSiteVisits.select(DiveSiteVisits.authorDid).where { DiveSiteVisits.siteUri eq uri }.withDistinct().count()
            if (athletes < activeAthletes) continue
            DiveSites.update({ (DiveSites.uri eq uri) and (DiveSites.status eq SegmentStatus.PROPOSED) }) {
                it[status] = SegmentStatus.ACTIVE
            }
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
                    },
                )
            }
            .map { it[SegmentTracks.uri] }
            .forEach { jobs.enqueueTrack(it, now) }
    }

    /** Re-tiers [activity]'s efforts and pairs; an activity gone or now a valid member loses them. */
    private fun JdbcTransaction.retier(activity: String) {
        val row = Activities.selectAll().where { Activities.uri eq activity }.firstOrNull()
        if (row == null || combinedIntoOf(listOf(activity)).isNotEmpty()) {
            dropSegmentUseOfActivity(activity)
            dropPairsOfActivities(listOf(activity))
            return
        }
        val visibility = effortVisibility(row)
        SegmentEfforts.update({ SegmentEfforts.activityUri eq activity }) { it[SegmentEfforts.visibility] = visibility }
        if (!DiveSiteVisits.select(DiveSiteVisits.activityUri).where { DiveSiteVisits.activityUri eq activity }.empty()) {
            val conditions = visitConditions(row)
            val visitTier = visitVisibility(row)
            val diveTier = narrowestOf(row, DetailCategory.DIVE)
            DiveSiteVisits.update({ DiveSiteVisits.activityUri eq activity }) {
                it[DiveSiteVisits.visibility] = visitTier
                it[minTemperature] = conditions.minTemperature
                it[visibilityCm] = conditions.visibility
                it[maxDepthCm] = conditions.maxDepth
                it[DiveSiteVisits.diveTier] = diveTier
            }
        }
        retierPairs(row)
    }

    /** The narrowest of the activity's visibility and its Map and Pace/Speed categories', from the indexed rows. */
    private fun JdbcTransaction.effortVisibility(activity: ResultRow): String =
        narrowestOf(activity, DetailCategory.MAP, DetailCategory.SPEED)

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
        private const val SECONDS_PER_MINUTE = 60
        private val POLL_INTERVAL = 2.seconds
        private val BACKOFF_BASE = 30.seconds
        private val BACKOFF_MAX = 1.hours
        private const val BACKOFF_DOUBLINGS = 8
    }
}

/**
 * The narrowest of [activity]'s visibility and those of its [categories], from the indexed record, details and streams rows: the tier its
 * efforts (Map, Pace/Speed) or its pairs (Start time, Map) are served at.
 */
internal fun JdbcTransaction.narrowestOf(activity: ResultRow, vararg categories: String): String {
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
                it[ActivityStreams.visibility] to AtJson.decodeFromString(ListSerializer(String.serializer()), it[ActivityStreams.channels])
            }
    val tiers = DetailCategory.currentTiersOfChannels(tier, record, details, parts)
    return categories.map { tiers[it] ?: tier }.fold(tier, Visibility::narrower)
}

/**
 * A segment track blob's pieces, or [IllegalArgumentException] when it isn't a valid track: it doesn't decode, a piece's arrays (its
 * altitudes included, when it has them) don't line up, or a piece's times are negative or go backwards. Times may repeat, since they're
 * whole seconds. A missing altitude sample stays null.
 */
internal fun segmentTrackPieces(bytes: ByteArray): List<List<TimedPoint>> =
    AtJson.decodeFromString(SegmentTrackData.serializer(), bytes.decodeToString()).pieces.map { piece ->
        require(piece.lat.size == piece.t.size && piece.lng.size == piece.t.size) { "a piece's arrays don't line up" }
        require(piece.t.all { it >= 0 }) { "a piece's times are negative" }
        require(piece.t.zipWithNext().all { (a, b) -> b >= a }) { "a piece's times go backwards" }
        val alt = piece.alt
        require(alt == null || alt.size == piece.t.size) { "a piece's altitudes don't line up" }
        piece.t.indices.map {
            TimedPoint(piece.lat[it] / E7, piece.lng[it] / E7, piece.t[it].toDouble(), alt?.get(it)?.let { cm -> cm / CM_PER_M })
        }
    }

private const val CM_PER_M = 100.0

/** Whether an activity of [sport] and [subSport] visits dive sites: a dive that isn't a pool session (dive-sites/site-visits). */
internal fun visitsSites(sport: String, subSport: String?): Boolean =
    sport == Sport.DIVE && subSport != SubSport.POOL_APNEA && subSport != SubSport.POOL
