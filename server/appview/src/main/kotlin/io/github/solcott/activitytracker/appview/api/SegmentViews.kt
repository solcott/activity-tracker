package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.PathSectionSegments
import io.github.solcott.activitytracker.appview.db.PathSegments
import io.github.solcott.activitytracker.appview.db.Paths
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.excludedFromLeaderboards
import io.github.solcott.activitytracker.appview.db.hidesFromLeaderboards
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.DAILY_SEGMENT_LIMIT
import io.github.solcott.activitytracker.appview.index.duplicatesOf
import io.github.solcott.activitytracker.appview.index.groupKey
import io.github.solcott.activitytracker.appview.index.resolveHandoff
import io.github.solcott.activitytracker.appview.index.segmentsCreatedSince
import io.github.solcott.activitytracker.appview.index.validLine
import io.github.solcott.activitytracker.appview.index.validPositionE7
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.mayPropose
import io.github.solcott.activitytracker.appview.segments.plausibleEffort
import io.github.solcott.activitytracker.appview.segments.ridersAmong
import io.github.solcott.activitytracker.appview.segments.servedElevation
import io.github.solcott.activitytracker.appview.segments.terrainProfile
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.ElevationProfile
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.lexicon.app.GetPaths
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.LeaderboardAgeGroup
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.LeaderboardGender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardPeriod
import io.github.solcott.activitytracker.lexicon.app.LeaderboardWeightClass
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.PathOneWay
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.SearchPathNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentBoard
import io.github.solcott.activitytracker.lexicon.app.SegmentClimbs
import io.github.solcott.activitytracker.lexicon.app.SegmentElevationSource
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.location.E7_PER_DEGREE
import io.github.solcott.activitytracker.location.SegmentClimb
import io.github.solcott.activitytracker.location.SegmentDirection
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.SegmentHeading
import io.github.solcott.activitytracker.location.e7ToDegrees
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Segments (segments/segment-records) and the efforts on them (segments/segment-efforts). A segment is public, but an account that isn't
 * served, or a taken-down segment record, hides it ([served]). An effort is served only to viewers admitted to its stored visibility, and
 * only while they may also see its activity's row ([visibleTo] on both), so an activity narrowed or taken down hides its efforts at once.
 * Whether an effort is the athlete's best is judged among the efforts the same viewer may see. Search lists active segments, and proposed
 * ones only to their creator and their riders; every view carries the status the AppView derived.
 */
