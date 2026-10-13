package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState

/**
 * Whether this content stays pinned while a list scrolls under it, like a sticky header. Native iOS views sit under the Compose canvas and
 * show through each other's cut-outs, so [AppCheckbox] and [AppTriStateCheckbox] place theirs over the canvas here instead, where a row's
 * mark scrolling underneath can't show through.
 */
val LocalPinnedOverContent = staticCompositionLocalOf { false }

/**
 * A multi-select mark: Material's checkbox, or on iOS a native `UIImageView` with the filled checkmark circle SF Symbol its lists use to
 * pick rows (UIKit has no checkbox). Pass a null [onCheckedChange] when an enclosing row is `toggleable`, so the whole row is the target
 * and screen readers announce it once.
 */
@Composable
expect fun AppCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true)

/**
 * A parent's mark over child checkboxes: [AppCheckbox] when all or none of them are on, and the mixed mark (a dash, or on iOS the
 * `minus.circle.fill` SF Symbol) for [ToggleableState.Indeterminate], as Material's and Apple's guidelines draw a partly selected group.
 * Pass a null [onClick] when an enclosing row is clickable.
 */
@Composable
expect fun AppTriStateCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true)
