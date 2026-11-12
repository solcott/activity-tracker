package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.data.training.EffectiveTraining
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** Thresholds the training samples share: a 170 bpm threshold heart rate, 190 max, 55 resting and a 250 W FTP. */
private val training =
    EffectiveTraining(functionalThresholdPower = 250, maxHeartRate = 190, thresholdHeartRate = 170, restingHeartRate = 55)

private val heartRateZones = Zones.compute(ZoneModels.defaultHeartRate, training)?.toImmutableList()
private val powerZones = Zones.compute(ZoneModels.defaultPower, training)?.toImmutableList()

object SettingsSamples {
    val default: SettingsState
        get() =
            SettingsState(
                handle = "alice.test",
                needsSignIn = false,
                units = UnitSystem.Metric,
                saving = false,
                error = null,
                defaultActivityVisibility = Visibility.FOLLOWERS,
                privacyZoneCount = 1,
                hidesRouteEnds = true,
                segmentMatching = true,
                eventSink = {},
            )

    val all: Map<String, SettingsState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                SampleKind.LOADING to default.copy(defaultActivityVisibility = null, privacyZoneCount = null, segmentMatching = null),
                "moderator" to default.copy(canModerate = true),
                "needsSignIn" to default.copy(needsSignIn = true),
            )
}

object ActivityDefaultsSamples {
    /** Followers by default, heart rate kept to the athlete and gear Only me, as it starts. */
    private val defaults =
        VisibilityFields(
            activity = Visibility.FOLLOWERS,
            categories =
                DetailCategory.all
                    .map { category ->
                        val narrowed = category == DetailCategory.HEART_RATE || category == DetailCategory.GEAR
                        CategoryVisibility(category, if (narrowed) Visibility.SELF else Visibility.FOLLOWERS)
                    }
                    .toImmutableList(),
        )

    val default: ActivityDefaultsState
        get() = ActivityDefaultsState(defaults = defaults, saving = false, error = null, eventSink = {})

    val all: Map<String, ActivityDefaultsState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                SampleKind.LOADING to default.copy(defaults = null),
                "locked" to default.copy(defaults = VisibilityFields(canChoose = false)),
                "saveFailed" to default.copy(error = UiError(UiError.Kind.SaveSettings)),
            )
}

object SegmentsAndGroupsSamples {
    /** Matching on and shown on leaderboards, bracket boards off, grouped with people they follow. */
    val default: SegmentsAndGroupsState
        get() =
            SegmentsAndGroupsState(
                error = null,
                segmentMatching = true,
                showOnLeaderboards = true,
                bracketBoards = false,
                groupActivities = GroupChoice.CONNECTED,
                eventSink = {},
            )

    val all: Map<String, SegmentsAndGroupsState>
        get() =
            mapOf(
                SampleKind.LOADED to default,
                SampleKind.LOADING to
                    default.copy(segmentMatching = null, showOnLeaderboards = null, bracketBoards = null, groupActivities = null),
                "signInAgain" to default.copy(segmentMatching = null, canUseSegments = false, groupActivities = null, canUseGroups = false),
                "saveFailed" to default.copy(error = UiError(UiError.Kind.SaveSettings)),
            )
}

object TrainingZonesSamples {
    private val summary =
        ZoneSummary(
            sport = null,
            kinds = persistentListOf(ZoneKindOption(ZoneKind.HeartRate), ZoneKindOption(ZoneKind.Power)),
            training = training,
            override = null,
            paceUnit = PaceUnit.PerKilometer,
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateZones = heartRateZones,
            powerModel = ZoneModels.defaultPower,
            powerZones = powerZones,
            speedModel = ZoneModels.frielRunningPace,
            speedZones = null,
        )
    private val zones =
        TrainingZones(
            defaults = summary,
            sports = persistentListOf(),
            addableSports = persistentListOf(Sport.RUN, Sport.RIDE),
            hasZones = true,
            hasHistory = true,
        )

    val all: Map<String, TrainingZonesState>
        get() = contentSamples(null, zones) { TrainingZonesState(it) }

    /** Swimming's own threshold heart rate and pace, with its pace zones per 100 m; its max heart rate is the defaults'. */
    private val swim: ZoneSummary
        get() {
            val override = SportTraining(sport = Sport.SWIM, thresholdHeartRate = 165, thresholdSpeed = SWIM_THRESHOLD_SPEED)
            val effective = training.copy(functionalThresholdPower = null, thresholdHeartRate = 165, thresholdSpeed = SWIM_THRESHOLD_SPEED)
            return summary.copy(
                sport = Sport.SWIM,
                kinds = ZoneKindOption.forSport(Sport.SWIM),
                training = effective,
                override = override,
                paceUnit = PaceUnit.Per100Meters,
                heartRateZones = Zones.compute(ZoneModels.defaultHeartRate, effective)?.toImmutableList(),
                speedModel = ZoneModels.frielSwimmingPace,
                speedZones = Zones.compute(ZoneModels.frielSwimmingPace, effective)?.toImmutableList(),
            )
        }

