package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.profile.ProfileEdit
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.TrainingNarrowing
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.LeaderboardBracketsRecord
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The brackets choice (segments/leaderboard-brackets): turning it on writes only brackets computed from the athlete's details, wherever
 * they're stored; off deletes the record; and while it's on, the brackets are rewritten only when one changes.
 */
class LeaderboardBracketsRepositoryTest {
    private val pds = FakeAthletePds()
    private val key = "${Nsid.ACTOR_LEADERBOARD_BRACKETS}/self"

    /** 2025-06-10 at noon UTC: midday, so the local date is the same in any zone a test runs in. */
    private var now = Instant.parse("2025-06-10T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private class Repos(val profiles: ProfileRepository, val brackets: LeaderboardBracketsRepository)

    private suspend fun repos(scope: String? = null): Repos {
        val clients = pds.session(scope).clients
        val profiles = ProfileRepository(clients, clock, Logger, TrainingNarrowing {})
        return Repos(profiles, LeaderboardBracketsRepository(clients, profiles, clock, Logger))
    }

    /** Alice's details as stored: [public] in her profile record, [onlyMe] in her `self` tier. */
    private fun details(public: ProfileDetails = ProfileDetails(), onlyMe: ProfileDetails = ProfileDetails()) {
        val profile = ProfileRecord(createdAt = Instant.parse("2025-01-01T00:00:00Z"), details = public.takeIf { it != ProfileDetails() })
        pds.stored.getOrPut(pds.repo) { mutableMapOf() }["${Nsid.ACTOR_PROFILE}/self"] =
            AtJson.encodeToJsonElement(ProfileRecord.serializer(), profile).jsonObject
        pds.stored.getOrPut(AthleteSpace.SELF) { mutableMapOf() }["${Nsid.ACTOR_PRIVATE_DETAILS}/self"] =
            AtJson.encodeToJsonElement(PrivateDetailsRecord.serializer(), PrivateDetailsRecord(onlyMe)).jsonObject
    }

    private fun stored(): LeaderboardBracketsRecord? =
        pds.stored[AthleteSpace.SELF]?.get(key)?.let { AtJson.decodeFromJsonElement(LeaderboardBracketsRecord.serializer(), it) }

    private fun bracketWrites() = pds.writes.filter { it.endsWith(key) }

    /** Waits, in real time, until [done]: the fake PDS answers on Ktor's threads, which the test scheduler can't run ahead. */
    @Suppress("InjectDispatcher") // Real time on purpose: runTest's virtual clock would skip the wait.
    private suspend fun eventually(done: () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { while (!done()) delay(10.milliseconds) } }

    @Test
    fun turningOnWritesOnlyBracketsFromEveryTier() = runTest {
        details(
            public = ProfileDetails(gender = "female"),
            onlyMe = ProfileDetails(birthDate = "1984-03-02", weight = 70_000, height = 1_700),
        )
        val r = repos()
        assertEquals(false, r.brackets.load(), "off until chosen")

        assertTrue(r.brackets.save(true))
        val record = pds.stored.getValue(AthleteSpace.SELF).getValue(key)
        assertEquals(setOf("\$type", "updatedAt", "ageGroup", "weightClass", "gender"), record.keys, "nothing but brackets")
        assertEquals("35-44", record["ageGroup"]?.jsonPrimitive?.content, "41 from an Only me birth date")
        assertEquals("65-74", record["weightClass"]?.jsonPrimitive?.content)
        assertEquals("female", record["gender"]?.jsonPrimitive?.content)
        assertFalse("1984" in record.toString() || "70000" in record.toString())
        assertEquals(listOf("put self $key"), bracketWrites())
    }

    @Test
    fun turningOffDeletesTheRecord() = runTest {
        details(onlyMe = ProfileDetails(birthDate = "1984-03-02"))
        val r = repos()
        r.brackets.save(true)

        assertFalse(r.brackets.save(false))
        assertNull(stored())
        assertEquals(false, repos().brackets.load(), "another session reads it as off")
        assertFalse(r.brackets.save(false), "already off is done")
    }

    @Test
    fun aFailedSaveKeepsTheEarlierChoice() = runTest {
        details(onlyMe = ProfileDetails(birthDate = "1984-03-02"))
        val r = repos()
        r.brackets.load()
        pds.offline = true

        assertTrue(runCatching { r.brackets.save(true) }.isFailure)
        assertEquals(false, r.brackets.current.value)
        pds.offline = false
        assertNull(stored())
    }

    @Test
    fun aGrantWithoutTheRecordCantUseIt() = runTest {
        val scope = "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.ACTOR_SEGMENT_PREFS}"
        val brackets = repos(scope).brackets

        assertFalse(brackets.canUse)
        assertNull(brackets.load())
        assertTrue(runCatching { brackets.save(true) }.isFailure)
        assertNull(stored())
    }

    @Test
    fun turning45RewritesTheAgeGroupAtTheNextSession() = runTest {
        details(onlyMe = ProfileDetails(birthDate = "1980-06-11"))
        repos().brackets.save(true)
        assertEquals("35-44", stored()?.ageGroup, "44 the day before")

        now = Instant.parse("2025-06-11T12:00:00Z")
        val next = repos()
        val session = launch { next.brackets.refreshOnSave() }
        eventually { stored()?.ageGroup == "45-54" }
        session.cancel()
    }

    @Test
    fun nothingChangedWritesNothing() = runTest {
        details(onlyMe = ProfileDetails(birthDate = "1984-03-02", weight = 70_000, height = 1_700))
        repos().brackets.save(true)
        pds.writes.clear()

        repos().brackets.refresh()
        assertEquals(emptyList(), bracketWrites())
    }

    @Test
    fun aWeightEditRewritesTheWeightClass() = runTest {
        details(onlyMe = ProfileDetails(birthDate = "1984-03-02", weight = 70_000, height = 1_700))
        val r = repos()
        r.brackets.save(true)
        val session = launch { r.brackets.refreshOnSave() }

        val own = r.profiles.ownProfile()
        r.profiles.save(
            ProfileEdit(own.displayName.orEmpty(), own.description.orEmpty(), own.details.copy(weight = 76_000), own.visibility)
        )
        eventually { stored()?.weightClass == "75-84" }
        session.cancel()
        assertEquals("35-44", stored()?.ageGroup)
    }

    @Test
    fun whileOffAProfileSaveWritesNothing() = runTest {
        details(onlyMe = ProfileDetails(weight = 70_000, height = 1_700))
        val r = repos()
        r.brackets.refresh(ProfileDetails(weight = 76_000, height = 1_700))
        assertEquals(emptyList(), bracketWrites())
    }
}
