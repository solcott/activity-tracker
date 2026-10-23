@file:Suppress("TooManyFunctions") // activityView.gear, and gear usage with the counted-activities rule its three queries share.

package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearSportTotals
import io.github.solcott.activitytracker.lexicon.app.GearStarting
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GearYearTotals
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Coalesce
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
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

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
 * The gear references [viewer] may see among those [items] picks: the activity record's or a details record's in a tier of the Gear
 * category they're admitted to (always, for the athlete). One row per activity and item, since mid-move an item is listed from two origins,
 * so each activity counts once.
 */
private fun visibleRefs(viewer: Did?, items: Op<Boolean>): QueryAlias =
    ActivityGear.select(ActivityGear.activityUri, ActivityGear.gearUri)
        .where { items and visibleTo(viewer, ActivityGear.visibility, ActivityGear.authorDid, ActivityGear.activityUri, null) }
        .withDistinct()
        .alias("refs")

/**
 * The activities counted in usage of the items [items] picks, for [viewer], joined to their references ([refs]): each one they may see that
 * lists an item in a record they may see, except a recording combined into another activity, which counts only through that one
 * (`countedFor` in the design). The totals, breakdowns and `getGearActivities` all read it, so the numbers match the list.
 */
private fun counted(viewer: Did?, items: Op<Boolean>): Pair<QueryAlias, Join> {
    val refs = visibleRefs(viewer, items)
    return refs to Activities.join(refs, JoinType.INNER, Activities.uri, refs[ActivityGear.activityUri])
}

private fun countedWhere(viewer: Did?): Op<Boolean> = visibleTo(viewer) and not(isMember())

/** The activities counted in [gear]'s usage for [viewer], newest first by start: `getGearActivities`'s page after [cursor]. */
internal fun countedActivities(viewer: Did?, gear: String, cursor: String?, pageSize: Int): Query {
    val (_, join) = counted(viewer, ActivityGear.gearUri eq gear)
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
    items: Op<Boolean>,
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
    val gear = ActivityGear.gearUri eq item[GearItems.uri]
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
        grouped(viewer, ActivityGear.gearUri inList items.map { it[GearItems.uri] }).associate { (uri, row) -> uri to row.totals() }
    return items.associate { it[GearItems.uri] to (counted[it[GearItems.uri]] ?: GearTotals.ZERO).plus(it.starting()) }
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
