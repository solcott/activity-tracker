package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.SegmentCellIndex
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.Logger

// Routes (segments/segment-routes): segments of one kind that follow the same path, in any sport group and either direction, named by
// their earliest-indexed live member. Each member is compared with routes' namers only, never chained through other members.

/** The route a segment row is in: its [Segments.routeUri], or its own URI before it has one. */
internal val routeKey: Expression<String> = Coalesce(Segments.routeUri, Segments.uri)

/**
 * Sets the route of the segment at [uri], or of each member of the route it names, earliest first: what indexing a new, changed or
 * re-statused segment calls. A segment naming a route (or with none yet) has every member derived again, itself first among them by index
 * order; any other is just given its own.
 */
internal fun JdbcTransaction.reroute(uri: String) {
    val row = Segments.select(Segments.routeUri).where { Segments.uri eq uri }.firstOrNull() ?: return
    val route = row[Segments.routeUri]
    if (route == null || route == uri) rederiveRoutes(listOf(uri)) else assignRoute(uri)
}

/**
 * Derives again, earliest-indexed first, the route of every segment in the routes [roots] named, and of [roots] themselves while they're
 * indexed: after those segments were deleted, archived, re-lined or marked duplicate, so the next earliest live member names the route.
 */
internal fun JdbcTransaction.rederiveRoutes(roots: List<String>) {
    if (roots.isEmpty()) return
    val members =
        Segments.select(Segments.uri)
            .where { (Segments.routeUri inList roots) or (Segments.uri inList roots) }
            .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
            .map { it[Segments.uri] }
    // Cleared first, so no member is taken for a route's namer while its old route is being taken apart.
    Segments.update({ Segments.uri inList members }) { it[routeUri] = null }
    for (member in members) assignRoute(member)
}

/**
 * Gives the segment at [uri] its route: the earliest-indexed live segment of its kind naming a route ([namersAlong]) that came before it,
 * else itself. A live segment that names its own route then takes in the later namers it follows, deriving their routes again.
 */
private fun JdbcTransaction.assignRoute(uri: String) {
    val row =
        Segments.select(Segments.kind, Segments.line, Segments.firstIndexedAt, Segments.status).where { Segments.uri eq uri }.firstOrNull()
            ?: return
    val line = SegmentGeometry.decodePolyline(row[Segments.line])
    val order = IndexOrder(row[Segments.firstIndexedAt], uri)
    val namers = line?.let { namersAlong(row[Segments.kind], it, except = uri) }.orEmpty()
    val root = namers.firstOrNull { earlier(it, order) }
    Segments.update({ Segments.uri eq uri }) { it[routeUri] = root?.uri ?: uri }
    if (root == null && row[Segments.status] in LIVE_STATUSES) {
        rederiveRoutes(namers.filter { earlier(order, it) }.map { it.uri })
    }
}

/**
 * The live segments of [kind] other than [except] that name a route (their own, or none yet) and follow [line] either way
 * ([SegmentGeometry.sameRoute]), earliest first indexed first. Like duplicates, they must have cells at both of the line's ends.
 */
private fun JdbcTransaction.namersAlong(kind: String, line: List<GeoPoint>, except: String): List<IndexOrder> {
    fun near(point: GeoPoint) =
        SegmentCellIndex.select(SegmentCellIndex.segmentUri)
            .where { SegmentCellIndex.cell eq SegmentCells.cellOf(point.lat, point.lng) }
            .map { it[SegmentCellIndex.segmentUri] }
            .toSet()
    val nearBoth = (near(line.first()) intersect near(line.last())) - except
    if (nearBoth.isEmpty()) return emptyList()
    return Segments.select(Segments.uri, Segments.line, Segments.firstIndexedAt)
        .where {
            (Segments.uri inList nearBoth) and
                (Segments.kind eq kind) and
                (Segments.status inList LIVE_STATUSES) and
                (Segments.routeUri.isNull() or (Segments.routeUri eq Segments.uri))
        }
        .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
        .filter { row -> SegmentGeometry.decodePolyline(row[Segments.line])?.let { SegmentGeometry.sameRoute(line, it) } == true }
        .map { IndexOrder(it[Segments.firstIndexedAt], it[Segments.uri]) }
}

/**
 * Gives routes to segments indexed before routes existed, earliest first indexed first, [batch] at a time, each batch in its own
 * transaction, until none is left. Indexing gives new ones theirs, so it's run once on start; it resumes where it stopped. Returns how many
 * it gave routes to.
 */
internal suspend fun assignMissingRoutes(db: Database, batch: Int = ROUTE_BATCH): Int {
    var total = 0
    while (true) {
        val done = db.tx {
            val missing =
                Segments.select(Segments.uri)
                    .where { Segments.routeUri.isNull() }
                    .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                    .limit(batch)
                    .map { it[Segments.uri] }
            // assignRoute always sets one, so each batch moves on.
            for (uri in missing) assignRoute(uri)
            missing.size
        }
        total += done
        if (done < batch) return total
    }
}

/** [assignMissingRoutes] run once on start, logging how many it gave routes to, or why it stopped. */
internal suspend fun assignMissingRoutes(db: Database, log: Logger) {
    try {
        val given = assignMissingRoutes(db)
        if (given > 0) log.info("Gave {} segments their routes", given)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        log.warn("Giving segments their routes failed: {}", e.message)
    }
}

private const val ROUTE_BATCH = 500
