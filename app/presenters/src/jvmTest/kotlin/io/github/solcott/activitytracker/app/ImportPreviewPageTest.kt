package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.record.ActivityTypeEvent
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
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
        val ride = mtbRide()
        presenterTestOf({
            ImportActivityPresenter(
                ImportActivityScreen(),
                FakeNavigator(ImportActivityScreen()),
                repos.activities,
                repos.manager,
                ActivityFileReader { ride },
                repos.typeChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
            )
        }) {
            awaitItem().eventSink(ImportActivityEvent.FilePicked("ride.fit", bytes))
            val asRide = awaitUntil { state -> state.page?.hero.orEmpty().any { it.key == FigureKey.AvgSpeed } }
            assertTrue(asRide.page!!.hero.none { it.key == FigureKey.AvgPace })

            asRide.type.eventSink(ActivityTypeEvent.Select(ActivityType(Sport.RUN)))
            val asRun = awaitUntil { state -> state.page?.hero.orEmpty().any { it.key == FigureKey.AvgPace } }
            assertTrue(asRun.page!!.hero.none { it.key == FigureKey.AvgSpeed })
            cancelAndIgnoreRemainingEvents()
        }
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
                    )
                ),
            laps = emptyList(),
            records = (0L..10L).map { i -> DecodedSample(millis(i * 60), null, null, null, 140, 80, 180, null, null, null) },
        )
    }
}
