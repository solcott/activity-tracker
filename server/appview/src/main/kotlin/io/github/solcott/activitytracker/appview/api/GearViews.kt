@file:Suppress("TooManyFunctions") // activityView.gear, and gear usage with the counted-activities rule its three queries share.

package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.GearMountRows
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearMountView
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Join
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.union

// Serving the gear an activity lists (`activityView.gear`), which `Views.hydrate` asks for a page at a time, and gear usage
// (`gear.getGear`, `getActorGear`, `getGearActivities`), which `Views` answers from the helpers at the end of this file.

/**
 * The gear of [activities] (each one [viewer] may see) as [viewer] sees it, in one query: the list of the narrowest record listing gear in
 * a tier of its gear category they're admitted to (the activity record, or a details record laid over it, as clients merge them), in the
 * athlete's order, keeping only items that still exist and that they may see themselves. Photo links of non-public items are signed for
 * [mediaViewer].
 */
internal fun JdbcTransaction.gearOf(
    activities: List<String>,
    viewer: Did?,
    urls: MediaUrls,
    mediaViewer: Did?,
): Map<String, List<GearView>> =
    ActivityGear.join(GearItems, JoinType.INNER, ActivityGear.gearUri, GearItems.uri)
        .select(
            ActivityGear.activityUri,
            ActivityGear.origin,
            ActivityGear.listedIn,
            ActivityGear.position,
            ActivityGear.visibility,
            GearItems.uri,
            GearItems.authorDid,
            GearItems.visibility,
            GearItems.name,
            GearItems.type,
            GearItems.kind,
            GearItems.brand,
            GearItems.model,
            GearItems.photoCid,
        )
        .where {
            (ActivityGear.activityUri inList activities) and
                (GearItems.authorDid eq ActivityGear.authorDid) and
                visibleTo(viewer, ActivityGear.visibility, ActivityGear.authorDid, ActivityGear.activityUri, null) and
                visibleTo(viewer, GearItems.visibility, GearItems.authorDid, GearItems.uri, GearItems.cid)
        }
        .toList()
        .groupBy { it[ActivityGear.activityUri] }
        .mapValues { (_, rows) ->
            val narrowest = rows.maxBy { Visibility.rank(it[ActivityGear.visibility]) }
            rows
                .filter {
                    it[ActivityGear.origin] == narrowest[ActivityGear.origin] &&
                        it[ActivityGear.listedIn] == narrowest[ActivityGear.listedIn]
                }
                .sortedBy { it[ActivityGear.position] }
                .map { it.gearView(urls, mediaViewer) }
        }
        .filterValues { it.isNotEmpty() }

/** The [GearItems] row's `gearView`, its photo link signed for [mediaViewer] when the item isn't public. */
internal fun ResultRow.gearView(urls: MediaUrls, mediaViewer: Did?): GearView {
    val author = this[GearItems.authorDid]
    val photo =
        this[GearItems.photoCid]?.let { cid ->
            if (this[GearItems.visibility] == Visibility.PUBLIC) urls.url(author, cid)
            else mediaViewer?.let { urls.privateUrl(author, cid, it) }
        }
    return GearView(
        uri = AtUri(this[GearItems.uri]),
        name = this[GearItems.name],
        type = this[GearItems.type],
        kind = this[GearItems.kind],
        brand = this[GearItems.brand],
        model = this[GearItems.model],
        photo = photo,
    )
}

/**
 * [view] with only the [gear] URIs on its record and details records: a viewer never learns of an item they may not see, and a list with
 * nothing left is dropped.
 */
internal fun ActivityView.withGearOnly(gear: List<GearView>?): ActivityView {
    val visible = gear.orEmpty().map { it.uri }.toSet()
    fun List<AtUri>?.kept() = this?.filter { it in visible }?.ifEmpty { null }
    return copy(
        record = record.copy(gear = record.gear.kept()),
        details = details?.map { it.copy(record = it.record.copy(gear = it.record.gear.kept())) },
        gear = gear?.ifEmpty { null },
    )
}

/** The item at [uri] if [viewer] may see it; `NotFound` otherwise, as if it didn't exist. */
internal fun JdbcTransaction.requireVisibleGear(uri: String, viewer: Did?): ResultRow =
    GearItems.selectAll().where { (GearItems.uri eq uri) and gearVisibleTo(viewer) }.singleOrNull()
        ?: throw XrpcError(HttpStatusCode.BadRequest.value, "NotFound", "Gear not found")

