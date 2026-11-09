package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.combinedIntoOf
import io.github.solcott.activitytracker.appview.api.follows
import io.github.solcott.activitytracker.appview.api.isBlocked
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityGroupPairs
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTrackCells
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.groupableTracks
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.dropPairsOfActivities
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.location.TimedPoint
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/**
 * The [SegmentMatcher]'s `group` job (feed/group-activities): replaces a groupable track's activity's pairs with the other athletes'
 * activities done together with it.
 *
 * Candidates are other athletes' groupable tracks in the same sport group, starting near enough in time and sharing a cell with this one
 * ([SegmentTrackCells], written by the `track` job first), at most [MAX_CANDIDATES]. Each must pass the pair rule ([mayPair]) and then
 * [GroupMatch.together] on both tracks' points at absolute times: the activity's exact start, from every details tier the AppView holds,
 * plus the track's offsets. The exact start is used, never served: a pair is shown only at a tier that includes the Start time category.
 *
 * Whichever of two tracks is matched second finds the first, so a pair appears once both are indexed. A pair row is replaced whenever
 * either side is matched again.
 */
internal class GroupPairing(private val db: Database, private val blobs: SpaceBlobs, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(GroupPairing::class.java)

    /** What pairing needs to know of one groupable track and its activity. */
    private data class Side(
        val trackUri: String,
        val author: Did,
        val activity: String,
        val dataCid: String,
        val group: String,
        val mode: String,
        /** Epoch seconds. */
        val start: Double,
        /** Seconds. */
        val elapsed: Double,
        val visibility: String,
    )

    suspend fun match(trackUri: String) {
        val me = db.tx { sideOf(trackUri).also { if (it == null) dropPairsOf(trackUri) } } ?: return
        val candidates = db.tx { candidatesOf(me) }
        val fetched = mutableMapOf<String, GroupMatch.Track?>()
        suspend fun track(side: Side) = fetched.getOrPut(side.trackUri) { trackOf(side) }
        val mine = track(me)
        val partners =
            if (mine == null) emptyList()
            else candidates.filter { other -> track(other)?.let { GroupMatch.together(mine, it, GroupMatch.toleranceM(me.group)) } == true }
        val now = clock.now().toEpochMilliseconds()
        db.tx {
            // Consent, the pair rule or the track may have changed while the blobs were fetched.
            if (sideOf(trackUri) != me) return@tx
            dropPairsOfActivities(listOf(me.activity))
            val kept = partners.filter { sideOf(it.trackUri) == it && mayPair(me, it) }
            ActivityGroupPairs.batchInsert(kept, shouldReturnGeneratedValues = false) { other ->
                val (a, b) = if (me.activity < other.activity) me to other else other to me
                this[ActivityGroupPairs.activityA] = a.activity
                this[ActivityGroupPairs.activityB] = b.activity
                this[ActivityGroupPairs.authorA] = a.author.value
                this[ActivityGroupPairs.authorB] = b.author.value
                this[ActivityGroupPairs.visibilityA] = a.visibility
                this[ActivityGroupPairs.visibilityB] = b.visibility
                this[ActivityGroupPairs.matchedAt] = now
            }
        }
    }

    /** [side]'s points at absolute times, or null when its blob is gone or isn't a valid track. */
    @Suppress("ReturnCount") // Its points, or why there are none.
    private suspend fun trackOf(side: Side): GroupMatch.Track? {
        val space = SpaceUri.of(side.author, Nsid.SPACE_ATHLETE, AthleteSpace.SELF)
        val blob = blobs.fetch(space, side.author, side.dataCid) ?: return null
        val pieces =
            try {
                segmentTrackPieces(blob.bytes)
            } catch (e: IllegalArgumentException) {
                log.info("Segment track {} isn't a valid track, so it pairs with nothing: {}", side.trackUri, e.message)
                return null
            }
        return GroupMatch.Track(pieces.map { piece -> piece.map { TimedPoint(it.lat, it.lng, side.start + it.t) } }, side.elapsed)
    }

    private fun JdbcTransaction.dropPairsOf(trackUri: String) {
        SegmentTracks.select(SegmentTracks.activityUri)
            .where { SegmentTracks.uri eq trackUri }
            .firstOrNull()
            ?.let { dropPairsOfActivities(listOf(it[SegmentTracks.activityUri])) }
    }

    /** The groupable track at [trackUri], if its activity is indexed, isn't a valid member of a combined one and has a sport group. */
    @Suppress("ReturnCount") // Each reason a track can't pair.
    private fun JdbcTransaction.sideOf(trackUri: String): Side? {
        val track = groupableTracks().andWhere { SegmentTracks.uri eq trackUri }.firstOrNull() ?: return null
        val activity = track[SegmentTracks.activityUri]
        val row = Activities.selectAll().where { Activities.uri eq activity }.firstOrNull() ?: return null
        if (combinedIntoOf(listOf(activity)).isNotEmpty()) return null
        val group = SegmentSport.of(row[Activities.sport], row[Activities.subSport])?.group ?: return null
        // Lift-served skiing matches alpine segments but is never paired (feed/group-activities): a resort day crosses strangers' all day.
        if (group == SegmentSportGroup.ALPINE) return null
        val exact = exactRecord(row)
        return Side(
            trackUri = trackUri,
            author = Did(track[SegmentTracks.authorDid]),
            activity = activity,
            dataCid = track[SegmentTracks.dataCid],
            group = group,
            mode = track[GroupPrefs.mode],
            start = exact.startedAt.toEpochMilliseconds() / MS_PER_S,
            elapsed = exact.elapsedTime.toDouble(),
            visibility = pairVisibility(row),
        )
    }

    /**
     * Other athletes' groupable tracks that could have been with [me]: their activity's indexed start (the local day's midnight when its
     * Start time is narrower, so within a day before the exact one) no later than [me]'s end and no earlier than [START_SLACK] before
     * [me]'s start, sharing a cell with [me]'s track, in [me]'s sport group and passing the pair rule.
     */
    private fun JdbcTransaction.candidatesOf(me: Side): List<Side> {
        val startMs = (me.start * MS_PER_S).toLong()
        val endMs = ((me.start + me.elapsed) * MS_PER_S).toLong()
        val myCells = SegmentTrackCells.select(SegmentTrackCells.cell).where { SegmentTrackCells.trackUri eq me.trackUri }
        return groupableTracks()
            .adjustColumnSet { join(Activities, JoinType.INNER, SegmentTracks.activityUri, Activities.uri) }
            .andWhere {
                (SegmentTracks.authorDid neq me.author.value) and
                    (Activities.startedAt greaterEq startMs - START_SLACK.inWholeMilliseconds) and
                    (Activities.startedAt lessEq endMs) and
                    exists(
                        SegmentTrackCells.select(SegmentTrackCells.cell).where {
                            (SegmentTrackCells.trackUri eq SegmentTracks.uri) and (SegmentTrackCells.cell inSubQuery myCells)
                        },
                    )
            }
            .limit(MAX_CANDIDATES)
            .map { it[SegmentTracks.uri] }
            .mapNotNull { sideOf(it) }
            .filter { it.group == me.group && mayPair(me, it) }
    }

    private fun JdbcTransaction.mayPair(a: Side, b: Side): Boolean = mayPair(a.author.value, a.mode, b.author.value, b.mode)

    companion object {
        /** The most other tracks one job compares, so a huge group ride stays bounded. */
        const val MAX_CANDIDATES = 200
        private val START_SLACK = 2.days
        private const val MS_PER_S = 1000.0
    }
}

