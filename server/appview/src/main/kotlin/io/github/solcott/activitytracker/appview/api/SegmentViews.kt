package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.DAILY_SEGMENT_LIMIT
import io.github.solcott.activitytracker.appview.index.duplicatesOf
import io.github.solcott.activitytracker.appview.index.segmentsCreatedSince
import io.github.solcott.activitytracker.appview.index.validLine
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.http.HttpStatusCode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.times
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Segments (segments/segment-records) and the efforts on them (segments/segment-efforts). A segment is public, but an account that isn't
 * served, or a taken-down segment record, hides it ([served]). An effort is served only to viewers admitted to its stored visibility, and
 * only while they may also see its activity's row ([visibleTo] on both), so an activity narrowed or taken down hides its efforts at once.
 * Whether an effort is the athlete's best is judged among the efforts the same viewer may see. Search lists active segments, and proposed
 * ones only to their creator and their riders; every view carries the status the AppView derived.
 */
@Suppress("TooManyFunctions") // The seven segment routes, and the filters and views they share.
class SegmentViews(
    private val db: Database,
    private val avatars: AvatarUrls,
    private val clock: Clock = Clock.System,
    /** The segment importer's account, whose allowance is always the whole [DAILY_SEGMENT_LIMIT]. */
    private val segmentImporter: Did? = null,
    /** How many candidate segments [search] reads at a time, whatever page size the caller asks for. */
    private val searchBatch: Int = Paging.MAX_LIMIT,
    /** How many batches one [search] reads at most, so a request's work is bounded however many segments lie near its box. */
    private val searchBatches: Int = SEARCH_BATCHES,
) {
    suspend fun segment(viewer: Did?, params: GetSegment.Params): GetSegment.Output = db.tx {
        val row =
            Segments.selectAll().where { (Segments.uri eq params.uri.value) and segmentServed(viewer) }.firstOrNull()
                ?: throw notFound("Segment not found")
        // Only the fastest the viewer may see is read, however often they've ridden it.
        val best = viewer?.let { own ->
            effortViews(
                    viewer,
                    (SegmentEfforts.segmentUri eq params.uri.value) and (SegmentEfforts.authorDid eq own.value),
                    order = listOf(SegmentEfforts.elapsedS to SortOrder.ASC, SegmentEfforts.id to SortOrder.ASC),
                    limit = 1,
                )
                .singleOrNull()
        }
        GetSegment.Output(views(listOf(row)).single().copy(viewer = best?.let { SegmentViewerState(it) }))
    }

    /**
     * Segments in a box of at most [SearchSegments.MAX_SPAN_E7] a side whose line has a point in it, oldest indexed first: active ones, and
     * proposed ones the viewer created or has an effort on. It reads at most [searchBatches] batches of [searchBatch] candidates, so a page
     * may hold fewer than the limit, or none, with a cursor to carry on from; only a page without one ends the search.
     */
    suspend fun search(viewer: Did?, params: SearchSegments.Params): SearchSegments.Output {
        val minLat = params.minLat
        val minLng = params.minLng
        val maxLat = params.maxLat
        val maxLng = params.maxLng
        requireSearchBox(minLat, minLng, maxLat, maxLng)
        val cells = SegmentCells.cellsIn(minLat / E7, minLng / E7, maxLat / E7, maxLng / E7)
        val limit = Paging.clamp(params.limit)
        return db.tx {
            val found = mutableListOf<ResultRow>()
            var cursor = params.cursor
            var exhausted = false
            var batches = 0
            // Cells and the stored box only narrow it down: each candidate's line is checked against the box, a batch at a time.
            while (found.size < limit && !exhausted && batches < searchBatches) {
                batches++
                val page =
                    Segments.selectAll()
                        .where {
                            segmentServed(viewer) and
                                unhidden(viewer, Segments.authorDid, Segments.uri) and
                                (Segments.minLatE7 lessEq maxLat) and
                                (Segments.maxLatE7 greaterEq minLat) and
                                (Segments.minLngE7 lessEq maxLng) and
                                (Segments.maxLngE7 greaterEq minLng) and
                                (params.sport?.let { Segments.sport eq it } ?: Op.TRUE) and
                                listedTo(viewer) and
                                exists(
                                    SegmentCellIndex.select(SegmentCellIndex.cell).where {
                                        (SegmentCellIndex.segmentUri eq Segments.uri) and (SegmentCellIndex.cell inList cells)
                                    }
                                ) and
                                keysetAfter(Segments.indexedAt, Segments.uri, cursor)
                        }
                        .orderBy(Segments.indexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                        .limit(searchBatch)
                        .toList()
                exhausted = page.size < searchBatch
                page.lastOrNull()?.let { cursor = "${it[Segments.indexedAt]}::${it[Segments.uri]}" }
                found += page.filter { crosses(it[Segments.line], minLat, minLng, maxLat, maxLng) }
            }
            val shown = found.take(limit)
            val next =
                when {
                    found.size > limit -> shown.last().let { "${it[Segments.indexedAt]}::${it[Segments.uri]}" }
                    exhausted -> null
                    else -> cursor
                }
            SearchSegments.Output(views(shown), next)
        }
    }

    /**
     * The efforts on an activity the viewer may see, in the order they began; `NotFound` when they may not see the activity. The athlete's
     * entry on the viewer's all-time board carries its rank when it's in the top 10, and the athlete's own efforts on active segments say
     * why they aren't ranked (segments/segment-efforts).
     */
    suspend fun activitySegments(viewer: Did?, params: GetActivitySegments.Params): GetActivitySegments.Output = db.tx {
        val visible = Activities.select(Activities.uri).where { (Activities.uri eq params.activity.value) and visibleTo(viewer) }.any()
        if (!visible) throw notFound("Activity not found")
        val efforts = effortViews(viewer, SegmentEfforts.activityUri eq params.activity.value)
        val sport = Activities.select(Activities.sport).where { Activities.uri eq params.activity.value }.first()[Activities.sport]
        val own = viewer != null && params.activity.authority == viewer.value
        val reasons = if (own) notRankedReasons(params.activity.value) else emptyMap()
        // Only an active segment has boards, and only the fastest of this activity's efforts on a board can be the athlete's entry on it,
        // so the rest skip the board queries.
        val candidates =
            efforts
                .filter { SegmentStatus.of(it.segment.status) == SegmentStatus.ACTIVE }
                .groupBy { it.segment.uri to it.eBike }
                .values
                .mapNotNull { onBoard -> onBoard.minByOrNull { it.elapsed } }
                .mapTo(mutableSetOf()) { effortId(it) }
        GetActivitySegments.Output(
            efforts.map { effort ->
                effort.copy(
                    rank = if (effortId(effort) in candidates) topRank(viewer, params.activity.authority, effort, sport) else null,
                    notRanked = reasons[effortId(effort)].takeIf { SegmentStatus.of(effort.segment.status) == SegmentStatus.ACTIVE },
                )
            }
        )
    }

    /** One athlete's efforts on a segment the viewer may see, fastest first; `NotFound` when the segment isn't served. */
    suspend fun segmentEfforts(viewer: Did?, params: GetSegmentEfforts.Params): GetSegmentEfforts.Output = db.tx {
        if (!Segments.select(Segments.uri).where { (Segments.uri eq params.segment.value) and segmentServed(viewer) }.any())
            throw notFound("Segment not found")
        val athlete = resolveActor(params.actor) ?: return@tx GetSegmentEfforts.Output(emptyList())
        val limit = Paging.clamp(params.limit)
        val efforts =
            effortViews(
                viewer,
                (SegmentEfforts.segmentUri eq params.segment.value) and
                    (SegmentEfforts.authorDid eq athlete) and
                    keysetAfter(SegmentEfforts.elapsedS, SegmentEfforts.id, params.cursor),
                order = listOf(SegmentEfforts.elapsedS to SortOrder.ASC, SegmentEfforts.id to SortOrder.ASC),
                limit = limit,
            )
        val next = efforts.takeIf { it.size == limit }?.last()?.let { "${it.elapsed}::${effortId(it)}" }
        GetSegmentEfforts.Output(efforts, next)
    }

    /**
     * A segment's leaderboard (segments/segment-leaderboards): each athlete's fastest [rankable] effort the viewer may see, fastest first,
     * with competition ranks over the whole board, and the viewer's own entry. Pages by a `HAVING` keyset on (best, athlete), as
     * [Views.likes] does; ranks come from counting the athletes ahead, so no window function is needed.
     */
    suspend fun leaderboard(viewer: Did?, params: GetSegmentLeaderboard.Params): GetSegmentLeaderboard.Output {
        val (since, following) = filtersOf(viewer, params)
        val requested = requestedBoard(params.board)
        val brackets = bracketsOf(params.ageGroup, params.weightClass, params.gender)
        return db.tx {
            val status =
                Segments.select(Segments.status)
                    .where { (Segments.uri eq params.segment.value) and segmentServed(viewer) }
                    .firstOrNull()
                    ?.get(Segments.status) ?: throw notFound("Segment not found")
            if (status != SegmentStatus.ACTIVE)
                return@tx GetSegmentLeaderboard.Output(emptyList(), board = requested, boards = listOfNotNull(requested))
            val (served, boards) = boardsOf(viewer, params.segment.value, requested)
            if (served == null) return@tx GetSegmentLeaderboard.Output(emptyList())
            val board = boardOf(viewer, params.segment.value, served, since, following) and brackets
            val limit = Paging.clamp(params.limit)
            val after =
                params.cursor?.let(::parseCursor)?.let { (best, did) ->
                    (BEST greater best) or ((BEST eq best) and (SegmentEfforts.authorDid greater did))
                } ?: Op.TRUE
            val page =
                bests(board, after).orderBy(BEST to SortOrder.ASC, SegmentEfforts.authorDid to SortOrder.ASC).limit(limit).mapNotNull { row
                    ->
                    row[BEST]?.let { row[SegmentEfforts.authorDid] to it }
                }
            val entries = entries(viewer, board, page)
            val next = page.takeIf { it.size == limit }?.last()?.let { (did, best) -> "$best::$did" }
            val own = viewer?.let { me ->
                bests(board and (SegmentEfforts.authorDid eq me.value), Op.TRUE).firstOrNull()?.get(BEST)?.let { me.value to it }
            }
            GetSegmentLeaderboard.Output(entries, own?.let { entries(viewer, board, listOf(it)).singleOrNull() }, next, served, boards)
        }
    }

    /**
     * A segment's Mayors board (segments/segment-mayors): athletes by how many of their efforts a leaderboard of the same filters could
     * rank in [GetSegmentMayors.Params.period]'s window, most first, then by the date each count was reached ([REACHED], their latest
     * counted effort), earliest first. Pages and ranks as [leaderboard] does, by a `HAVING` keyset on (count, date, athlete).
     */
    suspend fun mayors(viewer: Did?, params: GetSegmentMayors.Params): GetSegmentMayors.Output {
        val period = params.period ?: MayorPeriod.LAST_90_DAYS
        if (period !in MayorPeriod.all) invalidRequest("Unknown period $period")
        val (since, until) = mayorWindow(period, today())
        val following = following(viewer, params.scope)
        val requested = requestedBoard(params.board)
        val brackets = bracketsOf(params.ageGroup, params.weightClass, params.gender)
        return db.tx {
            val status =
                Segments.select(Segments.status)
                    .where { (Segments.uri eq params.segment.value) and segmentServed(viewer) }
                    .firstOrNull()
                    ?.get(Segments.status) ?: throw notFound("Segment not found")
            if (status != SegmentStatus.ACTIVE)
                return@tx GetSegmentMayors.Output(emptyList(), board = requested, boards = listOfNotNull(requested))
            val (served, boards) = boardsOf(viewer, params.segment.value, requested)
            if (served == null) return@tx GetSegmentMayors.Output(emptyList())
            val board = boardOf(viewer, params.segment.value, served, since, following, until) and brackets
            val limit = Paging.clamp(params.limit)
            val after =
                params.cursor?.let(::parseMayorCursor)?.let { (efforts, reached, did) ->
                    (EFFORTS less efforts) or
                        ((EFFORTS eq efforts) and (REACHED greater reached)) or
                        ((EFFORTS eq efforts) and (REACHED eq reached) and (SegmentEfforts.authorDid greater did))
                } ?: Op.TRUE
            val page =
                counts(board, after)
                    .orderBy(EFFORTS to SortOrder.DESC, REACHED to SortOrder.ASC, SegmentEfforts.authorDid to SortOrder.ASC)
                    .limit(limit)
                    .mapNotNull(::mayorRow)
            val entries = mayorEntries(board, page)
            val next = page.takeIf { it.size == limit }?.last()?.let { (did, efforts, reached) -> "$efforts::$reached::$did" }
            val own = viewer?.let { me -> counts(board and (SegmentEfforts.authorDid eq me.value), Op.TRUE).firstOrNull()?.let(::mayorRow) }
            GetSegmentMayors.Output(entries, own?.let { mayorEntries(board, listOf(it)).singleOrNull() }, next, served, boards)
        }
    }

    /**
     * [viewer]'s check of a line before publishing it (segments/segment-records): the live segments of its sport it would duplicate,
     * earliest first, as indexing would judge them, and how many more new segments [viewer] may create today (always all of them for the
     * segment importer). `InvalidRequest` for a line indexing would drop.
     */
    suspend fun check(viewer: Did, input: CheckSegment.Input): CheckSegment.Output {
        val line = runCatching { validLine(input.sport, input.line) }.getOrElse { invalidRequest(it.message ?: "Invalid line") }
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            val uris = duplicatesOf(input.sport, line)
            val rows = Segments.selectAll().where { (Segments.uri inList uris) and segmentServed(viewer) }.associateBy { it[Segments.uri] }
            val created = if (viewer == segmentImporter) 0 else segmentsCreatedSince(viewer, now)
            val remaining = (DAILY_SEGMENT_LIMIT - created).coerceAtLeast(0).toInt()
            CheckSegment.Output(views(uris.mapNotNull { rows[it] }), remaining)
        }
    }

    /** Segments search lists to [viewer]: active ones, and proposed ones they created or have an effort on. */
    private fun listedTo(viewer: Did?): Op<Boolean> {
        val active = Segments.status eq SegmentStatus.ACTIVE
        if (viewer == null) return active
        val ridden =
            exists(
                SegmentEfforts.select(SegmentEfforts.id).where {
                    (SegmentEfforts.segmentUri eq Segments.uri) and (SegmentEfforts.authorDid eq viewer.value)
                }
            )
        return active or ((Segments.status eq SegmentStatus.PROPOSED) and ((Segments.authorDid eq viewer.value) or ridden))
    }

    /** Segments served to [viewer]: not of an account that isn't served, nor taken down. */
    private fun segmentServed(viewer: Did?): Op<Boolean> = served(viewer, Segments.authorDid, Segments.uri, Segments.cid)

    /** Efforts matching [where] that [viewer] may see, each with whether it's its athlete's best on its segment among those. */
    private fun JdbcTransaction.effortViews(
        viewer: Did?,
        where: Op<Boolean>,
        order: List<Pair<Expression<*>, SortOrder>> =
            listOf(SegmentEfforts.startOffsetS to SortOrder.ASC, SegmentEfforts.id to SortOrder.ASC),
        limit: Int? = null,
    ): List<EffortView> {
        val rows =
            visibleEfforts()
                .select(SegmentEfforts.columns + listOf(Segments.uri, Segments.name, Segments.sport, Segments.distanceM, Segments.status))
                .where { where and effortsVisibleTo(viewer) }
                .apply { order.forEach { (expression, direction) -> orderBy(expression, direction) } }
                .let { if (limit != null) it.limit(limit) else it }
                .toList()
        val pairs = rows.map { it[SegmentEfforts.authorDid] to it[SegmentEfforts.segmentUri] }.distinct()
        val bests = pairs.associateWith { (author, segment) ->
            val min = SegmentEfforts.elapsedS.min()
            visibleEfforts()
                .select(min)
                .where { (SegmentEfforts.authorDid eq author) and (SegmentEfforts.segmentUri eq segment) and effortsVisibleTo(viewer) }
                .firstOrNull()
                ?.get(min)
        }
        return rows.map {
            val elapsed = it[SegmentEfforts.elapsedS]
            EffortView(
                segment =
                    SegmentViewBasic(
                        AtUri(it[Segments.uri]),
                        it[Segments.name],
                        it[Segments.sport],
                        it[Segments.distanceM],
                        status = it[Segments.status],
                    ),
                activity = AtUri(it[SegmentEfforts.activityUri]),
                startOffset = it[SegmentEfforts.startOffsetS],
                elapsed = elapsed,
                startedOn = it[SegmentEfforts.startedOn],
                eBike = it[SegmentEfforts.eBike].takeIf { e -> e },
                best = (bests[it[SegmentEfforts.authorDid] to it[SegmentEfforts.segmentUri]] == elapsed).takeIf { b -> b },
            )
        }
    }

    /** Efforts joined to their activity and segment. */
    private fun visibleEfforts() =
        SegmentEfforts.join(Activities, JoinType.INNER, SegmentEfforts.activityUri, Activities.uri)
            .join(Segments, JoinType.INNER, SegmentEfforts.segmentUri, Segments.uri)

    /** [visibleEfforts] with the segment track each came from, whose exclusion [rankable] reads. */
    private fun rankedEfforts() = visibleEfforts().join(SegmentTracks, JoinType.INNER, SegmentEfforts.trackUri, SegmentTracks.uri)

    /**
     * Efforts a leaderboard ranks, over [rankedEfforts]: on an active segment, from an activity that isn't excluded, by an athlete who
     * doesn't hide from leaderboards, and no faster than the segment's sport group allows. All read now, so none needs a rematch.
     */
    private fun rankable(): Op<Boolean> =
        (Segments.status eq SegmentStatus.ACTIVE) and
            (SegmentTracks.leaderboardExcluded eq false) and
            notExists(
                SegmentPrefs.select(SegmentPrefs.did).where {
                    (SegmentPrefs.did eq SegmentEfforts.authorDid) and (SegmentPrefs.hideFromLeaderboards eq true)
                }
            ) and
            plausible()

    /** The effort's average speed is within its segment's group cap: `elapsed * cap >= distance * 1000`, in integers both databases run. */
    private fun plausible(): Op<Boolean> =
        SegmentSportGroup.all
            .map { group ->
                (Segments.sport eq group) and
                    (SegmentEfforts.elapsedS.times(SegmentSportGroup.maxSpeedMmPerS(group).toLong()) greaterEq
                        Segments.distanceM.times(MM_PER_M))
            }
            .reduce { a, b -> a or b }

    /**
     * The efforts on [segment]'s [board] (a [SegmentBoard] type) that [viewer] may see and that are [rankable], from [since] and before
     * [until] (each a `YYYY-MM-DD`) when given.
     */
    @Suppress("LongParameterList") // The board's filters.
    private fun boardOf(
        viewer: Did?,
        segment: String,
        board: String,
        since: String?,
        following: Boolean,
        until: String? = null,
    ): Op<Boolean> {
        val own = viewer?.value
        val type =
            if (board == SegmentBoard.E_BIKE) SegmentEfforts.eBike eq true
            else (SegmentEfforts.eBike eq false) and (Activities.sport eq board)
        return (SegmentEfforts.segmentUri eq segment) and
            type and
            effortsVisibleTo(viewer) and
            rankable() and
            (since?.let { SegmentEfforts.startedOn greaterEq it } ?: Op.TRUE) and
            (until?.let { SegmentEfforts.startedOn less it } ?: Op.TRUE) and
            (if (following && own != null) {
                (SegmentEfforts.authorDid eq own) or
                    (SegmentEfforts.authorDid inSubQuery
                        Follows.select(Follows.subjectDid).where { (Follows.authorDid eq own) and effectiveFollow() })
            } else Op.TRUE)
    }

    /**
     * The board to serve on [segment] and the types to list: [requested], or else the listed type with the most efforts; and the types
     * [viewer] may see [rankable] efforts of over all time, most first, ties by token, with [requested] added when it has none. The served
     * board is null only when nothing was requested and nothing is listed.
     */
    private fun JdbcTransaction.boardsOf(viewer: Did?, segment: String, requested: String?): Pair<String?, List<String>> {
        val count = SegmentEfforts.id.count()
        val listed =
            rankedEfforts()
                .select(SegmentEfforts.eBike, Activities.sport, count)
                .where { (SegmentEfforts.segmentUri eq segment) and effortsVisibleTo(viewer) and rankable() }
                .groupBy(SegmentEfforts.eBike, Activities.sport)
                .map { (if (it[SegmentEfforts.eBike]) SegmentBoard.E_BIKE else it[Activities.sport]) to it[count] }
                .filter { (type, _) -> type in SegmentBoard.all }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, counts) -> counts.sum() }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
                .map { it.key }
        val boards = if (requested == null || requested in listed) listed else listed + requested
        return (requested ?: listed.firstOrNull()) to boards
    }

    /** [board] (a [SegmentBoard] type, or null for the default); `InvalidRequest` when unknown. */
    private fun requestedBoard(board: String?): String? {
        if (board != null && board !in SegmentBoard.all) invalidRequest("Unknown board $board")
        return board
    }

    /** Each athlete's best time on [board], as `author_did` and [BEST], filtered by [having]. */
    private fun bests(board: Op<Boolean>, having: Op<Boolean>) =
        rankedEfforts().select(SegmentEfforts.authorDid, BEST).where { board }.groupBy(SegmentEfforts.authorDid).having { having }

    /** How many athletes on [board] are ahead of a best time of [best]: one less than its rank. */
    private fun JdbcTransaction.ahead(board: Op<Boolean>, best: Long): Long = bests(board, BEST less best).count()

    /**
     * Entries for [page] (athlete and best time, fastest first): competition ranks counted over the whole board, each athlete's effort the
     * earliest of their fastest, and equal times listed earliest first.
     */
    private fun JdbcTransaction.entries(viewer: Did?, board: Op<Boolean>, page: List<Pair<String, Long>>): List<LeaderboardEntry> {
        if (page.isEmpty()) return emptyList()
        val ranks =
            ranksOf(
                page,
                ahead = { (_, best) -> ahead(board, best) },
                before = { (did, best) ->
                    bests(board, (BEST less best) or ((BEST eq best) and (SegmentEfforts.authorDid less did))).count()
                },
                tied = { a, b -> a.second == b.second },
            )
        val chosen =
            rankedEfforts()
                .select(SegmentEfforts.id, SegmentEfforts.authorDid, SegmentEfforts.elapsedS, SegmentEfforts.startedOn)
                .where { board and (SegmentEfforts.authorDid inList page.map { it.first }) }
                .groupBy { it[SegmentEfforts.authorDid] }
                .mapValues { (_, rows) ->
                    rows
                        .minWith(compareBy({ it[SegmentEfforts.elapsedS] }, { it[SegmentEfforts.startedOn] }, { it[SegmentEfforts.id] }))[
                            SegmentEfforts.id]
                }
        val efforts = effortViews(viewer, SegmentEfforts.id inList chosen.values).associateBy(::effortId)
        val athletes = basicProfiles(page.map { it.first }, avatars)
        return page
            .mapIndexedNotNull { i, (did, _) ->
                val effort = chosen[did]?.let(efforts::get) ?: return@mapIndexedNotNull null
                LeaderboardEntry(ranks[i], athletes[did] ?: ProfileViewBasic(Did(did), Handle(UNKNOWN_HANDLE)), effort)
            }
            .sortedWith(compareBy({ it.rank }, { it.effort.startedOn }))
    }

    /**
     * [effort]'s rank on its segment's all-time, everyone board of its own type (its activity's [sport], or [SegmentBoard.E_BIKE]) as
     * [viewer] sees it, when it is [author]'s entry there (their fastest [rankable] effort the viewer may see, which needn't be their PR: a
     * faster one may be excluded) and in the top [TOP_RANKS]; else null.
     */
    private fun JdbcTransaction.topRank(viewer: Did?, author: String, effort: EffortView, sport: String): Int? {
        val type = if (effort.eBike == true) SegmentBoard.E_BIKE else sport
        val board = boardOf(viewer, effort.segment.uri.value, type, since = null, following = false)
        val ranked = !rankedEfforts().select(SegmentEfforts.id).where { board and (SegmentEfforts.id eq effortId(effort)) }.empty()
        val entry = ranked && bests(board and (SegmentEfforts.authorDid eq author), Op.TRUE).firstOrNull()?.get(BEST) == effort.elapsed
        return if (entry) (ahead(board, effort.elapsed).toInt() + 1).takeIf { it <= TOP_RANKS } else null
    }

    /** Why leaderboards don't rank each of [activity]'s efforts that they don't, by effort ID, for its own athlete only. */
    private fun JdbcTransaction.notRankedReasons(activity: String): Map<String, String> {
        val rows =
            SegmentEfforts.join(Segments, JoinType.INNER, SegmentEfforts.segmentUri, Segments.uri)
                .join(SegmentTracks, JoinType.INNER, SegmentEfforts.trackUri, SegmentTracks.uri)
                .select(
                    SegmentEfforts.id,
                    SegmentEfforts.authorDid,
                    SegmentEfforts.elapsedS,
                    Segments.sport,
                    Segments.distanceM,
                    SegmentTracks.leaderboardExcluded,
                )
                .where { SegmentEfforts.activityUri eq activity }
                .toList()
        val author = rows.firstOrNull()?.get(SegmentEfforts.authorDid) ?: return emptyMap()
        val hidden =
            !SegmentPrefs.select(SegmentPrefs.did)
                .where { (SegmentPrefs.did eq author) and (SegmentPrefs.hideFromLeaderboards eq true) }
                .empty()
        return rows
            .mapNotNull { row ->
                val implausible =
                    row[SegmentEfforts.elapsedS] * SegmentSportGroup.maxSpeedMmPerS(row[Segments.sport]) <
                        row[Segments.distanceM] * MM_PER_M
                when {
                    hidden -> NotRanked.HIDDEN
                    row[SegmentTracks.leaderboardExcluded] -> NotRanked.EXCLUDED
                    implausible -> NotRanked.IMPLAUSIBLE
                    else -> null
                }?.let { row[SegmentEfforts.id] to it }
            }
            .toMap()
    }

    /**
     * Competition ranks for [page], in board order: the first entry's is one more than the athletes [ahead] of it, and each later one
     * shares the rank of the entry before when [tied] with it, else takes its place on the board, counted from the athletes listed [before]
     * the first (ties with the first entry may sit on earlier pages).
     */
    private fun <T> ranksOf(page: List<T>, ahead: (T) -> Long, before: (T) -> Long, tied: (T, T) -> Boolean): IntArray {
        val ranks = IntArray(page.size)
        if (page.isEmpty()) return ranks
        val position = before(page.first())
        page.forEachIndexed { i, entry ->
            ranks[i] =
                when {
                    i == 0 -> ahead(entry).toInt() + 1
                    tied(entry, page[i - 1]) -> ranks[i - 1]
                    else -> position.toInt() + i + 1
                }
        }
        return ranks
    }

    /** Each athlete's count of efforts on [board], as `author_did`, [EFFORTS] and [REACHED], filtered by [having]. */
    private fun counts(board: Op<Boolean>, having: Op<Boolean>) =
        rankedEfforts()
            .select(SegmentEfforts.authorDid, EFFORTS, REACHED)
            .where { board }
            .groupBy(SegmentEfforts.authorDid)
            .having { having }

    /** A [counts] row as athlete, count and the date it was reached; null for an empty group. */
    private fun mayorRow(row: ResultRow): Triple<String, Long, String>? =
        row[REACHED]?.let { Triple(row[SegmentEfforts.authorDid], row[EFFORTS], it) }

    /** Entries for [page] (athlete, count and date reached, in board order), ranked over the whole Mayors board. */
    private fun JdbcTransaction.mayorEntries(board: Op<Boolean>, page: List<Triple<String, Long, String>>): List<MayorEntry> {
        val ranks =
            ranksOf(
                page,
                ahead = { (_, efforts, reached) ->
                    counts(board, (EFFORTS greater efforts) or ((EFFORTS eq efforts) and (REACHED less reached))).count()
                },
                before = { (did, efforts, reached) ->
                    counts(
                            board,
                            (EFFORTS greater efforts) or
                                ((EFFORTS eq efforts) and (REACHED less reached)) or
                                ((EFFORTS eq efforts) and (REACHED eq reached) and (SegmentEfforts.authorDid less did)),
                        )
                        .count()
                },
                tied = { a, b -> a.second == b.second && a.third == b.third },
            )
        val athletes = basicProfiles(page.map { it.first }, avatars)
        return page.mapIndexed { i, (did, efforts, reached) ->
            MayorEntry(ranks[i], athletes[did] ?: ProfileViewBasic(Did(did), Handle(UNKNOWN_HANDLE)), efforts.toInt(), reached)
        }
    }

    /**
     * [params]' period start ([periodStart]) and whether it asks for the following scope; `InvalidRequest` for an unknown period or scope,
     * or the following scope without a [viewer].
     */
    private fun filtersOf(viewer: Did?, params: GetSegmentLeaderboard.Params): Pair<String?, Boolean> {
        val period = params.period ?: LeaderboardPeriod.ALL_TIME
        if (period !in LeaderboardPeriod.all) invalidRequest("Unknown period $period")
        return periodStart(period) to following(viewer, params.scope)
    }

    /**
     * Whether [scope] (a [LeaderboardScope] value, null for everyone) is the following scope; `InvalidRequest` when unknown, or without a
     * [viewer].
     */
    private fun following(viewer: Did?, scope: String?): Boolean {
        val chosen = scope ?: LeaderboardScope.EVERYONE
        if (chosen !in LeaderboardScope.all) invalidRequest("Unknown scope $chosen")
        val following = chosen == LeaderboardScope.FOLLOWING
        if (following && viewer == null) invalidRequest("The following scope needs a signed-in viewer")
        return following
    }

    /**
     * The athletes the [ageGroup], [weightClass] and [gender] filters admit (segments/leaderboard-brackets): those whose indexed brackets
     * match every filter given, so an athlete without brackets is on no filtered board. `InvalidRequest` for an unknown value.
     */
    private fun bracketsOf(ageGroup: String?, weightClass: String?, gender: String?): Op<Boolean> {
        val filters =
            listOf(
                    Triple(ageGroup, LeaderboardAgeGroup.all, LeaderboardBrackets.ageGroup),
                    Triple(weightClass, LeaderboardWeightClass.all, LeaderboardBrackets.weightClass),
                    Triple(gender, LeaderboardGender.all, LeaderboardBrackets.gender),
                )
                .mapNotNull { (value, known, column) ->
                    value?.let {
                        if (it !in known) invalidRequest("Unknown bracket $it")
                        column eq it
                    }
                }
        if (filters.isEmpty()) return Op.TRUE
        return exists(
            LeaderboardBrackets.select(LeaderboardBrackets.did).where {
                filters.fold(LeaderboardBrackets.did eq SegmentEfforts.authorDid) { all, filter -> all and filter }
            }
        )
    }

    /** The first local date of [period] by the AppView's UTC date, as `YYYY-MM-DD`; null for all time. */
    private fun periodStart(period: String): String? {
        val today = today()
        return when (period) {
            LeaderboardPeriod.YEAR -> today.withDayOfYear(1).toString()
            LeaderboardPeriod.MONTH -> today.withDayOfMonth(1).toString()
            else -> null
        }
    }

    /** The AppView's current date in UTC, which board periods and windows are counted from. */
    private fun today(): LocalDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds()), ZoneOffset.UTC)

    private fun effortsVisibleTo(viewer: Did?): Op<Boolean> =
        visibleTo(viewer, SegmentEfforts.visibility, SegmentEfforts.authorDid, SegmentEfforts.activityUri, null) and
            visibleTo(viewer) and
            segmentServed(viewer)

    private fun JdbcTransaction.views(rows: List<ResultRow>): List<SegmentView> {
        val creators = basicProfiles(rows.map { it[Segments.authorDid] }, avatars)
        return rows.map {
            val author = it[Segments.authorDid]
            SegmentView(
                uri = AtUri(it[Segments.uri]),
                cid = Cid(it[Segments.cid]),
                creator = creators[author] ?: ProfileViewBasic(Did(author), Handle(UNKNOWN_HANDLE)),
                record = AtJson.decodeFromString(SegmentRecord.serializer(), it[Segments.record]),
                distanceMeters = it[Segments.distanceM],
                status = it[Segments.status],
                duplicateOf = it[Segments.duplicateOf]?.let(::AtUri),
            )
        }
    }

    /** Refuses a search box off the globe, upside down, or wider than [SearchSegments.MAX_SPAN_E7] on either side as `InvalidRequest`. */
    private fun requireSearchBox(minLat: Int, minLng: Int, maxLat: Int, maxLng: Int) {
        if (listOf(minLat, maxLat).any { abs(it) > MAX_LAT_E7 } || listOf(minLng, maxLng).any { abs(it) > MAX_LNG_E7 })
            invalidRequest("The box is outside the globe")
        if (minLat > maxLat || minLng > maxLng) invalidRequest("The box's minimum is above its maximum")
        if (maxLat - minLat > SearchSegments.MAX_SPAN_E7 || maxLng - minLng > SearchSegments.MAX_SPAN_E7)
            invalidRequest("The box is wider than one degree")
    }

    /** Whether the precision-6 [line] has a point (every [SegmentGeometry.REFERENCE_SPACING_M]) in the box, in 1e-7 degrees. */
    private fun crosses(line: String, minLat: Int, minLng: Int, maxLat: Int, maxLng: Int): Boolean =
        SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty()).any {
            it.lat * E7 in minLat.toDouble()..maxLat.toDouble() && it.lng * E7 in minLng.toDouble()..maxLng.toDouble()
        }

    companion object {
        private const val E7 = 1e7
        private const val MAX_LAT_E7 = 900_000_000
        private const val MAX_LNG_E7 = 1_800_000_000
        private const val MM_PER_M = 1_000L

        /** The batches of candidates one search reads at most: 300 rows at the default batch size. */
        private const val SEARCH_BATCHES = 3

        /** The ranks an activity's efforts show (segments/segment-efforts). */
        private const val TOP_RANKS = 10

        /** An athlete's best time on a board. */
        private val BEST = SegmentEfforts.elapsedS.min()

        /** How many of an athlete's efforts a Mayors board counts. */
        private val EFFORTS = SegmentEfforts.id.count()

        /** The local date an athlete's Mayors count was reached: their latest counted effort's. */
        private val REACHED = SegmentEfforts.startedOn.max()

        /** The parts of a Mayors cursor: count, date and athlete. */
        private const val MAYOR_CURSOR_PARTS = 3

        /** Splits a `"<count>::<date>::<did>"` Mayors cursor; null when it is malformed. */
        private fun parseMayorCursor(cursor: String): Triple<Long, String, String>? {
            val (efforts, reached, did) =
                cursor.split("::", limit = MAYOR_CURSOR_PARTS).takeIf { it.size == MAYOR_CURSOR_PARTS } ?: return null
            return efforts.toLongOrNull()?.let { Triple(it, reached, did) }
        }

        /** An effort's ID, rebuilt from its view as the matcher writes it, for the keyset cursor. */
        private fun effortId(effort: EffortView) = "${effort.activity.value} ${effort.segment.uri.value} ${effort.startOffset}"

        private fun invalidRequest(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

        private fun notFound(message: String) = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", message)
    }
}

/**
 * The window of a Mayors board for [period] (a known [MayorPeriod] value) on [today]: its first local date and the first one after it, as
 * `YYYY-MM-DD`, each null when unbounded. "This" windows have no end, so an effort dated ahead of the UTC date still counts, and weeks
 * start on Monday.
 */
internal fun mayorWindow(period: String, today: LocalDate): Pair<String?, String?> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val window: Pair<LocalDate?, LocalDate?> =
        when (period) {
            MayorPeriod.ALL_TIME -> null to null
            MayorPeriod.THIS_YEAR -> today.withDayOfYear(1) to null
            MayorPeriod.LAST_YEAR -> today.withDayOfYear(1).minusYears(1) to today.withDayOfYear(1)
            MayorPeriod.THIS_MONTH -> today.withDayOfMonth(1) to null
            MayorPeriod.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1) to today.withDayOfMonth(1)
            MayorPeriod.THIS_WEEK -> monday to null
            MayorPeriod.LAST_WEEK -> monday.minusWeeks(1) to monday
            else -> today.minusDays(LAST_DAYS - 1) to null
        }
    return window.first?.toString() to window.second?.toString()
}

/** The days [MayorPeriod.LAST_90_DAYS] covers, today included. */
private const val LAST_DAYS = 90L
