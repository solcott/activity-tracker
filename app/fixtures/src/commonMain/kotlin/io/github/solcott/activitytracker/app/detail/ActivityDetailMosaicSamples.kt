package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AspectRatio
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Sample pages of the Mosaic layout (feed/activity-page-layout), beside [ActivityDetailSamples]'. */
object ActivityDetailMosaicSamples {
    private fun photo(n: Int, width: Int = 4, height: Int = 3) =
        ActivityMediaView.Image("https://cdn.example/photo$n.webp", alt = "Photo $n", aspectRatio = AspectRatio(width, height))

    /** The ride's cards in a ride's order, power leading (feed/activity-page-layout), and thirteen 5 km laps. */
    private val orderedRidePage: ActivityPageContent
        get() {
            val rank = listOf(DetailSection.Power, DetailSection.HeartRate)
            val laps =
                (1..13)
                    .map { n ->
                        LapRow(
                            n,
                            600 + n * 7,
                            590 + n * 7,
                            5.0,
                            30.0 - n * 0.3,
                            132 + n,
                            190 + n * 3,
                            86,
                            40.0,
                            startSeconds = (n - 1) * 610,
                        )
                    }
                    .toImmutableList()
            val cards =
                ActivityDetailSamples.ridePage.cards
                    .sortedBy { rank.indexOf(it.section).let { i -> if (i < 0) rank.size else i } }
                    .toImmutableList()
            return ActivityDetailSamples.ridePage.copy(
                cards = cards,
                charts = (ActivityDetailSamples.ridePage.charts ?: ActivityDetailSamples.rideCharts).copy(laps = laps),
            )
        }

