package io.github.solcott.activitytracker.app.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.CircuitContent
import com.slack.circuit.foundation.NavDecoration
import com.slack.circuit.foundation.ProvideRecordLifecycle
import com.slack.circuit.foundation.animation.AnimatedNavDecoration
import com.slack.circuit.runtime.ExperimentalCircuitApi
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.navigation.NavArgument
import com.slack.circuit.runtime.navigation.NavStackList
import com.slack.circuit.runtime.navigation.navStackListOf
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import io.github.solcott.activitytracker.app.host.settingsPagesOnTop
import io.github.solcott.activitytracker.app.settings.ActivityDefaultsScreen
import io.github.solcott.activitytracker.app.settings.LocalSettingsPane
import io.github.solcott.activitytracker.app.settings.PrivacyZonesScreen
import io.github.solcott.activitytracker.app.settings.SettingsPage
import io.github.solcott.activitytracker.app.settings.SettingsPane
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.ui.adaptive.LocalIsPaneRoot
import io.github.solcott.activitytracker.app.ui.adaptive.WindowWidth

/**
 * The signed-in app's decoration: [SettingsListDetailDecoration] around the gesture navigation one (iOS swipe, Android predictive back).
 */
@OptIn(ExperimentalCircuitApi::class)
internal fun appNavDecoration(circuit: Circuit): NavDecoration =
    SettingsListDetailDecoration(AnimatedNavDecoration(circuit.animatedScreenTransforms, GestureNavigationDecorationFactory()))

/**
 * Lays Settings out beside the page it opened on a wide window (ui/settings-layout), and hands everything else to [delegate] (the gesture
 * navigation decoration) unchanged.
 *
 * **The back stack stays the only selection state.** When the stack ends in `[…, Settings, page, …subpage]` (only [SettingsPage]s above
 * Settings) and the content is at least [WindowWidth.Expanded] wide, [delegate] gets the stack trimmed to end at Settings, and Settings'
 * content is a [ListDetailPaneScaffold]: the list, and beside it the top record. With nothing above Settings the detail pane shows
 * [ActivityDefaultsScreen] as plain [CircuitContent], not a record, so what the stack holds never depends on the window's width. Narrow, or
 * with any other screen on top (a profile opened from the blocked list, Moderation), it passes through, so phones keep push, pop and the
 * edge swipe.
 *
 * Each record's content is a `movableContentOf` kept per record key, so crossing the breakpoint moves the list and the open page between
 * [delegate]'s slot and a pane instead of composing them again: the list keeps its scroll and nothing reloads.
 */