/**
 * Whether athletes [a] and [b], with grouping choices [modeA] and [modeB], may be paired now (feed/group-activities "Who may be paired"):
 * both on, an effective follow either way when either chose [GroupMode.CONNECTED], and no block between them. `GroupViews.pairRuleHolds` is
 * the same rule as SQL, rechecked when pairs are read; change both together.
 */
@Suppress("ReturnCount") // Each part of the rule.
internal fun JdbcTransaction.mayPair(a: String, modeA: String, b: String, modeB: String): Boolean {
    if (a == b || !GroupMode.isOn(modeA) || !GroupMode.isOn(modeB)) return false
    if (isBlocked(a, b)) return false
    val connected = modeA == GroupMode.CONNECTED || modeB == GroupMode.CONNECTED
    return !connected || follows(a, b) || follows(b, a)
}

/** [activity]'s record with every indexed details record laid over it, widest first: its exact start and elapsed time. */
internal fun JdbcTransaction.exactRecord(activity: ResultRow): ActivityRecord {
    val record = AtJson.decodeFromString(ActivityRecord.serializer(), activity[Activities.record])
    val details =
        ActivityDetails.select(ActivityDetails.visibility, ActivityDetails.record)
            .where { ActivityDetails.activityUri eq activity[Activities.uri] }
            .map { Visibility.rank(it[ActivityDetails.visibility]) to it[ActivityDetails.record] }
            .sortedBy { it.first }
            .map { AtJson.decodeFromString(ActivityDetailsRecord.serializer(), it.second) }
    return effectiveRecord(record, details)
}

/** The tier [activity] is shown as a partner at: the narrowest of its visibility and its Start time and Map categories'. */
internal fun JdbcTransaction.pairVisibility(activity: ResultRow): String =
    narrowestOf(activity, DetailCategory.START_TIME, DetailCategory.MAP)

/** Sets [activity]'s side of each of its pairs to its current [pairVisibility]. */
internal fun JdbcTransaction.retierPairs(activity: ResultRow) {
    val uri = activity[Activities.uri]
    val visibility = pairVisibility(activity)
    ActivityGroupPairs.update({ ActivityGroupPairs.activityA eq uri }) { it[visibilityA] = visibility }
    ActivityGroupPairs.update({ ActivityGroupPairs.activityB eq uri }) { it[visibilityB] = visibility }
}