// The eight segment routes, and the filters and views they share (name search uses them too); its config.
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
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
    /** Who may propose segment names (segments/segment-names), for the viewer's flag. */
    private val names: SegmentNameRules = SegmentNameRules(),
    /**
     * How many distinct athletes' efforts with elevation a segment without terrain elevation needs to serve them
     * (`SEGMENT_ACTIVE_ATHLETES`).
     */
    private val activeAthletes: Int = SegmentMatcher.DEFAULT_ACTIVE_ATHLETES,
) {
    suspend fun segment(viewer: Did?, params: GetSegment.Params): GetSegment.Output = db.tx {
        // A handed-off segment's old URI serves its successor (segments/segment-handoffs).
        val uri = params.uri.value.takeIf(::segmentIndexed) ?: resolveHandoff(params.uri.value, ::segmentIndexed) ?: params.uri.value
        val row =
            Segments.selectAll().where { (Segments.uri eq uri) and SegmentPlace.servedTo(viewer) }.firstOrNull()
                ?: throw notFound("Segment not found")
        // Only the fastest the viewer may see is read, however often they've ridden it.
        val best = viewer?.let { own ->
            effortViews(
                    viewer,
                    (SegmentEfforts.segmentUri eq uri) and (SegmentEfforts.authorDid eq own.value),
                    order = listOf(SegmentEfforts.elapsedS to SortOrder.ASC, SegmentEfforts.id to SortOrder.ASC),
                    limit = 1,
                )
                .singleOrNull()
        }
        val state = viewer?.let {
            SegmentViewerState(
                best = best,
                canProposeName = mayPropose(uri, it.value, clock.now().toEpochMilliseconds(), names),
                canVoteOnNames = ridersAmong(uri, listOf(it.value)).isNotEmpty(),
            )
        }
        val view =
            views(listOf(row))
                .single()
                .copy(nameHistory = nameHistory(viewer, uri, avatars), nameProposals = nameProposals(viewer, uri, avatars), viewer = state)
        // The profile is the terrain's, so it's served only beside terrain elevation (segments/segment-elevation).
        val profile =
            terrainProfile(row)
                ?.takeIf { view.elevationSource == SegmentElevationSource.TERRAIN }
                ?.let { ElevationProfile(it, maxGrade = row[Segments.terrainMaxGrade]) }
        GetSegment.Output(view, elevationProfile = profile, groupMembers = groupMembers(viewer, row))
    }

    /**
     * The other segments of [row]'s segment group (segments/segment-groups) that a search would list to [viewer], earliest indexed first;
     * none for a lift.
     */
    private fun JdbcTransaction.groupMembers(viewer: Did?, row: ResultRow): List<GetSegment.GroupMember> {
        if (row[Segments.kind] == SegmentKind.LIFT) return emptyList()
        val uri = row[Segments.uri]
        val group = row[Segments.groupUri] ?: uri
        return Segments.select(Segments.uri, Segments.sport, Segments.kind, Segments.currentName, Segments.line)
            .where {
                ((Segments.groupUri eq group) or (Segments.groupUri.isNull() and (Segments.uri eq group))) and
                    (Segments.uri neq uri) and
                    (Segments.kind neq SegmentKind.LIFT) and
                    SegmentPlace.servedTo(viewer) and
                    unhidden(viewer, Segments.authorDid, Segments.uri) and
                    SegmentPlace.listedTo(viewer)
            }
            .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
            .limit(MAX_ROUTE_MEMBERS)
            .map {
                GetSegment.GroupMember(
                    uri = AtUri(it[Segments.uri]),
                    sport = it[Segments.sport],
                    kind = it[Segments.kind],
                    name = it[Segments.currentName],
                    line = it[Segments.line],
                )
            }
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
        val box = SearchBox(minLat, minLng, maxLat, maxLng)
        val limit = Paging.clamp(params.limit)
        return db.tx {
            val found = mutableListOf<ResultRow>()
            var cursor = params.cursor
            var exhausted = false
            var batches = 0
            // Served, listed and in the box's cells: what a page may hold, before each line is checked against the box.
            val inArea =
                SegmentPlace.servedTo(viewer) and
                    unhidden(viewer, Segments.authorDid, Segments.uri) and
                    (params.sport?.let { Segments.sport eq it } ?: Op.TRUE) and
                    SegmentPlace.listedTo(viewer) and
                    segmentsNear(box)
            fun crossesBox(row: ResultRow) = crosses(row[Segments.line], minLat, minLng, maxLat, maxLng)
            // Cells and the stored box only narrow it down: each candidate's line is checked against the box, a batch at a time.
            while (found.size < limit && !exhausted && batches < searchBatches) {
                batches++
                val page =
                    Segments.selectAll()
                        .where { inArea and keysetAfter(Segments.indexedAt, Segments.uri, cursor) }
                        .orderBy(Segments.indexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                        .limit(searchBatch)
                        .toList()
                exhausted = page.size < searchBatch
                page.lastOrNull()?.let { cursor = cursorOf(it[Segments.indexedAt], it[Segments.uri]) }
                found += page.filter(::crossesBox)
            }
            val shown = found.take(limit)
            val next =
                when {
                    found.size > limit -> shown.last().let { cursorOf(it[Segments.indexedAt], it[Segments.uri]) }
                    exhausted -> null
                    else -> cursor
                }
            SearchSegments.Output(views(shown + groupMatesOf(shown, inArea).filter(::crossesBox)), next)
        }
    }

    /**
     * The other segments [filter] admits in the segment groups of [rows] (segments/segment-groups), first indexed first, so a page never
     * splits a segment group: an older client shows them as segments, and a later page repeating one is harmless since clients keep each
     * URI once.
     */
    private fun groupMatesOf(rows: List<ResultRow>, filter: Op<Boolean>): List<ResultRow> {
        if (rows.isEmpty()) return emptyList()
        val groups = rows.map { it[Segments.groupUri] ?: it[Segments.uri] }.distinct()
        val held = rows.mapTo(mutableSetOf()) { it[Segments.uri] }
        return Segments.selectAll()
            .where { filter and inGroups(groups) }
            .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
            .filterNot { it[Segments.uri] in held }
    }

    /** Segments in one of [groups], counting a segment with no segment group yet as its own. */
    private fun inGroups(groups: List<String>): Op<Boolean> =
        (Segments.groupUri inList groups) or (Segments.groupUri.isNull() and (Segments.uri inList groups))

    /**
     * Segments whose current name contains the query (segments/segment-records): the ones [search] would list to [viewer], never lifts, by
     * segment group (segments/segment-groups). Segment groups are ranked by their best-matching member (the whole name, a prefix, a word's
     * prefix, anywhere), then nearest the given centre, measured to each segment's box, or by name without one, and paged by segment group:
     * a page holds every member of its segment groups the search would list, matching or not, next to each other. With a box, only segments
     * whose line has a point in it, so a page may be short with a cursor to carry on from.
     */
    @Suppress("LongMethod") // One ranked, grouped query and the members of its page.
    suspend fun searchNames(viewer: Did?, params: SearchSegmentNames.Params): SearchSegmentNames.Output {
        val box = nameSearchBox(params.minLat, params.minLng, params.maxLat, params.maxLng)
        val near = nameSearchCentre(params.nearLat, params.nearLng)
        val q = params.q.trim().take(SearchSegmentNames.MAX_QUERY_LENGTH).lowercase()
        if (q.isEmpty()) return SearchSegmentNames.Output(emptyList())
        val limit = Paging.clamp(params.limit)
        val offset = offsetOf(params.cursor)
        return db.tx {
            val name = Segments.currentName.lowerCase()
            val match = NameMatch(q)
            val rank = nameRank(name, q, match)
            val listed = nameListed(viewer, params.sport, box)
            // Within a match, nearest the centre first, or by name without one; each segment group by its best member.
            val bestRank = CustomFunction("MIN", IntegerColumnType(), rank)
            val within: Expression<*> =
                near?.let { (lat, lng) ->
                    CustomFunction(
                        "MIN",
                        LongColumnType(),
                        BoxDistance(lat, lng, Segments.minLatE7, Segments.maxLatE7, Segments.minLngE7, Segments.maxLngE7),
                    )
                } ?: CustomFunction("MIN", VarCharColumnType(), name)
            val groups =
                Segments.select(groupKey, bestRank, within)
                    .where { listed and (name like match.anywhere) }
                    .groupBy(groupKey)
                    .orderBy(bestRank to SortOrder.ASC, within to SortOrder.ASC, groupKey to SortOrder.ASC)
                    .limit(limit)
                    .offset(offset.toLong())
                    .map { it[groupKey] }
            val members =
                if (groups.isEmpty()) emptyList()
                else
                    Segments.selectAll()
                        .where { listed and inGroups(groups) }
                        .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                        .toList()
            val place = groups.withIndex().associate { (i, group) -> group to i }
            val ordered = members.sortedBy { place.getValue(it[Segments.groupUri] ?: it[Segments.uri]) }
            val shown = box?.let { b -> ordered.filter { crosses(it[Segments.line], b.minLat, b.minLng, b.maxLat, b.maxLng) } } ?: ordered
            SearchSegmentNames.Output(views(shown), nextOffset(offset, limit, groups.size))
        }
    }

    /**
     * A trail or road and every run segment on it search would list to [viewer] (segments/segment-paths), with its terrain profile and the
     * stretch of each of those along it (segments/path-profile); `NotFound` when not indexed.
     */
    suspend fun path(viewer: Did?, params: GetPath.Params): GetPath.Output = db.tx {
        val row = Paths.selectAll().where { Paths.uri eq params.uri.value }.firstOrNull() ?: throw notFound("Path not found")
        val segments =
            Segments.join(PathSegments, JoinType.INNER, Segments.uri, PathSegments.segmentUri)
                .select(Segments.columns)
                .where { (PathSegments.pathUri eq params.uri.value) and onPathListed(viewer) }
                .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                .toList()
        val views = views(segments)
        val profile =
            pathProfile(params.uri.value, row[Paths.lines], segments.map { ListedLine(AtUri(it[Segments.uri]), it[Segments.line]) })
        GetPath.Output(pathViews(viewer, listOf(row)).single(), views, sectionsOf(params.uri, views.map { it.uri }), profile)
    }

    /**
     * [path]'s sections holding any of [listed], in its record's order, each with only those, in [listed]'s order (segments/segment-paths):
     * sections only narrow what the viewer already gets.
     */
    private fun JdbcTransaction.sectionsOf(path: AtUri, listed: List<AtUri>): List<GetPath.SectionView> {
        if (listed.isEmpty()) return emptyList()
        // Only the listed segments' rows, so a family of thousands of section keys costs no more than the segments the viewer gets.
        val bySegment =
            listed
                .map { it.value }
                .chunked(SECTION_CHUNK)
                .flatMap { chunk ->
                    PathSectionSegments.selectAll()
                        .where { (PathSectionSegments.pathUri eq path.value) and (PathSectionSegments.segmentUri inList chunk) }
                        .map {
                            it[PathSectionSegments.segmentUri] to
                                (it[PathSectionSegments.sectionOrder] to it[PathSectionSegments.sectionName])
                        }
                }
                .toMap()
        return listed
            .mapNotNull { uri -> bySegment[uri.value]?.let { it to uri } }
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedBy { (section, _) -> section.first }
            .map { (section, uris) -> GetPath.SectionView(section.second, uris) }
    }

    /** The views of up to [GetPaths.MAX_URIS] paths, in the order asked, leaving out those not indexed. */
    suspend fun paths(viewer: Did?, params: GetPaths.Params): GetPaths.Output {
        if (params.uris.size > GetPaths.MAX_URIS) invalidRequest("At most ${GetPaths.MAX_URIS} paths, not ${params.uris.size}")
        val asked = params.uris.map { it.value }.distinct()
        return db.tx {
            val rows = Paths.selectAll().where { Paths.uri inList asked }.associateBy { it[Paths.uri] }
            GetPaths.Output(pathViews(viewer, asked.mapNotNull { rows[it] }))
        }
    }

    /**
     * Trails and roads, and segment groups on none, found by name (segments/segment-paths). A path counts when its own name matches and it
     * has a segment the search lists, or when one of the segments it lists matches; a matching segment on no path brings its segment group.
     * Each result takes its best match (the whole name, a prefix, a word's prefix, anywhere), then its nearest box to the centre, else its
     * name; results page by offset. Matches are read best first, a batch at a time, only until the page and one more result are found
     * ([bestHits]), so an early page never ranks every match.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod") // Two kinds of match, merged and ranked, then each result's view.
    suspend fun searchPathNames(viewer: Did?, params: SearchPathNames.Params): SearchPathNames.Output {
        val box = nameSearchBox(params.minLat, params.minLng, params.maxLat, params.maxLng)
        val near = nameSearchCentre(params.nearLat, params.nearLng)
        val q = params.q.trim().take(SearchPathNames.MAX_QUERY_LENGTH).lowercase()
        if (q.isEmpty()) return SearchPathNames.Output(emptyList())
        val limit = Paging.clamp(params.limit).coerceAtMost(SearchPathNames.MAX_LIMIT)
        val offset = offsetOf(params.cursor)
        return db.tx {
            val match = NameMatch(q)
            val listed = nameListed(viewer, params.sport, box)
            val need = offset + limit + 1
            val segmentHits =
                bestHits(need) { size, skip ->
                    val segments =
                        Segments.select(
                                Segments.uri,
                                groupKey,
                                Segments.currentName,
                                Segments.minLatE7,
                                Segments.maxLatE7,
                                Segments.minLngE7,
                                Segments.maxLngE7,
                            )
                            .where { listed and (Segments.currentName.lowerCase() like match.anywhere) }
                            // Best first, so the cap keeps the matches a page shows, in an order every page reads alike.
                            .orderByBestMatches(
                                Segments.currentName,
                                q,
                                match,
                                near,
                                Segments.minLatE7,
                                Segments.maxLatE7,
                                Segments.minLngE7,
                                Segments.maxLngE7,
                                Segments.uri,
                            )
                            .limit(size)
                            .offset(skip)
                            .toList()
                    val onPaths = pathsOf(segments.map { it[Segments.uri] })
                    segments
                        .flatMap { row ->
                            val name = row[Segments.currentName].lowercase()
                            val rank = rankOf(name, q)
                            val away =
                                near?.let {
                                    boxDistance(
                                        it,
                                        row[Segments.minLatE7],
                                        row[Segments.maxLatE7],
                                        row[Segments.minLngE7],
                                        row[Segments.maxLngE7],
                                    )
                                } ?: 0L
                            val paths = onPaths[row[Segments.uri]].orEmpty()
                            if (paths.isEmpty()) listOf(NameHit(row[groupKey], path = false, rank, away, name))
                            else paths.map { NameHit(it, path = true, rank, away, name) }
                        }
                        .let { NameBatch(it, segments.size) }
                }
            val pathHits =
                bestHits(need) { size, skip ->
                    val named =
                        Paths.select(Paths.uri, Paths.name, Paths.minLatE7, Paths.maxLatE7, Paths.minLngE7, Paths.maxLngE7)
                            .where { Paths.name.lowerCase() like match.anywhere }
                            .orderByBestMatches(
                                Paths.name,
                                q,
                                match,
                                near,
                                Paths.minLatE7,
                                Paths.maxLatE7,
                                Paths.minLngE7,
                                Paths.maxLngE7,
                                Paths.uri,
                            )
                            .limit(size)
                            .offset(skip)
                            .toList()
                    val withListed =
                        if (named.isEmpty()) emptySet()
                        else
                            Segments.join(PathSegments, JoinType.INNER, Segments.uri, PathSegments.segmentUri)
                                .select(PathSegments.pathUri)
                                .where { (PathSegments.pathUri inList named.map { it[Paths.uri] }) and listed }
                                .withDistinct()
                                .mapTo(mutableSetOf()) { it[PathSegments.pathUri] }
                    named
                        .filter { it[Paths.uri] in withListed }
                        .map { row ->
                            val away =
                                near?.let {
                                    boxDistance(it, row[Paths.minLatE7], row[Paths.maxLatE7], row[Paths.minLngE7], row[Paths.maxLngE7])
                                } ?: 0L
                            val name = row[Paths.name].lowercase()
                            NameHit(row[Paths.uri], path = true, rankOf(name, q), away, name)
                        }
                        .let { NameBatch(it, named.size) }
                }
            val ranked =
                (segmentHits + pathHits)
                    .groupBy { it.path to it.key }
                    .values
                    .map { same -> same.minWith(NAME_HIT_ORDER) }
                    .sortedWith(NAME_HIT_ORDER.thenBy { it.key })
            val page = ranked.drop(offset).take(limit)
            val pathRows = Paths.selectAll().where { Paths.uri inList page.filter { it.path }.map { it.key } }.toList()
            val pathViews = pathViews(viewer, pathRows).associateBy { it.uri.value }
            val groupKeys = page.filterNot { it.path }.map { it.key }
            val members =
                if (groupKeys.isEmpty()) emptyMap()
                else
                    Segments.selectAll()
                        .where { listed and inGroups(groupKeys) }
                        .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                        .filter { row -> box == null || crosses(row[Segments.line], box.minLat, box.minLng, box.maxLat, box.maxLng) }
                        .groupBy { it[Segments.groupUri] ?: it[Segments.uri] }
            val results = page.mapNotNull { hit ->
                if (hit.path) pathViews[hit.key]?.let { SearchPathNames.Result(path = it) }
                else members[hit.key]?.let { SearchPathNames.Result(group = views(it)) }
            }
            SearchPathNames.Output(results, (offset + limit).toString().takeIf { ranked.size > offset + limit })
        }
    }

    /** Run segments that name search lists to [viewer]: served, unhidden, listed, of [sport] when given, and with a point in [box]. */
    private fun nameListed(viewer: Did?, sport: String?, box: SearchBox?): Op<Boolean> {
        val inBox = box?.let(::segmentsNear) ?: Op.TRUE
        return SegmentPlace.servedTo(viewer) and
            unhidden(viewer, Segments.authorDid, Segments.uri) and
            SegmentPlace.listedTo(viewer) and
            (Segments.kind neq SegmentKind.LIFT) and
            (sport?.let { Segments.sport eq it } ?: Op.TRUE) and
            inBox
    }

    /** Segments on a path that [viewer] may find: what [nameListed] admits without a sport or a box. */
    private fun onPathListed(viewer: Did?): Op<Boolean> = nameListed(viewer, sport = null, box = null)

    /** The paths each of [segments] lies on. */
    private fun pathsOf(segments: List<String>): Map<String, List<String>> =
        segments
            .chunked(PATH_LOOKUP_CHUNK)
            .flatMap { chunk ->
                PathSegments.selectAll()
                    .where { PathSegments.segmentUri inList chunk }
                    .map { it[PathSegments.segmentUri] to it[PathSegments.pathUri] }
            }
            .groupBy({ it.first }, { it.second })

    /** [rows] of [Paths] as [viewer] sees them: what the segments on each that search lists to them cover. */
    private fun JdbcTransaction.pathViews(viewer: Did?, rows: List<ResultRow>): List<PathView> {
        if (rows.isEmpty()) return emptyList()
        val covered =
            Segments.join(PathSegments, JoinType.INNER, Segments.uri, PathSegments.segmentUri)
                .select(PathSegments.pathUri, Segments.uri, Segments.sport, groupKey, Segments.authorDid, Segments.distanceM)
                .where { (PathSegments.pathUri inList rows.map { it[Paths.uri] }) and onPathListed(viewer) }
                .groupBy { it[PathSegments.pathUri] }
        val oneWays = oneWaysOf(covered)
        return rows.map { row ->
            val on = covered[row[Paths.uri]].orEmpty()
            PathView(
                uri = AtUri(row[Paths.uri]),
                cid = Cid(row[Paths.cid]),
                kind = row[Paths.kind],
                name = row[Paths.name],
                lines = row[Paths.lines].split('\n'),
                lengthMeters = row[Paths.lengthM],
                source = row[Paths.dataSource],
                license = row[Paths.license],
                attribution = row[Paths.attribution],
                sports = SegmentSportGroup.all.filter { sport -> on.any { it[Segments.sport] == sport } },
                groupCount = on.map { it[groupKey] }.distinct().size,
                oneWays = oneWays[row[Paths.uri]].orEmpty(),
                // Stored cleaned, and cleaned again so a tightened rule needs no reindex (segments/trail-attributes).
                trail = TrailAttributesSanitizer.decoded(row[Paths.trail]),
            )
        }
    }

    /**
     * For each path in [covered] (its segments, by path URI), the sport groups other than alpine whose segments on it are all served
     * one-way, each with its way (segments/segment-paths): its longest such segment's direction, by its served climb, else its served
     * elevation, else its heading or turn. Only the importer's segments may be one-way, so only theirs are read in full, in one query.
     */
    private fun JdbcTransaction.oneWaysOf(covered: Map<String, List<ResultRow>>): Map<String, List<PathOneWay>> {
        val bySport = covered.mapValues { (_, on) ->
            SegmentSportGroup.all
                .filter { it != SegmentSportGroup.ALPINE }
                .associateWith { sport -> on.filter { it[Segments.sport] == sport } }
                .filterValues { rows -> rows.isNotEmpty() && rows.all { byImporter(it[Segments.authorDid]) } }
        }
        val uris = bySport.values.flatMap { sports -> sports.values.flatten().map { it[Segments.uri] } }.distinct()
        if (uris.isEmpty()) return emptyMap()
        val full =
            uris
                .chunked(PATH_LOOKUP_CHUNK)
                .flatMap { chunk -> Segments.selectAll().where { Segments.uri inList chunk }.toList() }
                .associateBy { it[Segments.uri] }
        val records = full.mapValues { (_, row) -> AtJson.decodeFromString(SegmentRecord.serializer(), row[Segments.record]) }
        return bySport.mapValues { (_, sports) ->
            sports.mapNotNull { (sport, rows) ->
                val held = rows.mapNotNull { full[it[Segments.uri]] }
                if (held.size != rows.size || !held.all { records.getValue(it[Segments.uri]).oneWay == true }) return@mapNotNull null
                val row = held.sortedBy { it[Segments.uri] }.maxBy { it[Segments.distanceM] }
                val points = SegmentGeometry.decodePolyline(row[Segments.line])?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                val climb =
                    SegmentClimb.ofWord(SegmentClimbs.of(records.getValue(row[Segments.uri]).climb))
                        ?: servedElevation(row, activeAthletes)?.let { SegmentClimb.of(it.gain, it.loss, row[Segments.distanceM]) }
                PathOneWay(sport, SegmentDirection.of(SegmentHeading.of(points), climb).word)
            }
        }
    }

    /**
     * The efforts on an activity the viewer may see, in the order they began; `NotFound` when they may not see the activity. The athlete's
     * entry on the viewer's all-time board carries its rank when it's in the top 10, and the athlete's own efforts on active segments say
     * why they aren't ranked (segments/segment-efforts).
     */
    suspend fun activitySegments(viewer: Did?, params: GetActivitySegments.Params): GetActivitySegments.Output = db.tx {
        requireVisibleActivity(params.activity.value, viewer)
        val efforts = effortViews(viewer, SegmentEfforts.activityUri eq params.activity.value)
        val sport = Activities.select(Activities.sport).where { Activities.uri eq params.activity.value }.first()[Activities.sport]
        val own = viewer != null && params.activity.authority == viewer.value
        val reasons = if (own) notRankedReasons(params.activity.value) else emptyMap()
        // Only an active run has boards, and only the fastest of this activity's efforts on a board can be the athlete's entry on it, so
        // the rest skip the board queries.
        fun ranked(effort: EffortView) =
            SegmentStatus.of(effort.segment.status) == SegmentStatus.ACTIVE && SegmentKind.isRanked(effort.segment.kind)
        val candidates =
            efforts
                .filter(::ranked)
                .groupBy { it.segment.uri to it.eBike }
                .values
                .mapNotNull { onBoard -> onBoard.minByOrNull { it.elapsed } }
                .mapTo(mutableSetOf()) { effortId(it) }
        GetActivitySegments.Output(
            efforts.map { effort ->
                effort.copy(
                    rank = if (effortId(effort) in candidates) topRank(viewer, params.activity.authority, effort, sport) else null,
                    notRanked = reasons[effortId(effort)].takeIf { ranked(effort) },
                )
            },
        )
    }

    /** One athlete's efforts on a segment the viewer may see, fastest first; `NotFound` when the segment isn't served. */
    suspend fun segmentEfforts(viewer: Did?, params: GetSegmentEfforts.Params): GetSegmentEfforts.Output = db.tx {
        requireServedPlace(SegmentPlace, viewer, params.segment.value)
        val athlete = resolveShownActor(viewer, params.actor) ?: return@tx GetSegmentEfforts.Output(emptyList())
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
        val next = nextCursor(efforts, limit) { cursorOf(it.elapsed, effortId(it)) }
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
            if (requireServedPlace(SegmentPlace, viewer, params.segment.value) != SegmentStatus.ACTIVE)
                return@tx GetSegmentLeaderboard.Output(emptyList(), board = requested, boards = listOfNotNull(requested))
            val (served, boards) = boardsOf(viewer, params.segment.value, requested)
            if (served == null) return@tx GetSegmentLeaderboard.Output(emptyList())
            val board = boardOf(viewer, params.segment.value, served, since, following) and brackets
            val limit = Paging.clamp(params.limit)
            val page =
                bests(board, havingAfter(BEST, SegmentEfforts.authorDid, params.cursor))
                    .orderBy(BEST to SortOrder.ASC, SegmentEfforts.authorDid to SortOrder.ASC)
                    .limit(limit)
                    .mapNotNull { row -> row[BEST]?.let { row[SegmentEfforts.authorDid] to it } }
            val entries = entries(viewer, board, page)
            val next = nextCursor(page, limit) { (did, best) -> cursorOf(best, did) }
            val own = viewer?.let { me ->
                bests(board and (SegmentEfforts.authorDid eq me.value), Op.TRUE).firstOrNull()?.get(BEST)?.let { me.value to it }
            }
            GetSegmentLeaderboard.Output(entries, own?.let { entries(viewer, board, listOf(it)).singleOrNull() }, next, served, boards)
        }
    }

    /**
     * A segment's Mayors board (segments/segment-mayors): athletes by how many of their efforts a leaderboard of the same filters could
     * rank in [GetSegmentMayors.Params.period]'s window, most first, then by the date each count was reached ([REACHED], their latest
     * counted effort), earliest first. Pages and ranks as every [CountBoard] does.
     */
    suspend fun mayors(viewer: Did?, params: GetSegmentMayors.Params): GetSegmentMayors.Output {
        val period = params.period ?: MayorPeriod.LAST_90_DAYS
        if (period !in MayorPeriod.all) invalidRequest("Unknown period $period")
        val (since, until) = mayorWindow(period, today())
        val following = followingScope(viewer, params.scope)
        val requested = requestedBoard(params.board)
        val brackets = bracketsOf(params.ageGroup, params.weightClass, params.gender)
        return db.tx {
            if (requireServedPlace(SegmentPlace, viewer, params.segment.value) != SegmentStatus.ACTIVE)
                return@tx GetSegmentMayors.Output(emptyList(), board = requested, boards = listOfNotNull(requested))
            val (served, boards) = boardsOf(viewer, params.segment.value, requested)
            if (served == null) return@tx GetSegmentMayors.Output(emptyList())
            val board = boardOf(viewer, params.segment.value, served, since, following, until) and brackets
            val page = mayorBoard.page(board, params.cursor, Paging.clamp(params.limit))
            val own = viewer?.let { mayorBoard.entryOf(board, it.value) }
            GetSegmentMayors.Output(mayorEntries(page.entries), own?.let { mayorEntries(listOf(it)).single() }, page.next, served, boards)
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
            // An athlete's check is always for a run: lifts come only from the importer (segments/segment-lifts).
            val uris = duplicatesOf(input.sport, SegmentKind.RUN, line)
            val rows =
                Segments.selectAll().where { (Segments.uri inList uris) and SegmentPlace.servedTo(viewer) }.associateBy { it[Segments.uri] }
            val created = if (viewer == segmentImporter) 0 else segmentsCreatedSince(viewer, now)
            val remaining = (DAILY_SEGMENT_LIMIT - created).coerceAtLeast(0).toInt()
            CheckSegment.Output(views(uris.mapNotNull { rows[it] }), remaining)
        }
    }

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
                .select(
                    SegmentEfforts.columns +
                        listOf(Segments.uri, Segments.currentName, Segments.sport, Segments.kind, Segments.distanceM, Segments.status),
                )
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
                        it[Segments.currentName],
                        it[Segments.sport],
                        it[Segments.distanceM],
                        status = it[Segments.status],
                        kind = it[Segments.kind].takeIf { k -> k != SegmentKind.RUN },
                    ),
                activity = AtUri(it[SegmentEfforts.activityUri]),
                startOffset = it[SegmentEfforts.startOffsetS],
                elapsed = elapsed,
                startedOn = it[SegmentEfforts.startedOn],
                eBike = it[SegmentEfforts.eBike].takeIf { e -> e },
                // A lift ride is never anyone's fastest (segments/segment-lifts).
                best =
                    (SegmentKind.isRanked(it[Segments.kind]) &&
                            bests[it[SegmentEfforts.authorDid] to it[SegmentEfforts.segmentUri]] == elapsed)
                        .takeIf { b -> b },
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
     * Efforts a leaderboard ranks, over [rankedEfforts]: on an active segment that isn't a lift, from an activity that isn't excluded, by
     * an athlete who doesn't hide from leaderboards, and no faster than the segment's sport group allows. All read now, so none needs a
     * rematch.
     */
    private fun rankable(): Op<Boolean> = rankedSegment() and rankableEffort()

    /** A segment whose efforts leaderboards and Mayors rank: active, and a run (segments/segment-lifts). */
    private fun rankedSegment(): Op<Boolean> = (Segments.status eq SegmentStatus.ACTIVE) and (Segments.kind inList SegmentKind.ranked)

    /** [rankable] less the segment's status: what [notRankedReasons] gives a reason for failing, from the same Ops. */
    private fun rankableEffort(): Op<Boolean> =
        not(hidesFromLeaderboards(SegmentEfforts.authorDid)) and not(excludedFromLeaderboards()) and plausibleEffort()

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
        val type =
            if (board == SegmentBoard.E_BIKE) SegmentEfforts.eBike eq true
            else (SegmentEfforts.eBike eq false) and (Activities.sport eq board)
        return (SegmentEfforts.segmentUri eq segment) and
            type and
            effortsVisibleTo(viewer) and
            rankable() and
            (since?.let { SegmentEfforts.startedOn greaterEq it } ?: Op.TRUE) and
            (until?.let { SegmentEfforts.startedOn less it } ?: Op.TRUE) and
            followedOrOwn(SegmentEfforts.authorDid, viewer, following)
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
                LeaderboardEntry(ranks[i], athletes[did] ?: unknownProfile(did), effort)
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

    /**
     * Why leaderboards don't rank each of [activity]'s efforts that they don't, by effort ID, for its own athlete only: the first of
     * [rankableEffort]'s Ops each fails, read in SQL, so the reasons can't drift from the boards.
     */
    private fun JdbcTransaction.notRankedReasons(activity: String): Map<String, String> {
        val reason =
            Case()
                .When(hidesFromLeaderboards(SegmentEfforts.authorDid), stringLiteral(NotRanked.HIDDEN))
                .When(excludedFromLeaderboards(), stringLiteral(NotRanked.EXCLUDED))
                .Else(stringLiteral(NotRanked.IMPLAUSIBLE))
        return rankedEfforts()
            .select(SegmentEfforts.id, reason)
            .where { (SegmentEfforts.activityUri eq activity) and not(rankableEffort()) }
            .associate { it[SegmentEfforts.id] to it[reason] }
    }

    /** The Mayors board over [rankedEfforts]: each athlete's [EFFORTS], reached on their [REACHED] date. */
    private val mayorBoard = CountBoard(::rankedEfforts, SegmentEfforts.authorDid, EFFORTS, REACHED)

    /** [MayorEntry]s for [entries], with their athletes' profiles. */
    private fun JdbcTransaction.mayorEntries(entries: List<CountEntry>): List<MayorEntry> {
        val athletes = basicProfiles(entries.map { it.athlete }, avatars)
        return entries.map { MayorEntry(it.rank, athletes[it.athlete] ?: unknownProfile(it.athlete), it.count.toInt(), it.reached) }
    }

    /**
     * [params]' period start ([periodStart]) and whether it asks for the following scope; `InvalidRequest` for an unknown period or scope,
     * or the following scope without a [viewer].
     */
    private fun filtersOf(viewer: Did?, params: GetSegmentLeaderboard.Params): Pair<String?, Boolean> {
        val period = params.period ?: LeaderboardPeriod.ALL_TIME
        if (period !in LeaderboardPeriod.all) invalidRequest("Unknown period $period")
        return periodStart(period) to followingScope(viewer, params.scope)
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
            },
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
        SegmentEfforts.rowsVisibleTo(viewer) and visibleTo(viewer) and SegmentPlace.servedTo(viewer)

    /**
     * Whether [author] is the segment importer, whose segments alone may be served one-way or with a climb (segments/segment-direction).
     */
    private fun byImporter(author: String): Boolean = segmentImporter != null && author == segmentImporter.value

    private fun JdbcTransaction.views(rows: List<ResultRow>): List<SegmentView> {
        val creators = basicProfiles(rows.map { it[Segments.authorDid] }, avatars)
        val onPaths = pathsOf(rows.map { it[Segments.uri] })
        return rows.map {
            val author = it[Segments.authorDid]
            // Effort elevation only as the segment's aggregate, never an effort's own (segments/segment-elevation).
            val elevation = servedElevation(it, activeAthletes)
            val record = AtJson.decodeFromString(SegmentRecord.serializer(), it[Segments.record])
            SegmentView(
                uri = AtUri(it[Segments.uri]),
                cid = Cid(it[Segments.cid]),
                creator = creators[author] ?: unknownProfile(author),
                record = record,
                distanceMeters = it[Segments.distanceM],
                status = it[Segments.status],
                duplicateOf = it[Segments.duplicateOf]?.let(::AtUri),
                group = AtUri(it[Segments.groupUri] ?: it[Segments.uri]),
                paths = onPaths[it[Segments.uri]].orEmpty().sorted().map(::AtUri),
                name = it[Segments.currentName],
                elevationGain = elevation?.gain,
                elevationLoss = elevation?.loss,
                elevationSource = elevation?.source,
                // Only the importer's data-driven one-way rule and terrain climb are honoured, never an athlete's own
                // (segments/segment-direction).
                oneWay = true.takeIf { record.oneWay == true && byImporter(author) },
                climb = SegmentClimbs.of(record.climb)?.takeIf { byImporter(author) },
                // Trail attributes come only from the importer's source data, never an athlete's own (segments/trail-attributes).
                trail = if (byImporter(author)) TrailAttributesSanitizer.clean(record.trail) else null,
            )
        }
    }

    /** Whether the precision-6 [line] has a point (every [SegmentGeometry.REFERENCE_SPACING_M]) in the box, in 1e-7 degrees. */
    private fun crosses(line: String, minLat: Int, minLng: Int, maxLat: Int, maxLng: Int): Boolean =
        SegmentGeometry.resample(SegmentGeometry.decodePolyline(line).orEmpty()).any {
            it.lat * E7_PER_DEGREE in minLat.toDouble()..maxLat.toDouble() && it.lng * E7_PER_DEGREE in minLng.toDouble()..maxLng.toDouble()
        }

    companion object {
        /** The most segment group members `getSegment` lists, as its lexicon caps them. */
        private const val MAX_ROUTE_MEMBERS = 100

        /** How many segment URIs one path lookup carries. */
        private const val PATH_LOOKUP_CHUNK = 1_000

        /** The batches of candidates one search reads at most: 300 rows at the default batch size. */
        private const val SEARCH_BATCHES = 3

        /** How well a name matches a name search: the whole name, its start, a word's start, anywhere. Lower is better. */

        /** The ranks an activity's efforts show (segments/segment-efforts). */
        private const val TOP_RANKS = 10

        /** An athlete's best time on a board. */
        private val BEST = SegmentEfforts.elapsedS.min()

        /** How many of an athlete's efforts a Mayors board counts. */
        private val EFFORTS = SegmentEfforts.id.count()

        /** The local date an athlete's Mayors count was reached: their latest counted effort's. */
        private val REACHED = SegmentEfforts.startedOn.max()

        /** An effort's ID, rebuilt from its view as the matcher writes it, for the keyset cursor. */
        private fun effortId(effort: EffortView) = "${effort.activity.value} ${effort.segment.uri.value} ${effort.startOffset}"
    }
}

