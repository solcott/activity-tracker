package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DecodedActivity
import io.github.solcott.activitytracker.activityfile.DecodedSample
import io.github.solcott.activitytracker.activityfile.DecodedSensor
import io.github.solcott.activitytracker.activityfile.DecodedSource
import io.github.solcott.activitytracker.activityfile.DecodedSummary
import io.github.solcott.activitytracker.app.detail.ActivityPage
import io.github.solcott.activitytracker.app.detail.ChartsBuilder
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.record.importDraft
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The import preview shows what the published activity's page shows its owner. */
class ActivityPagePreviewTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Test
    fun aFitRideWithPowerPreviewsItsPage() = runTest {
        val ride = ActivityFileReader { mtbRide() }.readFile(FIT_HEADER).activities.single()
        val draft =
            importDraft(ride, ActivityType(Sport.RIDE, "mountain"), title = null, training = null, emptyList(), source = null)
                .copy(visibility = Visibility.PUBLIC, categories = mapOf(DetailCategory.HEART_RATE to Visibility.SELF))

        val preview = ActivityPage.fromDraft(draft, UnitSystem.Metric)

        // As published: the record at the activity's tier, the narrower heart rate in a details record, merged back for the owner.
        val full = draft.toRecord(createdAt = draft.startedAt)
        val uri = AtUri("at://did:plc:me/app.activitytracker.feed.activity/1")
        val stored = DetailCategory.recordFor(full, draft.visibility, draft.categories)
        val details = DetailCategory.detailsFor(full, uri, draft.visibility, draft.categories).values.toList()
        val streams =
            GetActivityStreams.Output(
                uri,
                draft.visibility,
                emptyList(),
                checkNotNull(draft.streams),
                draft.heartRateZones,
                draft.powerZones,
                draft.trainingLoad,
            )
        val published =
            ActivityPage.build(
                effectiveRecord(stored, details),
                ChartsBuilder.forSport(ChartsBuilder.build(streams), full.sport),
                ActivityPage.narrowerThan(draft.visibility, draft.tiers),
            )

        assertEquals(published, preview)
        assertTrue(preview.hero.isNotEmpty())
        val sections = preview.cards.map { it.section }
        assertTrue(DetailSection.HeartRate in sections && DetailSection.Power in sections && DetailSection.Cadence in sections)
        assertEquals(Visibility.SELF, preview.cards.first { it.section == DetailSection.HeartRate }.mark)
        assertTrue(preview.trainingLoad.isNotEmpty())
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
                        maxPowerWatts = 640,
                        normalizedPowerWatts = 205,
                        maxCadence = 115,
                        totalDescentMeters = 82.0,
                        totalWorkJoules = 108_400.0,
                        thresholdPowerWatts = 250,
                        intensityFactor = 0.82,
                        trainingStressScore = 11.2,
                    )
                ),
            laps = emptyList(),
            records =
                (0L..10L).map { i ->
                    DecodedSample(millis(i * 60), null, null, null, 140 + i.toInt(), 80, 180 + i.toInt(), null, null, null)
                },
            sensors = listOf(DecodedSensor(1, "antplus", "bikePower", "sram", "1052", "low", null)),
        )
    }

    private companion object {
        /** Just a FIT header: the reader picks the decoder by the ".FIT" signature at byte 8, and the fake decoder ignores the rest. */
        val FIT_HEADER = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0, '.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())
    }
}
