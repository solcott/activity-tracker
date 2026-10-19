package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.detail.ActivityCharts
import io.github.solcott.activitytracker.app.detail.AvgMaxTable
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.HeroRow
import io.github.solcott.activitytracker.app.detail.ITEM_GAP
import io.github.solcott.activitytracker.app.detail.MetricCard
import io.github.solcott.activitytracker.app.detail.MetricCardUi
import io.github.solcott.activitytracker.app.detail.StatsList
import io.github.solcott.activitytracker.app.detail.TrainingLoadCard
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.CategoryLock
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import kotlinx.collections.immutable.ImmutableList

/**
 * What the activity's page will show once saved, read-only: the file, then the notices that change what shows (its time zone, a duplicate),
 * the hero, the map, and the training load, cards and stats as the page lays them out.
 */
@Composable
internal fun PreviewContent(state: ImportActivityState, preview: ImportPreview, medium: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
        Text(preview.fileName, style = MaterialTheme.typography.titleMedium)
        preview.clock?.let { clock ->
            TimeZoneNotice(clock, { state.eventSink(ImportActivityEvent.TimeZoneChanged(it)) }, enabled = !state.saving)
        }
        PreviewDuplicate(state, preview)
        val page = state.page
        if (page == null) {
            AppProgressIndicator()
            return@Column
        }
        HeroRow(page.hero)
        preview.polyline?.let { ActivityMap(it, gaps = preview.polylineGaps) }
        page.mapMark?.let { CategoryLock(DetailCategory.MAP, it) }
        if (medium) AvgMaxTable(page.avgMax)
        TrainingLoadCard(page.trainingLoad)
        page.charts?.let { charts -> PreviewCards(page.cards, charts, columns = if (medium) 2 else 1, elevationBehind = medium) }
        StatsList(page.stats, columns = if (medium) 2 else 1)
    }
}

/** The metric cards, [columns] to a row; laps and the other tables take a row of their own. */
@Composable
private fun PreviewCards(cards: ImmutableList<MetricCard>, charts: ActivityCharts, columns: Int, elevationBehind: Boolean) {
    val rows = mutableListOf<List<MetricCard>>()
    for (card in cards) {
        val last = rows.lastOrNull()
        if (last != null && fitsBeside(last, card, columns)) rows[rows.lastIndex] = last + card else rows += listOf(card)
    }
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
            row.forEach {
                val modifier = if (it.section in TABLE_SECTIONS) Modifier.fillMaxWidth() else Modifier.weight(1f)
                MetricCardUi(it, charts, modifier, elevationBehind = elevationBehind)
            }
            if (row.none { it.section in TABLE_SECTIONS }) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Whether [card] can join [row]: neither is a table, which takes a row of its own, and the row has room. */
private fun fitsBeside(row: List<MetricCard>, card: MetricCard, columns: Int): Boolean =
    row.size < columns && card.section !in TABLE_SECTIONS && row.none { it.section in TABLE_SECTIONS }

private val TABLE_SECTIONS = setOf(DetailSection.Laps, DetailSection.Legs, DetailSection.Lengths, DetailSection.Sets)