/**
 * The window of a Mayors board for [period] (a known [MayorPeriod] value) on [today]: its first local date and the first one after it, as
 * `YYYY-MM-DD`, each null when unbounded. "This" windows have no end, so an effort dated ahead of the UTC date still counts, and weeks
 * start on Monday.
 */
internal fun mayorWindow(period: String, today: LocalDate): DateWindow {
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
    return DateWindow(window.first?.toString(), window.second?.toString())
}

/** A board's window: its first local date [since] and the first one after it, [until], as `YYYY-MM-DD`, each null when unbounded. */
internal data class DateWindow(val since: String?, val until: String?)

/** The days [MayorPeriod.LAST_90_DAYS] covers, today included. */
private const val LAST_DAYS = 90L

/** How many segment URIs one `IN` list of `sectionsOf` carries. */
private const val SECTION_CHUNK = 1_000

/** A search box, 1e-7 degrees. */
internal data class SearchBox(val minLat: Int, val minLng: Int, val maxLat: Int, val maxLng: Int)

/** How well a name matches a name search: the whole name, its start, a word's start, anywhere. Lower is better. */
internal const val NAME_WHOLE = 0
internal const val NAME_PREFIX = 1
internal const val NAME_WORD = 2
internal const val NAME_ANYWHERE = 3
private const val BOX_SIDES = 4