    /** Running with nothing of its own: the defaults' heart rate and power, and no threshold pace yet. */
    private val run: ZoneSummary
        get() = summary.copy(sport = Sport.RUN, kinds = ZoneKindOption.forSport(Sport.RUN))

    /**
     * States beside [all], whose keys the content-loading conformance and the Android screenshot set use: swimming with its own zones
     * (`sports`), running with nothing of its own and no sport left to add (`runDefaults`), nothing saved yet (`noZones`), and a visibility
     * that failed to save (`saveFailed`).
     */
    val variants: Map<String, TrainingZonesState>
        get() {
            val loaded = ContentStates.loaded<TrainingZones?>(zones)
            return mapOf(
                "sports" to
                    TrainingZonesState(
                        ContentStates.loaded(zones.copy(sports = persistentListOf(swim), addableSports = persistentListOf(Sport.RIDE))),
                        visibility = Visibility.FOLLOWERS,
                    ),
                "runDefaults" to
                    TrainingZonesState(
                        ContentStates.loaded(zones.copy(sports = persistentListOf(run), addableSports = persistentListOf())),
                    ),
                "noZones" to
                    TrainingZonesState(
                        ContentStates.loaded(
                            zones.copy(
                                defaults = summary.copy(training = EffectiveTraining(), heartRateZones = null, powerZones = null),
                                hasZones = false,
                                hasHistory = false,
                            ),
                        ),
                    ),
                "saveFailed" to TrainingZonesState(loaded, visibility = Visibility.PUBLIC, error = UiError(UiError.Kind.SaveZones)),
            )
        }
}

/** A 4:30 per kilometer threshold pace, in mm/s. */
private const val RUN_THRESHOLD_SPEED = 3_704L

