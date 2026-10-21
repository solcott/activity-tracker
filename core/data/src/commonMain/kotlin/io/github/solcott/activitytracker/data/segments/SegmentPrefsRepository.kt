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
     * Whether the athlete hides from every segment leaderboard (segments/segment-leaderboards), as last read or saved with [current]; null
     * until read. Kept by every save of either setting.
     */
    val hiddenFromLeaderboards: StateFlow<Boolean?>
        field = MutableStateFlow<Boolean?>(null)

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
    suspend fun load(): SegmentMatching? = mutex.withLock { current.value ?: read()?.let(::remember) }

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
    suspend fun refresh(): SegmentMatching? = mutex.withLock { read()?.let(::remember) ?: current.value }

    /**
     * Saves [matching] and returns it, keeping the leaderboard choice as stored. Throws if the PDS can't be reached, keeping the setting
     * saved before, and refuses a session that can't write it. Each save sets [saved], after which [SegmentTrackSweeper.sweep] writes or
     * deletes the segment tracks.
     */
    suspend fun save(matching: Boolean): SegmentMatching = mutex.withLock {
        val hidden = storedHidden()
        val record = write(matching, hidden)
        (if (matching) SegmentMatching.ON else SegmentMatching.OFF).also {
            current.value = it
            hiddenFromLeaderboards.value = hidden
            saved.value = record.updatedAt
        }
    }

    /**
     * Saves whether the athlete hides from every leaderboard, keeping matching as stored now (not as this session last read it). Throws as
     * [save] does, keeping the choice saved before. Offered only while matching is on, so it never needs a sweep.
     */
    suspend fun saveLeaderboards(hidden: Boolean) = mutex.withLock {
        val matching = readForSave().matching
        write(matching.isOn, hidden)
        current.value = matching
        hiddenFromLeaderboards.value = hidden
    }

    /** The leaderboard choice to keep in a save, read now: another device may have changed it. False only when there's no record yet. */
    private suspend fun storedHidden(): Boolean = readForSave().hidden

    /** The record read now, before a save keeps the setting it doesn't change; throws when it can't be read, as the save would. */
    private suspend fun readForSave(): Read = read() ?: error("The segment settings can't be read")

    private suspend fun write(matching: Boolean, hidden: Boolean): SegmentPrefsRecord {
        clients.requireSpacesFor(Visibility.SELF, Nsid.ACTOR_SEGMENT_PREFS)
        val record = SegmentPrefsRecord(matching = matching, updatedAt = clock.now(), hideFromLeaderboards = hidden.takeIf { it })
        clients.spaces.putRecord(
            clients.tiers.ensure(AthleteSpace.SELF),
            Nsid.ACTOR_SEGMENT_PREFS,
            SegmentPrefsRecord.RKEY,
            AtJson.encodeToJsonElement(SegmentPrefsRecord.serializer(), record),
        )
        return record
    }

    private fun remember(read: Read): SegmentMatching {
        current.value = read.matching
        hiddenFromLeaderboards.value = read.hidden
        return read.matching
    }

    /** The record as read: its setting and leaderboard choice. */
    private class Read(val matching: SegmentMatching, val hidden: Boolean)

    /** The record's settings; null when it can't be read now. A record that doesn't decode counts as off: any client can write it. */
    private suspend fun read(): Read? {
        if (!canUse) return null
        val read = runSuspendCatching {
            clients.spaces.getRecord(clients.tiers.uri(AthleteSpace.SELF), clients.did, Nsid.ACTOR_SEGMENT_PREFS, SegmentPrefsRecord.RKEY)
        }
            .getOrNull()
        return when (read) {
            is SpaceRead.Found ->
                runCatching { AtJson.decodeFromJsonElement(SegmentPrefsRecord.serializer(), read.value.value) }
                    .getOrNull()
                    ?.let { Read(if (it.matching) SegmentMatching.ON else SegmentMatching.OFF, it.hideFromLeaderboards == true) }
                    ?: Read(SegmentMatching.OFF, hidden = false)
            SpaceRead.NotFound -> Read(SegmentMatching.NEVER_ASKED, hidden = false)
            SpaceRead.NoAccess,
            null -> null
        }
    }
}