@Stable
internal class SettingsListDetailDecoration(private val delegate: NavDecoration) : NavDecoration {
    @Composable
    override fun <T : NavArgument> DecoratedContent(
        args: NavStackList<T>,
        navigator: Navigator,
        modifier: Modifier,
        content: @Composable (T) -> Unit,
    ) {
        val movable = remember { MovableRecords(content) }.also { it.content = content }
        BoxWithConstraints(modifier) {
            val layout = SettingsLayout.of(args.toList(), wide = WindowWidth.of(maxWidth, maxHeight) == WindowWidth.Expanded)
            if (layout == null) {
                delegate.DecoratedContent(args, navigator, Modifier.fillMaxSize()) { movable.of(it)(it) }
            } else {
                // The stack below Settings, then Settings itself: what the delegate animates and handles back for.
                val trimmed = checkNotNull(navStackListOf(args.toList().drop(layout.pages.size)))
                val top = layout.pages.firstOrNull()
                delegate.DecoratedContent(trimmed, navigator, Modifier.fillMaxSize()) { arg ->
                    if (arg.key != layout.settings.key) {
                        movable.of(arg)(arg)
                    } else {
                        ListAndDetail(
                            SettingsPane(twoPane = true, shownPage = layout.shownPage),
                            // The pane's first page has no back button: the list beside it is where to go instead.
                            detailIsPaneRoot = top == null || top == layout.pages.last(),
                            list = { movable.of(layout.settings)(layout.settings) },
                            detail = {
                                if (top == null) CircuitContent(ActivityDefaultsScreen, navigator = navigator) else movable.of(top)(top)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One movable content per record, so a record's composition follows it between the delegate's slot and a pane. */
@Stable
private class MovableRecords<T : NavArgument>(content: @Composable (T) -> Unit) {
    /** The latest content the navigable content passed, which every record's movable content draws. */
    var content by mutableStateOf(content)

    private val byKey = mutableMapOf<String, @Composable (T) -> Unit>()

    /** [arg]'s movable content, made once per record key in this holder, which the decoration remembers. */
    fun of(arg: T): @Composable (T) -> Unit = byKey.getOrPut(arg.key) { movableContentOf { record: T -> content(record) } }
}

/** Settings on a wide window: its record, and the settings pages above it, nearest first ([pages] empty while none is open). */
private class SettingsLayout<T : NavArgument>(val settings: T, val pages: List<T>) {
    /** The page the list marks: the first one opened, or the one shown until a row is chosen. */
    val shownPage: SettingsPage
        get() = pages.lastOrNull()?.screen as? SettingsPage ?: ActivityDefaultsScreen

    companion object {
        /** The layout for a stack listed top first, or null to pass it through. */
        fun <T : NavArgument> of(stack: List<T>, wide: Boolean): SettingsLayout<T>? =
            // The rule the presenter host asks too, so the two shells can't disagree about when Settings shows beside a page.
            settingsPagesOnTop(stack) { it.screen }?.takeIf { wide }?.let { pages -> SettingsLayout(stack[pages], stack.take(pages)) }
    }
}

/** The list beside the detail, both live; the list marks [pane]'s page, and [detail]'s bar drops its back button if [detailIsPaneRoot]. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ListAndDetail(
    pane: SettingsPane,
    detailIsPaneRoot: Boolean,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    ListDetailPaneScaffold(
        directive = TwoPanes,
        value = BothPanes,
        listPane = {
            AnimatedPane(Modifier.preferredWidth(LIST_PANE_WIDTH)) {
                OnScreen { CompositionLocalProvider(LocalSettingsPane provides pane, content = list) }
            }
        },
        detailPane = {
            AnimatedPane { OnScreen { CompositionLocalProvider(LocalIsPaneRoot provides detailIsPaneRoot, content = detail) } }
        },
    )
}

/**
 * Marks a record active for as long as its pane is composed. Circuit pauses the presenter of any record that isn't the stack's top, which
 * would freeze the list's summaries beside the open page; a pane that's composed is on screen and should run.
 */
@Composable
@NonRestartableComposable
private fun OnScreen(content: @Composable () -> Unit) {
    ProvideRecordLifecycle(isActive = true, content = content)
}

/**
 * Two panes side by side, with Material's spacing. Built by hand, as this layout is only used once the content is wide enough: no vertical
 * split, and no moving focus to the page that opened (it would land on the page's first field).
 */
private val TwoPanes =
    PaneScaffoldDirective(
        maxHorizontalPartitions = 2,
        horizontalPartitionSpacerSize = 24.dp,
        maxVerticalPartitions = 1,
        verticalPartitionSpacerSize = 0.dp,
        defaultPanePreferredWidth = 360.dp,
        defaultPanePreferredHeight = 420.dp,
        excludedBounds = emptyList(),
        shouldAutoFocusCurrentDestination = false,
    )

/** Both the list and the detail shown; no extra pane. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BothPanes =
    ThreePaneScaffoldValue(
        primary = PaneAdaptedValue.Expanded, // ListDetailPaneScaffoldRole.Detail
        secondary = PaneAdaptedValue.Expanded, // ListDetailPaneScaffoldRole.List
        tertiary = PaneAdaptedValue.Hidden,
    )

private val LIST_PANE_WIDTH = 360.dp

/**
 * Settings laid out by [SettingsListDetailDecoration] as on a wide window, for previews and screenshots: [list] beside [page]. With
 * [pageOpen] false, [page] stands in for the page shown until a row is chosen, which the decoration draws as plain content rather than a
 * record. [shownPage] is the page [page] draws, whose row the list marks. Fill a wide enough space (840dp or more), or it lays out as a
 * phone: [list] alone.
 */
@Composable
fun SettingsListDetailPreview(
    list: @Composable () -> Unit,
    page: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    pageOpen: Boolean = true,
    shownPage: SettingsPage = PrivacyZonesScreen,
) {
    val args = checkNotNull(navStackListOf(listOfNotNull(PreviewArg(shownPage).takeIf { pageOpen }, PreviewArg(SettingsScreen))))
    // One page content, drawn by whichever of the two the layout uses: the record's slot, or the stand-in for the page shown first.
    val pageContent = remember { movableContentOf(page) }
    val circuit = remember { Circuit.Builder().setOnUnavailableContent { _, _ -> pageContent() }.build() }
    val decoration = remember { SettingsListDetailDecoration(TopOnlyDecoration) }
    CircuitCompositionLocals(circuit) {
        decoration.DecoratedContent(args, Navigator.NoOp, modifier) { arg -> if (arg.screen == SettingsScreen) list() else pageContent() }
    }
}

private data class PreviewArg(override val screen: Screen) : NavArgument {
    override val key: String = screen.toString()
}

/** Draws only the top of the stack, with no transitions: all a still preview needs from the gesture decoration. */
private object TopOnlyDecoration : NavDecoration {
    @Composable
    override fun <T : NavArgument> DecoratedContent(
        args: NavStackList<T>,
        navigator: Navigator,
        modifier: Modifier,
        content: @Composable (T) -> Unit,
    ) {
        Box(modifier) { content(args.top) }
    }
}
