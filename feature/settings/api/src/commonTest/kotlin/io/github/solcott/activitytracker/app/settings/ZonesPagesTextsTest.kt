package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.training.EffectiveTraining
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneBasis
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** What the zones pages say and decide for both UIs ("The zones pages read the same in both UIs", dev/shared-display-text). */
class ZonesPagesTextsTest {
    private fun written(text: String) = AppText.written(text)

    private val km = AppText.named("zones_pace_unit_km")

    @Test
    fun heartRateAndPowerRangesCarryTheirUnit() {
        val zone = Zone("3", ZoneName.Tempo, low = 143, high = 161)
        val last = Zone("5", ZoneName.Vo2Max, low = 180, high = null)
        assertEquals(
            AppText.named("zones_summary_value_bpm", AppText.named("zones_range", written("143"), written("161"))),
            ZoneTexts.range(zone, ZoneKind.HeartRate, PaceUnit.PerKilometer),
        )
        assertEquals(
            AppText.named("zones_summary_value_bpm", AppText.named("zones_range_open", written("180"))),
            ZoneTexts.range(last, ZoneKind.HeartRate, PaceUnit.PerKilometer),
        )
        // Plain whole numbers, as the shared page wrote them: a bound is never grouped.
        assertEquals(
            AppText.named("zones_summary_value_watts", AppText.named("zones_range", written("1000"), written("1200"))),
            ZoneTexts.range(Zone("6", ZoneName.AnaerobicCapacity, 1000, 1200), ZoneKind.Power, PaceUnit.PerKilometer),
        )
    }

    @Test
    fun speedRangesReadAsPacesSlowestFirst() {
        val unit = PaceUnit.PerKilometer
        fun pace(mmPerSecond: Long) = written(PaceText.format(mmPerSecond, unit))
        assertEquals(
            AppText.named("zones_pace_slower", pace(3_001), km),
            ZoneTexts.range(Zone("1", ZoneName.Recovery, low = 0, high = 3_000), ZoneKind.Speed, unit),
        )
        assertEquals(
            AppText.named("zones_pace_range", pace(3_001), pace(3_501), km),
            ZoneTexts.range(Zone("2", ZoneName.Aerobic, low = 3_001, high = 3_500), ZoneKind.Speed, unit),
        )
        assertEquals(
            AppText.named("zones_pace_faster", pace(4_000), km),
            ZoneTexts.range(Zone("7", ZoneName.Maximum, low = 4_000, high = null), ZoneKind.Speed, unit),
        )
        assertEquals("5:10", PaceText.format(3_226, unit))
    }

    @Test
    fun aZoneIsNamedAndItsKindAndModelHeaded() {
        assertEquals(AppText.named("zone_name_tempo"), ZoneTexts.name(Zone("3", ZoneName.Tempo, 143, 161)))
        assertEquals(AppText.named("zones_speed_heading"), ZoneTexts.heading(ZoneKind.Speed))
        assertEquals(AppText.named("zones_heart_rate_heading"), ZoneTexts.heading(ZoneKind.HeartRate))
        assertEquals(AppText.named("zones_power_heading"), ZoneTexts.heading(ZoneKind.Power))
        assertEquals(AppText.named("zones_pace_unit_100yd"), ZoneTexts.paceUnit(PaceUnit.Per100Yards))
        assertEquals(
            AppText.Named("zone_model_cogganHeartRate", fallback = written("cogganHeartRate")),
            ZoneTexts.model(ZoneModels.defaultHeartRate),
        )
        // Another client's model reads as its token.
        val unknown = ZoneModel("someoneElses", ZoneBasis.MaxHeartRate, emptyList())
        assertEquals(written("someoneElses"), (ZoneTexts.model(unknown) as AppText.Named).fallback)
        assertEquals(AppText.named("zones_basis_reserve"), ZoneTexts.basis(ZoneBasis.HeartRateReserve))
        assertEquals(AppText.named("zones_needs_ftp"), ZoneTexts.missing(ZoneBasis.FunctionalThresholdPower))
    }

    @Test
    fun theDefaultsListEveryHeartRateAndPower() {
        val lines = TrainingZonesTexts.thresholds(summary(sport = null))
        assertEquals(
            listOf(
                "zones_summary_threshold_heart_rate",
                "zones_summary_max_heart_rate",
                "zones_summary_resting_heart_rate",
                "zones_summary_threshold_power",
            ),
            lines.map { (it.label as AppText.Named).name },
        )
        assertEquals(AppText.named("zones_summary_value_bpm", written("170")), lines[0].value)
        assertEquals(AppText.named("zones_summary_value_watts", written("250")), lines[3].value)
        assertTrue(lines.none { it.muted })
        assertEquals(AppText.named("zones_overview_default"), TrainingZonesTexts.heading(summary(sport = null)))
        assertEquals(AppText.named("zones_overview_default_help"), TrainingZonesTexts.help(summary(sport = null)))
    }

