package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberAppTopBarScrollBehavior(): TopAppBarScrollBehavior =
    if (LocalIsNavRoot.current) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()

@Composable
actual fun AppTopBar(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    subtitle: String?,
    scrollBehavior: TopAppBarScrollBehavior?,
    titleMenu: ImmutableList<AppMenuItem>?,
    actions: @Composable RowScope.() -> Unit,
) {
    val root = LocalIsNavRoot.current
    IosNavigationBar(title, subtitle, titleMenu, onBack?.takeUnless { root }, large = root, actions, scrollBehavior?.state, modifier)
}

@Composable
private fun IosNavigationBar(
    title: String,
    subtitle: String?,
    titleMenu: ImmutableList<AppMenuItem>?,
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
    // At a root, like iOS 26, the large title hands over to the inline one once it's mostly scrolled under the bar: a short timed
    // cross-fade in which the outgoing title blurs away and the incoming one sharpens, rather than a fade tied to the scroll.
    val collapsed by remember(scrollState) { derivedStateOf { (scrollState?.collapsedFraction ?: 0f) >= INLINE_TITLE_THRESHOLD } }
    val inlineTitle by animateFloatAsState(if (!large || collapsed) 1f else 0f, tween(TITLE_SWAP_MILLIS), label = "inlineTitle")
    var barHeight by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxWidth().onSizeChanged { barHeight = it.height }) {
        backdrop?.let { BarEffect(it, material, barHeight, Modifier.matchParentSize()) }
        Column(
            Modifier.fillMaxWidth()
                .then(if (backdrop == null) Modifier.background(colors.surface) else Modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            CenteredBar(
                leading = {
                    onBack?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it, Modifier.testTag(TestTags.BACK)) }
                },
                title = {
                    Column(
                        Modifier.titleSwap(rise = INLINE_TITLE_RISE) { inlineTitle },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TopBarTitle(title, MaterialTheme.typography.titleMedium, titleMenu)
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
                Column(Modifier.collapsing(scrollState).padding(start = 16.dp, end = 16.dp, bottom = 8.dp).titleSwap { 1f - inlineTitle }) {
                    TopBarTitle(title, MaterialTheme.typography.headlineLarge, titleMenu)
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
}

/**
 * The bar's [barMaterial] over [backdrop], laid out over a bar [barHeight] px tall. Like UIKit's scroll edge effect, iOS 26's soft edge
 * runs a little past the bar into the content, so it's at full strength behind the title and buttons and fades out below them.
 */
@Composable
private fun BarEffect(backdrop: HazeState, material: Float, barHeight: Int, modifier: Modifier = Modifier) {
    val overhang = if (usesLiquidGlass) EDGE_OVERHANG else 0.dp
    val overhangPx = with(LocalDensity.current) { overhang.toPx() }
    // Where the fade starts, as a fraction of the effect's height: the bar's bottom edge.
    val fadeStart = if (barHeight > 0) barHeight / (barHeight + overhangPx) else 1f
    val color = MaterialTheme.colorScheme.surface
    Box(
        modifier
            // A native view under the list, like the route map, shows through the canvas's cut-out, which Haze copies into its layer
            // along with the content and so clears out the material's opaque fill. Filling the bar in the canvas itself covers that
            // cut-out; Compose content, already behind an opaque material there, looks the same.
            .background(color.copy(alpha = material))
            .overhang(overhang)
            .hazeBlur(HazeInput.Sources(backdrop), barMaterial(color, material, fadeStart, usesLiquidGlass))
    )
}

/**
 * UIKit's bar background over content scrolled under it, faded in by [alpha]: a blur, mostly opaque in [color], so text behind the title
 * and buttons isn't legible. With [edgeEffect] it's iOS 26's soft scroll edge effect, which fades out from [fadeStart] (a fraction of the
 * effect's height) to untouched content at the bottom of its area.
 */
private fun barMaterial(color: Color, alpha: Float, fadeStart: Float, edgeEffect: Boolean): HazeBlurStyle = HazeBlurStyle {
    blurRadius(BAR_BLUR)
    alpha(alpha)
    backgroundColor(color)
    colorEffects(listOf(HazeColorEffect.tint(color.copy(alpha = BAR_TINT_ALPHA))))
    if (edgeEffect) {
        // A mask rather than Haze's progressive blur, which leaves text sharp at full intensity on iOS. Its stops are fractions because
        // Haze's gradients with absolute offsets draw nothing on iOS.
        mask(Brush.verticalGradient(0f to Color.Black, fadeStart to Color.Black, 1f to Color.Transparent))
    }
}

/**
 * One side of the title cross-fade, at [shown] (0 to 1, read at draw time): it fades and blurs as it goes, and with a [rise] it settles
 * that far upward as it comes in.
 */
private fun Modifier.titleSwap(rise: Dp = 0.dp, shown: () -> Float): Modifier = graphicsLayer {
    val gone = 1f - shown()
    alpha = 1f - gone
    translationY = gone * rise.toPx()
    val radius = gone * TITLE_SWAP_BLUR.toPx()
    renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
}

/** Lays the effect out [by] taller than the bar, hanging below it, without making the bar any taller. */
private fun Modifier.overhang(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx()
    val placeable =
        measurable.measure(constraints.copy(minHeight = constraints.minHeight + extra, maxHeight = constraints.maxHeight + extra))
    layout(placeable.width, placeable.height - extra) { placeable.place(0, 0) }
}

/**
 * Collapses the large title as [state] scrolls: its height shrinks by the scroll behaviour's height offset, which this sets the limit of,
 * and the title slides up into the bar unclipped, where the title cross-fade blurs it away.
 */
private fun Modifier.collapsing(state: TopAppBarState?): Modifier =
    if (state == null) {
        this
    } else {
        layout { measurable, constraints ->
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
/** How far iOS 26's soft edge runs past the bar into the content as it fades out. */
private val EDGE_OVERHANG = 12.dp
/** How far the large title has collapsed when the inline one takes over. */
private const val INLINE_TITLE_THRESHOLD = 0.6f
private const val TITLE_SWAP_MILLIS = 220
private val TITLE_SWAP_BLUR = 8.dp
private val INLINE_TITLE_RISE = 6.dp
