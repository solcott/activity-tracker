package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Whether this screen is the bottom of the back stack, which is a tab's root once signed in. `App` provides it per back-stack record. A
 * root's bar has no back button, and on iOS it shows a large title.
 */
val LocalIsNavRoot = compositionLocalOf { false }

/**
 * How a screen's [AppTopBar] follows its content's scrolling. Pass it to the bar and put its `nestedScrollConnection` on the screen (or,
 * under pull to refresh, on the list inside it). At a tab's root on iOS the large title collapses into the bar as the content scrolls.
 */
@Composable
fun rememberAppTopBarScrollBehavior(): TopAppBarScrollBehavior =
    if (LocalUiPlatform.current == UiPlatform.Ios && LocalIsNavRoot.current) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }

/**
 * A screen's title bar. Material platforms get Material 3's top app bar. iOS gets a navigation bar drawn in Compose (so it slides with the
 * screen and follows the swipe-back gesture): a back chevron, a centred title, tinted bar buttons, and a large title at a tab's root.
 *
 * [onBack] shows a back button unless this is the root screen ([LocalIsNavRoot]). Put [AppIconButton]s and [AppMenu]s in [actions]. With a
 * [scrollBehavior] ([rememberAppTopBarScrollBehavior]) the bar marks content scrolled under it (Material's scrolled colour, iOS's
 * hairline), and on iOS the large title collapses into the bar.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val root = LocalIsNavRoot.current
    val back = onBack?.takeUnless { root }
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        IosNavigationBar(title, subtitle, back, large = root, actions, scrollBehavior?.state, modifier)
    } else {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            },
            modifier = modifier,
            navigationIcon = { back?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it) } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun IosNavigationBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    large: Boolean,
    actions: @Composable RowScope.() -> Unit,
    scrollState: TopAppBarState?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Like UIKit's scroll-edge appearance, the bar is clear (the screen's own background) at rest and its effect fades in once content
    // scrolls under it: iOS 26's soft edge effect, or before that a material and hairline.
    val scrolled by remember(scrollState) { derivedStateOf { (scrollState?.contentOffset ?: 0f) < 0f } }
    val material by animateFloatAsState(if (scrolled) 1f else 0f, label = "barMaterial")
    val backdrop = LocalBarBackdrop.current
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (backdrop == null) {
                    Modifier.background(colors.surface)
                } else {
                    Modifier.hazeBlur(HazeInput.Sources(backdrop), barMaterial(colors.surface, material, edgeEffect = usesLiquidGlass))
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        CenteredBar(
            leading = { onBack?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it) } },
            title = {
                // At a root, the inline title fades in once the large title has scrolled under the bar.
                Column(
                    Modifier.graphicsLayer { alpha = if (large) inlineTitleAlpha(scrollState) else 1f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            trailing = { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
            modifier = Modifier.fillMaxWidth().height(IOS_BAR_HEIGHT).padding(horizontal = 4.dp),
        )
        if (large) {
            Column(Modifier.collapsing(scrollState).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        }
        HorizontalDivider(
            Modifier.graphicsLayer { alpha = if (usesLiquidGlass) 0f else material },
            thickness = HAIRLINE,
            color = colors.outlineVariant,
        )
    }
}

/**
 * UIKit's bar background over content scrolled under it, faded in by [alpha]. With [edgeEffect] it's iOS 26's soft scroll edge effect: the
 * content blurs and washes into the bar's [color] behind the title and buttons, fading to untouched at the bar's bottom edge. Otherwise
 * it's the earlier bar material: a uniform blur, mostly opaque in [color], so titles stay legible over busy content.
 */
private fun barMaterial(color: Color, alpha: Float, edgeEffect: Boolean): HazeBlurStyle = HazeBlurStyle {
    blurRadius(BAR_BLUR)
    alpha(alpha)
    if (edgeEffect) {
        // Full strength behind the title and buttons, fading out over the bar's last stretch.
        progressive(
            HazeProgressive.verticalGradient(
                easing = { ((it - EDGE_FADE_START) / (1f - EDGE_FADE_START)).coerceIn(0f, 1f) },
                startIntensity = 1f,
                endIntensity = 0f,
            )
        )
        val tint = color.copy(alpha = BAR_TINT_ALPHA)
        colorEffects(listOf(HazeColorEffect.tint(Brush.verticalGradient(0f to tint, EDGE_FADE_START to tint, 1f to Color.Transparent))))
    } else {
        backgroundColor(color)
        colorEffects(listOf(HazeColorEffect.tint(color.copy(alpha = BAR_TINT_ALPHA))))
    }
}

/** 0 while the large title shows, rising to 1 over the last part of its collapse. Without a scroll state it never collapses. */
private fun inlineTitleAlpha(state: TopAppBarState?): Float =
    state?.let { ((it.collapsedFraction - INLINE_TITLE_FADE_START) / (1f - INLINE_TITLE_FADE_START)).coerceIn(0f, 1f) } ?: 0f

/**
 * Collapses the large title as [state] scrolls: its height shrinks by the scroll behaviour's height offset, which this sets the limit of,
 * and the title slides up under the bar.
 */
private fun Modifier.collapsing(state: TopAppBarState?): Modifier =
    if (state == null) {
        this
    } else {
        clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val limit = -placeable.height.toFloat()
            if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
            val height = (placeable.height + state.heightOffset).roundToInt().coerceAtLeast(0)
            layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
        }
    }

/** UIKit's bar layout: the title is centred on the bar, not between the buttons, and only shrinks as far as the wider side allows. */
@Composable
private fun CenteredBar(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(LEADING)) { leading() }
            Box(Modifier.layoutId(TITLE)) { title() }
            Box(Modifier.layoutId(TRAILING)) { trailing() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadingPlaceable = measurables.first { it.layoutId == LEADING }.measure(loose)
        val trailingPlaceable = measurables.first { it.layoutId == TRAILING }.measure(loose)
        val side = maxOf(leadingPlaceable.width, trailingPlaceable.width)
        val titleWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
        val titlePlaceable =
            measurables.first { it.layoutId == TITLE }.measure(Constraints(maxWidth = titleWidth, maxHeight = constraints.maxHeight))
        val height = constraints.maxHeight
        layout(constraints.maxWidth, height) {
            leadingPlaceable.place(0, (height - leadingPlaceable.height) / 2)
            titlePlaceable.place((constraints.maxWidth - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            trailingPlaceable.place(constraints.maxWidth - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}

private const val LEADING = "leading"
private const val TITLE = "title"
private const val TRAILING = "trailing"
private val IOS_BAR_HEIGHT = 44.dp
private val HAIRLINE = 0.5.dp
private val BAR_BLUR = 24.dp
private const val BAR_TINT_ALPHA = 0.8f
private const val EDGE_FADE_START = 0.7f
private const val INLINE_TITLE_FADE_START = 0.8f
