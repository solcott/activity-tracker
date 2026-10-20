package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.RecordingResult
import io.github.solcott.activitytracker.location.RecordingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** The categories a new activity's form offers (`feed/detail-visibility`): those its draft has data in. */
class DraftCategoriesTest {
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    private fun sample(i: Int) = LocationSample(45.0 + i * 1e-4, -122.0, 100.0, 5.0, 5.0, start + i.seconds)

    private val route = (0..4).map(::sample)

    private fun draft(type: ActivityType = ActivityType(Sport.RIDE, SubSport.MOUNTAIN)) =
        ActivityDraft(type = type, title = null, description = null, startedAt = start, elapsed = 30.minutes)

    private val mtbRide =
        draft()
            .copy(
                distanceMeters = 12_000.0,
                avgHeartRate = 150,
                avgPowerWatts = 210,
                avgCadence = 80,
                calories = 600,
                route = route,
                streams =
                    ActivityStreamData(
                        time = listOf(0, 1, 2),
                        speed = listOf(5000, 5000, 5000),
                        heartRate = listOf(150, 151, 152),
                        power = listOf(200, 210, 220),
                        cadence = listOf(80, 81, 82),
                    ),
            )

    @Test
    fun mountainBikeRideHasNoDiveOrPhysiology() {
        assertEquals(
            setOf(
                DetailCategory.START_TIME,
                DetailCategory.HEART_RATE,
                DetailCategory.POWER,
                DetailCategory.SPEED,
                DetailCategory.CALORIES,
                DetailCategory.MAP,
                DetailCategory.CADENCE,
            ),
            mtbRide.heldCategories(),
        )
        assertFalse(mtbRide.hasRunningForm)
    }

    @Test
    fun diveWithoutRouteHasNoMap() {
        val dive =
            draft(ActivityType(Sport.DIVE))
                .copy(
                    dive = ActivityDive(),
                    streams = ActivityStreamData(time = listOf(0, 1), depth = listOf(0, 500), heartRate = listOf(80, 82)),
                )
        assertEquals(setOf(DetailCategory.START_TIME, DetailCategory.DIVE, DetailCategory.HEART_RATE), dive.heldCategories())
    }

    @Test
    fun manualEntryHasStartTimeAndSpeedOnlyWithADistance() {
        val manual = draft(ActivityType(Sport.RUN)).copy(movingSeconds = 1800)
        assertEquals(setOf(DetailCategory.START_TIME), manual.heldCategories())
        assertEquals(
            setOf(DetailCategory.START_TIME, DetailCategory.SPEED),
            manual.copy(distanceMeters = 5000.0).heldCategories(),
        )
    }

    @Test
    fun runningFormNamesTheCadenceCategory() {
        val run =
            draft(ActivityType(Sport.RUN))
                .copy(streams = ActivityStreamData(time = listOf(0, 1), cadence = listOf(170, 172), stanceTime = listOf(250, 248)))
        assertTrue(run.hasRunningForm)
        assertTrue(DetailCategory.CADENCE in run.heldCategories())
    }

    @Test
    fun recordingHoldsOnlyRecordedCategories() {
        val state =
            RecordingState(
                status = RecordingState.Status.Recording,
                startedAt = start,
                activeBeforeSegment = 4.seconds,
                samples = route,
                distanceMeters = 50.0,
                movingSeconds = 4,
            )
        val recorded = ActivityDraft.fromRecording(RecordingResult(state, start + 4.seconds), draft().type, null, null, TimeZone.UTC)
        assertTrue(ActivityDraft.RECORDED_CATEGORIES.containsAll(recorded.heldCategories()), "${recorded.heldCategories()}")
    }

    @Test
    fun agreesWithWhatPublishingWillRead() {
        val record = mtbRide.toRecord(start, polyline = SegmentedPolyline("_p~iF~ps|U_ulLnnqC", null))
        val read = DetailCategory.currentTiers(Visibility.PUBLIC, record, emptyList(), listOf(Visibility.PUBLIC to mtbRide.streams!!))
        assertEquals(read.keys, mtbRide.heldCategories())
    }
}
