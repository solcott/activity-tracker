package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.browse.MapBrowseTexts
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform

/**
 * A row of a list that shares a selection with a map of lines (segments/segment-browse, segments/path-page, dive-sites/site-browse): its
 * [name], after its [rating]'s swatch when it is rated, then its [details] line. It's marked while [selected] or under the mouse pointer
 * (here or on its line: [hover] holds its [key]), and a mouse over it puts its [key] in [hover], so the map highlights its line. A tap on a
 * touch screen calls [onTap], which selects it or opens it once selected; a click with a pointer calls [onOpen]. [indent] moves its text in
 * under a heading, while its mark still fills the list's width.
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
) {
    val touch = LocalUiPlatform.current.isMobile
    Column(
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
            .padding(start = 16.dp + indent, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        // A rated trail's swatch before its name (segments/trail-ratings), read as its level's name.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RatingSwatch(rating)
            Text(name.string(), style = MaterialTheme.typography.titleMedium)
        }
        Text(details.string(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
