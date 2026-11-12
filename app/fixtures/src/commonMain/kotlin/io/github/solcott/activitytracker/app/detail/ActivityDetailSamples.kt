package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.ShownRoute
import io.github.solcott.activitytracker.app.feed.FeedSamples
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeActivity
import io.github.solcott.activitytracker.app.fixtures.fakeActivityRecord
import io.github.solcott.activitytracker.app.fixtures.fakeComment
import io.github.solcott.activitytracker.app.fixtures.fakeEffort
import io.github.solcott.activitytracker.app.fixtures.fakeGroupPartner
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentBasic
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.interactions.CommentThread
import io.github.solcott.activitytracker.app.interactions.CommentsPaging
import io.github.solcott.activitytracker.app.interactions.LikesList
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object ActivityDetailSamples {
    val activity: ActivityView = fakeActivity()

    /**
     * A page of [activity] as someone else sees it, with one comment, one segment effort and one athlete it was done with. A new draft each
     * time.
     */
    val page: ActivityDetailPage
        get() {
            val comment = fakeComment()
            return ActivityDetailPage(
                activity = activity,
                comments =
                    CommentsPaging(
                        items = persistentListOf(CommentThread(comment.uri, comment, persistentListOf())),
                        refresh = LoadStatus.Idle,
                        hasAnswer = true,
                        endReached = true,
                    ),
                sentComments = persistentListOf(),
                draftComment = textFieldStateOf(),
                viewerDid = Fixed.BOB,
                isOwn = false,
                likesList = null,
                eventSink = {},
                page =
                    ActivityPageContent(
                        start =
                            Figure(
                                FigureKey.StartTime,
                                FigureValue.StartTime(activity.record.startedAt.epochSeconds, activity.record.utcOffset, dateOnly = false),
                            ),
                    ),
                route = activity.record.polyline?.let { ShownRoute(it, gaps = null, ends = null) },
                segments = groupEfforts(listOf(fakeEffort(activity = activity.uri))),
                partners = persistentListOf(fakeGroupPartner()),
            )
        }

    /**
     * Overlapping efforts from the Corner Canyon trails, grouped: a whole trail over its two stretches, a lone effort, and two parent
     * trails that start together, the shorter nested in the longer and over a section.
     */
    val nestedSegments: ImmutableList<SegmentEffortGroup>
        get() {
            fun effort(rkey: String, name: String, start: Long, elapsed: Long, rank: Int? = 1) =
                fakeEffort(
                    fakeSegmentBasic(fakeSegment(rkey, record = fakeSegmentRecord(name = name))),
                    activity.uri,
                    elapsed = elapsed,
                    rank = rank,
                    best = true,
                    startOffset = start,
                )
            return groupEfforts(
                listOf(
                    effort("3ksrattler", "Rattler (eastbound)", 300, 908),
                    effort("3ksrattler1", "Rattler: Bonneville Shoreline - Draper → Upper Rattler Connector", 300, 534),
                    effort("3ksrattler2", "Rattler: Upper Rattler Connector → Ghost Falls", 834, 374),
                    effort("3ksghost", "Ghost Falls: Ghost Connector → Corner Canyon Road", 1300, 601),
                    effort("3ksrushcanyon", "Rush › Exit Stage Left → Canyon Hollow", 2400, 492),
                    effort("3ksrushshore", "Rush › Exit Stage Left → Bonneville Shoreline", 2400, 574),
                    effort("3ksesl", "Rush - Exit Stage Left", 2420, 89, rank = null),
                ),
            )
        }

    /** A resort day's lifts (segments/segment-lifts): three rides on the quad, one on the gondola, in the order first ridden. */
    val liftRides: ImmutableList<LiftRides>
        get() {
            fun lift(rkey: String, name: String) =
                fakeSegmentBasic(
                    fakeSegment(rkey, record = fakeSegmentRecord(name = name, sport = SegmentSportGroup.ALPINE, kind = SegmentKind.LIFT)),
                )
            val gondola = lift("base-gondola", "Base Gondola")
            val quad = lift("summit-quad", "Summit Quad")
            return liftRidesOf(
                listOf(
                    fakeEffort(gondola, activity.uri, elapsed = 540, startOffset = 0),
                    fakeEffort(quad, activity.uri, elapsed = 412, startOffset = 900),
                    fakeEffort(quad, activity.uri, elapsed = 430, startOffset = 1800),
                    fakeEffort(quad, activity.uri, elapsed = 405, startOffset = 2700),
                ),
            )
        }

    /** A resort day's runs (segments/segment-lifts): two descents of an alpine run, the faster one the PR. */
    val skiRuns: ImmutableList<SegmentEffortGroup>
        get() {
            val run = fakeSegmentBasic(fakeSegment("lynx", record = fakeSegmentRecord(name = "Lynx", sport = SegmentSportGroup.ALPINE)))
            return groupEfforts(
                listOf(
                    fakeEffort(run, activity.uri, elapsed = 212, startOffset = 600, best = true),
                    fakeEffort(run, activity.uri, elapsed = 230, startOffset = 1500),
                ),
            )
        }

    val all: Map<String, ActivityDetailState>
        get() = contentSamples<ActivityDetailPage?, ActivityDetailState>(null, page) { ActivityDetailState(it, eventSink = {}) }

    /** Seconds the sample ride's streams run for. */
    private const val RIDE_SECONDS = 3_600

    /** [count] points of a line from [base] swinging by [swing], one a minute, the same every run. */
    private fun samples(base: Double, swing: Double, count: Int = 61): ImmutableList<ChartSample> =
        (0 until count)
            .map { i ->
                ChartSample(i * RIDE_SECONDS / (count - 1), base + swing * kotlin.math.sin(i / 6.0) + swing / 3 * kotlin.math.cos(i / 2.0))
            }
            .toImmutableList()

    private fun line(kind: StreamKind, base: Double, swing: Double, average: Double? = base, max: Double? = base + swing * 1.3) =
        LineSeries(kind, samples(base, swing), average = average, max = max)

    private fun zones(vararg seconds: Int, step: Int, from: Int): ImmutableList<ZoneTime> =
        seconds
            .mapIndexed { i, s ->
                ZoneTime("${i + 1}", ZoneName.entries[i], from + i * step, (from + (i + 1) * step).takeIf { i < seconds.lastIndex }, s)
            }
            .toImmutableList()

    private val heartRate = line(StreamKind.HeartRate, 142.0, 18.0, max = 176.0)
    private val power = line(StreamKind.Power, 205.0, 60.0, max = 512.0)
    private val elevation = line(StreamKind.Elevation, 1_620.0, 80.0, average = null, max = 1_745.0)
    private val grade = line(StreamKind.Grade, 1.5, 4.0, average = null, max = 9.4)
    private val speed = line(StreamKind.Speed, 8_800.0, 2_200.0, max = 15_400.0)

    private val laps: ImmutableList<LapRow> =
        persistentListOf(
            LapRow(1, 1_200, 1_180, 10.6, 32.4, 136, 192, 88, 120.0),
            LapRow(2, 1_200, 1_195, 11.0, 33.1, 145, 214, 90, 160.0),
            LapRow(3, 1_200, 1_190, 10.8, 32.7, 147, 209, 87, 130.0),
        )

    private val rideCharts =
        ActivityCharts(
            visibility = Visibility.PUBLIC,
            durationSeconds = RIDE_SECONDS,
            lines = persistentListOf(heartRate, power, speed, elevation, grade),
            heartRateZones = zones(300, 1_500, 1_200, 480, 120, step = 20, from = 100),
            powerZones = zones(600, 1_200, 1_080, 540, 180, step = 50, from = 100),
            normalizedPower = 221,
            powerCurve = persistentListOf(),
            gears = persistentListOf(),
            laps = laps,
        )

    private fun figure(key: FigureKey, value: FigureValue, mark: String? = null) = Figure(key, value, mark = mark)

    /** What the owner's ride page shows: its hero, training load, a card per metric with charts, zones and laps, and its stats. */
    private val ridePage: ActivityPageContent =
        ActivityPageContent(
            start = page.page.start,
            hero =
                persistentListOf(
                    figure(FigureKey.Distance, FigureValue.Distance(32_400.0)),
                    figure(FigureKey.MovingTime, FigureValue.Duration(3_600)),
                    figure(FigureKey.ElevationGain, FigureValue.Elevation(410.0)),
                    figure(FigureKey.AvgSpeed, FigureValue.Speed(9_000)),
                    figure(FigureKey.AvgPower, FigureValue.Quantity(205, FigureUnit.Watts), mark = Visibility.FOLLOWERS),
                    figure(FigureKey.Calories, FigureValue.Quantity(812, FigureUnit.Kilocalories)),
                ),
            trainingLoad =
                persistentListOf(
                    figure(FigureKey.Ftp, FigureValue.Quantity(250, FigureUnit.Watts)),
                    figure(FigureKey.IntensityFactor, FigureValue.Decimal(0.88, 2)),
                    figure(FigureKey.TrainingStressScore, FigureValue.Decimal(77.0, 0)),
                ),
            cards =
                persistentListOf(
                    MetricCard(
                        DetailSection.HeartRate,
                        headline =
                            persistentListOf(
                                figure(FigureKey.Avg, FigureValue.Quantity(142, FigureUnit.Bpm)),
                                figure(FigureKey.Max, FigureValue.Quantity(176, FigureUnit.Bpm)),
                            ),
                        series = persistentListOf(heartRate),
                        zones = rideCharts.heartRateZones,
                    ),
                    MetricCard(
                        DetailSection.Power,
                        headline =
                            persistentListOf(
                                figure(FigureKey.Avg, FigureValue.Quantity(205, FigureUnit.Watts)),
                                figure(FigureKey.Max, FigureValue.Quantity(512, FigureUnit.Watts)),
                                figure(FigureKey.NormalizedPower, FigureValue.Quantity(221, FigureUnit.Watts)),
                            ),
                        rows = persistentListOf(figure(FigureKey.Work, FigureValue.Quantity(738, FigureUnit.Kilojoules))),
                        series = persistentListOf(power),
                        zones = rideCharts.powerZones,
                        mark = Visibility.FOLLOWERS,
                    ),
                    MetricCard(
                        DetailSection.Speed,
                        headline =
                            persistentListOf(
                                figure(FigureKey.Avg, FigureValue.Speed(9_000)),
                                figure(FigureKey.Max, FigureValue.Speed(15_400)),
                            ),
                        series = persistentListOf(speed),
                    ),
                    MetricCard(
                        DetailSection.Elevation,
                        headline =
                            persistentListOf(
                                figure(FigureKey.ElevationGain, FigureValue.Elevation(410.0)),
                                figure(FigureKey.Highest, FigureValue.Elevation(1_745.0)),
                            ),
                        rows = persistentListOf(figure(FigureKey.MaxGrade, FigureValue.Stream(StreamKind.Grade, 9.4))),
                        series = persistentListOf(elevation, grade),
                    ),
                    MetricCard(DetailSection.Laps, headline = persistentListOf()),
                ),
            stats =
                persistentListOf(
                    StatsGroup(
                        StatsGroupKind.Timing,
                        persistentListOf(
                            figure(FigureKey.MovingTime, FigureValue.Duration(3_600)),
                            figure(FigureKey.ElapsedTime, FigureValue.Duration(3_900)),
                        ),
                    ),
                    StatsGroup(
                        StatsGroupKind.DistanceSpeed,
                        persistentListOf(
                            figure(FigureKey.Distance, FigureValue.Distance(32_400.0)),
                            figure(FigureKey.AvgSpeed, FigureValue.Speed(9_000)),
                        ),
                    ),
                    StatsGroup(
                        StatsGroupKind.Device,
                        persistentListOf(
                            figure(FigureKey.Device, FigureValue.Text("Edge 840")),
                            figure(
                                FigureKey.GearItem,
                                FigureValue.GearItem("Road bike", "bike", "road", uri = "at://did:plc:alice/gear/1"),
                            ),
                        ),
                    ),
                ),
            avgMax =
                persistentListOf(
                    AvgMaxRow(AvgMaxMetric.HeartRate, FigureValue.Quantity(142, FigureUnit.Bpm), FigureValue.Quantity(176, FigureUnit.Bpm)),
                    AvgMaxRow(AvgMaxMetric.Power, FigureValue.Quantity(205, FigureUnit.Watts), FigureValue.Quantity(512, FigureUnit.Watts)),
                    AvgMaxRow(AvgMaxMetric.Speed, FigureValue.Speed(9_000), FigureValue.Speed(15_400)),
                ),
            charts = rideCharts,
        )

    /** A run's pace card: average and best pace, and the pace line with the faster at the top. */
    private val runPage: ActivityPageContent =
        ActivityPageContent(
            start = page.page.start,
            hero =
                persistentListOf(
                    figure(FigureKey.Distance, FigureValue.Distance(10_200.0)),
                    figure(FigureKey.MovingTime, FigureValue.Duration(3_000)),
                    figure(FigureKey.AvgPace, FigureValue.Pace(3_400, swim = false)),
                    figure(FigureKey.AvgHeartRate, FigureValue.Quantity(151, FigureUnit.Bpm)),
                ),
            cards =
                persistentListOf(
                    MetricCard(
                        DetailSection.Speed,
                        headline =
                            persistentListOf(
                                figure(FigureKey.Avg, FigureValue.Pace(3_400, swim = false)),
                                figure(FigureKey.Best, FigureValue.Pace(4_600, swim = false)),
                            ),
                        series = persistentListOf(line(StreamKind.Speed, 3_400.0, 500.0, max = 4_600.0)),
                        pace = PaceKind.Distance,
                    ),
                ),
            charts =
                rideCharts.copy(lines = persistentListOf(line(StreamKind.Speed, 3_400.0, 500.0, max = 4_600.0)), laps = persistentListOf()),
        )

    private val ownManage = ActivityManage(otherRecordings = 1, canUngroup = true, canCombine = true, busy = false, error = null)

    /**
     * States beyond content loading, each on its own: the owner's ride with every card, a run's pace card, the owner missing zones, likes
     * shown, a combine suggestion, a hidden activity, and a ride with nested segment efforts and lifts.
     */
    val variants: Map<String, ActivityDetailState>
        get() {
            val own = page.copy(viewerDid = Fixed.ALICE, isOwn = true)
            fun state(page: ActivityDetailPage) = ActivityDetailState(ContentStates.loaded(page), eventSink = {})
            return mapOf(
                "ownRide" to
                    state(
                        own.copy(
                            page = ridePage,
                            charts = rideCharts,
                            manage = ownManage,
                            canHideRouteEnds = true,
                            export = ActivityExportState(persistentListOf(ExportFormat.GPX, ExportFormat.TCX, ExportFormat.FIT)),
                            ownGroup = OwnGroupState(left = false),
                            canAddPeople = true,
                            zoneAdded = true,
                        ),
                    ),
                "run" to
                    state(
                        page.copy(
                            activity = fakeActivity("3krun", record = fakeActivityRecord(sport = Sport.RUN, title = "Tempo run")),
                            page = runPage,
                            partners = persistentListOf(),
                        ),
                    ),
                "missingZones" to
                    state(
                        own.copy(
                            page = ridePage.copy(cards = persistentListOf(ridePage.cards.first().copy(zones = null))),
                            chartsMissingZones = true,
                            manage = ownManage,
                        ),
                    ),
                "likesShown" to
                    state(
                        page.copy(
                            likesList =
                                LikesList(
                                    persistentListOf(fakeProfileBasic(Fixed.CAROL), fakeProfileBasic(Fixed.BOB)),
                                    isLoading = false,
                                    error = null,
                                ),
                        ),
                    ),
                "combineSuggestion" to
                    state(
                        own.copy(
                            manage = ownManage,
                            combineSuggestion =
                                CombineSuggestionState(
                                    CombineCue.SwitchedDevices,
                                    fakeActivity("3kother", record = fakeActivityRecord(title = "Ride (watch)")),
                                    asPrompt = false,
                                ),
                        ),
                    ),
                "hidden" to state(page.copy(activity = activity.copy(labels = listOf(LabelView(Did("did:plc:labeler"), "!hide"))))),
                "segments" to state(page.copy(segments = nestedSegments, lifts = liftRides)),
            )
        }

    /** The owner's own page. */
    val own: ActivityDetailState
        get() =
            ActivityDetailState(
                ContentStates.loaded(page.copy(viewerDid = Fixed.ALICE, isOwn = true, ownGroup = OwnGroupState(left = false))),
                eventSink = {},
            )
}

object CombinePickerSamples {
    val candidates: ImmutableList<ActivityView> =
        persistentListOf(
            fakeActivity("3kactivity3", fakeProfileBasic(), FeedSamples.activities.first().record.copy(title = "Ride (watch)")),
        )

    val all: Map<String, CombinePickerState>
        get() =
            contentSamples<ImmutableList<ActivityView>, CombinePickerState>(persistentListOf(), candidates) { CombinePickerState(it) } +
                (SampleKind.EMPTY to CombinePickerState(ContentStates.loaded<ImmutableList<ActivityView>>(persistentListOf())))
}