object ThresholdHistorySamples {
    private val groups: ImmutableList<ThresholdHistoryGroup> =
        persistentListOf(
            ThresholdHistoryGroup(
                sport = null,
                expanded = true,
                thresholds =
                    persistentListOf(
                        ThresholdHistorySection(
                            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
                            sport = null,
                            paceUnit = PaceUnit.PerKilometer,
                            rows =
                                persistentListOf(
                                    ThresholdHistoryRow(
                                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 250, Fixed.CREATED_AT),
                                        HistoryDate(2026, 5, 31),
                                        current = true,
                                    ),
                                    ThresholdHistoryRow(
                                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 238),
                                        date = null,
                                        current = false,
                                    ),
                                ),
                            showingAll = false,
                            chart = null,
                            undatedCount = 1,
                            change = null,
                        ),
                    ),
            ),
        )

    val all: Map<String, ThresholdHistoryState>
        get() = contentSamples(persistentListOf(), groups) { ThresholdHistoryState(it) }

    /** Beyond content loading: a threshold with more values than a collapsed section shows, every one shown. */
    val variants: Map<String, ThresholdHistoryState>
        get() =
            mapOf(
                ALL_ROWS to ThresholdHistoryState(ContentStates.loaded(allRows)),
                // Charts: the defaults expanded with a rising power and a flat heart rate, Run collapsed to its tiles.
                "charted" to ThresholdHistoryState(ContentStates.loaded(charted(defaultsExpanded = true, runExpanded = false))),
                // Every group collapsed: the overview tiles with their sparklines.
                "collapsed" to ThresholdHistoryState(ContentStates.loaded(charted(defaultsExpanded = false, runExpanded = false))),
                // Run's pace expanded, which got faster, in minutes per mile.
                "pace" to ThresholdHistoryState(ContentStates.loaded(charted(defaultsExpanded = false, runExpanded = true))),
                // A value being removed: its row shows progress and no other can be removed.
                "removing" to
                    ThresholdHistoryState(
                        ContentStates.loaded(charted(defaultsExpanded = true, runExpanded = false)),
                        removing = power.rows.first().entry,
                    ),
                // A removal that failed.
                "removeFailed" to
                    ThresholdHistoryState(
                        ContentStates.loaded(charted(defaultsExpanded = true, runExpanded = false)),
                        error = UiError(UiError.Kind.SaveZones),
                    ),
            )

    const val ALL_ROWS = "allRows"

    /** Threshold power 230 W on Jan 10 to 250 W on May 31, with one undated value; the last placed before today. */
    private val power =
        ThresholdHistorySection(
            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
            sport = null,
            paceUnit = PaceUnit.PerKilometer,
            rows =
                persistentListOf(
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 250, source = "manual"),
                        HistoryDate(2026, 5, 31),
                        current = true,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 238, source = "device"),
                        HistoryDate(2026, 3, 2),
                        current = false,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 230, source = "garmin"),
                        HistoryDate(2026, 1, 10),
                        current = false,
                    ),
                    ThresholdHistoryRow(ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 225), null, current = false),
                ),
            showingAll = false,
            chart =
                ThresholdChart(
                    persistentListOf(
                        ChartPoint(0f, 0f, 230, current = false),
                        ChartPoint(0.35f, 0.4f, 238, current = false),
                        ChartPoint(0.8f, 1f, 250, current = true),
                    ),
                    start = HistoryDate(2026, 1, 10),
                    lowest = 230,
                    highest = 250,
                ),
            undatedCount = 1,
            change = ThresholdTrend(from = 230, to = 250, since = HistoryDate(2026, 1, 10)),
        )

    /** Threshold heart rate 170 bpm, set twice to the same value: a flat line and no change. */
    private val heartRate =
        ThresholdHistorySection(
            threshold = Threshold.THRESHOLD_HEART_RATE,
            sport = null,
            paceUnit = PaceUnit.PerKilometer,
            rows =
                persistentListOf(
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.THRESHOLD_HEART_RATE, 170, source = "strava"),
                        HistoryDate(2026, 4, 12),
                        current = true,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.THRESHOLD_HEART_RATE, 170, source = "manual"),
                        HistoryDate(2026, 2, 1),
                        current = false,
                    ),
                ),
            showingAll = false,
            chart =
                ThresholdChart(
                    persistentListOf(ChartPoint(0f, 0.5f, 170, current = false), ChartPoint(0.6f, 0.5f, 170, current = true)),
                    start = HistoryDate(2026, 2, 1),
                    lowest = 170,
                    highest = 170,
                ),
            undatedCount = 0,
            change = ThresholdTrend(from = 170, to = 170, since = HistoryDate(2026, 2, 1)),
        )

    /** Max heart rate with one value: "Only value", no chart. */
    private val maxHeartRate =
        ThresholdHistorySection(
            threshold = Threshold.MAX_HEART_RATE,
            sport = null,
            paceUnit = PaceUnit.PerKilometer,
            rows =
                persistentListOf(
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.MAX_HEART_RATE, 192, source = "device"),
                        HistoryDate(2026, 3, 20),
                        current = true,
                    ),
                ),
            showingAll = false,
            chart = null,
            undatedCount = 0,
            change = null,
        )

    /** Run's threshold pace, 3.0 to 3.2 m/s (8:56 to 8:23 a mile), and its own max heart rate. */
    private val runPace =
        ThresholdHistorySection(
            threshold = Threshold.THRESHOLD_SPEED,
            sport = Sport.RUN,
            paceUnit = PaceUnit.PerMile,
            rows =
                persistentListOf(
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.THRESHOLD_SPEED, 3_200, sport = Sport.RUN, source = "device"),
                        HistoryDate(2026, 6, 1),
                        current = true,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.THRESHOLD_SPEED, 3_100, sport = Sport.RUN, source = "manual"),
                        HistoryDate(2026, 4, 15),
                        current = false,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.THRESHOLD_SPEED, 3_000, sport = Sport.RUN, source = "manual"),
                        HistoryDate(2026, 2, 3),
                        current = false,
                    ),
                ),
            showingAll = false,
            chart =
                ThresholdChart(
                    persistentListOf(
                        ChartPoint(0f, 0f, 3_000, current = false),
                        ChartPoint(0.5f, 0.5f, 3_100, current = false),
                        ChartPoint(0.85f, 1f, 3_200, current = true),
                    ),
                    start = HistoryDate(2026, 2, 3),
                    lowest = 3_000,
                    highest = 3_200,
                ),
            undatedCount = 0,
            change = ThresholdTrend(from = 3_000, to = 3_200, since = HistoryDate(2026, 2, 3)),
        )

    private val runMaxHeartRate =
        ThresholdHistorySection(
            threshold = Threshold.MAX_HEART_RATE,
            sport = Sport.RUN,
            paceUnit = PaceUnit.PerMile,
            rows =
                persistentListOf(
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.MAX_HEART_RATE, 188, sport = Sport.RUN, source = "garmin"),
                        HistoryDate(2026, 5, 2),
                        current = true,
                    ),
                    ThresholdHistoryRow(
                        ThresholdEntry(Threshold.MAX_HEART_RATE, 194, sport = Sport.RUN, source = "manual"),
                        HistoryDate(2026, 1, 20),
                        current = false,
                    ),
                ),
            showingAll = false,
            chart =
                ThresholdChart(
                    persistentListOf(ChartPoint(0f, 1f, 194, current = false), ChartPoint(0.7f, 0f, 188, current = true)),
                    start = HistoryDate(2026, 1, 20),
                    lowest = 188,
                    highest = 194,
                ),
            undatedCount = 0,
            change = ThresholdTrend(from = 194, to = 188, since = HistoryDate(2026, 1, 20)),
        )

    private fun charted(defaultsExpanded: Boolean, runExpanded: Boolean): ImmutableList<ThresholdHistoryGroup> =
        persistentListOf(
            ThresholdHistoryGroup(null, defaultsExpanded, persistentListOf(power, heartRate, maxHeartRate)),
            ThresholdHistoryGroup(Sport.RUN, runExpanded, persistentListOf(runPace, runMaxHeartRate)),
        )

    private val allRows: ImmutableList<ThresholdHistoryGroup> =
        persistentListOf(
            ThresholdHistoryGroup(
                sport = null,
                expanded = true,
                thresholds =
                    persistentListOf(
                        ThresholdHistorySection(
                            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
                            sport = null,
                            paceUnit = PaceUnit.PerKilometer,
                            rows =
                                listOf(250L, 245L, 240L, 238L, 230L)
                                    .mapIndexed { i, watts ->
                                        ThresholdHistoryRow(
                                            ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, watts),
                                            HistoryDate(2026, 5 - i, 1),
                                            current = i == 0,
                                        )
                                    }
                                    .toImmutableList(),
                            showingAll = true,
                            chart = null,
                            undatedCount = 0,
                            change = null,
                        ),
                    ),
            ),
        )
}

