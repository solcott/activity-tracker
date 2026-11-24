package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform

/**
 * A row of a list that shares a selection with a map of lines (segments/segment-browse, segments/path-page, dive-sites/site-browse): its
 * [rating]'s swatch when it is rated, beside its [name]'s first line, then under the name its [details] line and an [extra] line when
 * given, a [closed] line in the error colour when riders say it's closed, and [trailing] at its end (a Trail row's leader). It's marked
 * while [selected] or under the mouse pointer (here or on its line: [hover] holds its [key]), and a mouse over it puts its [key] in
 * [hover], so the map highlights its line. A tap on a touch screen calls [onTap], which selects it or opens it once selected; a click with
 * a pointer calls [onOpen]. [indent] moves its content in under a heading, while its mark still fills the list's width.
 */
@Composable
fun SelectableRow(
    key: String,
    name: AppText,
    details: AppText,
    selected: Boolean,
    hover: MapBrowseHover,
    onTap: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    rating: TrailRating? = null,
    indent: Dp = 0.dp,
    extra: AppText? = null,
    trailing: (@Composable () -> Unit)? = null,
    closed: AppText? = null,
) {
    val touch = LocalUiPlatform.current.isMobile
    val nameStyle = MaterialTheme.typography.titleMedium
    Row(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    selected -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ALPHA))
                    hover.uri == key -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    else -> Modifier
                },
            )
            .mapBrowseHover(hover, key)
            .semantics { this.selected = selected }
            .clickable(onClickLabel = MapBrowseTexts.rowAction(selected, touch).string()) { if (touch) onTap() else onOpen() }
            .padding(start = 16.dp + indent, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rating != null) {
            // A rated trail's swatch (segments/trail-ratings), centred on the name's first line so a wrapped name and the lines under it
            // start under the name, not under the swatch.
            Box(Modifier.height(with(LocalDensity.current) { nameStyle.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                RatingSwatch(rating)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name.string(), style = nameStyle)
            Text(details.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (extra != null) Text(extra.string(), style = MaterialTheme.typography.bodyMedium)
            if (closed != null) {
                // Riders say it's closed (segments/trail-reports): the icon too, so it reads without colour.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlatformIcon(AppIcon.Closed, null, tint = MaterialTheme.colorScheme.error, size = 16.dp)
                    Text(closed.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        trailing?.invoke()
    }
}

/** The selected row's tint: the brand colour, faint enough that its text reads as on the plain list. */
private const val SELECTED_ALPHA = 0.12f

/**
 * Puts [key] in [hover] while a mouse pointer is over this element, so the map highlights what [key] names, and takes it out when the
 * pointer leaves, unless another row or the map has taken the hover since. Touch and stylus pointers never hover.
 */
fun Modifier.mapBrowseHover(hover: MapBrowseHover, key: String): Modifier =
    pointerInput(hover, key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.firstOrNull()?.type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Enter -> hover.uri = key
                    PointerEventType.Exit -> if (hover.uri == key) hover.uri = null
                    else -> Unit
                }
            }
        }
    }