    @Test
    fun aSportMarksWhatItTakesFromTheDefaults() {
        val run =
            summary(
                sport = Sport.RUN,
                training = EffectiveTraining(maxHeartRate = 190, thresholdHeartRate = 172, thresholdSpeed = 3_704),
                override = SportTraining(sport = Sport.RUN, thresholdHeartRate = 172, thresholdSpeed = 3_704),
            )
        val lines = TrainingZonesTexts.thresholds(run)
        assertEquals(
            listOf(
                "zones_summary_threshold_pace",
                "zones_summary_threshold_heart_rate",
                "zones_summary_max_heart_rate",
                "zones_summary_threshold_power",
            ),
            lines.map { (it.label as AppText.Named).name },
        )
        assertEquals(AppText.named("zones_summary_value_pace", written("4:30"), km), lines[0].value)
        assertFalse(lines[1].muted)
        assertEquals(AppText.named("zones_overview_from_default", AppText.named("zones_summary_value_bpm", written("190"))), lines[2].value)
        assertTrue(lines[2].muted)
        // No power anywhere: not set, and muted, never "(default)".
        assertEquals(AppText.named("zones_summary_unset"), lines[3].value)
        assertTrue(lines[3].muted)
        assertEquals(AppText.named("zones_overview_heading", LabelText.sport(Sport.RUN)), TrainingZonesTexts.heading(run))
        assertEquals(AppText.named("zones_overview_sport_help", LabelText.sport(Sport.RUN)), TrainingZonesTexts.help(run))
    }

    @Test
    fun aHeartRateOnlySportListsNoPaceOrPower() {
        val hike = summary(sport = Sport.HIKE, kinds = ZoneKindOption.forSport(Sport.HIKE))
        assertEquals(2, TrainingZonesTexts.thresholds(hike).size)
    }

    @Test
    fun theThresholdsTakeWhatTheLexiconDoes() {
        assertEquals(Threshold.THRESHOLD_HEART_RATE, ZoneThreshold.ThresholdHeartRate.token)
        assertEquals(Threshold.THRESHOLD_SPEED, ZoneThreshold.Pace.token)
        assertEquals(3, ZoneThreshold.MaxHeartRate.maxLength)
        assertEquals(4, ZoneThreshold.Power.maxLength)
        assertTrue(ZoneThreshold.RestingHeartRate.digitsOnly)
        assertFalse(ZoneThreshold.Pace.digitsOnly)
        // The slowest threshold pace PaceText writes, in any unit.
        assertEquals(6, ZoneThreshold.Pace.maxLength)
        assertTrue(ZoneThreshold.MaxHeartRate.isValid("", PaceUnit.PerKilometer))
        assertFalse(ZoneThreshold.MaxHeartRate.isValid("300", PaceUnit.PerKilometer))
        assertTrue(ZoneThreshold.Power.isValid("2500", PaceUnit.PerKilometer))
        assertFalse(ZoneThreshold.Power.isValid("2501", PaceUnit.PerKilometer))
        assertTrue(ZoneThreshold.Pace.isValid("4:30", PaceUnit.PerKilometer))
        assertFalse(ZoneThreshold.Pace.isValid("soon", PaceUnit.PerKilometer))
    }

    @Test
    fun theDefaultsFormSavesOnlyWhatItTakes() {
        val state = zonesState(maxHeartRate = "190")
        assertTrue(state.canSave)
        assertFalse(zonesState(maxHeartRate = "300").canSave)
        assertFalse(zonesState(maxHeartRate = "190", saving = true).canSave)
        assertFalse(zonesState(maxHeartRate = "190", content = ContentState(Unit, null, LoadStatus.Loading)).canSave)
        assertFalse(state.isValid(ZoneThreshold.MaxHeartRate, "300"))
        assertTrue(state.autoUpdates(ZoneThreshold.MaxHeartRate))
        assertFalse(state.autoUpdates(ZoneThreshold.Power))
    }

    @Test
    fun aSportsFormHintsItsDefaults() {
        val state = sportState()
        assertEquals(AppText.named("sport_zones_default", written("190")), SportZonesTexts.defaultHint(state, ZoneThreshold.MaxHeartRate))
        assertNull(SportZonesTexts.defaultHint(state, ZoneThreshold.Power))
        assertNull(SportZonesTexts.defaultHint(state, ZoneThreshold.Pace))
        assertEquals(AppText.named("sport_zones_title", LabelText.sport(Sport.RUN)), SportZonesTexts.title(Sport.RUN))
        assertEquals(AppText.named("sport_zones_help", LabelText.sport(Sport.RUN)), SportZonesTexts.help(Sport.RUN))
        assertEquals(AppText.named("sport_zones_remove", LabelText.sport(Sport.RUN)), SportZonesTexts.remove(Sport.RUN))
        assertEquals(AppText.named("sport_zones_threshold_pace", km), SportZonesTexts.paceLabel(PaceUnit.PerKilometer))
        assertTrue(state.canSave)
        assertTrue(state.isValid(ZoneThreshold.Pace, "4:30"))
        assertFalse(state.isValid(ZoneThreshold.Pace, "4:3"))
    }

