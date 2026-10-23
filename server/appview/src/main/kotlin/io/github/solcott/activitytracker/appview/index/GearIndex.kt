package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.GearItems
import io.github.solcott.activitytracker.appview.db.GearMountRows
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearMounts
import io.github.solcott.activitytracker.lexicon.app.MountGraph
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `gear.item` records and of the gear activities list, which the Indexer hands off here.

/**
 * [placement]'s copy of a gear item: a blank or over-long name or type drops it (like any invalid record), its other text is clamped and
 * its photo kept only when the lexicon allows it. It's placed by origin like an activity ([place]), so mid-move the narrower copy is the
 * row.
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertGear(author: Did, uri: AtUri, cid: String, json: JsonObject, placement: Placement, now: Long) {
    val record = AtJson.decodeFromJsonElement(GearItemRecord.serializer(), json)
    require(record.name.isNotBlank() && record.name.length <= MAX_GEAR_NAME_LENGTH) { "gear name" }
    require(record.type.isNotBlank() && record.type.length <= MAX_GEAR_TOKEN_LENGTH) { "gear type" }
    val photo = boundedPhotos(listOfNotNull(record.photo))?.firstOrNull()
    place(Nsid.GEAR_ITEM, uri, cid, json, placement, placement.visibility, now) {
        GearItems.upsert {
            it[GearItems.uri] = uri.value
            it[origin] = placement.origin
            it[GearItems.cid] = cid
            it[authorDid] = author.value
            it[visibility] = placement.visibility
            it[name] = record.name
            it[type] = record.type
            it[kind] = record.kind?.take(MAX_GEAR_TOKEN_LENGTH)
            it[brand] = record.brand?.take(MAX_GEAR_BRAND_LENGTH)
            it[model] = record.model?.take(MAX_GEAR_BRAND_LENGTH)
            it[photoCid] = photo?.image?.ref?.link
            it[photoMime] = photo?.image?.mimeType
            it[retiredAt] = record.retiredAt?.toEpochMilliseconds()
            it[startingDistance] = record.startingDistance?.takeIf { d -> d >= 0 }
            it[startingTime] = record.startingTime?.takeIf { t -> t >= 0 }
            it[GearItems.record] = AtJson.encodeToString(JsonObject.serializer(), json)
            it[indexedAt] = now
        }
        replaceGearMounts(author, uri, record.mounts)
    }
}

/** Deletes the held copy of the gear item at [uri] and its mounts. */
internal fun JdbcTransaction.deleteHeldGear(uri: AtUri) {
    GearItems.deleteWhere { GearItems.uri eq uri.value }
    GearMountRows.deleteWhere { componentUri eq uri.value }
}

/**
 * Replaces [component]'s rows in [GearMountRows] with the [mounts] [GearMounts] keeps against those already indexed: each refused one is
 * dropped, not the item. Untrusted: URIs that don't fit the column are dropped first, and at most [GearItemRecord.MAX_MOUNTS] are read.
 */
internal fun JdbcTransaction.replaceGearMounts(author: Did, component: AtUri, mounts: List<GearMount>?) {
    GearMountRows.deleteWhere { componentUri eq component.value }
    val bounded = mounts.orEmpty().take(GearItemRecord.MAX_MOUNTS).filter { it.parent.value.length <= MAX_GEAR_URI_LENGTH }
    if (bounded.isEmpty()) return
    val graph =
        MountGraph(
            parentsOf = { item ->
                GearMountRows.select(GearMountRows.parentUri)
                    .where { GearMountRows.componentUri eq item.value }
                    .map { AtUri(it[GearMountRows.parentUri]) }
            },
            childrenOf = { item ->
                GearMountRows.select(GearMountRows.componentUri)
                    .where { GearMountRows.parentUri eq item.value }
                    .map { AtUri(it[GearMountRows.componentUri]) }
            },
        )
    GearMountRows.batchInsert(GearMounts.kept(component, bounded, graph)) { mount ->
        this[GearMountRows.componentUri] = component.value
        this[GearMountRows.parentUri] = mount.parent.value
        this[GearMountRows.mountedFrom] = mount.from.toEpochMilliseconds()
        this[GearMountRows.mountedTo] = mount.to?.toEpochMilliseconds()
        this[GearMountRows.authorDid] = author.value
    }
}

/**
 * Replaces the gear [activity] lists in its [listing] record ([ActivityGear.SOURCE_RECORD] or [ActivityGear.SOURCE_DETAILS]) from
 * [placement]'s origin with [refs]. Untrusted: only URIs of its author's own `gear.item` records that fit the column count, once each, at
 * most [ActivityRecord.MAX_GEAR]. The activity record has one copy indexed, so its references replace every origin's.
 */
internal fun JdbcTransaction.replaceActivityGear(
    activity: AtUri,
    author: Did,
    placement: Placement,
    listing: String,
    refs: List<AtUri>?,
) {
    if (listing == ActivityGear.SOURCE_RECORD) {
        ActivityGear.deleteWhere { (activityUri eq activity.value) and (listedIn eq listing) }
    } else {
        ActivityGear.deleteWhere { (activityUri eq activity.value) and (listedIn eq listing) and (origin eq placement.origin) }
    }
    val kept =
        refs
            .orEmpty()
            .filter { it.authority == author.value && it.collection == Nsid.GEAR_ITEM && it.rkey != null }
            .map { it.value }
            .filter { it.length <= MAX_GEAR_URI_LENGTH }
            .distinct()
            .take(ActivityRecord.MAX_GEAR)
    ActivityGear.batchInsert(kept.withIndex()) { (position, gear) ->
        this[ActivityGear.activityUri] = activity.value
        this[ActivityGear.origin] = placement.origin
        this[ActivityGear.listedIn] = listing
        this[ActivityGear.gearUri] = gear
        this[ActivityGear.position] = position
        this[ActivityGear.authorDid] = author.value
        this[ActivityGear.visibility] = placement.visibility
    }
}

// Records are untrusted: text is held to the lexicon's maxLength, which is also the column size.
private const val MAX_GEAR_NAME_LENGTH = 1000
private const val MAX_GEAR_TOKEN_LENGTH = 64
private const val MAX_GEAR_BRAND_LENGTH = 1280
private const val MAX_GEAR_URI_LENGTH = 512
