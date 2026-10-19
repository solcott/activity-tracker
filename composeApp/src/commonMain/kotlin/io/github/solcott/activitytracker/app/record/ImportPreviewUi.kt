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
import androidx.compose.ui.unit.dp
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
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.import_adding_to
import io.github.solcott.activitytracker.composeApp.resources.import_duplicate_untitled
import io.github.solcott.activitytracker.composeApp.resources.import_too_far
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

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
        state.addingTo?.let { AddingTo(it, tooFar = state.tooFarFromTarget) }
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

/** The activity a recording is added to, and why it can't be when the file starts too far from it. */
@Composable
private fun AddingTo(target: ExistingActivityPreview, tooFar: Boolean, modifier: Modifier = Modifier) {
    val title = target.title ?: stringResource(Res.string.import_duplicate_untitled)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(Res.string.import_adding_to, listOfNotNull(title, target.start, target.device).joinToString(" · ")),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (tooFar) {
            Text(
                stringResource(Res.string.import_too_far),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