    @Test
    fun privacyDistancesAndConditions() {
        assertEquals(AppText.named("privacy_zones_off"), PrivacyZonesTexts.distance(0, UnitSystem.Metric))
        assertEquals(StatText.distance(800.0, UnitSystem.Imperial), PrivacyZonesTexts.distance(800, UnitSystem.Imperial))
        assertEquals(AppText.named("privacy_zones_locating"), PrivacyZonesTexts.addHere(locating = true))
        assertEquals(AppText.named("privacy_zones_add_here"), PrivacyZonesTexts.addHere(locating = false))
        val state = privacyState()
        assertEquals(listOf(0, 200, 400), state.hideEndsChoices)
        assertTrue(state.enabled)
        assertTrue(state.canAddHere)
        assertFalse(state.copy(locating = true).canAddHere)
        assertFalse(state.copy(saving = true).enabled)
        assertFalse(state.copy(canEdit = false).canAddHere)
    }

    @Test
    fun zonesAreShownToEveryVisibility() {
        assertEquals(Visibility.all, TrainingZonesState().visibilityOptions)
    }

    private fun summary(
        sport: String?,
        training: EffectiveTraining =
            EffectiveTraining(functionalThresholdPower = 250, maxHeartRate = 190, thresholdHeartRate = 170, restingHeartRate = 55),
        override: SportTraining? = null,
        kinds: kotlinx.collections.immutable.ImmutableList<ZoneKindOption> = ZoneKindOption.forSport(sport),
    ) =
        ZoneSummary(
            sport = sport,
            kinds = kinds,
            training = training,
            override = override,
            paceUnit = PaceUnit.PerKilometer,
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateZones = null,
            powerModel = ZoneModels.defaultPower,
            powerZones = null,
            speedModel = ZoneModels.frielRunningPace,
            speedZones = null,
        )

    private fun zonesState(
        maxHeartRate: String,
        saving: Boolean = false,
        content: ContentState<Unit> = ContentState(Unit, Origin.Network, LoadStatus.Idle),
    ) =
        ZonesState(
            thresholdHeartRate = textFieldStateOf("170"),
            maxHeartRate = textFieldStateOf(maxHeartRate),
            restingHeartRate = textFieldStateOf(""),
            functionalThresholdPower = textFieldStateOf(""),
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateModels = ZoneModels.heartRate.toImmutableList(),
            heartRateZones = null,
            powerModel = ZoneModels.defaultPower,
            powerModels = ZoneModels.power.toImmutableList(),
            powerZones = null,
            autoUpdate = persistentSetOf(Threshold.MAX_HEART_RATE),
            content = content,
            saving = saving,
            error = null,
            eventSink = {},
        )

    private fun sportState() =
        SportZonesState(
            sport = Sport.RUN,
            kinds = ZoneKindOption.forSport(Sport.RUN),
            thresholdHeartRate = textFieldStateOf("172"),
            maxHeartRate = textFieldStateOf(),
            functionalThresholdPower = textFieldStateOf(),
            thresholdPace = textFieldStateOf("4:30"),
            paceUnit = PaceUnit.PerKilometer,
            defaults = EffectiveTraining(maxHeartRate = 190, thresholdHeartRate = 170),
            heartRateModel = ZoneModels.defaultHeartRate,
            heartRateModels = ZoneModels.heartRate.toImmutableList(),
            heartRateZones = null,
            powerModel = ZoneModels.defaultPower,
            powerModels = ZoneModels.power.toImmutableList(),
            powerZones = null,
            speedModel = ZoneModels.frielRunningPace,
            speedModels = ZoneModels.speed.toImmutableList(),
            speedZones = null,
            saved = false,
            content = ContentState(Unit, Origin.Network, LoadStatus.Idle),
            saving = false,
            error = null,
            eventSink = {},
        )

    private fun privacyState() =
        PrivacyZonesState(
            content = ContentState(Unit, Origin.Network, LoadStatus.Idle),
            zones = persistentListOf(PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 400, label = null)),
            hideEndsMeters = 0,
            choices = persistentListOf(200, 400),
            canEdit = true,
            saving = false,
            canUseLocation = true,
            hasLocationPermission = true,
            locating = false,
            label = textFieldStateOf(),
            error = null,
            eventSink = {},
        )
}