/** A name search's box, checked like an area search's: all four sides or none, else `InvalidRequest`. */
internal fun nameSearchBox(minLat: Int?, minLng: Int?, maxLat: Int?, maxLng: Int?): SearchBox? {
    val given = listOfNotNull(minLat, minLng, maxLat, maxLng)
    if (given.isEmpty()) return null
    if (given.size != BOX_SIDES) invalidRequest("Give all four sides of the box, or none")
    val box = SearchBox(given[0], given[1], given[2], given[3])
    requireSearchBox(box.minLat, box.minLng, box.maxLat, box.maxLng)
    return box
}

/** The centre a name search orders by, 1e-7 degrees. */
internal data class NameSearchCentre(val lat: Int, val lng: Int)

/** A name search's centre, both coordinates or none, on the globe, else `InvalidRequest`. */
internal fun nameSearchCentre(lat: Int?, lng: Int?): NameSearchCentre? {
    if (lat == null && lng == null) return null
    if (lat == null || lng == null) invalidRequest("Give both nearLat and nearLng, or neither")
    if (!validPositionE7(lat, lng)) invalidRequest("The centre is outside the globe")
    return NameSearchCentre(lat, lng)
}

/**
 * How far a bounding box ([minLat]..[maxLat], [minLng]..[maxLng] columns) lies from a point ([lat], [lng], 1e-7 degrees), for ordering
 * only: the gap to the box along each axis (zero inside it), longitude scaled by the cosine at [lat] in thousandths. A point is a box whose
 * sides are one column. Plain `GREATEST` and `BIGINT` arithmetic, so H2 runs it too.
 */
