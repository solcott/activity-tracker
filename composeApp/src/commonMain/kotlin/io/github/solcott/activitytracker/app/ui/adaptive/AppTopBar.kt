package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * Whether this screen is the bottom of the back stack, which is a tab's root once signed in. `App` provides it per back-stack record. A
 * root's bar has no back button, and on iOS it shows a large title.
 */
val LocalIsNavRoot = compositionLocalOf { false }

/**
 * How a screen's [AppTopBar] follows its content's scrolling. Pass it to the bar and put its `nestedScrollConnection` on the screen (or,
 * under pull to refresh, on the list inside it). At a tab's root on iOS the large title collapses into the bar as the content scrolls.
 */
@Composable expect fun rememberAppTopBarScrollBehavior(): TopAppBarScrollBehavior

/**
 * A screen's title bar. Material platforms get Material 3's top app bar. iOS gets a navigation bar drawn in Compose (so it slides with the
 * screen and follows the swipe-back gesture): a back chevron, a centred title, tinted bar buttons, and a large title at a tab's root.
 *
 * [onBack] shows a back button unless this is the root screen ([LocalIsNavRoot]). Put [AppIconButton]s and [AppMenu]s in [actions]. With a
 * [scrollBehavior] ([rememberAppTopBarScrollBehavior]) the bar marks content scrolled under it (Material's scrolled colour, iOS's
 * hairline), and on iOS the large title collapses into the bar.
 *
 * With a [titleMenu] the title is a button with a down chevron that opens it, for a screen whose title is a choice (the feed's scope).
 */
@Composable
expect fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    titleMenu: ImmutableList<AppMenuItem>? = null,
    actions: @Composable RowScope.() -> Unit = {},
)

/** The bar's title in [style]; with a [menu], a button that opens it, marked by a down chevron sized to the text. */
@Composable
internal fun TopBarTitle(title: String, style: TextStyle, menu: ImmutableList<AppMenuItem>?) {
    if (menu == null) {
        Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    var open by rememberSaveable { mutableStateOf(false) }
    val chevron = with(LocalDensity.current) { style.fontSize.toDp() } * TITLE_CHEVRON_SCALE
    Box {
        Row(
            Modifier.clickable(role = Role.Button) { open = true }.handCursor(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            PlatformIcon(AppIcon.ChevronDown, null, size = chevron)
        }
        AppMenu(expanded = open, onDismiss = { open = false }, items = menu)
    }
}

/** The title menu's chevron, relative to the title's font size. */
private const val TITLE_CHEVRON_SCALE = 0.7f
