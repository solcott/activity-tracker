package io.github.solcott.activitytracker.appview.db

import io.github.solcott.activitytracker.lexicon.app.GroupMode
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The segment tracks whose athlete's setting says matching is on now: the only ones whose data may be used for segments. A segment track
 * stored while the setting is off, or before one arrives, is left out.
 */
fun usableSegmentTracks(): Query =
    SegmentTracks.join(SegmentPrefs, JoinType.INNER, SegmentTracks.authorDid, SegmentPrefs.did).selectAll().where {
        matchingOn(SegmentTracks.authorDid)
    }

/** The [GroupMode] values that let an athlete be grouped. */
val GROUPING_MODES = GroupMode.ON

/**
 * A [SegmentPrefs] row saying [author]'s segment matching is on: the one consent rule every read of segment tracks for segments goes
 * through, whether joined, in an `exists` or for one athlete.
 */
fun matchingOn(author: Expression<String>): Op<Boolean> = (SegmentPrefs.did eq author) and (SegmentPrefs.matching eq true)

/** A [GroupPrefs] row saying [author]'s grouping choice is one of [modes], by default any that is on ([GROUPING_MODES]). */
fun groupingIn(author: Expression<String>, modes: List<String> = GROUPING_MODES): Op<Boolean> =
    (GroupPrefs.did eq author) and (GroupPrefs.mode inList modes)

/**
 * The segment tracks whose athlete's grouping choice is on now and whose activity hasn't left its group (feed/group-activities): the only
 * ones whose data may be used to pair activities.
 */
fun groupableTracks(): Query =
    SegmentTracks.join(GroupPrefs, JoinType.INNER, SegmentTracks.authorDid, GroupPrefs.did).selectAll().where {
        groupingIn(SegmentTracks.authorDid) and not(leftGroup())
    }

/** The segment tracks some purpose may read now: [usableSegmentTracks] or [groupableTracks]. Their cells are kept for both. */
fun heldSegmentTracks(): Query = SegmentTracks.selectAll().where { heldTrack() }

/** Whether the track in the outer query's [SegmentTracks] row may be read for some purpose now. */
fun heldTrack(): Op<Boolean> =
    exists(SegmentPrefs.select(SegmentPrefs.did).where { matchingOn(SegmentTracks.authorDid) }) or
        exists(GroupPrefs.select(GroupPrefs.did).where { groupingIn(SegmentTracks.authorDid) and not(leftGroup()) })

/** [author] hides from leaderboards, Mayors and Regulars (`actor.segmentPrefs`). */
fun hidesFromLeaderboards(author: Expression<String>): Op<Boolean> =
    exists(SegmentPrefs.select(SegmentPrefs.did).where { (SegmentPrefs.did eq author) and (SegmentPrefs.hideFromLeaderboards eq true) })

/** The outer query's [SegmentTracks] row excludes its activity from leaderboards, Mayors and Regulars. */
fun excludedFromLeaderboards(): Op<Boolean> = SegmentTracks.leaderboardExcluded eq true

/** The outer query's [SegmentTracks] row keeps its activity out of every group (feed/group-activities). */
fun leftGroup(): Op<Boolean> = SegmentTracks.groupExcluded eq true

/** [activity]'s segment track keeps it out of every group ([leftGroup]). */
fun leftGroup(activity: Expression<String>): Op<Boolean> =
    exists(SegmentTracks.select(SegmentTracks.uri).where { (SegmentTracks.activityUri eq activity) and leftGroup() })
