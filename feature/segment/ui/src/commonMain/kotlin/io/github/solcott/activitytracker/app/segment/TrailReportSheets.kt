package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.solcott.activitytracker.app.ui.AddPhotosTile
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RemovablePhotoTile
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppFilterChip
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.rememberPhotoPicks
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_done
import io.github.solcott.activitytracker.composeApp.resources.details_photo
import io.github.solcott.activitytracker.composeApp.resources.details_preparing_photos
import io.github.solcott.activitytracker.composeApp.resources.trail_add_photos
import io.github.solcott.activitytracker.composeApp.resources.trail_photos_choose
import io.github.solcott.activitytracker.composeApp.resources.trail_photos_from_rides
import io.github.solcott.activitytracker.composeApp.resources.trail_photos_from_rides_detail
import io.github.solcott.activitytracker.composeApp.resources.trail_photos_limit
import io.github.solcott.activitytracker.composeApp.resources.trail_photos_take
import io.github.solcott.activitytracker.composeApp.resources.trail_report_note
import io.github.solcott.activitytracker.composeApp.resources.trail_report_on_trail
import io.github.solcott.activitytracker.composeApp.resources.trail_report_post
import io.github.solcott.activitytracker.composeApp.resources.trail_report_public
import io.github.solcott.activitytracker.composeApp.resources.trail_report_remove_picked
import io.github.solcott.activitytracker.composeApp.resources.trail_report_share_body
import io.github.solcott.activitytracker.composeApp.resources.trail_report_share_confirm
import io.github.solcott.activitytracker.composeApp.resources.trail_report_share_title
import io.github.solcott.activitytracker.composeApp.resources.trail_report_sheet_title
import io.github.solcott.activitytracker.composeApp.resources.trail_report_surface
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * Reporting conditions (segments/trail-reports) while any of it is open: the report sheet, the add-photos screen in its place while that's
 * open, and the question before a photo from a non-public ride is shared. [segmentName] names the segment the rides passed.
 */
@Composable
internal fun TrailReportSheets(fields: ReportFields, segmentName: String) {
    val send = fields.eventSink
    val addPhotos = fields.addPhotos
    val sheet = fields.sheet
    if (addPhotos != null || sheet != null) {
        AppBottomSheet(
            onDismiss = { send(if (addPhotos != null) ReportEvent.CloseAddPhotos else ReportEvent.Close) },
            sheetState = rememberAppSheetState(),
        ) {
            if (addPhotos != null) AddPhotosContent(addPhotos, segmentName, send) else if (sheet != null) ReportSheetContent(sheet, fields)
        }
    }
    if (fields.confirmingShare) {
        AppAlertDialog(
            title = stringResource(Res.string.trail_report_share_title),
            message = stringResource(Res.string.trail_report_share_body),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.trail_report_share_confirm)) { send(ReportEvent.ConfirmShare(true)) },
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { send(ReportEvent.ConfirmShare(false)) },
        )
    }
}

/**
 * The report sheet: what was ridden, the tags in their two groups (Closed in the error colour), the note with its count, the photos with
 * their remove buttons and a tile to add more, the public notice, and Post report, held back until a tag or photo is chosen.
 */
