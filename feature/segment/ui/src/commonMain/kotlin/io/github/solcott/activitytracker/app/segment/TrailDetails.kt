package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.string
import kotlinx.collections.immutable.ImmutableList

/**
 * The Trail details section (segments/trail-attributes): its heading when [titled], then one card per [groups] entry (Difficulty, Surface &
 * width, Access & season), each with its title and its rows, a row's label beside what the data says. A Website row is a link that opens
 * its URL in the system browser, only after [TrailTexts.website] checks its scheme again. Nothing when [groups] is empty.
 */
@Composable
internal fun TrailDetailGroups(
    groups: ImmutableList<TrailDetailGroup>,
    modifier: Modifier = Modifier,
    titled: Boolean = true,
    /** "Riders confirmed these in Nov 2026", under the heading (segments/trail-detail-suggestions). */
    lastConfirmed: AppText? = null,
    /** Opens a row's sheet, for a row that [TrailDetail.opens]. */
    onOpenSegment: (String) -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    if (groups.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titled) Text(TrailTexts.title.string(), style = MaterialTheme.typography.titleMedium)
        lastConfirmed?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlatformIcon(AppIcon.Check, null, tint = MaterialTheme.colorScheme.primary, size = 16.dp)
                Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (group in groups) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The card's heading, set apart from its rows by weight, colour and a hairline.
                    Text(
                        group.title.string(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    for (row in group.rows) TrailDetailRow(row, onOpen, onOpenSegment)
                }
            }
        }
    }
}

@Composable
private fun TrailDetailRow(row: TrailDetail, onOpen: (String) -> Unit, onOpenSegment: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val key = row.key
    val tap = if (row.opens && key != null) Modifier.clickable(onClickLabel = row.label.string()) { onOpen(key) } else Modifier
    Column {
        Row(tap.heightIn(min = if (row.opens) 48.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            // Labels are medium weight in a lighter grey, values regular in the text colour, so each row reads label then value.
            Text(
                row.label.string(),
                Modifier.width(LABEL_WIDTH),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LABEL_ALPHA),
            )
            val link = TrailTexts.website(row.link)
            if (link != null) {
                AppTextButton(onClick = { uriHandler.openUri(link) }) { Text(row.value.string()) }
            } else {
                Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        row.value.string(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (row.question) FontStyle.Italic else FontStyle.Normal,
                        color = if (row.question) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    SourceLine(row)
                }
            }
            if (row.opens) PlatformIcon(AppIcon.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // On the Trail page, the segments riders changed this detail on, each opening its page (segments/trail-attributes).
        if (row.varying.isNotEmpty()) {
            Column(Modifier.padding(start = LABEL_WIDTH, bottom = 4.dp)) {
                row.varying.forEach { segment ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSegment(segment.uri) }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(segment.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                segment.detail.string(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PlatformIcon(AppIcon.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A row's source and note: the community badge or the importer's source in grey, then its suggestions or doubt in the brand colour. */
@Composable
private fun SourceLine(row: TrailDetail) {
    if (row.source == null && row.note == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.source?.let { source ->
            if (row.community) {
                Text(
                    source.string(),
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Text(source.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        row.note?.let {
            Text(
                it.string(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The labels' column, wide enough for the longest ("Maintained by", "Typical grade") on one line. */
private val LABEL_WIDTH = 128.dp

/** How much of [androidx.compose.material3.ColorScheme.onSurfaceVariant] a label takes: lighter than it, still 4.5:1 on the card. */
private const val LABEL_ALPHA = 0.85f
