package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SegmentPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The athlete's segment matching setting as read: never chosen (no record), on, or off. */
enum class SegmentMatching {
    NEVER_ASKED,
    ON,
    OFF;

    val isOn: Boolean
        get() = this == ON
}

/**
 * The athlete's segment matching setting: the `actor.segmentPrefs` record in their `self` space. While it's on, each activity with a track
 * gets a segment track the AppView may match against ([SegmentTracks]); it's off until the athlete turns it on. Read once per session
 * (again after a failed read); anything unknown counts as off for writing. Saves are direct calls, like
 * [PrivacyZones][io.github.solcott.activitytracker.data.activity.PrivacyZones]: consent that silently applied later would be the wrong
 * failure.
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentPrefsRepository(private val clients: SessionClients, private val clock: Clock) {
    private val mutex = Mutex()

    /** The setting as last read or saved this session; null until it has been read. */
    val current: StateFlow<SegmentMatching?>
        field = MutableStateFlow<SegmentMatching?>(null)

    /**
     * When this session last saved the setting; null before a save.
     * [ActivityRepository.repairRoutesOnSave][io.github.solcott.activitytracker.data.activity.ActivityRepository.repairRoutesOnSave] sweeps
     * after each.
     */
    val saved: StateFlow<Instant?>
        field = MutableStateFlow<Instant?>(null)

    /** Whether this session can use segment matching: not one signed in before its `space:` grant listed the setting and segment tracks. */
    val canUse: Boolean
        get() = clients.canWriteToSpaces(Nsid.ACTOR_SEGMENT_PREFS) && clients.canWriteToSpaces(Nsid.FEED_SEGMENT_TRACK)

    /** Whether segment tracks may be written now: the setting was read this session and says on. */
    val isOn: Boolean
        get() = canUse && current.value == SegmentMatching.ON

    /** The setting, read once per session; null when it can't be read now (or this session can't use it). */
    suspend fun load(): SegmentMatching? = mutex.withLock { current.value ?: read()?.also { current.value = it } }

    /**
     * Whether to ask the athlete once whether to turn matching on: this session can use it, they've never chosen (no record, as read, not
     * just unreadable), and they have at least one activity with a track to match.
     */
    suspend fun shouldAsk(): Boolean {
        if (!canUse || load() != SegmentMatching.NEVER_ASKED) return false
        val tracks = runSuspendCatching {
            clients.spaces.listRecords(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.FEED_ACTIVITY_TRACK, 1)
        }
            .getOrNull()
        return tracks is SpaceRead.Found && tracks.value.records.isNotEmpty()
    }

    /** The setting read again, as Settings shows it on opening: another device may have changed it. Null when it can't be read now. */
    suspend fun refresh(): SegmentMatching? = mutex.withLock { read()?.also { current.value = it } ?: current.value }

    /**
     * Saves [matching] and returns it. Throws if the PDS can't be reached, keeping the setting saved before, and refuses a session that
     * can't write it. Each save sets [saved], after which [SegmentTrackSweeper.sweep] writes or deletes the segment tracks.
     */
    suspend fun save(matching: Boolean): SegmentMatching = mutex.withLock {
        clients.requireSpacesFor(Visibility.SELF, Nsid.ACTOR_SEGMENT_PREFS)
        val record = SegmentPrefsRecord(matching = matching, updatedAt = clock.now())
        clients.spaces.putRecord(
            clients.tiers.ensure(AthleteSpace.SELF),
            Nsid.ACTOR_SEGMENT_PREFS,
            SegmentPrefsRecord.RKEY,
            AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), record),
        )
        (if (matching) SegmentMatching.ON else SegmentMatching.OFF).also {
            current.value = it
            saved.value = record.updatedAt
        }
    }

    /** The record's setting; null when it can't be read now. A record that doesn't decode counts as off: any client can write it. */
    private suspend fun read(): SegmentMatching? {
        if (!canUse) return null
        val read = runSuspendCatching {
            clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.ACTOR_SEGMENT_PREFS, SegmentPrefsRecord.RKEY)
        }
            .getOrNull()
        return when (read) {
            is SpaceRead.Found ->
                runCatching { AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), read.value.value) }
                    .getOrNull()
                    ?.let { if (it.matching) SegmentMatching.ON else SegmentMatching.OFF } ?: SegmentMatching.OFF
            SpaceRead.NotFound -> SegmentMatching.NEVER_ASKED
            SpaceRead.NoAccess,
            null -> null
        }
    }
}
