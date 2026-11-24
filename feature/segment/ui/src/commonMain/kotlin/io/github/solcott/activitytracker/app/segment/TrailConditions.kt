package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.details_photo
import io.github.solcott.activitytracker.composeApp.resources.trail_add_photos
import io.github.solcott.activitytracker.composeApp.resources.trail_conditions_title
import io.github.solcott.activitytracker.composeApp.resources.trail_photo_hidden
import io.github.solcott.activitytracker.composeApp.resources.trail_report_conditions
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

// What a segment is like now (segments/trail-reports): the Segment page's Conditions card and the pieces the Trail page, the reports list
// and the sheets share with it: condition chips and photo thumbnails.

/**
 * The Conditions card: its title (opening every report) with the count once read; the lead tag large, Closed in the error colour with its
 * icon, and its riders line; the other tags as chips; the newest note; or, with nothing current, that there's nothing and the last report.
 * Then the photo strip with "+13" over its last one and See all, the question for a recent rider, and Report conditions and Add photos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConditionsCard(card: ConditionsCard, send: (ReportEvent) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().testTag(CONDITIONS_CARD_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button) { send(ReportEvent.OpenReports) }
                    .handCursor(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.trail_conditions_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                card.count?.let {
                    Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlatformIcon(AppIcon.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
            }
            card.lead?.takeIf { card.leadLine != null }?.let { lead -> Lead(lead, card.closed, card.leadLine) }
            card.empty?.let { empty ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(empty.string(), style = MaterialTheme.typography.titleSmall)
                    card.emptyDetail?.let {
                        Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (card.tags.isNotEmpty() && card.empty == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.tags.forEach { ConditionChip(it) }
                }
            }
            card.note?.let { note ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(note.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        AppText.named("path_segment_condition", AppText.written(note.author), note.age).string(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (card.photos.isNotEmpty()) {
                // See all sits close under the strip it opens.
                Column {
                    PhotoStrip(card.photos, card.morePhotos, onOpen = { send(ReportEvent.OpenPhoto(it)) })
                    card.seeAll?.let { seeAll -> AppTextButton(onClick = { send(ReportEvent.OpenPhoto(0)) }) { Text(seeAll.string()) } }
                }
            }
            card.ask?.let { Text(it.string(), style = MaterialTheme.typography.bodyLarge) }
            if (card.offersReport) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(onClick = { send(ReportEvent.Open) }, Modifier.weight(1f)) {
                        Text(stringResource(Res.string.trail_report_conditions))
                    }
                    AppSecondaryButton(onClick = { send(ReportEvent.OpenAddPhotos) }, Modifier.weight(1f)) {
                        Text(stringResource(Res.string.trail_add_photos))
                    }
                }
            }
        }
    }
}

/** The lead tag in large type, Closed in the error colour behind its icon, over its riders line. */
@Composable
private fun Lead(lead: AppText, closed: Boolean, line: AppText?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val color = if (closed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            if (closed) PlatformIcon(AppIcon.Lock, null, tint = color, size = 24.dp)
            Text(lead.string(), style = MaterialTheme.typography.headlineSmall, color = color)
        }
        line?.let { Text(it.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * A condition as a chip: neutral and tonal, since trail-sign colours are for ratings alone; [closed] takes the error container with the
 * closed icon, so it reads without colour too.
 */
@Composable
internal fun ConditionChip(text: AppText, modifier: Modifier = Modifier, closed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier,
        shape = CHIP_SHAPE,
        color = if (closed) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (closed) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (closed) PlatformIcon(AppIcon.Lock, null, size = 14.dp)
            Text(text.string(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Up to four photos in a row of squares, [more] ("+13") over the last; a tap opens the one tapped ([onOpen] with its index), or first
 * reveals one blurred behind its label.
 */
@Composable
internal fun PhotoStrip(
    photos: ImmutableList<PhotoThumb>,
    more: AppText?,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    count: Int = TrailReportTexts.STRIP,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.take(count).forEachIndexed { index, photo ->
            val last = index == count - 1
            PhotoThumbTile(photo, onOpen = { onOpen(index) }, Modifier.weight(1f).aspectRatio(1f), cover = more?.takeIf { last })
        }
        // Keeps a short strip's tiles the size a full one's are.
        repeat(count - photos.size.coerceAtMost(count)) { Box(Modifier.weight(1f)) }
    }
}

/** A photo thumbnail that opens on a tap: blurred behind its label's reason until tapped once, with [cover] ("+13") drawn over it. */
@Composable
internal fun PhotoThumbTile(photo: PhotoThumb, onOpen: () -> Unit, modifier: Modifier = Modifier, cover: AppText? = null) {
    var revealed by remember(photo.url) { mutableStateOf(false) }
    val hidden = photo.blur?.takeUnless { revealed }
    Box(modifier.clip(THUMB_SHAPE).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AsyncImage(
            photo.url,
            contentDescription = photo.alt ?: stringResource(Res.string.details_photo),
            modifier =
                Modifier.fillMaxSize()
                    .then(if (hidden != null) Modifier.blur(BLUR_RADIUS).clearAndSetSemantics {} else Modifier)
                    .clickable(role = Role.Image, onClick = onOpen)
                    .handCursor(),
            contentScale = ContentScale.Crop,
        )
        if (hidden != null) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SCRIM_ALPHA)).clickable(
                    role = Role.Button,
                ) {
                    revealed = true
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Text(hidden.string(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                Text(
                    stringResource(Res.string.trail_photo_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (cover != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_ALPHA)).clickable(onClick = onOpen), Alignment.Center) {
                Text(cover.string(), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

/** Where a test finds the Conditions card. */
internal const val CONDITIONS_CARD_TAG = "conditions-card"

internal val THUMB_SHAPE = RoundedCornerShape(8.dp)
private val CHIP_SHAPE = RoundedCornerShape(8.dp)
private val BLUR_RADIUS = 16.dp
private const val SCRIM_ALPHA = 0.7f
private const val COVER_ALPHA = 0.45f
