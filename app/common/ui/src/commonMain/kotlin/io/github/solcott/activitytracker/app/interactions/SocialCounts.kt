package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppHighlight
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.string

// The counts row at the top of a page's social card: each kind of count is an icon and a number on one line.

/** A page's counts on one line, at the top of its social card: likes first, then what else people did with it. */
@Composable
fun SocialCountsRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

/**
 * One count: [icon] and [count], read as [label] ("3 likes"). With [onClick] the whole of it is one button, which says [onClickLabel]
 * ("Show likes"). With [onIconClick] the icon is a button of its own instead, named [iconLabel] (Like on someone else's activity), [filled]
 * and tinted the like colour once given, and the number alone takes [onClick].
 */
@Composable
@Suppress("LongParameterList") // A count, how it reads, and its one or two taps.
fun SocialCount(
    icon: AppIcon,
    count: String,
    label: String,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    filled: Boolean = false,
    iconLabel: String? = null,
    onIconClick: (() -> Unit)? = null,
) {
    val iconTint = if (filled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    if (onIconClick == null) {
        Count(count, label, onClickLabel, onClick, modifier) {
            PlatformIcon(icon, contentDescription = null, filled = filled, tint = iconTint, size = 20.dp)
        }
    } else {
        // The number's highlight tucks under the icon button's empty edge, so the inset doesn't push it away from its icon.
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(-COUNT_TUCK), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(icon, iconLabel, onIconClick, filled = filled, tint = iconTint)
            Count(count, label, onClickLabel, onClick, Modifier)
        }
    }
}

/** The number, after [icon] when it isn't a button of its own, read as [label] and tapped as one button when [onClick] is given. */
@Composable
private fun Count(
    count: String,
    label: String,
    onClickLabel: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .highlight()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick).handCursor() else Modifier)
            .clearAndSetSemantics {
                contentDescription = label
                if (onClick != null) {
                    role = Role.Button
                    onClick(onClickLabel) {
                        onClick()
                        true
                    }
                }
            }
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .padding(horizontal = AppHighlight.Horizontal),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(count, style = MaterialTheme.typography.labelLarge, color = countColor(onClick != null))
    }
}

/** The comment count: opens the Comments screen. */
@Composable
fun CommentCount(preview: CommentsPreview, modifier: Modifier = Modifier) {
    SocialCount(
        AppIcon.Comment,
        preview.countNumber,
        preview.countText.string(),
        modifier,
        onClick = { preview.eventSink(CommentsPreviewEvent.OpenAll) },
    )
}

/** A count that opens something takes the accent, as a text button does; one that doesn't stays quiet. */
@Composable
@ReadOnlyComposable
private fun countColor(tappable: Boolean): Color =
    if (tappable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

/** How far a count's highlight reaches under the icon button before it: about the empty edge around the button's icon. */
private val COUNT_TUCK = 8.dp
