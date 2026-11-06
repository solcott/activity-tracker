package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.DiveSiteVisits
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.segments.deleteEfforts
import io.github.solcott.activitytracker.lexicon.Did
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere

// What segments derived from segment tracks, dropped in one place (segments/segment-track).

/** Everything segments derived from [author]'s segment tracks ([dropSegmentUse]). */
internal fun JdbcTransaction.dropSegmentUseOf(author: Did) =
    dropSegmentUse(efforts = { authorDid eq author.value }, visits = { authorDid eq author.value }, riders = { authorDid eq author.value })

/** Everything segments derived from [activity] or its segment track, which shares its rkey ([dropSegmentUse]). */
internal fun JdbcTransaction.dropSegmentUseOfActivity(activity: String) {
    val track = trackOf(activity)
    dropSegmentUse(
        efforts = { (activityUri eq activity) or (trackUri eq track) },
        visits = { (activityUri eq activity) or (trackUri eq track) },
        riders = { trackUri eq track },
    )
}

/** Everything segments derived from the segment tracks at [uris] ([dropSegmentUse]). */
internal fun JdbcTransaction.dropSegmentUseOfTracks(uris: Collection<String>) {
    if (uris.isEmpty()) return
    dropSegmentUse(efforts = { trackUri inList uris }, visits = { trackUri inList uris }, riders = { trackUri inList uris })
}

/**
 * Drops what segments derived from segment tracks: the efforts matched from them ([deleteEfforts]), their dive site visits and their riders
 * of staged candidates. Every teardown of that goes through here, so a new table derived from tracks for segments is dropped in one place
 * and nothing outlives the consent, track or activity it came from (segments/segment-track). The tracks' cells and their activities' pairs
 * aren't segments' use; callers that drop those do so themselves.
 */
private fun JdbcTransaction.dropSegmentUse(
    efforts: SegmentEfforts.() -> Op<Boolean>,
    visits: DiveSiteVisits.() -> Op<Boolean>,
    riders: SegmentCandidateRiders.() -> Op<Boolean>,
) {
    deleteEfforts(efforts)
    DiveSiteVisits.deleteWhere { visits() }
    SegmentCandidateRiders.deleteWhere { riders() }
}
