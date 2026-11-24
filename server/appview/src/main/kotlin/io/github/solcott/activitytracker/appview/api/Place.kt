package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.DiveSiteCreations
import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.SegmentCreations
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.index.CREATION_WINDOW_MS
import io.github.solcott.activitytracker.appview.index.DAILY_DIVE_SITE_LIMIT
import io.github.solcott.activitytracker.appview.index.DAILY_SEGMENT_LIMIT
import io.github.solcott.activitytracker.appview.segments.isImporter
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select

// The rules segments and dive sites share as places athletes create, ride or dive, and search lists (segments/segment-records,
// dive-sites/site-records): which are served to a viewer, which search lists to them, and NotFound for one that isn't served.

/**
 * A log of each creator's new places, for the daily limit of [dailyLimit] per [CREATION_WINDOW_MS] ([countCreation]): the [author], the new
 * place's [place] URI and when it was indexed ([at]). A delete doesn't touch it, so deleting a place frees no slot; the segment importer
 * isn't limited. [noun] names the places in the error past the limit.
 */
internal class Creations(
    val author: Column<String>,
    val place: Column<String>,
    val at: Column<Long>,
    val dailyLimit: Long,
    val noun: String,
)

/**
 * A table of places and the columns the shared rules read. [used] is whether an athlete has an effort on, or a visit to, the outer query's
 * place, which lists a proposed place to them; [notFound] is the error a place that isn't served answers; [creations] logs new ones for the
 * daily limit. The table is [uri]'s.
 */
@Suppress("LongParameterList") // The columns and rules each kind of place supplies.
internal class Place(
    val uri: Column<String>,
    val cid: Column<String>,
    val author: Column<String>,
    val status: Column<String>,
    val used: (athlete: String) -> Op<Boolean>,
    val notFound: () -> XrpcError,
    val creations: Creations,
) {
    val table: Table
        get() = uri.table
}

internal val SegmentPlace =
    Place(
        Segments.uri,
        Segments.cid,
        Segments.authorDid,
        Segments.status,
        used = { athlete ->
            exists(
                SegmentEfforts.select(SegmentEfforts.id).where {
                    (SegmentEfforts.segmentUri eq Segments.uri) and (SegmentEfforts.authorDid eq athlete)
                },
            )
        },
        notFound = { notFound("Segment not found") },
        creations =
            Creations(SegmentCreations.authorDid, SegmentCreations.segmentUri, SegmentCreations.createdAt, DAILY_SEGMENT_LIMIT, "segments"),
    )

internal val DiveSitePlace =
    Place(
        DiveSites.uri,
        DiveSites.cid,
        DiveSites.authorDid,
        DiveSites.status,
        used = { athlete ->
            exists(
                DiveSiteVisits.select(DiveSiteVisits.activityUri).where {
                    (DiveSiteVisits.siteUri eq DiveSites.uri) and (DiveSiteVisits.authorDid eq athlete)
                },
            )
        },
        notFound = { notFound("Dive site not found") },
        creations =
            Creations(
                DiveSiteCreations.authorDid,
                DiveSiteCreations.siteUri,
                DiveSiteCreations.createdAt,
                DAILY_DIVE_SITE_LIMIT,
                "dive sites",
            ),
    )

/** Places served to [viewer]: not of an account that isn't served, nor taken down ([served]). */
internal fun Place.servedTo(viewer: Did?): Op<Boolean> = served(viewer, author, uri, cid)

/** Places search lists to [viewer]: active ones, and proposed ones they created or have used ([Place.used]). */
internal fun Place.listedTo(viewer: Did?): Op<Boolean> {
    val active = status eq SegmentStatus.ACTIVE
    if (viewer == null) return active
    return active or ((status eq SegmentStatus.PROPOSED) and ((author eq viewer.value) or used(viewer.value)))
}

/** The status of the place at [place] when it's served to [viewer] ([servedTo]); [Place.notFound] when it isn't. */
internal fun JdbcTransaction.requireServedPlace(target: Place, viewer: Did?, place: String): String =
    target.table.select(target.status).where { (target.uri eq place) and target.servedTo(viewer) }.firstOrNull()?.get(target.status)
        ?: throw target.notFound()

/**
 * How many places [author] had indexed new (or, for segments, re-lined) in the [CREATION_WINDOW_MS] before [now], deleted ones included.
 */
internal fun JdbcTransaction.createdSince(target: Place, author: Did, now: Long): Long {
    val log = target.creations
    return log.author.table.select(log.at).where { (log.author eq author.value) and (log.at greater now - CREATION_WINDOW_MS) }.count()
}

/** Counts [author]'s new place at [uri] against their daily allowance, pruning entries past the window; throws once it's used up. */
internal fun JdbcTransaction.countCreation(target: Place, author: Did, uri: String, now: Long) {
    val log = target.creations
    require(createdSince(target, author, now) < log.dailyLimit) { "over the daily limit of new ${log.noun}" }
    log.author.table.deleteWhere { (log.author eq author.value) and (log.at lessEq now - CREATION_WINDOW_MS) }
    log.author.table.insert {
        it[log.author] = author.value
        it[log.place] = uri
        it[log.at] = now
    }
}

/** How many more new places [creator] may create today: always the whole allowance for the segment [importer]. */
internal fun JdbcTransaction.remainingCreations(target: Place, creator: Did, importer: Did?, now: Long): Int {
    val created = if (importer.isImporter(creator)) 0 else createdSince(target, creator, now)
    return (target.creations.dailyLimit - created).coerceAtLeast(0).toInt()
}