@Suppress("LongParameterList") // A point and a box's four sides.
internal class BoxDistance(
    private val lat: Int,
    private val lng: Int,
    private val minLat: Expression<Int>,
    private val maxLat: Expression<Int>,
    private val minLng: Expression<Int>,
    private val maxLng: Expression<Int>,
) : ExpressionWithColumnType<Long>() {
    override val columnType = LongColumnType()

    private val lngScale = (cos(Math.toRadians(lat.e7ToDegrees())) * PER_MILLE).roundToLong()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            gap(minLat, maxLat, lat)
            append(" * $PER_MILLE + ")
            gap(minLng, maxLng, lng)
            append(" * $lngScale")
        }
    }

    private fun QueryBuilder.gap(min: Expression<Int>, max: Expression<Int>, at: Int) {
        append("GREATEST(CAST(")
        append(min)
        append(" AS BIGINT) - $at, $at - CAST(")
        append(max)
        append(" AS BIGINT), 0)")
    }

    private companion object {
        const val PER_MILLE = 1000
    }
}

/** How well a lower-cased [name] matches [q] in SQL, as [rankOf] ranks it in Kotlin ([NAME_WHOLE] best). */
private fun nameRank(name: ExpressionWithColumnType<String>, q: String, match: NameMatch): Expression<Int> =
    Case()
        .When(name eq q, intLiteral(NAME_WHOLE))
        .When(name like match.prefix, intLiteral(NAME_PREFIX))
        .When(name like match.wordPrefix, intLiteral(NAME_WORD))
        .Else(intLiteral(NAME_ANYWHERE))

