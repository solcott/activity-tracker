package io.github.solcott.activitytracker.app.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.segments.SegmentPrefsRepository
import io.github.solcott.activitytracker.data.segments.SegmentTrackStore
import io.github.solcott.activitytracker.lexicon.AtUri

/**
 * Whether an activity being edited is excluded from segment leaderboards (segments/segment-leaderboards). The choice lives on its segment
 * track, so it's offered only while matching is on and the track is stored and readable; [save] writes it only when it changed.
 */
@Stable
class LeaderboardExclusion internal constructor(private val activity: AtUri, private val tracks: SegmentTrackStore) {
    /** As stored; null when it isn't offered. */
    internal var stored by mutableStateOf<Boolean?>(null)

    /** As edited; null when it isn't offered. */
    var excluded by mutableStateOf<Boolean?>(null)
        internal set

    val changed: Boolean
        get() = excluded != stored

    fun set(excluded: Boolean) {
        if (this.excluded != null) this.excluded = excluded
    }

    /** Queues the changed choice on the segment track; nothing when it's unchanged or not offered. */
    suspend fun save() {
        val value = excluded ?: return
        if (changed) tracks.setExcluded(activity, value)
    }
}

@Composable
internal fun rememberLeaderboardExclusion(
    activity: AtUri,
    prefs: SegmentPrefsRepository,
    tracks: SegmentTrackStore,
): LeaderboardExclusion {
    val exclusion = remember(activity, tracks) { LeaderboardExclusion(activity, tracks) }
    LaunchedEffect(exclusion) {
        val stored = runSuspendCatching { if (prefs.load()?.isOn == true) tracks.excluded(activity) else null }.getOrNull()
        exclusion.stored = stored
        exclusion.excluded = stored
    }
    return exclusion
}