private fun gearVisibleTo(viewer: Did?) = visibleTo(viewer, GearItems.visibility, GearItems.authorDid, GearItems.uri, GearItems.cid)

/**
 * The gear references [viewer] may see, each paired with an item it counts for, among the items [items] picks (given that item's column):
 * the activity record's or a details record's in a tier of the Gear category they're admitted to (always, for the athlete). An item counts
 * the activities listing it ([listedRefs]) and those listing an item it was mounted on at their start, up to [GearMounts.MAX_DEPTH] levels
 * up ([mountedRefs]). One row per activity and item, since an activity can reach an item more than one way (from two origins mid-move, or
 * listing a bike and its power meter), so each activity counts once.
 */
private fun visibleRefs(viewer: Did?, items: (Column<String>) -> Op<Boolean>): QueryAlias {
    var refs = listedRefs(viewer, items).union(mountedRefs(viewer, items, 1))
    for (depth in 2..GearMounts.MAX_DEPTH) refs = refs.union(mountedRefs(viewer, items, depth))
    return refs.alias("refs")
}

private fun listedRefs(viewer: Did?, items: (Column<String>) -> Op<Boolean>): Query =
    ActivityGear.select(ActivityGear.activityUri, ActivityGear.gearUri).where { items(ActivityGear.gearUri) and refVisibleTo(viewer) }

/**
 * The references that reach an item [depth] mounts below the item they list (`gear/gear-components`): each mount's window holds the
 * activity's start, and for anyone but the athlete each item above the counted one is one they may see. A mount whose parent was deleted
 * reaches nothing.
 */
private fun mountedRefs(viewer: Did?, items: (Column<String>) -> Op<Boolean>, depth: Int): Query {
    val listed = GearItems.alias("listed")
    var join =
        ActivityGear.join(Activities, JoinType.INNER, ActivityGear.activityUri, Activities.uri)
            .join(listed, JoinType.INNER, ActivityGear.gearUri, listed[GearItems.uri])
    var where = refVisibleTo(viewer) and aliasVisibleTo(viewer, listed)
    var parent: Column<String> = ActivityGear.gearUri
    for (level in 1..depth) {
        val mount = GearMountRows.alias("mount$level")
        join = join.join(mount, JoinType.INNER, parent, mount[GearMountRows.parentUri])
        where =
            where and
                (Activities.startedAt greaterEq mount[GearMountRows.mountedFrom]) and
                (mount[GearMountRows.mountedTo].isNull() or (Activities.startedAt less mount[GearMountRows.mountedTo]))
        parent = mount[GearMountRows.componentUri]
        if (level < depth) {
            val between = GearItems.alias("between$level")
            join = join.join(between, JoinType.INNER, parent, between[GearItems.uri])
            where = where and aliasVisibleTo(viewer, between)
        }
    }
    val counted = parent
    return join.select(ActivityGear.activityUri, counted).where { items(counted) and where }
}

private fun refVisibleTo(viewer: Did?) = visibleTo(viewer, ActivityGear.visibility, ActivityGear.authorDid, ActivityGear.activityUri, null)

private fun aliasVisibleTo(viewer: Did?, item: Alias<GearItems>) =
    visibleTo(viewer, item[GearItems.visibility], item[GearItems.authorDid], item[GearItems.uri], item[GearItems.cid])

/**
 * The activities counted in usage of the items [items] picks, for [viewer], joined to their references ([refs]): each one they may see that
 * lists an item in a record they may see, or an item it was mounted on, except a recording combined into another activity, which counts
 * only through that one (`countedFor` in the design). The totals, breakdowns and `getGearActivities` all read it, so the numbers match the
 * list.
 */
private fun counted(viewer: Did?, items: (Column<String>) -> Op<Boolean>): Pair<QueryAlias, Join> {
    val refs = visibleRefs(viewer, items)
    return refs to Activities.join(refs, JoinType.INNER, Activities.uri, refs[ActivityGear.activityUri])
}

private fun countedWhere(viewer: Did?): Op<Boolean> = visibleTo(viewer) and not(isMember())