/**
 * This query ordered as a capped name search reads its matches, as [NAME_HIT_ORDER] ranks them: best match, then nearest [near] by the box,
 * then name, then [uri] so equal ones come in the same order every time.
 */
@Suppress("LongParameterList") // The name, the centre, and the box's four columns.
private fun Query.orderByBestMatches(
    name: Column<String>,
    q: String,
    match: NameMatch,
    near: NameSearchCentre?,
    minLat: Column<Int>,
    maxLat: Column<Int>,
    minLng: Column<Int>,
    maxLng: Column<Int>,
    uri: Column<String>,
): Query {
    val lower = name.lowerCase()
    return listOfNotNull(
            nameRank(lower, q, match),
            near?.let { (lat, lng) -> BoxDistance(lat, lng, minLat, maxLat, minLng, maxLng) },
            lower,
            uri,
        )
        .fold(this) { query, expression -> query.orderBy(expression, SortOrder.ASC) }
}

/** The most matching segments, and paths, one path name search reads. */
private const val MAX_NAME_MATCHES = 5_000

/** One batch of a name search's rows read: the [hits] they make and how many [rows] were read. */
internal data class NameBatch(val hits: List<NameHit>, val rows: Int)

/** The batch [bestHits] reads first; each later one is twice the last. */
internal const val FIRST_NAME_BATCH = 100