object ZonesSamples {
    private fun state(content: ContentState<Unit>, threshold: String = "170") =
        ZonesState(
            thresholdHeartRate = textFieldStateOf(threshold),
            maxHeartRate = textFieldStateOf("190"),
            restingHeartRate = textFieldStateOf("55"),
            functionalThresholdPower = textFieldStateOf("250"),
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateModels = ZoneModels.heartRate.toImmutableList(),
            heartRateZones = heartRateZones,
            powerModel = ZoneModels.defaultPower,
            powerModels = ZoneModels.power.toImmutableList(),
            powerZones = powerZones,
            autoUpdate = persistentSetOf(),
            content = content,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, ZonesState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), threshold = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), threshold = ""),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
                SampleKind.INVALID to state(ContentStates.loaded(Unit), threshold = "fast"),
            )

    /**
     * States beside [all]: saving (`saving`), a failed save (`saveFailed`), imports updating the max heart rate (`autoUpdate`), and no
     * threshold power, so no power zones (`missing`).
     */
    val variants: Map<String, ZonesState>
        get() {
            val loaded = state(ContentStates.loaded(Unit))
            return mapOf(
                "saving" to loaded.copy(saving = true),
                "saveFailed" to loaded.copy(error = UiError(UiError.Kind.SaveZones)),
                "autoUpdate" to loaded.copy(autoUpdate = persistentSetOf(Threshold.MAX_HEART_RATE)),
                "missing" to loaded.copy(functionalThresholdPower = textFieldStateOf(), powerZones = null),
            )
        }
}

object SportZonesSamples {
    private fun state(content: ContentState<Unit>, pace: String = "4:30") =
        SportZonesState(
            sport = Sport.RUN,
            kinds = persistentListOf(ZoneKindOption(ZoneKind.HeartRate), ZoneKindOption(ZoneKind.Speed)),
            thresholdHeartRate = textFieldStateOf("172"),
            maxHeartRate = textFieldStateOf(),
            functionalThresholdPower = textFieldStateOf(),
            thresholdPace = textFieldStateOf(pace),
            paceUnit = PaceUnit.PerKilometer,
            defaults = training,
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateModels = ZoneModels.heartRate.toImmutableList(),
            heartRateZones = heartRateZones,
            powerModel = ZoneModels.defaultPower,
            powerModels = ZoneModels.power.toImmutableList(),
            powerZones = null,
            speedModel = ZoneModels.frielRunningPace,
            speedModels = ZoneModels.speed.toImmutableList(),
            speedZones = null,
            saved = false,
            content = content,
            saving = false,
            error = null,
            eventSink = {},
        )

