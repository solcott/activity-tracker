package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every sample page's cards build their native charts and tables, as the native page asks for them. */
class SampleCardChartsTest {
    private fun page(name: String): ActivityDetailPage = ActivityDetailSamples.variants.getValue(name).content.data as ActivityDetailPage

    @Test
    fun everyVariantsCardsChart() {
        for ((name, state) in ActivityDetailSamples.variants) {
            val page = state.content.data as? ActivityDetailPage ?: continue
            val charts = page.page.charts ?: continue
            page.page.cards.forEach { card -> UnitSystem.entries.forEach { CardLines.of(card, charts, it) } }
            assertTrue(name.isNotEmpty())
        }
    }

    @Test
    fun theMoreChartsPageDrawsItsCurveGearsPairAndSteps() {
        val page = page("moreCharts").page
        val charts = page.charts!!
        val lines = page.cards.map { CardLines.of(it, charts, UnitSystem.Metric) }
        assertEquals(2, lines[0].size, "power and its curve")
        assertTrue(lines[0][0].chart.backdrop.isNotEmpty())
        assertEquals(1, lines[1].single().chart.otherCount)
        assertTrue(lines[2].single().chart.steps)
        assertTrue(lines[3].single().chart.steps)
    }

    @Test
    fun theDivePageMarksAndDrawsBothTanks() {
        val page = page("dive").page
        val charts = page.charts!!
        val depth = CardLines.of(page.cards[0], charts, UnitSystem.Metric).single().chart
        assertEquals(2, depth.markCount)
        assertEquals(1, depth.otherCount)
        assertEquals(1, CardLines.of(page.cards[1], charts, UnitSystem.Metric).single().chart.otherCount)
    }

    @Test
    fun theStructuresPageHasEachTable() {
        val charts = page("structures").page.charts!!
        assertEquals(4, StructureTableTexts.legs(charts.legs, UnitSystem.Metric).rows.size)
        assertEquals(4, StructureTableTexts.lengths(charts.lengths).rows.size)
        assertEquals(3, StructureTableTexts.sets(charts.sets, UnitSystem.Metric).rows.size)
    }
}