/** The activities counted in [gear]'s usage for [viewer], newest first by start: `getGearActivities`'s page after [cursor]. */
internal fun countedActivities(viewer: Did?, gear: String, cursor: String?, pageSize: Int): Query {
    val (_, join) = counted(viewer) { it eq gear }
    return join
        .select(Activities.columns)
        .where { countedWhere(viewer) and keysetBefore(Activities.startedAt, Activities.uri, cursor) }
        .orderBy(Activities.startedAt to SortOrder.DESC, Activities.uri to SortOrder.DESC)
        .limit(pageSize)
}

private val isDive = Activities.sport eq Sport.DIVE
private val activityCount = Activities.uri.count()
private val movingTimeSum = Coalesce(Activities.movingTime, Activities.elapsedTime).sum()
private val distanceSum = Activities.distance.sum()
private val gainSum = Activities.elevationGain.sum()
private val lossSum = Activities.elevationLoss.sum()
private val dayCount = Activities.startedOn.countDistinct()
private val diveCount = Case().When(isDive, longLiteral(1)).Else(longLiteral(0)).sum()
private val bottomTimeSum = Case().When(isDive, Coalesce(Activities.diveBottomTime, longLiteral(0))).Else(longLiteral(0)).sum()
private val aggregates: List<Expression<*>> =
    listOf(activityCount, movingTimeSum, distanceSum, gainSum, lossSum, dayCount, diveCount, bottomTimeSum)

private fun ResultRow.totals(): GearTotals {
    val dives = this[diveCount] ?: 0
    return GearTotals(
        activities = this[activityCount],
        movingTime = this[movingTimeSum] ?: 0,
        distance = this[distanceSum] ?: 0,
        elevationGain = this[gainSum] ?: 0,
        elevationLoss = this[lossSum] ?: 0,
        days = this[dayCount],
        dives = dives.takeIf { it > 0 },
        bottomTime = this[bottomTimeSum]?.takeIf { dives > 0 },
    )
}

/** [viewer]'s totals of the items [items] picks, grouped by item and [by], in one query: each item's URI with its row. */
private fun JdbcTransaction.grouped(
    viewer: Did?,
    items: (Column<String>) -> Op<Boolean>,
    by: List<Expression<*>> = emptyList(),
): List<Pair<String, ResultRow>> {
    val (refs, join) = counted(viewer, items)
    val gear = refs[ActivityGear.gearUri]
    val keys: List<Expression<*>> = listOf(gear) + by
    // Each groupBy adds its columns to the query's.
    return keys
        .fold(join.select(keys + aggregates).where { countedWhere(viewer) }) { query, key -> query.groupBy(key) }
        .map { it[gear] to it }
}

/** The item's starting usage, from its [GearItems] row; null when it has none. */
private fun ResultRow.starting(): GearStarting? {
    val distance = this[GearItems.startingDistance]
    val time = this[GearItems.startingTime]
    return if (distance == null && time == null) null else GearStarting(distance, time)
}

private fun GearTotals.plus(starting: GearStarting?): GearTotals =
    if (starting == null) this else copy(distance = distance + (starting.distance ?: 0), movingTime = movingTime + (starting.time ?: 0))

/** The usage of [item] (a row [requireVisibleGear] returned) for [viewer]: totals with its starting usage, and both breakdowns without. */
internal fun JdbcTransaction.gearUsage(item: ResultRow, viewer: Did?): GearUsage {
    val uri = item[GearItems.uri]
    val gear: (Column<String>) -> Op<Boolean> = { it eq uri }
    val starting = item.starting()
    val totals = grouped(viewer, gear).singleOrNull()?.second?.totals() ?: GearTotals.ZERO
    val bySport =
        grouped(viewer, gear, listOf(Activities.sport, Activities.subSport))
            .map { (_, it) -> GearSportTotals(it[Activities.sport], it.totals(), it[Activities.subSport]) }
            .sortedWith(compareByDescending<GearSportTotals> { it.totals.activities }.thenBy { it.sport }.thenBy { it.subSport.orEmpty() })
    val byYear =
        grouped(viewer, gear, listOf(Activities.startedYear))
            .mapNotNull { (_, row) -> row[Activities.startedYear]?.let { GearYearTotals(it, row.totals()) } }
            .sortedByDescending { it.year }
    return GearUsage(totals = totals.plus(starting), bySport = bySport, byYear = byYear, starting = starting)
}

