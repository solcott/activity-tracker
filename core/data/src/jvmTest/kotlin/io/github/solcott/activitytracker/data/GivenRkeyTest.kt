package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * A draft or a combine saved under a record key chosen ahead (feed/batch-import): saving it again, as a resumed batch save does, rewrites
 * the same records and keeps one upload queue item.
 */
class GivenRkeyTest {
    private val pds = FakeAthletePds()

    private fun draft(title: String, rkey: String, start: String = "2026-10-14T09:00:00Z") =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = title,
            description = null,
            startedAt = Instant.parse(start),
            elapsed = 60.minutes,
            streams = ActivityStreamData(time = (0..3600 step 10).toList(), heartRate = (0..3600 step 10).map { 140 }),
            importBatch = "3kbatch",
            rkey = rkey,
        )

    @Test
    fun aDraftIsSavedUnderItsRkeyAndSavingItAgainKeepsOneItem() = runTest {
        val s = pds.session()
        pds.pdsUnreachable = true
        val first = s.activities.publishBatch(flowOf(draft("Ride", "3kgiven")), drain = false).single()
        val again = s.activities.publishBatch(flowOf(draft("Ride", "3kgiven")), drain = false).single()

        assertEquals("3kgiven", first.rkey)
        assertEquals(first, again)
        assertEquals(1, s.db.uploads().liveOfBatch(pds.did.value, "3kbatch").size, "one item for the activity, however often it's queued")

        pds.pdsUnreachable = false
        s.activities.sync()
        assertEquals("Ride", pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/3kgiven")["title"]?.toString()?.trim('"'))
        assertEquals(0, s.db.outbox().pendingCount(pds.did.value).first())
    }

    @Test
    fun aCombineUnderItsRkeyRewritesTheSameCombinedActivity() = runTest {
        val s = pds.session()
        val drafts = listOf(draft("Watch", "3kwatch"), draft("Edge", "3kedge", start = "2026-10-14T09:01:00Z"))
        val first = s.manager.combine(drafts, rkey = "3kcombined")
        val again = s.manager.combine(drafts, rkey = "3kcombined")

        assertEquals("3kcombined", first.rkey)
        assertEquals(first, again)
        val activities = pds.stored.getValue(pds.repo).keys.filter { it.startsWith("${Nsid.FEED_ACTIVITY}/") }
        assertEquals(setOf("3kwatch", "3kedge", "3kcombined"), activities.map { it.substringAfterLast('/') }.toSet())
    }
}