@Composable
private fun ReportSheetContent(sheet: ReportSheet, fields: ReportFields) {
    val send = fields.eventSink
    Column(
        Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.trail_report_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Text(sheet.subtitle.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TagGroup(stringResource(Res.string.trail_report_surface), sheet.surface, send)
        TagGroup(stringResource(Res.string.trail_report_on_trail), sheet.onTrail, send)
        AppTextField(
            state = sheet.note,
            label = stringResource(Res.string.trail_report_note),
            supportingText = sheet.noteCount.string(),
            isError = !sheet.noteFits,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
            modifier = Modifier.fillMaxWidth(),
        )
        PickedPhotos(sheet, send)
        Text(
            stringResource(Res.string.trail_report_public),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fields.error?.let { ErrorText(it) }
        AppButton(onClick = { send(ReportEvent.Post) }, Modifier.fillMaxWidth(), enabled = sheet.canPost) {
            if (sheet.posting) AppProgressIndicator(small = true) else Text(stringResource(Res.string.trail_report_post))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(title: String, tags: ImmutableList<TagToggle>, send: (ReportEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val warn = MaterialTheme.colorScheme.error
                AppFilterChip(
                    selected = tag.selected,
                    onClick = { send(ReportEvent.Toggle(tag.value)) },
                    label = { Text(tag.label.string(), color = if (tag.warns) warn else Color.Unspecified) },
                    leadingIcon = if (tag.warns) ({ PlatformIcon(AppIcon.Lock, null, tint = warn, size = 18.dp) }) else null,
                )
            }
        }
    }
}

/** The photos the report will carry, each with a remove button, and a tile opening the add-photos screen while there's room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickedPhotos(sheet: ReportSheet, send: (ReportEvent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sheet.photos.forEach { photo ->
            RemovablePhotoTile(
                stringResource(Res.string.trail_report_remove_picked),
                onRemove = { send(ReportEvent.RemovePhoto(photo.key)) },
                size = SHEET_TILE,
            ) {
                AsyncImage(photo.url, contentDescription = photo.alt, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (sheet.canAddPhotos) {
            AddPhotosTile(stringResource(Res.string.trail_add_photos), onClick = { send(ReportEvent.OpenAddPhotos) }, size = SHEET_TILE)
        }
    }
}

/**
 * The add-photos screen, in the sheet's place: Take photo (where there's a camera) and Choose from library, which add straight to the
 * report; then the viewer's own rides that passed the segment, each with its visibility, whose photos are ticked and added together.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPhotosContent(add: AddPhotos, segmentName: String, send: (ReportEvent) -> Unit) {
    val room = (TrailReportRecord.MAX_IMAGES - add.picked).coerceAtLeast(1)
    val picks = rememberPhotoPicks(room) { send(ReportEvent.AddPicked(it)) }
    Column(Modifier.padding(bottom = 24.dp)) {
        AppSheetHeader(
            title = stringResource(Res.string.trail_add_photos),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { send(ReportEvent.CloseAddPhotos) },
            confirmLabel = add.addLabel?.string() ?: stringResource(Res.string.action_done),
            onConfirm = { send(ReportEvent.AddTicked) },
            confirmEnabled = add.addLabel != null,
        )
        Column(
            Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                picks.take?.let { take ->
                    AppSecondaryButton(onClick = take, Modifier.weight(1f), enabled = !picks.preparing) {
                        Text(stringResource(Res.string.trail_photos_take))
                    }
                }
                AppSecondaryButton(onClick = { picks.choose() }, Modifier.weight(1f), enabled = !picks.preparing) {
                    Text(stringResource(Res.string.trail_photos_choose))
                }
            }
            if (picks.preparing) Text(stringResource(Res.string.details_preparing_photos), style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.trail_photos_from_rides), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.trail_photos_from_rides_detail, segmentName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (add.loading) AppProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            add.rides.forEach { ride ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ride.title.string(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
                        VisibilityTag(ride.visibility.string())
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ride.photos.forEach { photo -> RidePhotoTile(photo, onToggle = { send(ReportEvent.TogglePhoto(photo.key)) }) }
                    }
                }
            }
            Text(
                stringResource(Res.string.trail_photos_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A ride's visibility as a small neutral tag. */
@Composable
private fun VisibilityTag(label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** A ride's photo that ticks on a tap, with a check over it while ticked; faded once the report is full. */
@Composable
private fun RidePhotoTile(photo: RidePhoto, onToggle: () -> Unit) {
    val ring = if (photo.selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        Modifier.size(SHEET_TILE)
            .alpha(if (photo.enabled || photo.selected) 1f else DISABLED_ALPHA)
            .clip(THUMB_SHAPE)
            .border(3.dp, ring, THUMB_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .toggleable(photo.selected, enabled = photo.enabled || photo.selected, role = Role.Checkbox) { onToggle() }
            .handCursor(),
    ) {
        AsyncImage(
            photo.url,
            contentDescription = photo.alt ?: stringResource(Res.string.details_photo),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (photo.selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                PlatformIcon(AppIcon.Check, null, tint = MaterialTheme.colorScheme.onPrimary, size = 16.dp)
            }
        }
    }
}

private val SHEET_TILE = 88.dp
private const val DISABLED_ALPHA = 0.4f
