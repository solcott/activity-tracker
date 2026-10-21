package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.ktor.http.HttpStatusCode
import kotlin.math.abs
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Segments (segments/segment-records) and the efforts on them (segments/segment-efforts). A segment is public, but an account that isn't
 * served, or a taken-down segment record, hides it ([served]). An effort is served only to viewers admitted to its stored visibility, and
 * only while they may also see its activity's row ([visibleTo] on both), so an activity narrowed or taken down hides its efforts at once.
 * Whether an effort is the athlete's best is judged among the efforts the same viewer may see.
 */
class SegmentViews(private val db: Database, private val avatars: AvatarUrls) {
    suspend fun segment(viewer: Did?, params: GetSegment.Params): GetSegment.Output = db.tx {
        val row =
            Segments.selectAll().where { (Segments.uri eq params.uri.value) and segmentServed(viewer) }.firstOrNull()
                ?: throw notFound("Segment not found")
        val best = viewer?.let { own ->
            effortViews(viewer, (SegmentEfforts.segmentUri eq params.uri.value) and (SegmentEfforts.authorDid eq own.value)).minByOrNull {
                it.elapsed
            }
        }
        GetSegment.Output(views(listOf(row)).single().copy(viewer = best?.let { SegmentViewerState(it) }))
    }

    /** Segments in a box of at most [SearchSegments.MAX_SPAN_E7] a side whose line has a point in it, oldest indexed first. */
    suspend fun search(viewer: Did?, params: SearchSegments.Params): SearchSegments.Output {
        val minLat = params.minLat
        val minLng = params.minLng
        val maxLat = params.maxLat
        val maxLng = params.maxLng
        if (listOf(minLat, maxLat).any { abs(it) > MAX_LAT_E7 } || listOf(minLng, maxLng).any { abs(it) > MAX_LNG_E7 })
            invalidRequest("The box is outside the globe")
        if (minLat > maxLat || minLng > maxLng) invalidRequest("The box's minimum is above its maximum")
        if (maxLat - minLat > SearchSegments.MAX_SPAN_E7 || maxLng - minLng > SearchSegments.MAX_SPAN_E7)
            invalidRequest("The box is wider than one degree")
        val cells = SegmentCells.cellsIn(minLat / E7, minLng / E7, maxLat / E7, maxLng / E7)
        val limit = Paging.clamp(params.limit)
        return db.tx {
            val found = mutableListOf<ResultRow>()
            var cursor = params.cursor
            var exhausted = false
            // Cells and the stored box only narrow it down: each candidate's line is checked against the box, a page at a time.
            while (found.size < limit && !exhausted) {
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
                                exists(
                                    SegmentCellIndex.select(SegmentCellIndex.cell).where {
                                        (SegmentCellIndex.segmentUri eq Segments.uri) and (SegmentCellIndex.cell inList cells)
                                    }
                                ) and
                                keysetAfter(Segments.indexedAt, Segments.uri, cursor)
                        }
                        .orderBy(Segments.indexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                        .limit(limit)
                        .toList()
                exhausted = page.size < limit
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

    /** The efforts on an activity the viewer may see, in the order they began; `NotFound` when they may not see the activity. */
    suspend fun activitySegments(viewer: Did?, params: GetActivitySegments.Params): GetActivitySegments.Output = db.tx {
        val visible = Activities.select(Activities.uri).where { (Activities.uri eq params.activity.value) and visibleTo(viewer) }.any()
        if (!visible) throw notFound("Activity not found")
        GetActivitySegments.Output(effortViews(viewer, SegmentEfforts.activityUri eq params.activity.value))
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
                .select(SegmentEfforts.columns + listOf(Segments.uri, Segments.name, Segments.sport, Segments.distanceM))
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
                segment = SegmentViewBasic(AtUri(it[Segments.uri]), it[Segments.name], it[Segments.sport], it[Segments.distanceM]),
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
            )
        }
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

        /** An effort's ID, rebuilt from its view as the matcher writes it, for the keyset cursor. */
        private fun effortId(effort: EffortView) = "${effort.activity.value} ${effort.segment.uri.value} ${effort.startOffset}"

        private fun invalidRequest(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)

        private fun notFound(message: String) = XrpcError(HttpStatusCode.BadRequest.value, "NotFound", message)
    }
}