/** Each of [items]' totals for [viewer], with its starting usage, in one grouped query; an item nothing counts for has none counted. */
internal fun JdbcTransaction.gearTotals(items: List<ResultRow>, viewer: Did?): Map<String, GearTotals> {
    if (items.isEmpty()) return emptyMap()
    val counted =
        items.map { it[GearItems.uri] }.let { uris -> grouped(viewer, { it inList uris }) }.associate { (uri, row) -> uri to row.totals() }
    return items.associate { it[GearItems.uri] to (counted[it[GearItems.uri]] ?: GearTotals.ZERO).plus(it.starting()) }
}

/**
 * The items mounted on [parent] that [viewer] may see (`gear/gear-components`), each with its totals for them: those mounted now first,
 * oldest mount first, then those mounted before, latest end first. An item mounted on it twice is listed once per mount.
 */
internal fun JdbcTransaction.gearComponents(parent: String, viewer: Did?, urls: MediaUrls): List<GearComponent> {
    val rows =
        GearMountRows.join(GearItems, JoinType.INNER, GearMountRows.componentUri, GearItems.uri)
            .selectAll()
            .where { (GearMountRows.parentUri eq parent) and gearVisibleTo(viewer) }
            .toList()
    if (rows.isEmpty()) return emptyList()
    val totals = gearTotals(rows.distinctBy { it[GearItems.uri] }, viewer)
    val (current, previous) = rows.partition { it[GearMountRows.mountedTo] == null }
    return (current.sortedBy { it[GearMountRows.mountedFrom] } + previous.sortedByDescending { it[GearMountRows.mountedTo] }).map {
        GearComponent(
            gear = it.gearView(urls, viewer),
            from = Instant.fromEpochMilliseconds(it[GearMountRows.mountedFrom]),
            totals = totals.getValue(it[GearItems.uri]),
            to = it[GearMountRows.mountedTo]?.let(Instant::fromEpochMilliseconds),
        )
    }
}

/** The periods [component] was mounted on a parent [viewer] may see, oldest first; a parent not indexed (or deleted) isn't listed. */
internal fun JdbcTransaction.gearMountViews(component: String, viewer: Did?, urls: MediaUrls): List<GearMountView> =
    GearMountRows.join(GearItems, JoinType.INNER, GearMountRows.parentUri, GearItems.uri)
        .selectAll()
        .where { (GearMountRows.componentUri eq component) and gearVisibleTo(viewer) }
        .orderBy(GearMountRows.mountedFrom to SortOrder.ASC)
        .map {
            GearMountView(
                parent = it.gearView(urls, viewer),
                from = Instant.fromEpochMilliseconds(it[GearMountRows.mountedFrom]),
                to = it[GearMountRows.mountedTo]?.let(Instant::fromEpochMilliseconds),
            )
        }

/**
 * A page of [author]'s items [viewer] may see, those in use first, then by name and URI: a keyset after [cursor], `"<0 in use|1
 * retired>::<uri>::<name>"` (the name last, since it may hold the separator).
 */
internal fun JdbcTransaction.actorGearPage(author: String, viewer: Did?, cursor: String?, pageSize: Int): List<ResultRow> {
    val retired = Case().When(GearItems.retiredAt.isNull(), longLiteral(0)).Else(longLiteral(1))
    return GearItems.selectAll()
        .where { (GearItems.authorDid eq author) and gearVisibleTo(viewer) and gearAfter(cursor) }
        .orderBy(retired to SortOrder.ASC, GearItems.name to SortOrder.ASC, GearItems.uri to SortOrder.ASC)
        .limit(pageSize)
        .toList()
}

/** The cursor [actorGearPage] reads for [row]. */
internal fun actorGearCursor(row: ResultRow): String =
    "${if (row[GearItems.retiredAt] == null) 0 else 1}::${row[GearItems.uri]}::${row[GearItems.name]}"

private fun gearAfter(cursor: String?): Op<Boolean> {
    val parts = cursor?.split("::", limit = 3)?.takeIf { it.size == 3 && it[0] in setOf("0", "1") } ?: return Op.TRUE
    val (flag, uri, name) = parts
    val later = (GearItems.name greater name) or ((GearItems.name eq name) and (GearItems.uri greater uri))
    return if (flag == "0") (GearItems.retiredAt.isNull() and later) or GearItems.retiredAt.isNotNull()
    else GearItems.retiredAt.isNotNull() and later
}
