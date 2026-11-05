@file:Suppress("TooManyFunctions") // Indexing dive sites: validation, the creation limit, statuses and duplicates, visits, deletes.

package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.DiveSiteCells
import io.github.solcott.activitytracker.appview.db.DiveSiteCreations
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.deleteSegmentNames
import io.github.solcott.activitytracker.appview.segments.recomputeCurrentName
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSite
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentCells
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.diveSite` records (dive-sites/site-records), from the repo only.

/** How many new dive sites one creator may have indexed in any [CREATION_WINDOW_MS]; the segment importer isn't limited. */
internal const val DAILY_DIVE_SITE_LIMIT = 10L

private const val E7 = 1e7
private const val MAX_LAT_E7 = 900_000_000
private const val MAX_LNG_E7 = 1_800_000_000

/** The point of [lat] and [lng], in 1e-7 degrees. */
internal fun pointE7(lat: Int, lng: Int) = GeoPoint(lat / E7, lng / E7)

/** Whether [lat] and [lng], in 1e-7 degrees, are a valid position. */
internal fun validPositionE7(lat: Int, lng: Int): Boolean = lat in -MAX_LAT_E7..MAX_LAT_E7 && lng in -MAX_LNG_E7..MAX_LNG_E7

/** A site row's centre. */
internal fun ResultRow.siteCentre() = pointE7(this[DiveSites.latE7], this[DiveSites.lngE7])

/**
 * A dive site from [author]'s repo. Indexed only with a valid name, centre and radius ([DiveSiteRecord.MIN_RADIUS] to
 * [DiveSiteRecord.MAX_RADIUS]) and a known source; anything else is dropped like an undecodable record, as is a new site past its creator's
 * [DAILY_DIVE_SITE_LIMIT] (new ones in the last day, deleted ones included), unless [author] is the segment [importer]. A new or moved site
 * gets its status again (duplicate of the earliest live site within [CheckDiveSite.DUPLICATE_METERS] of its centre, else proposed), and a
 * new, moved or re-sized one loses its visits, which are matched again with the dives near it; the sites that duplicated its old centre are
 * checked again. A rename keeps its status: an adopted community name in effect stays its current name.
 */
@Suppress("LongParameterList", "LongMethod") // The record, and where it's from; then each part of indexing it.
internal fun JdbcTransaction.upsertDiveSite(
    json: JsonObject,
    author: Did,
    uri: AtUri,
    cid: String,
    jobs: SegmentJobs,
    now: Long,
    importer: Did? = null,
) {
    val record = AtJson.decodeFromJsonElement(DiveSiteRecord.serializer(), json)
    val name = validName(record.name)
    requireValidSite(record)
    val attribution = runCatching { validAttribution(record.attribution) }.getOrNull()

    val old = DiveSites.selectAll().where { DiveSites.uri eq uri.value }.firstOrNull()
    if (old == null && author != importer) countCreation(author, uri, now)
    val centre = pointE7(record.lat, record.lng)
    val moved = old == null || old[DiveSites.latE7] != record.lat || old[DiveSites.lngE7] != record.lng
    val resized = moved || old[DiveSites.radiusM] != record.radius
    val firstIndexed = old?.get(DiveSites.firstIndexedAt) ?: now
    val original = if (moved) diveSiteDuplicatesOf(centre, before = firstIndexed to uri.value).firstOrNull() else null
    val (status, duplicate) = siteStatusAfter(old, moved, original)
    DiveSites.upsert {
        it[DiveSites.uri] = uri.value
        it[DiveSites.cid] = cid
        it[authorDid] = author.value
        it[DiveSites.name] = name.take(MAX_NAME_LENGTH)
        it[currentName] = name.take(MAX_NAME_LENGTH)
        it[DiveSites.record] = AtJson.encodeToString(DiveSiteRecord.serializer(), record.copy(name = name, attribution = attribution))
        it[latE7] = record.lat
        it[lngE7] = record.lng
        it[radiusM] = record.radius
        it[dataSource] = record.source.take(MAX_TOKEN_LENGTH)
        it[license] = record.license.take(MAX_TOKEN_LENGTH)
        it[DiveSites.status] = status
        it[duplicateOf] = duplicate
        it[firstIndexedAt] = firstIndexed
        it[indexedAt] = now
    }
    recomputeCurrentName(uri.value)
    if (!resized) return
    replaceDiveSiteCells(uri.value, centre, record.radius)
    rematchVisitsOf(listOf(uri.value), jobs, now)
    if (original == null) jobs.enqueueSite(uri.value, now)
    if (old != null && moved) recheckDiveSiteDuplicatesOf(listOf(uri.value), jobs, now)
}

/**
 * A site's status and the site it duplicates after an upsert: derived again for a new or [moved] one (duplicate of [original], else
 * proposed), kept from [old] otherwise.
 */
private fun siteStatusAfter(old: ResultRow?, moved: Boolean, original: String?): Pair<String, String?> =
    when {
        old == null || moved -> (if (original != null) SegmentStatus.DUPLICATE else SegmentStatus.PROPOSED) to original
        else -> old[DiveSites.status] to old[DiveSites.duplicateOf]
    }

/** Throws for a site indexing drops: a centre off the globe, a radius out of bounds, or an unknown source. */
private fun requireValidSite(record: DiveSiteRecord) {
    require(validPositionE7(record.lat, record.lng)) { "a centre out of range" }
    require(record.radius in DiveSiteRecord.MIN_RADIUS..DiveSiteRecord.MAX_RADIUS) { "a radius of ${record.radius} m" }
    require(record.source in KNOWN_SOURCES) { "unknown source ${record.source}" }
}

/** Counts [author]'s new site at [uri] against their daily allowance; throws once it's used up. */
private fun JdbcTransaction.countCreation(author: Did, uri: AtUri, now: Long) {
    require(diveSitesCreatedSince(author, now) < DAILY_DIVE_SITE_LIMIT) { "over the daily limit of new dive sites" }
    DiveSiteCreations.deleteWhere { (authorDid eq author.value) and (createdAt lessEq now - CREATION_WINDOW_MS) }
    DiveSiteCreations.insert {
        it[authorDid] = author.value
        it[siteUri] = uri.value
        it[createdAt] = now
    }
}

private val KNOWN_SOURCES = setOf(SegmentSource.ATHLETE, SegmentSource.OSM, SegmentSource.AGENCY)

/** How many dive sites [author] had indexed new in the [CREATION_WINDOW_MS] before [now], deleted ones included. */
internal fun JdbcTransaction.diveSitesCreatedSince(author: Did, now: Long): Long =
    DiveSiteCreations.select(DiveSiteCreations.createdAt)
        .where { (DiveSiteCreations.authorDid eq author.value) and (DiveSiteCreations.createdAt greater now - CREATION_WINDOW_MS) }
        .count()

/**
 * The live ([LIVE_STATUSES]) dive sites whose centre lies within [CheckDiveSite.DUPLICATE_METERS] of [centre], earliest first indexed
 * first. With [before], a site's own first-indexed time and URI, only those that came before it, so a site never duplicates itself or a
 * later one.
 */
internal fun JdbcTransaction.diveSiteDuplicatesOf(centre: GeoPoint, before: Pair<Long, String>? = null): List<String> {
    val near = SegmentCells.cellsOf(centre, CheckDiveSite.DUPLICATE_METERS.toDouble())
    val inCells =
        DiveSiteCells.select(DiveSiteCells.siteUri).where { DiveSiteCells.cell inList near }.map { it[DiveSiteCells.siteUri] }.toSet()
    if (inCells.isEmpty()) return emptyList()
    return DiveSites.selectAll()
        .where { (DiveSites.uri inList inCells) and (DiveSites.status inList LIVE_STATUSES) }
        .orderBy(DiveSites.firstIndexedAt to SortOrder.ASC, DiveSites.uri to SortOrder.ASC)
        .filter { row ->
            before == null ||
                row[DiveSites.firstIndexedAt] < before.first ||
                (row[DiveSites.firstIndexedAt] == before.first && row[DiveSites.uri] < before.second)
        }
        .filter { SegmentGeometry.distanceMeters(it.siteCentre(), centre) <= CheckDiveSite.DUPLICATE_METERS }
        .map { it[DiveSites.uri] }
}

/**
 * Checks again each site marked duplicate of one of [originals], which was deleted, archived or moved, earliest first indexed first, so
 * each in turn sees the ones before it settled. One that duplicates no other live site becomes proposed and is matched with the dives near
 * it.
 */
internal fun JdbcTransaction.recheckDiveSiteDuplicatesOf(originals: List<String>, jobs: SegmentJobs, now: Long) {
    if (originals.isEmpty()) return
    val duplicates =
        DiveSites.selectAll()
            .where { (DiveSites.status eq SegmentStatus.DUPLICATE) and (DiveSites.duplicateOf inList originals) }
            .orderBy(DiveSites.firstIndexedAt to SortOrder.ASC, DiveSites.uri to SortOrder.ASC)
            .toList()
    for (row in duplicates) {
        val uri = row[DiveSites.uri]
        val original = diveSiteDuplicatesOf(row.siteCentre(), before = row[DiveSites.firstIndexedAt] to uri).firstOrNull()
        DiveSites.update({ DiveSites.uri eq uri }) {
            it[status] = if (original != null) SegmentStatus.DUPLICATE else SegmentStatus.PROPOSED
            it[duplicateOf] = original
        }
        if (original == null) jobs.enqueueSite(uri, now)
    }
}

/**
 * Drops the visits to [sites] and queues their dives' tracks to be matched again, since each may now visit another site containing its
 * entry.
 */
internal fun JdbcTransaction.rematchVisitsOf(sites: List<String>, jobs: SegmentJobs, now: Long) {
    if (sites.isEmpty()) return
    val tracks =
        DiveSiteVisits.select(DiveSiteVisits.trackUri).where { DiveSiteVisits.siteUri inList sites }.map { it[DiveSiteVisits.trackUri] }
    DiveSiteVisits.deleteWhere { siteUri inList sites }
    tracks.distinct().forEach { jobs.enqueueTrack(it, now) }
}

/** A deleted dive site: it, its cells, its visits and its name proposals, their votes and history go; its duplicates are checked again. */
internal fun JdbcTransaction.deleteDiveSite(uri: AtUri, jobs: SegmentJobs, now: Long) = deleteDiveSites(listOf(uri.value), jobs, now)

/** [author]'s dive sites, for a deleted account. */
internal fun JdbcTransaction.deleteDiveSitesBy(author: Did, jobs: SegmentJobs, now: Long) =
    deleteDiveSites(DiveSites.select(DiveSites.uri).where { DiveSites.authorDid eq author.value }.map { it[DiveSites.uri] }, jobs, now)

private fun JdbcTransaction.deleteDiveSites(uris: List<String>, jobs: SegmentJobs, now: Long) {
    if (uris.isEmpty()) return
    rematchVisitsOf(uris, jobs, now)
    deleteSegmentNames(uris)
    DiveSiteCells.deleteWhere { siteUri inList uris }
    DiveSites.deleteWhere { DiveSites.uri inList uris }
    recheckDiveSiteDuplicatesOf(uris, jobs, now)
}

private fun JdbcTransaction.replaceDiveSiteCells(uri: String, centre: GeoPoint, radius: Int) {
    DiveSiteCells.deleteWhere { siteUri eq uri }
    DiveSiteCells.batchInsert(SegmentCells.cellsOf(centre, radius.toDouble())) {
        this[DiveSiteCells.cell] = it
        this[DiveSiteCells.siteUri] = uri
    }
}
