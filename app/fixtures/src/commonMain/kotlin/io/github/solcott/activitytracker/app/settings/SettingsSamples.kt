package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.data.training.EffectiveTraining
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
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
                units = UnitSystem.Metric,
                saving = false,
                error = null,
                privacyZoneCount = 1,
                segmentMatching = true,
                eventSink = {},
            )

    val all: Map<String, SettingsState>
        get() = mapOf(SampleKind.LOADED to default)
}

object TrainingZonesSamples {
    private val summary =
        ZoneSummary(
            sport = null,
            kinds = persistentSetOf(ZoneKind.HeartRate, ZoneKind.Power),
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
}

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
                        )
                    ),
            )
        )

    val all: Map<String, ThresholdHistoryState>
        get() = contentSamples(persistentListOf(), groups) { ThresholdHistoryState(it) }
}

object ZonesSamples {
    private fun state(content: ContentState<Unit>, threshold: String = "170") =
        ZonesState(
            thresholdHeartRate = TextFieldState(threshold),
            maxHeartRate = TextFieldState("190"),
            restingHeartRate = TextFieldState("55"),
            functionalThresholdPower = TextFieldState("250"),
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
}

object SportZonesSamples {
    private fun state(content: ContentState<Unit>, pace: String = "4:30") =
        SportZonesState(
            sport = Sport.RUN,
            kinds = persistentSetOf(ZoneKind.HeartRate, ZoneKind.Speed),
            thresholdHeartRate = TextFieldState("172"),
            maxHeartRate = TextFieldState(),
            functionalThresholdPower = TextFieldState(),
            thresholdPace = TextFieldState(pace),
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
}

/** Meters of each route end hidden, as the privacy zones screen offers them. */
private val HIDE_ENDS_CHOICES = persistentListOf(0, 200, 400, 800)

object PrivacyZonesSamples {
    private fun state(content: ContentState<Unit>, zones: ImmutableList<PrivacyZone>?) =
        PrivacyZonesState(
            content = content,
            zones = zones,
            hideEndsMeters = 200,
            choices = HIDE_ENDS_CHOICES,
            canEdit = zones != null,
            saving = false,
            canUseLocation = false,
            hasLocationPermission = false,
            locating = false,
            label = TextFieldState(),
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
}
