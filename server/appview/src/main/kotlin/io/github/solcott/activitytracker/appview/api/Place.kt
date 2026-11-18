package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// The rules segments and dive sites share as places athletes create, ride or dive, and search lists (segments/segment-records,
// dive-sites/site-records): which are served to a viewer, which search lists to them, and NotFound for one that isn't served.

/**
 * A table of places and the columns the shared rules read. [used] is whether an athlete has an effort on, or a visit to, the outer query's
 * place, which lists a proposed place to them; [notFound] is the error a place that isn't served answers. The table is [uri]'s.
 */
internal class Place(
    val uri: Column<String>,
    val cid: Column<String>,
    val author: Column<String>,
    val status: Column<String>,
    val used: (athlete: String) -> Op<Boolean>,
    val notFound: () -> XrpcError,
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
