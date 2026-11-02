package io.github.solcott.activitytracker.appview.db

import io.github.solcott.activitytracker.lexicon.app.GroupMode
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
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
        SegmentPrefs.matching eq true
    }

/** The [GroupMode] values that let an athlete be grouped. */
val GROUPING_MODES = listOf(GroupMode.CONNECTED, GroupMode.ANYONE)

/**
 * The segment tracks whose athlete's grouping choice is on now and whose activity hasn't left its group (feed/group-activities): the only
 * ones whose data may be used to pair activities.
 */
fun groupableTracks(): Query =
    SegmentTracks.join(GroupPrefs, JoinType.INNER, SegmentTracks.authorDid, GroupPrefs.did).selectAll().where {
        (GroupPrefs.mode inList GROUPING_MODES) and (SegmentTracks.groupExcluded eq false)
    }

/** The segment tracks some purpose may read now: [usableSegmentTracks] or [groupableTracks]. Their cells are kept for both. */
fun heldSegmentTracks(): Query = SegmentTracks.selectAll().where { heldTrack() }

/** Whether the track in the outer query's [SegmentTracks] row may be read for some purpose now. */
fun heldTrack(): Op<Boolean> =
    exists(
        SegmentPrefs.select(SegmentPrefs.did).where { (SegmentPrefs.did eq SegmentTracks.authorDid) and (SegmentPrefs.matching eq true) }
    ) or
        exists(
            GroupPrefs.select(GroupPrefs.did).where {
                (GroupPrefs.did eq SegmentTracks.authorDid) and
                    (GroupPrefs.mode inList GROUPING_MODES) and
                    (SegmentTracks.groupExcluded eq false)
            }
        )
