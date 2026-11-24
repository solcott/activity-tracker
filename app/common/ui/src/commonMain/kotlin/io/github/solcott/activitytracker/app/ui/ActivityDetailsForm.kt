package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.solcott.activitytracker.app.edit.ActivityDetailsFields
import io.github.solcott.activitytracker.app.edit.ActivityTextLimits
import io.github.solcott.activitytracker.app.edit.DetailsEvent
import io.github.solcott.activitytracker.app.edit.DetailsFormTexts
import io.github.solcott.activitytracker.app.edit.EditMedia
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.details_add_photos
import io.github.solcott.activitytracker.composeApp.resources.details_description_label
import io.github.solcott.activitytracker.composeApp.resources.details_description_placeholder
import io.github.solcott.activitytracker.composeApp.resources.details_dive_site_label
import io.github.solcott.activitytracker.composeApp.resources.details_photos_heading
import io.github.solcott.activitytracker.composeApp.resources.details_preparing_photos
import io.github.solcott.activitytracker.composeApp.resources.details_remove_photo
import io.github.solcott.activitytracker.composeApp.resources.details_title_label
import io.github.solcott.activitytracker.textfield.TextFieldState
import org.jetbrains.compose.resources.stringResource

/**
 * What an athlete writes about an activity, shared by the import and edit screens: its [title] (blank saves as [titlePlaceholder]), then
 * [fields]' description, a dive's site, and photos, each compressed here ([compressPhoto]) as it's picked.
 */
@Composable
fun ActivityDetailsForm(
    title: TextFieldState,
    titlePlaceholder: String?,
    fields: ActivityDetailsFields,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivityTitleField(title, stringResource(Res.string.details_title_label), placeholder = titlePlaceholder, enabled = enabled)
        fields.diveSite?.let { site ->
            AppTextField(
                site,
                label = stringResource(Res.string.details_dive_site_label),
                enabled = enabled,
                lineLimits = TextFieldLineLimits.SingleLine,
                leadingIcon = { PlatformIcon(AppIcon.Location, contentDescription = null, size = 20.dp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActivityDescriptionField(fields.description, enabled = enabled)
        PhotoGrid(fields, enabled)
    }
}

/**
 * An activity's title, on every form that writes one, with its counter under it once it nears its limit, in the error colour past it
 * ([ActivityTextLimits]).
 */
@Composable
fun ActivityTitleField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    val text = state.text.toString()
    AppTextField(
        state,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        isError = !ActivityTextLimits.titleFits(text),
        supportingText = ActivityTextLimits.titleCounter(text)?.string(),
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * What the athlete writes about an activity: the details form's description, also on manual entry and Record, with its counter as
 * [ActivityTitleField] has.
 */
@Composable
fun ActivityDescriptionField(state: TextFieldState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val text = state.text.toString()
    AppTextField(
        state,
        label = stringResource(Res.string.details_description_label),
        placeholder = stringResource(Res.string.details_description_placeholder),
        enabled = enabled,
        isError = !ActivityTextLimits.descriptionFits(text),
        supportingText = ActivityTextLimits.descriptionCounter(text)?.string(),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 10),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The photos, each with a remove button, and a tile to pick more. */
@Composable
private fun PhotoGrid(fields: ActivityDetailsFields, enabled: Boolean, modifier: Modifier = Modifier) {
    val remaining = fields.maxMedia - fields.media.size
    val picks = rememberPhotoPicks(remaining) { fields.eventSink(DetailsEvent.PhotosPicked(it)) }
    val preparing = picks.preparing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.details_photos_heading), style = MaterialTheme.typography.labelLarge)
            Text(
                DetailsFormTexts.photosCount(fields).string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.media.forEachIndexed { index, media ->
                PhotoTile(media, onRemove = { fields.eventSink(DetailsEvent.RemoveMedia(index)) }, enabled = enabled)
            }
            if (fields.canAddMedia) {
                AddPhotosTile(onClick = { picks.choose() }, enabled = enabled && !preparing, preparing = preparing)
            }
        }
        if (preparing) {
            Text(stringResource(Res.string.details_preparing_photos), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PhotoTile(media: EditMedia, onRemove: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(TILE_SIZE).clip(TILE_SHAPE).background(MaterialTheme.colorScheme.surfaceVariant)) {
        val model: Any =
            when (media) {
                is EditMedia.Existing ->
                    when (val item = media.item) {
                        is MediaItem.Photo -> item.url
                    }
                is EditMedia.Picked -> remember(media) { media.photo.bytes.toByteArray() }
            }
        AsyncImage(model, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        AppIconButton(
            AppIcon.Close,
            contentDescription = stringResource(Res.string.details_remove_photo),
            onClick = onRemove,
            enabled = enabled,
            tint = Color.White,
            modifier =
                Modifier.align(Alignment.TopEnd).padding(2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun AddPhotosTile(onClick: () -> Unit, enabled: Boolean, preparing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(TILE_SIZE)
            .clip(TILE_SHAPE)
            .border(1.dp, MaterialTheme.colorScheme.outline, TILE_SHAPE)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .handCursor(),
        contentAlignment = Alignment.Center,
    ) {
        if (preparing) {
            AppProgressIndicator(small = true)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlatformIcon(AppIcon.AddPhoto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(Res.string.details_add_photos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val TILE_SIZE = 96.dp
private val TILE_SHAPE = RoundedCornerShape(8.dp)
