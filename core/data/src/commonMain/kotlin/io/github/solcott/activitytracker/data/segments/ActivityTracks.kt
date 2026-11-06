package io.github.solcott.activitytracker.data.segments

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace

/**
 * Whether the athlete has at least one activity track in their `self` space, as read now; false when it can't be read. Segment matching and
 * grouping ask once only when there's a track to use.
 */
internal suspend fun SessionClients.hasActivityTrack(): Boolean {
    val tracks = runSuspendCatching { spaces.listRecords(tiers.uri(AthleteSpace.SELF), did, Nsid.FEED_ACTIVITY_TRACK, 1) }.getOrNull()
    return tracks is SpaceRead.Found && tracks.value.records.isNotEmpty()
}
