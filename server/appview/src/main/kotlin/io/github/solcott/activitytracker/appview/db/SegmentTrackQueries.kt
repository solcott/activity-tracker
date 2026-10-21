package io.github.solcott.activitytracker.appview.db

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The segment tracks whose athlete's setting says matching is on now: the only ones whose data may be read or used. A segment track stored
 * while the setting is off, or before one arrives, is left out.
 */
fun usableSegmentTracks(): Query =
    SegmentTracks.join(SegmentPrefs, JoinType.INNER, SegmentTracks.authorDid, SegmentPrefs.did).selectAll().where {
        SegmentPrefs.matching eq true
    }
