package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// Serving the gear an activity lists (`activityView.gear`), which `Views.hydrate` asks for a page at a time.

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
                .map { row ->
                    val author = row[GearItems.authorDid]
                    val photo =
                        row[GearItems.photoCid]?.let { cid ->
                            if (row[GearItems.visibility] == Visibility.PUBLIC) urls.url(author, cid)
                            else mediaViewer?.let { urls.privateUrl(author, cid, it) }
                        }
                    GearView(
                        uri = AtUri(row[GearItems.uri]),
                        name = row[GearItems.name],
                        type = row[GearItems.type],
                        kind = row[GearItems.kind],
                        brand = row[GearItems.brand],
                        model = row[GearItems.model],
                        photo = photo,
                    )
                }
        }
        .filterValues { it.isNotEmpty() }

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
