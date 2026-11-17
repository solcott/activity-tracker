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

// Segment groups (segments/segment-groups): segments of one kind that follow the same path, in any sport group and either direction, named
// by
// their earliest-indexed live member. Each member is compared with segment groups' namers only, never chained through other members.

/** The segment group a segment row is in: its [Segments.groupUri], or its own URI before it has one. */
internal val groupKey: Expression<String> = Coalesce(Segments.groupUri, Segments.uri)

/**
 * Sets the segment group of the segment at [uri], or of each member of the segment group it names, earliest first: what indexing a new,
 * changed or re-statused segment calls. A segment naming a segment group (or with none yet) has every member derived again, itself first
 * among them by index order; any other is just given its own.
 */
internal fun JdbcTransaction.regroup(uri: String) {
    val row = Segments.select(Segments.groupUri).where { Segments.uri eq uri }.firstOrNull() ?: return
    val group = row[Segments.groupUri]
    if (group == null || group == uri) rederiveGroups(listOf(uri)) else assignGroup(uri)
}

/**
 * Derives again, earliest-indexed first, the segment group of every segment in the segment groups [roots] named, and of [roots] themselves
 * while they're indexed: after those segments were deleted, archived, re-lined or marked duplicate, so the next earliest live member names
 * the segment group.
 */
internal fun JdbcTransaction.rederiveGroups(roots: List<String>) {
    if (roots.isEmpty()) return
    val members =
        Segments.select(Segments.uri)
            .where { (Segments.groupUri inList roots) or (Segments.uri inList roots) }
            .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
            .map { it[Segments.uri] }
    // Cleared first, so no member is taken for a segment group's namer while its old segment group is being taken apart.
    Segments.update({ Segments.uri inList members }) { it[groupUri] = null }
    for (member in members) assignGroup(member)
}

/**
 * Gives the segment at [uri] its segment group: the earliest-indexed live segment of its kind naming a segment group ([namersAlong]) that
 * came before it, else itself. A live segment that names its own segment group then takes in the later namers it follows, deriving their
 * segment groups again.
 */
private fun JdbcTransaction.assignGroup(uri: String) {
    val row =
        Segments.select(Segments.kind, Segments.line, Segments.firstIndexedAt, Segments.status).where { Segments.uri eq uri }.firstOrNull()
            ?: return
    val line = SegmentGeometry.decodePolyline(row[Segments.line])
    val order = IndexOrder(row[Segments.firstIndexedAt], uri)
    val namers = line?.let { namersAlong(row[Segments.kind], it, except = uri) }.orEmpty()
    val root = namers.firstOrNull { earlier(it, order) }
    Segments.update({ Segments.uri eq uri }) { it[groupUri] = root?.uri ?: uri }
    if (root == null && row[Segments.status] in LIVE_STATUSES) {
        rederiveGroups(namers.filter { earlier(order, it) }.map { it.uri })
    }
}

/**
 * The live segments of [kind] other than [except] that name a segment group (their own, or none yet) and follow [line] either way
 * ([SegmentGeometry.sameLine]), earliest first indexed first. Like duplicates, they must have cells at both of the line's ends.
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
                (Segments.groupUri.isNull() or (Segments.groupUri eq Segments.uri))
        }
        .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
        .filter { row -> SegmentGeometry.decodePolyline(row[Segments.line])?.let { SegmentGeometry.sameLine(line, it) } == true }
        .map { IndexOrder(it[Segments.firstIndexedAt], it[Segments.uri]) }
}

/**
 * Gives segment groups to segments indexed before segment groups existed, earliest first indexed first, [batch] at a time, each batch in
 * its own transaction, until none is left. Indexing gives new ones theirs, so it's run once on start; it resumes where it stopped. Returns
 * how many it gave segment groups to.
 */
internal suspend fun assignMissingGroups(db: Database, batch: Int = ROUTE_BATCH): Int {
    var total = 0
    while (true) {
        val done = db.tx {
            val missing =
                Segments.select(Segments.uri)
                    .where { Segments.groupUri.isNull() }
                    .orderBy(Segments.firstIndexedAt to SortOrder.ASC, Segments.uri to SortOrder.ASC)
                    .limit(batch)
                    .map { it[Segments.uri] }
            // assignGroup always sets one, so each batch moves on.
            for (uri in missing) assignGroup(uri)
            missing.size
        }
        total += done
        if (done < batch) return total
    }
}

/** [assignMissingGroups] run once on start, logging how many it gave segment groups to, or why it stopped. */
internal suspend fun assignMissingGroups(db: Database, log: Logger) {
    try {
        val given = assignMissingGroups(db)
        if (given > 0) log.info("Gave {} segments their groups", given)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        log.warn("Giving segments their groups failed: {}", e.message)
    }
}

private const val ROUTE_BATCH = 500