    val all: Map<String, SportZonesState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), pace = ""),
                SampleKind.FAILED to state(ContentStates.failed(Unit), pace = ""),
                SampleKind.LOADED to state(ContentStates.loaded(Unit)),
                SampleKind.INVALID to state(ContentStates.loaded(Unit), pace = "soon"),
            )

    /**
     * States beside [all]: running saved with its own pace zones and max heart rate, so it can be removed (`saved`), swimming per 100 m
     * (`swim`), saving (`saving`) and a failed save (`saveFailed`).
     */
    val variants: Map<String, SportZonesState>
        get() {
            val loaded = state(ContentStates.loaded(Unit))
            val run = training.copy(thresholdHeartRate = 172, maxHeartRate = 188, thresholdSpeed = RUN_THRESHOLD_SPEED)
            val swimming = training.copy(thresholdSpeed = SWIM_THRESHOLD_SPEED)
            return mapOf(
                "saved" to
                    loaded.copy(
                        maxHeartRate = textFieldStateOf("188"),
                        heartRateZones = Zones.compute(ZoneModels.defaultHeartRate, run)?.toImmutableList(),
                        speedZones = Zones.compute(ZoneModels.frielRunningPace, run)?.toImmutableList(),
                        saved = true,
                    ),
                "swim" to
                    loaded.copy(
                        sport = Sport.SWIM,
                        kinds = ZoneKindOption.forSport(Sport.SWIM),
                        thresholdHeartRate = textFieldStateOf(),
                        thresholdPace = textFieldStateOf("1:45"),
                        paceUnit = PaceUnit.Per100Meters,
                        speedModel = ZoneModels.frielSwimmingPace,
                        speedZones = Zones.compute(ZoneModels.frielSwimmingPace, swimming)?.toImmutableList(),
                    ),
                "saving" to loaded.copy(saving = true),
                "saveFailed" to loaded.copy(error = UiError(UiError.Kind.SaveZones)),
            )
        }
}

/** A 1:45 per 100 m threshold pace, in mm/s. */
private const val SWIM_THRESHOLD_SPEED = 952L

/** Radii and hidden start and end distances, in meters, in the lexicon's steps as the presenter offers them (Off is the page's). */
private val DISTANCE_CHOICES = persistentListOf(200, 400, 600, 800)

object PrivacyZonesSamples {
    private fun state(content: ContentState<Unit>, zones: ImmutableList<PrivacyZone>?) =
        PrivacyZonesState(
            content = content,
            zones = zones,
            hideEndsMeters = 200,
            choices = DISTANCE_CHOICES,
            canEdit = zones != null,
            saving = false,
            canUseLocation = false,
            hasLocationPermission = false,
            locating = false,
            label = textFieldStateOf(),
            error = null,
            eventSink = {},
        )

    private val zones =
        persistentListOf(PrivacyZone(latitudeE7 = 400_150_000, longitudeE7 = -1_052_700_000, radiusMeters = 400, label = "Home"))

    val all: Map<String, PrivacyZonesState>
        get() =
            mapOf(
                SampleKind.LOADING to state(ContentStates.loading(Unit), null),
                SampleKind.FAILED to state(ContentStates.failed(Unit), null),
                SampleKind.LOADED to state(ContentStates.loaded(Unit), zones),
            )

    /**
     * States beside [all]: a phone that can add a zone where it is, with a zone that has no label (`phone`), finding the location
     * (`locating`), no zones and nothing hidden (`empty`), a session to sign in again (`signInAgain`), and a failed change (`saveFailed`).
     */
    val variants: Map<String, PrivacyZonesState>
        get() {
            val loaded = state(ContentStates.loaded(Unit), zones)
            val two = (zones + PrivacyZone(latitudeE7 = 400_200_000, longitudeE7 = -1_052_800_000, radiusMeters = 800)).toImmutableList()
            val phone = loaded.copy(zones = two, canUseLocation = true, hasLocationPermission = true)
            return mapOf(
                "phone" to phone,
                "locating" to phone.copy(locating = true, label = textFieldStateOf("Work")),
                "empty" to loaded.copy(zones = persistentListOf(), hideEndsMeters = 0),
                "signInAgain" to loaded.copy(canEdit = false, needsSignIn = true),
                "saveFailed" to loaded.copy(error = UiError(UiError.Kind.SavePrivacyZones)),
            )
        }
}
