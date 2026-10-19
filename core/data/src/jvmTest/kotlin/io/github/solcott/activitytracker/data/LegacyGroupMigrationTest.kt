package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.LegacyGroupMigration
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject

/** Turning legacy `recordingOf` groups into combined activities after sign-in (`feed/import-duplicates`), against the fake PDS. */
class LegacyGroupMigrationTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val start = Instant.parse("2026-05-12T16:00:00Z")

    private fun Session.migration() = LegacyGroupMigration(db, clients, manager, Logger)

    private fun dive(title: String, offsetSeconds: Int = 0) =
        ActivityDraft(
            type = ActivityType(Sport.DIVE),
            title = title,
            description = null,
            startedAt = start + offsetSeconds.seconds,
            elapsed = 45.minutes,
            streams = ActivityStreamData(time = (0..2700 step 10).toList(), depth = (0..2700 step 10).map { 1800 }),
        )

    private fun record(uri: AtUri): ActivityRecord? =
        pds.stored[pds.repo]?.get("${Nsid.FEED_ACTIVITY}/${uri.rkey}")?.let {
            AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it)
        }

    private fun view(uri: AtUri, recordings: List<AtUri> = emptyList(), combinedInto: AtUri? = null) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = requireNotNull(record(uri)),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            recordings = recordings.map { RecordingView(it, start) }.ifEmpty { null },
            combinedInto = combinedInto,
        )

    /** A dive with a backup computer's recording grouped under it, as clients used to write them, and the AppView serving the group. */
    private suspend fun Session.group(): Pair<AtUri, AtUri> {
        val main = activities.publish(dive("Blue Hole"))
        val backup = activities.publish(dive("Backup", offsetSeconds = 60))
        val key = "${Nsid.FEED_ACTIVITY}/${backup.rkey}"
        val grouped = record(backup)!!.copy(recordingOf = main)
        pds.stored.getValue(pds.repo)[key] = AtJson.encodeToJsonElement(ActivityRecord.serializer(), grouped).jsonObject
        pds.authorActivities = listOf(view(main, recordings = listOf(backup)))
        return main to backup
    }

    /** The combined activities stored, by URI. */
    private fun combined(): Map<AtUri, ActivityRecord> =
        pds.stored[pds.repo]
            .orEmpty()
            .filterKeys { it.startsWith("${Nsid.FEED_ACTIVITY}/") }
            .map { (key, value) ->
                AtUri.of(did, Nsid.FEED_ACTIVITY, key.substringAfter('/')) to
                    AtJson.decodeFromJsonElement(ActivityRecord.serializer(), value)
            }
            .filter { (_, record) -> record.combined != null }
            .toMap()

    @Test
    fun aGroupBecomesACombinedActivityAndItsRecordingIsCleared() = runTest {
        val s = pds.session()
        val (main, backup) = s.group()

        assertTrue(s.migration().run())

        val (_, made) = combined().entries.single().toPair()
        assertEquals(listOf(main, backup), made.combined?.members)
        assertEquals("Blue Hole", made.title, "it starts from the primary")
        assertNull(record(backup)?.recordingOf, "the recording's link is cleared")
        assertEquals("Backup", record(backup)?.title, "and nothing else about it changes")
    }

    @Test
    fun aRunInterruptedAfterTheCombineFinishesTheClearsNextTime() = runTest {
        val s = pds.session()
        val (main, backup) = s.group()
        // The combined activity is written; then the recording can't be read to clear it, as if the app stopped there.
        pds.onPut = { key, record ->
            if (key.startsWith("${Nsid.FEED_ACTIVITY}/") && "combined" in record) pds.failReadsOf = "${Nsid.FEED_ACTIVITY}/${backup.rkey}"
        }
        assertFalse(s.migration().run())
        val first = combined().keys.single()
        assertEquals(main, record(backup)?.recordingOf, "not cleared yet")

        pds.onPut = null
        pds.failReadsOf = null
        // The AppView now has the primary as a member, and lists only the combined activity.
        pds.authorActivities = emptyList()
        pds.served = GetActivity.Output(view(main, recordings = listOf(backup), combinedInto = first))
        // The app starts again: the same database, a new session.
        assertTrue(s.migration().run())

        assertEquals(setOf(first), combined().keys, "not combined twice")
        assertNull(record(backup)?.recordingOf)
    }

    @Test
    fun aFailureLeavesTheMigrationToRetry() = runTest {
        val s = pds.session()
        val (_, backup) = s.group()
        pds.failReadsOf = "${Nsid.FEED_ACTIVITY}/${backup.rkey}"

        assertFalse(s.migration().run(), "the group couldn't be read")
        assertTrue(combined().isEmpty())

        pds.failReadsOf = null
        assertTrue(s.migration().run(), "the next sign-in retries it")
        assertEquals(1, combined().size)
    }

    @Test
    fun aSecondRunDoesNothing() = runTest {
        val s = pds.session()
        s.group()
        assertTrue(s.migration().run())
        val writes = pds.writes.size
        val requests = pds.requests.size

        assertTrue(s.migration().run())

        assertEquals(writes, pds.writes.size)
        assertEquals(requests, pds.requests.size, "finished: the athlete's activities aren't even listed")
    }

    @Test
    fun aSessionStartsTheMigrationOnce() = runTest {
        val s = pds.session()
        s.group()
        // Failing, so only the session guard can stop a second run.
        pds.authorActivities = null
        val migration = s.migration()

        migration.runOncePerSession()
        migration.runOncePerSession()

        assertEquals(1, pds.requests.count { it.startsWith("/xrpc/${Nsid.FEED_GET_AUTHOR_ACTIVITIES} ") })
    }
}
