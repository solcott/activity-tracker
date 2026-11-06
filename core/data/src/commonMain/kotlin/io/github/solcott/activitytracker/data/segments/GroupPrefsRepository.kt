package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.readSelfRecord
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.activity.writeSelfRecord
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.app.GroupPrefsRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The athlete's grouping choice as read (feed/group-activities): never chosen (no record), off, or with whom. */
enum class GroupChoice(val mode: String?) {
    NEVER_ASKED(null),
    OFF(GroupMode.OFF),
    /** Grouped only with opted-in athletes they follow or who follow them. */
    CONNECTED(GroupMode.CONNECTED),
    /** Grouped with any opted-in athlete. */
    ANYONE(GroupMode.ANYONE);

    val isOn: Boolean
        get() = this == CONNECTED || this == ANYONE

    companion object {
        /** The choice a stored [mode] means; an unknown one counts as [OFF]. */
        fun of(mode: String): GroupChoice = entries.firstOrNull { it.mode == mode && it != NEVER_ASKED } ?: OFF
    }
}

/**
 * The athlete's grouping choice: the `actor.groupPrefs` record in their `self` space. While it's on, each activity with a track gets a
 * segment track the AppView may pair with other athletes' ([SegmentTracks], [TrackConsent]); it's off until the athlete chooses. Read once
 * per session (again after a failed read); anything unknown counts as off for writing. Saves are direct calls, like
 * [SegmentPrefsRepository]: consent that silently applied later would be the wrong failure.
 */
@SingleIn(SessionScope::class)
@Inject
class GroupPrefsRepository(private val clients: SessionClients, private val clock: Clock) {
    private val mutex = Mutex()

    /** The choice as last read or saved this session; null until it has been read. */
    val current: StateFlow<GroupChoice?>
        field = MutableStateFlow<GroupChoice?>(null)

    /** When this session last saved the choice; null before a save. [SegmentTrackSweeper.sweepOnSave] sweeps after each. */
    val saved: StateFlow<Instant?>
        field = MutableStateFlow<Instant?>(null)

    /** Whether this session can use grouping: not one signed in before its `space:` grant listed the choice and segment tracks. */
    val canUse: Boolean
        get() = clients.canWriteToSpaces(Nsid.ACTOR_GROUP_PREFS) && clients.canWriteToSpaces(Nsid.FEED_SEGMENT_TRACK)

    /** The choice, read once per session; null when it can't be read now (or this session can't use it). */
    suspend fun load(): GroupChoice? = mutex.withLock { current.value ?: read()?.also { current.value = it } }

    /**
     * Whether to ask the athlete once which choice they want: this session can use it, they've never chosen (no record, as read, not just
     * unreadable), and they have at least one activity with a track to pair.
     */
    suspend fun shouldAsk(): Boolean {
        if (!canUse || load() != GroupChoice.NEVER_ASKED) return false
        return clients.hasActivityTrack()
    }

    /** The choice read again, as Settings shows it on opening: another device may have changed it. Null when it can't be read now. */
    suspend fun refresh(): GroupChoice? = mutex.withLock { read()?.also { current.value = it } ?: current.value }

    /**
     * Saves [choice] (never [GroupChoice.NEVER_ASKED]) and returns it. Throws if the PDS can't be reached, keeping the choice saved before,
     * and refuses a session that can't write it. Each save sets [saved], after which [SegmentTrackSweeper.sweep] writes or deletes the
     * segment tracks.
     */
    suspend fun save(choice: GroupChoice): GroupChoice = mutex.withLock {
        val mode = requireNotNull(choice.mode) { "Never asked isn't a choice" }
        clients.requireSpacesFor(Visibility.SELF, Nsid.ACTOR_GROUP_PREFS)
        val record = GroupPrefsRecord(mode = mode, updatedAt = clock.now())
        clients.writeSelfRecord(Nsid.ACTOR_GROUP_PREFS, GroupPrefsRecord.RKEY, GroupPrefsRecord.serializer(), record)
        current.value = choice
        saved.value = record.updatedAt
        choice
    }

    /** The stored choice; null when it can't be read now. A record that doesn't decode counts as off: any client can write it. */
    private suspend fun read(): GroupChoice? {
        if (!canUse) return null
        val read = runSuspendCatching {
            clients.readSelfRecord(Nsid.ACTOR_GROUP_PREFS, GroupPrefsRecord.RKEY, GroupPrefsRecord.serializer())
        }
            .getOrNull()
        return when (read) {
            is SpaceRead.Found -> read.value?.let { GroupChoice.of(it.mode) } ?: GroupChoice.OFF
            SpaceRead.NotFound -> GroupChoice.NEVER_ASKED
            SpaceRead.NoAccess,
            null -> null
        }
    }
}

/**
 * Whether the athlete's segment tracks are wanted (segments/segment-track): while segment matching is on or grouping is on, each activity
 * with a full track has one; while both are off, none. Each setting is read as its repository reads it; one this session can't use counts
 * as never chosen.
 */
@SingleIn(SessionScope::class)
@Inject
class TrackConsent(private val segments: SegmentPrefsRepository, private val groups: GroupPrefsRepository) {
    /** What the two settings, read once per session, say of segment tracks. */
    enum class Wanted {
        /** At least one is on: write them. */
        YES,
        /** Both are off, or one off and the other never chosen: delete any left. */
        NO,
        /** Neither was ever chosen: none was ever written. */
        NEVER,
        /** One can't be read and the other isn't on: change nothing. */
        UNKNOWN,
    }

    /** Whether this session can use either setting. */
    val canUse: Boolean
        get() = segments.canUse || groups.canUse

    /** As last loaded this session, reading a setting once if it hasn't been. */
    suspend fun load(): Wanted = wanted(if (segments.canUse) segments.load() else SegmentMatching.NEVER_ASKED, groupsLoad())

    /** Both settings read again, as a sweep needs: another device may have changed either. */
    suspend fun refresh(): Wanted =
        wanted(
            if (segments.canUse) segments.refresh() else SegmentMatching.NEVER_ASKED,
            if (groups.canUse) groups.refresh() else GroupChoice.NEVER_ASKED,
        )

    private suspend fun groupsLoad(): GroupChoice? = if (groups.canUse) groups.load() else GroupChoice.NEVER_ASKED

    private fun wanted(matching: SegmentMatching?, grouping: GroupChoice?): Wanted =
        when {
            matching == SegmentMatching.ON || grouping?.isOn == true -> Wanted.YES
            matching == null || grouping == null -> Wanted.UNKNOWN
            matching == SegmentMatching.NEVER_ASKED && grouping == GroupChoice.NEVER_ASKED -> Wanted.NEVER
            else -> Wanted.NO
        }
}