/**
 * Hits read best first by [read] (`size` rows from `skip`, given back with how many rows it read), a batch at a time, until they hold
 * [need] distinct results or the rows run out, at most [MAX_NAME_MATCHES] rows. Rows come in the order [NAME_HIT_ORDER] ranks results by,
 * so a result left unread ranks no better than [need] of those read, and an early page never reads every match.
 */
internal fun bestHits(need: Int, read: (size: Int, skip: Long) -> NameBatch): List<NameHit> {
    val hits = mutableListOf<NameHit>()
    val results = mutableSetOf<Pair<Boolean, String>>()
    var skip = 0L
    var batch = maxOf(need * 2, FIRST_NAME_BATCH)
    while (skip < MAX_NAME_MATCHES) {
        val size = minOf(batch.toLong(), MAX_NAME_MATCHES - skip).toInt()
        val (found, rows) = read(size, skip)
        skip += rows
        hits += found
        found.mapTo(results) { it.path to it.key }
        if (rows < size || results.size >= need) break
        batch *= 2
    }
    return hits
}

/** A path name search's match: a path or a segment group by [key], its name's [rank], its box's distance [away] and its [name]. */
internal data class NameHit(val key: String, val path: Boolean, val rank: Int, val away: Long, val name: String)

private val NAME_HIT_ORDER: Comparator<NameHit> = compareBy<NameHit>({ it.rank }, { it.away }, { it.name })

