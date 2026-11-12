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
import io.github.solcott.activitytracker.app.ui.AuthorHeader
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RoutePreview
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import kotlinx.collections.immutable.toImmutableList

/**
 * One timeline item for an athlete's batch import (feed/import-cards): who, how many, over which months, which sports, how far, and a few
 * of the routes. No likes or comments; a tap opens the batch. What it says is [ImportCardTexts]', which the native card asks too.
 */
@Composable
internal fun ImportCard(card: ImportCardView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val openLabel = ImportCardTexts.open().string()
    Card(modifier.fillMaxWidth().clickable(onClickLabel = openLabel, onClick = onClick).handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthorHeader(card.author) {
                Text(
                    ImportCardTexts.range(card, LocalTimeZone.current).string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(ImportCardTexts.title(card).string(), style = MaterialTheme.typography.titleMedium)
            Text(ImportCardTexts.summary(card, LocalUnitSystem.current).string(), style = MaterialTheme.typography.bodyMedium)
            val routes = remember(card) { ImportCardTexts.routes(card) }
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
