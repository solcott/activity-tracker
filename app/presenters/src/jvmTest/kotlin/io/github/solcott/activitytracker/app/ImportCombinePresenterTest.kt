package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.record.DuplicateAction
import io.github.solcott.activitytracker.app.record.DuplicateCheck
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonPrimitive

/** Combining an import with what it repeats, of any sport, and adding a recording to an activity from its page. */
class ImportCombinePresenterTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val run =
        DecodedActivity(
            fileId = DecodedSource("activity", "garmin", "fenix7", start.toEpochMilliseconds()),
            sessions =
                listOf(
                    DecodedSummary(
                        "running",
                        null,
                        start.toEpochMilliseconds(),
                        1800.0,
                        1750.0,
                        5000.0,
                        30.0,
                        null,
                        4.0,
                        152,
                        171,
                        null,
                        null,
                        null,
                    )
                ),
            laps = emptyList(),
            records =
                (0L..2L).map { i ->
                    DecodedSample(
                        (start + (i * 60).seconds).toEpochMilliseconds(),
                        47.6 + i * 0.001,
                        -122.3,
                        null,
                        150,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                },
        )

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    /** One of Alice's activities as the AppView serves it. */
    @Suppress("LongParameterList") // Each a part of the view a test sets.
    private fun logged(
        rkey: String,
        from: Instant,
        minutes: Int,
        sport: String = Sport.RIDE,
        title: String = "Lunch loop",
        combinedInto: AtUri? = null,
        combined: CombinedActivity? = null,
    ) =
        ActivityView(
            uri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = ProfileViewBasic(fixture.did, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport,
                    from,
                    minutes * 60L,
                    Instant.parse("2026-09-21T00:00:00Z"),
                    title = title,
                    device = "Garmin Fenix 7",
                    combined = combined,
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-21T00:00:00Z"),
            combinedInto = combinedInto,
        )

    private fun presenter(
        repos: SessionTestFixture.Repos,
        navigator: FakeNavigator,
        screen: ImportActivityScreen = ImportActivityScreen(),
    ) =
        @Composable {
            ImportActivityPresenter(
                screen,
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { run },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }

    private fun savedActivities() =
        fixture.putRkeys
            .zip(fixture.putRecords)
            .filter { (_, record) -> record.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
            .map { (rkey, record) ->
                AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, rkey) to AtJson.decodeFromJsonElement(ActivityRecord.serializer(), record)
            }

    @Test
    fun aRunOverlappingALoggedWalkIsFlaggedAndCombinedByDefault() = runTest {
        val repos = fixture.repos()
        // Any sport: the athlete decides whether it's the same thing.
        val walk = logged("3kwalk", start - 2.minutes, 33, sport = Sport.WALK)
        fixture.ownActivities = listOf(walk)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), walk.record)
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val checked = awaitUntil { it.preview?.duplicate != null && it.duplicateCheck == DuplicateCheck.Done }
            val duplicate = assertNotNull(checked.preview?.duplicate)
            assertEquals("Lunch loop", duplicate.existing?.title)
            assertEquals("Garmin Fenix 7", duplicate.existing?.device)
            assertEquals(DuplicateAction.Combine, duplicate.action)
            checked.eventSink(ImportActivityEvent.Save("Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val (run, combined) = savedActivities()
        assertEquals(setOf(walk.uri, run.first), combined.second.combined?.members?.toSet())
        assertEquals("Lunch loop", combined.second.title)
    }

    @Test
    fun aMatchedMemberStandsForItsCombinedActivity() = runTest {
        val repos = fixture.repos()
        val combinedUri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3kcombined")
        val watch = logged("3kwatch", start - 2.minutes, 33, combinedInto = combinedUri)
        val edge = logged("3kedge", start - 1.minutes, 32, title = "Morning Ride", combinedInto = combinedUri)
        val combined =
            logged("3kcombined", start - 2.minutes, 33, title = "Big day", combined = CombinedActivity(listOf(watch.uri, edge.uri)))
        fixture.ownActivities = listOf(watch, combined, edge)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), watch.record)
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("Morning_Run.FIT", bytes))
            val checked = awaitUntil { it.preview?.duplicate != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals("Big day", checked.preview?.duplicate?.existing?.title, "the combined activity, not its member")
            checked.eventSink(ImportActivityEvent.Save("Morning Run"))
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
        val (run, rebuilt) = savedActivities()
        assertEquals(combinedUri, rebuilt.first, "rebuilt under its own rkey")
        assertEquals(setOf(watch.uri, edge.uri, run.first), rebuilt.second.combined?.members?.toSet(), "a third member")
    }

    @Test
    fun aPhoneRecordingIsAddedToTheRideTheWatchStopped() = runTest {
        val repos = fixture.repos()
        // The watch stopped five minutes into the run; the phone recorded it all.
        val watch = logged("3kwatch", start - 60.minutes, 65)
        fixture.servedActivity = watch
        fixture.ownActivities = null
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), watch.record)
        val screen = ImportActivityScreen(combineInto = watch.uri.value)
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenter(repos, navigator, screen)) {
            val initial = awaitItem()
            assertTrue(initial.addsRecording)
            initial.eventSink(ImportActivityEvent.FilePicked("phone.FIT", bytes))
            val read = awaitUntil { it.preview != null && it.addingTo != null }
            assertEquals("Lunch loop", read.addingTo?.title)
            assertFalse(read.tooFarFromTarget)
            assertEquals(DuplicateCheck.Done, read.duplicateCheck, "no duplicate search: the log isn't read")
            assertNull(read.preview?.duplicate)
            read.eventSink(ImportActivityEvent.Save("Morning Run"))
            navigator.awaitPop()
            val (run, combined) = savedActivities()
            assertEquals(ActivityDetailScreen(combined.first.value), navigator.awaitNextScreen(), "it opens the combined activity")
            assertEquals(setOf(watch.uri, run.first), combined.second.combined?.members?.toSet())
            assertEquals(start - 60.minutes, combined.second.startedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFileFromThreeDaysLaterIsRefused() = runTest {
        val repos = fixture.repos()
        val watch = logged("3kwatch", start - 3.days, 30)
        fixture.servedActivity = watch
        val screen = ImportActivityScreen(combineInto = watch.uri.value)
        presenterTestOf(presenter(repos, FakeNavigator(screen), screen)) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("later.FIT", bytes))
            val read = awaitUntil { it.preview != null && it.addingTo != null }
            assertTrue(read.tooFarFromTarget)
            read.eventSink(ImportActivityEvent.Save("Morning Run"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList(), savedActivities(), "nothing is saved")
    }
}
