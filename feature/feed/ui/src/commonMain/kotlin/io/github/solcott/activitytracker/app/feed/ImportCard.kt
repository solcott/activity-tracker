package io.github.solcott.activitytracker.app.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.settings.monthName
import io.github.solcott.activitytracker.app.ui.AuthorHeader
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.RoutePreview
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.import_batch_month
import io.github.solcott.activitytracker.composeApp.resources.import_card_open
import io.github.solcott.activitytracker.composeApp.resources.import_card_range
import io.github.solcott.activitytracker.composeApp.resources.import_card_sport
import io.github.solcott.activitytracker.composeApp.resources.import_card_title
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One timeline item for an athlete's batch import (feed/import-cards): who, how many, over which months, which sports, how far, and a few
 * of the routes. No likes or comments; a tap opens the batch.
 */
@Composable
internal fun ImportCard(card: ImportCardView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val openLabel = stringResource(Res.string.import_card_open)
    Card(modifier.fillMaxWidth().clickable(onClickLabel = openLabel, onClick = onClick).handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthorHeader(card.author) {
                Text(
                    stringResource(Res.string.import_card_range, monthYear(card.earliestStartedAt), monthYear(card.latestStartedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                pluralStringResource(Res.plurals.import_card_title, card.count.toInt(), card.count.toInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            val sports = card.sports.map { stringResource(Res.string.import_card_sport, sportLabel(it.sport), it.count.toInt()) }
            Text(
                (sports + listOfNotNull(card.distance?.let { Format.distance(it.toDouble()) })).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            val routes = remember(card) { card.routes.orEmpty().take(ImportCardView.MAX_ROUTES) }
            if (routes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    routes.forEach { route ->
                        RoutePreview(
                            route.polyline,
                            Modifier.weight(1f),
                            height = 72.dp,
                            gaps = route.polylineGaps?.toImmutableList(),
                        )
                    }
                }
            }
        }
    }
}

/** [instant]'s month and year in the viewer's zone, as the batch form heads its months. */
@Composable
private fun monthYear(instant: Instant): String {
    val date = instant.toLocalDateTime(LocalTimeZone.current).date
    return stringResource(Res.string.import_batch_month, monthName(date.month.ordinal + 1), date.year)
}