    /**
     * New in the Mosaic layout (feed/activity-page-layout): a ride with its map and four photos, power open and the rest as strips; a dive
     * with no route and one photo; a resort day; a workout with no route or photos; and a manual walk with three figures and nothing else.
     */
    val variants: Map<String, ActivityDetailState>
        get() {
            fun state(page: ActivityDetailPage) = ActivityDetailState(ContentStates.loaded(page), eventSink = {})
            val photos = listOf(photo(1), photo(2, 3, 4), photo(3), photo(4, 16, 9))
            val skiLaps =
                persistentListOf(
                    LapRow(1, 252, 252, 1.4, 38.0, 128, null, null, null, descent = 310.0),
                    LapRow(2, 420, 420, null, null, 96, null, null, null, intensity = "rest", liftName = "Single Chair"),
                    LapRow(3, 228, 228, 1.4, 41.0, 131, null, null, null, descent = 310.0),
                    LapRow(4, 430, 430, null, null, 95, null, null, null, intensity = "rest", liftName = "Single Chair"),
                    LapRow(5, 330, 330, 1.9, 35.0, 126, null, null, null, descent = 280.0),
                    LapRow(6, 390, 390, null, null, 94, null, null, null, intensity = "rest", liftName = "Sunnyside Double"),
                    LapRow(7, 362, 362, 2.1, 33.0, 124, null, null, null, descent = 280.0),
                )
            val skiCharts =
                ActivityDetailSamples.rideCharts.copy(
                    lines = persistentListOf(ActivityDetailSamples.speed, ActivityDetailSamples.elevation, ActivityDetailSamples.heartRate),
                    powerZones = null,
                    laps = skiLaps,
                    skiRuns = 4,
                )
            val strengthCharts =
                ActivityDetailSamples.rideCharts.copy(
                    lines = persistentListOf(ActivityDetailSamples.heartRate),
                    powerZones = null,
                    laps = persistentListOf(),
                    sets =
                        (1..8)
                            .map { n ->
                                SetRow(
                                    n,
                                    rest = false,
                                    category = "bench_press",
                                    exercise = "bench_press",
                                    reps = 8,
                                    weightGrams = 70_000,
                                    seconds = 40,
                                )
                            }
                            .toImmutableList(),
                )
            val hr = orderedRidePage.cards.first { it.section == DetailSection.HeartRate }
            val noMedia = ActivityDetailSamples.page.copy(route = null, segments = persistentListOf(), partners = persistentListOf())
            return mapOf(
                "photos" to
                    state(
                        ActivityDetailSamples.page.copy(
                            activity = ActivityDetailSamples.activity.copy(media = photos),
                            page = orderedRidePage,
                            charts = orderedRidePage.charts,
                            partners = persistentListOf(),
                        ),
                    ),
                "diveWithPhoto" to
                    state(
                        noMedia.copy(
                            activity =
                                fakeActivity(
                                        "3kdive",
                                        record = fakeActivityRecord(sport = Sport.DIVE, title = "Shark Reef", polyline = null),
                                    )
                                    .copy(media = listOf(photo(5, 4, 3))),
                            page = ActivityDetailSamples.divePage,
                        ),
                    ),
                "skiDay" to
                    state(
                        ActivityDetailSamples.page.copy(
                            activity = fakeActivity("3kski", record = fakeActivityRecord(sport = Sport.SKI, title = "Powder morning")),
                            page =
                                ActivityPageContent(
                                    start = ActivityDetailSamples.page.page.start,
                                    cards =
                                        persistentListOf(
                                            MetricCard(DetailSection.Laps, headline = persistentListOf()),
                                            orderedRidePage.cards.first { it.section == DetailSection.Speed },
                                            hr,
                                        ),
                                    charts = skiCharts,
                                ),
                            segments = ActivityDetailSamples.skiRuns,
                            lifts = ActivityDetailSamples.liftRides,
                            partners = persistentListOf(),
                        ),
                    ),
                "strength" to
                    state(
                        noMedia.copy(
                            activity =
                                fakeActivity(
                                    "3kgym",
                                    record =
                                        fakeActivityRecord(sport = Sport.STRENGTH, title = "Upper body", distance = null, polyline = null),
                                ),
                            page =
                                ActivityPageContent(
                                    start = ActivityDetailSamples.page.page.start,
                                    hero =
                                        persistentListOf(
                                            ActivityDetailSamples.figure(FigureKey.Time, FigureValue.Duration(3_134)),
                                            ActivityDetailSamples.figure(FigureKey.AvgHeartRate, FigureValue.Quantity(118, FigureUnit.Bpm)),
                                            ActivityDetailSamples.figure(
                                                FigureKey.Calories,
                                                FigureValue.Quantity(386, FigureUnit.Kilocalories),
                                            ),
                                            ActivityDetailSamples.figure(FigureKey.Sets, FigureValue.Count(8)),
                                        ),
                                    cards = persistentListOf(MetricCard(DetailSection.Sets, headline = persistentListOf()), hr),
                                    charts = strengthCharts,
                                ),
                        ),
                    ),
                "minimal" to
                    state(
                        noMedia.copy(
                            activity =
                                fakeActivity(
                                    "3kwalk",
                                    record =
                                        fakeActivityRecord(
                                            sport = Sport.WALK,
                                            title = "Evening walk",
                                            polyline = null,
                                            description = "Loop around the reservoir with Bo before the rain came in.",
                                        ),
                                ),
                            page =
                                ActivityPageContent(
                                    start = ActivityDetailSamples.page.page.start,
                                    hero =
                                        persistentListOf(
                                            ActivityDetailSamples.figure(FigureKey.Distance, FigureValue.Distance(4_800.0)),
                                            ActivityDetailSamples.figure(FigureKey.MovingTime, FigureValue.Duration(3_500)),
                                            ActivityDetailSamples.figure(FigureKey.AvgPace, FigureValue.Pace(1_372, false)),
                                        ),
                                ),
                        ),
                    ),
            )
        }
}

object ActivityDataSamples {
    /** All data for the Mosaic ride: its stats, avg/max table, thirteen laps and a combined ride's sources. */
    val data: ActivityData
        get() {
            val ride = checkNotNull(ActivityDetailMosaicSamples.variants.getValue("photos").content.data)
            return ActivityData.of(ride.page, ActivityDetailSamples.combinedPage.chartSources)
        }

    val all: Map<String, ActivityDataState>
        get() = contentSamples<ActivityData?, ActivityDataState>(null, data) { ActivityDataState(it, eventSink = {}) }

    /** Opened from the laps' Show all. */
    val atLaps: ActivityDataState
        get() = ActivityDataState(ContentStates.loaded(data.copy(start = DataSection.Laps)), eventSink = {})
}
