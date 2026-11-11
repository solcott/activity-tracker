package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.data.training.forSport
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** What the zones contracts offer both UIs (dev/ios-swift-interop, "Zones are named the same in both UIs" in dev/shared-display-text). */
class ZonesContractsTest {
    @Test
    fun aSportsKindsAreObjectsInTheOrderTheyShow() {
        assertEquals(listOf(ZoneKind.HeartRate, ZoneKind.Power), ZoneKindOption.forSport(null).map { it.kind })
        assertEquals(listOf(ZoneKind.HeartRate, ZoneKind.Power, ZoneKind.Speed), ZoneKindOption.forSport(Sport.RUN).map { it.kind })
        assertEquals(listOf(ZoneKind.HeartRate, ZoneKind.Speed), ZoneKindOption.forSport(Sport.SWIM).map { it.kind })
        assertEquals(listOf(ZoneKind.HeartRate), ZoneKindOption.forSport(Sport.HIKE).map { it.kind })
    }

    @Test
    fun aKindIsOfferedOnlyWhenItsInTheList() {
        val swim =
            ZoneSummary(
                sport = Sport.SWIM,
                kinds = ZoneKindOption.forSport(Sport.SWIM),
                training = TrainingDetails().forSport(Sport.SWIM),
                override = null,
                paceUnit = PaceUnit.Per100Meters,
                heartRateModel = ZoneModels.defaultHeartRate,
                heartRateZones = null,
                powerModel = ZoneModels.defaultPower,
                powerZones = null,
                speedModel = ZoneModels.frielRunningPace,
                speedZones = null,
            )
        assertTrue(swim.offers(ZoneKind.HeartRate))
        assertTrue(swim.offers(ZoneKind.Speed))
        assertFalse(swim.offers(ZoneKind.Power))
    }

    @Test
    fun aZoneIsLabelledByItsNumber() {
        assertEquals(
            AppText.named("zones_label", AppText.written("5a")),
            ZoneTexts.label(Zone("5a", ZoneName.SuperThreshold, low = 170, high = 175)),
        )
    }

    @Test
    fun aPrivacyZoneIsNamedByItsLabelOrItsPlace() {
        assertEquals(AppText.written("Home"), ZoneTexts.privacyZoneName(zone(label = "Home"), index = 0))
        assertEquals(AppText.named("privacy_zones_unnamed", AppText.written("2")), ZoneTexts.privacyZoneName(zone(label = null), index = 1))
    }

    @Test
    fun theShownRowsAreACopyEvenWhenAllShow() {
        val rows = (1..5).map { ThresholdHistoryRow(ThresholdEntry(Threshold.FUNCTIONAL_THRESHOLD_POWER, 200L + it), null, it == 1) }
        val all = section(rows.toImmutableList(), showingAll = true)
        assertEquals(rows, all.shownRows)
        assertNotSame<List<ThresholdHistoryRow>>(all.rows, all.shownRows)
        assertEquals(rows.take(3), section(rows.toImmutableList(), showingAll = false).shownRows)
        // An empty persistent list is the one Swift can't read, though a threshold always has a row.
        val empty = section(persistentListOf(), showingAll = true)
        assertNotSame<List<ThresholdHistoryRow>>(empty.rows, empty.shownRows)
    }

    private fun zone(label: String?) = PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 400, label = label)

    private fun section(rows: ImmutableList<ThresholdHistoryRow>, showingAll: Boolean) =
        ThresholdHistorySection(
            threshold = Threshold.FUNCTIONAL_THRESHOLD_POWER,
            sport = null,
            paceUnit = PaceUnit.PerKilometer,
            rows = rows,
            showingAll = showingAll,
            chart = null,
            undatedCount = 5,
            change = null,
        )
}
