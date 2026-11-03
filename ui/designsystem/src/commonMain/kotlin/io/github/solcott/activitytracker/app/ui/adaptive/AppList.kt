package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A titled group of [AppListRow]s, like a section of Settings. Material platforms get a coloured heading over plain rows. iOS gets an inset
 * grouped section: a small grey heading over a rounded cell, its rows split by hairlines inset to their text. [content] stacks top to
 * bottom and may also hold other controls (a segmented control), padded by the caller.
 */
@Composable expect fun AppListSection(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit)

/**
 * One row of a list: a [title] with an optional [summary] of its current value. With [onClick] it opens something: Material's list item, or
 * on iOS a cell with the summary at its end and a chevron. [trailing] replaces the chevron with a control (a switch). [selected] marks the
 * row whose page shows beside the list.
 */
@Composable
expect fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
)
