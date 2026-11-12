package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText.Companion.named
import io.github.solcott.activitytracker.app.text.AppText.Companion.written
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone

/** The activity page's figures as both UIs read them (feed/activity-page-layout, dev/shared-display-text). */
class FigureTextsTest {
    private val now = Instant.parse("2026-06-01T18:00:00Z")

    private fun value(value: FigureValue, units: UnitSystem = UnitSystem.Metric) = FigureTexts.value(value, units, now, TimeZone.UTC)

    @Test
    fun aDistanceReadsInTheViewersUnits() {
        assertEquals(named("unit_distance_mi", written("7.83")), value(FigureValue.Distance(12_600.0), UnitSystem.Imperial))
    }

    @Test
    fun aDivesGasesReadInOrder() {
        val gases = FigureValue.Gases(persistentListOf(GasMix(21, 0), GasMix(50, 0)), closedCircuit = false)
        assertEquals(named("text_arrow_pair", named("dive_gas_air"), named("dive_gas_nitrox", written("50"))), value(gases))
    }

    @Test
    fun aPaceIsTheTimeAKilometerTakes() {
        // 3.2 m/s is 5:12.5 /km, rounded once as the chart rounds it.
        assertEquals(named("unit_pace_per_km", written("5:13")), value(FigureValue.Pace(3_200, swim = false)))
    }

    @Test
    fun aStreamValueReadsInItsUnit() {
        assertEquals(named("unit_speed_kmh", written("36.0")), value(FigureValue.Stream(StreamKind.Speed, 10_000.0)))
    }

    @Test
    fun aSensorIsItsKindThenItsMakeAndModel() {
        assertEquals(named("text_dot_pair", LabelText.sensorType("power"), written("SRAM Red")), LabelText.sensor("power", "SRAM", "Red"))
        assertEquals(LabelText.sensorType("power"), LabelText.sensor("power", null, null))
    }

    @Test
    fun staminaJoinsItsRangeAndMinimum() {
        assertEquals(
            named("text_dot_pair", named("page_stamina_range", written("100"), written("70")), named("page_stamina_min", written("48"))),
            value(FigureValue.Stamina(100, 70, 48)),
        )
    }

    @Test
    fun labelsNameEveryFigure() {
        FigureKey.entries
            .filterNot { it in setOf(FigureKey.StreamAverage, FigureKey.LeftRight) }
            .forEach { key -> FigureTexts.label(Figure(key, FigureValue.Missing)) }
        assertEquals(
            named("page_best_power", written("20")),
            FigureTexts.label(Figure(FigureKey.BestPower, FigureValue.Missing, index = 20)),
        )
    }

    @Test
    fun aPaceCardIsTitledPace() {
        val card = MetricCard(DetailSection.Speed, persistentListOf(), pace = PaceKind.Distance)
        assertEquals(named("chart_pace"), FigureTexts.cardTitle(card))
        assertEquals(named("chart_speed"), FigureTexts.cardTitle(card.copy(pace = null)))
    }
}