/** How well [name] (lower-cased) matches [q], as a name search ranks it ([NAME_WHOLE] best). */
internal fun rankOf(name: String, q: String): Int =
    when {
        name == q -> NAME_WHOLE
        name.startsWith(q) -> NAME_PREFIX
        name.contains(" $q") -> NAME_WORD
        else -> NAME_ANYWHERE
    }

/** [BoxDistance] in Kotlin: how far a box (1e-7 degrees) lies from [centre], for ordering only. */
@Suppress("MagicNumber") // Thousandths, as BoxDistance scales longitude.
internal fun boxDistance(centre: NameSearchCentre, minLat: Int, maxLat: Int, minLng: Int, maxLng: Int): Long {
    fun gap(min: Int, max: Int, at: Int) = maxOf(min.toLong() - at, at - max.toLong(), 0L)
    val lngScale = (cos(Math.toRadians(centre.lat.e7ToDegrees())) * 1000).roundToLong()
    return gap(minLat, maxLat, centre.lat) * 1000 + gap(minLng, maxLng, centre.lng) * lngScale
}

/** Whether a segment is indexed at [uri], whatever its status or labels. */
private fun JdbcTransaction.segmentIndexed(uri: String): Boolean = Segments.select(Segments.uri).where { Segments.uri eq uri }.any()
