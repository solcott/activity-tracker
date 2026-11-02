package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * Leaving and rejoining the group on one of the athlete's own activities (feed/group-activities): the mark on its segment track
 * ([SegmentTrackStore.setGroupExcluded]), queued like their other writes. Offered only while grouping is on and the track is stored, not
 * still queued.
 */
@SingleIn(SessionScope::class)
@Inject
class OwnGroups(private val prefs: GroupPrefsRepository, private val store: SegmentTrackStore) {
    /**
     * Whether [activity] left its group; null when it can't be offered now: grouping is off or unknown, or the activity has no stored
     * segment track, or one is still queued.
     */
    suspend fun left(activity: AtUri): Boolean? {
        if (!prefs.canUse || runSuspendCatching { prefs.load() }.getOrNull()?.isOn != true) return null
        return store.groupExcluded(activity)
    }

    /** Queues leaving ([left]) or rejoining [activity]'s group. Throws when it has no stored segment track to mark. */
    suspend fun setLeft(activity: AtUri, left: Boolean) = store.setGroupExcluded(activity, left)
}
