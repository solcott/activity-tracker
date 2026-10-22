package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.LeaderboardBracketsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The athlete's choice of age, weight and gender leaderboards (segments/leaderboard-brackets): the `actor.leaderboardBrackets` record (rkey
 * `self`) in their `self` space, which exists only while the choice is on and holds only the [LeaderboardBrackets] computed here from their
 * own details, never the details. Saves are direct calls, like [SegmentPrefsRepository]: consent that silently applied later would be the
 * wrong failure. While it's on, [refreshOnSave] keeps the brackets current for the session.
 */
@SingleIn(SessionScope::class)
@Inject
class LeaderboardBracketsRepository(
    private val clients: SessionClients,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    logger: Logger,
) {
    private val logger = logger.withTag("LeaderboardBracketsRepository")
    private val mutex = Mutex()

    /** Whether the choice is on, as last read or saved this session; null until it has been read. */
    val current: StateFlow<Boolean?>
        field = MutableStateFlow<Boolean?>(null)

    /** The brackets as stored, as last read or written this session; null when off or not read. */
    private var stored: LeaderboardBrackets? = null

    /** Whether this session can use the choice: not one signed in before its `space:` grant listed the record. */
    val canUse: Boolean
        get() = clients.canWriteToSpaces(Nsid.ACTOR_LEADERBOARD_BRACKETS)

    /** The choice, read once per session; null when it can't be read now (or this session can't use it). */
    suspend fun load(): Boolean? = mutex.withLock { current.value ?: read()?.let(::remember) }

    /** The choice read again, as Settings shows it on opening: another device may have changed it. Null when it can't be read now. */
    suspend fun reload(): Boolean? = mutex.withLock { read()?.let(::remember) ?: current.value }

    /**
     * Turns the choice on, writing the brackets from the athlete's details as they're stored now, or off, deleting the record, and returns
     * it. Throws if the PDS can't be reached or the details can't be read, keeping the choice saved before, and refuses a session that
     * can't write it.
     */
    suspend fun save(on: Boolean): Boolean = mutex.withLock {
        clients.requireSpacesFor(Visibility.SELF, Nsid.ACTOR_LEADERBOARD_BRACKETS)
        if (on) {
            write(bracketsOf(profiles.ownProfile().details))
        } else {
            try {
                clients.spaces.deleteRecord(
                    clients.tiers.uri(AthleteSpace.SELF),
                    Nsid.ACTOR_LEADERBOARD_BRACKETS,
                    LeaderboardBracketsRecord.RKEY,
                )
            } catch (e: XrpcException) {
                // Already gone counts as done.
                if (e.error != SpaceErrors.SPACE_NOT_FOUND && e.error != SpaceErrors.RECORD_NOT_FOUND) throw e
            }
            stored = null
        }
        current.value = on
        on
    }

    /**
     * For the session: once at its start, from the details as stored, and after each profile save ([ProfileRepository.savedDetails]), from
     * the details saved, rewrites the brackets while the choice is on and only when one changed (a birthday, an edit). Best effort: a
     * failure is logged and left for the next session or save.
     */
    suspend fun refreshOnSave() {
        profiles.savedDetails.collect { details ->
            if (!canUse) return@collect
            runSuspendCatching { refresh(details) }.onFailure { e -> logger.w(e) { "Couldn't refresh the leaderboard brackets" } }
        }
    }

    /** Rewrites the brackets from [details] (or the details as stored, when null) if the choice is on and one of them changed. */
    internal suspend fun refresh(details: ProfileDetails? = null) = mutex.withLock {
        val on = current.value ?: read()?.let(::remember) ?: return@withLock
        if (!on) return@withLock
        val brackets = bracketsOf(details ?: profiles.ownProfile().details)
        if (brackets != stored) write(brackets)
    }

    private fun bracketsOf(details: ProfileDetails): LeaderboardBrackets =
        LeaderboardBrackets.of(details, clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)

    private suspend fun write(brackets: LeaderboardBrackets) {
        val record =
            LeaderboardBracketsRecord(
                updatedAt = clock.now(),
                ageGroup = brackets.ageGroup,
                weightClass = brackets.weightClass,
                gender = brackets.gender,
            )
        clients.spaces.putRecord(
            clients.tiers.ensure(AthleteSpace.SELF),
            Nsid.ACTOR_LEADERBOARD_BRACKETS,
            LeaderboardBracketsRecord.RKEY,
            AtJson.encodeToJsonElement(LeaderboardBracketsRecord.serializer(), record),
        )
        stored = brackets
    }

    private fun remember(read: Read): Boolean {
        current.value = read.on
        stored = read.brackets
        return read.on
    }

    /** The record as read: whether it exists, and its brackets (null when it doesn't decode, so the next refresh rewrites it). */
    private class Read(val on: Boolean, val brackets: LeaderboardBrackets?)

    /** The record; null when it can't be read now. */
    private suspend fun read(): Read? {
        if (!canUse) return null
        val read = runSuspendCatching {
            clients.spaces.getRecord(
                clients.tiers.uri(AthleteSpace.SELF),
                clients.did,
                Nsid.ACTOR_LEADERBOARD_BRACKETS,
                LeaderboardBracketsRecord.RKEY,
            )
        }
            .getOrNull()
        return when (read) {
            is SpaceRead.Found ->
                Read(
                    on = true,
                    runCatching { AtJson.decodeFromJsonElement(LeaderboardBracketsRecord.serializer(), read.value.value) }
                        .getOrNull()
                        ?.let { LeaderboardBrackets(it.ageGroup, it.weightClass, it.gender) },
                )
            SpaceRead.NotFound -> Read(on = false, brackets = null)
            SpaceRead.NoAccess,
            null -> null
        }
    }
}
