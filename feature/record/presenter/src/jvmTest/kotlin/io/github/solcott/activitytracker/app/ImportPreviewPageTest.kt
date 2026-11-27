package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.record.ActivityTypeEvent
import io.github.solcott.activitytracker.app.record.BatchSaveRunner
import io.github.solcott.activitytracker.app.record.IMPORT_DRAFT
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ImportActivityState
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimDrafts
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString

/** The import screen previews the activity's page as the chosen type would show it. */
class ImportPreviewPageTest {
    private val fixture = SessionTestFixture()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
    private val bytes = ByteString(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())

    @Test
    fun theHeroFollowsTheChosenType() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ presenter(repos, TrimDrafts()) }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
            val asRide = awaitUntil { state -> state.page?.hero.orEmpty().any { it.key == FigureKey.AvgSpeed } }
            assertTrue(asRide.page!!.hero.none { it.key == FigureKey.AvgPace })

            asRide.type.eventSink(ActivityTypeEvent.Select(ActivityType(Sport.RUN)))
            val asRun = awaitUntil { state -> state.page?.hero.orEmpty().any { it.key == FigureKey.AvgPace } }
            assertTrue(asRun.page!!.hero.none { it.key == FigureKey.AvgSpeed })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A trim chosen on the trim screen comes back through the drafts and cuts the preview's page; offering the draft again (a new type)
     * keeps it, and only a new file starts the activity whole (feed/activity-trim).
     */
    @Test
    fun aChosenTrimCutsThePageUntilANewFile() = runTest {
        val drafts = TrimDrafts()
        val repos = fixture.repos()
        presenterTestOf({ presenter(repos, drafts) }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
            val whole = awaitUntil { state -> state.trim != null && state.page != null }
            val wholeTime = whole.time()

            drafts.apply(IMPORT_DRAFT, KeptSpan(from = 60, to = 300))
            val cut = awaitUntil { state -> state.trim?.applied == true && state.time() != wholeTime }

            cut.type.eventSink(ActivityTypeEvent.Select(ActivityType(Sport.RUN)))
            val retyped = awaitUntil { state ->
                state.type.selected.sport == Sport.RUN && state.page?.hero.orEmpty().any { it.key == FigureKey.AvgPace }
            }
            assertEquals(true, retyped.trim?.applied)
            assertEquals(cut.time(), retyped.time())

            retyped.eventSink(ImportActivityEvent.FilePicked("again.fit", bytes))
            val again = awaitUntil { state -> state.trim?.applied == false && state.page != null }
            assertEquals(wholeTime, again.time())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ImportActivityState.time() =
        page?.hero?.firstOrNull { it.key in setOf(FigureKey.MovingTime, FigureKey.ElapsedTime, FigureKey.Time) }?.value

    @Composable
    private fun presenter(repos: SessionTestFixture.Repos, drafts: TrimDrafts): ImportActivityState {
        val ride = remember { mtbRide() }
        return ImportActivityPresenter(
            ImportActivityScreen(),
            FakeNavigator(ImportActivityScreen()),
            repos.activities,
            repos.manager,
            ActivityFileReader { ride },
            repos.typeChoices,
            repos.gearChoices,
            repos.newDeviceChoices,
            repos.profiles,
            repos.feed,
            repos.defaults,
            repos.units,
            repos.suggestions,
            repos.saveStore,
            repos.saves,
            BatchSaveRunner(
                repos.saveStore,
                repos.saves,
                repos.clients,
                repos.activities,
                repos.manager,
                ActivityFileReader { ride },
            ),
            drafts,
        )
    }

    private fun mtbRide(): DecodedActivity {
        val millis = { sec: Long -> (start + sec.seconds).toEpochMilliseconds() }
        return DecodedActivity(
            fileId = DecodedSource("activity", "garmin", "edge840", millis(0)),
            sessions =
                listOf(
                    DecodedSummary(
                        "cycling",
                        "mountain",
                        millis(0),
                        600.0,
                        600.0,
                        3000.0,
                        80.0,
                        null,
                        null,
                        140,
                        170,
                        avgPowerWatts = 180,
                        avgCadence = 75,
                        avgTemperatureCelsius = null,
                    ),
                ),
            laps = emptyList(),
            records = (0L..10L).map { i -> DecodedSample(millis(i * 60), null, null, null, 140, 80, 180, null, null, null) },
        )
    }
}
