package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.map_sheet_handle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Where a [MapSheetLayout]'s sheet rests: low with its top showing, halfway up the map, or over all of it. */
enum class MapSheetDetent {
    Peek,
    Middle,
    Full,
}

/** A [MapSheetLayout]'s sheet: where it rests, and a way to move it there. Kept across configuration changes. */
@Stable
class MapSheetState internal constructor(initial: MapSheetDetent) {
    internal val draggable = AnchoredDraggableState(initial)

    /** Where the sheet rests now, or last rested while a drag moves it. */
    val detent: MapSheetDetent
        get() = draggable.settledValue

    /** Moves the sheet to [detent], animated. */
    suspend fun moveTo(detent: MapSheetDetent) {
        if (draggable.anchors.hasPositionFor(detent)) draggable.animateTo(detent)
    }

    internal companion object {
        val Saver: Saver<MapSheetState, MapSheetDetent> = Saver(save = { it.detent }, restore = { MapSheetState(it) })
    }
}

/** A [MapSheetState] resting at [initial] until moved, its place saved with the screen's. */
@Composable
fun rememberMapSheetState(initial: MapSheetDetent = MapSheetDetent.Middle): MapSheetState =
    rememberSaveable(saver = MapSheetState.Saver) { MapSheetState(initial) }

/**
 * A phone's map with a sheet over it (segments/path-page), as Apple Maps draws one: the [map] fills the space and the [sheet] rests at one
 * of three heights ([MapSheetDetent]): its top alone showing, halfway up, or over the whole map. The sheet is dragged by its grabber or by
 * its content: a list in it first moves the sheet up to full height and then scrolls, and pulling down from the list's top moves the sheet
 * down again. [map] is given how much of its lower part the resting sheet hides, so it can frame what it shows above the sheet. The sheet
 * looks as Material's standard bottom sheet does: the theme's extra-large top corners, its container colour and elevation, and its handle.
 */
@OptIn(ExperimentalMaterial3Api::class) // BottomSheetDefaults: the standard sheet's shape, colour and elevation.
@Composable
fun MapSheetLayout(
    map: @Composable (coveredBottom: Dp) -> Unit,
    sheet: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: MapSheetState = rememberMapSheetState(),
) {
    val draggable = state.draggable
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = with(density) { maxHeight.toPx() }
        val peek = with(density) { PEEK.toPx() }.coerceAtMost(height)
        val anchors =
            remember(height, peek) {
                DraggableAnchors {
                    MapSheetDetent.Full at 0f
                    MapSheetDetent.Middle at height * MIDDLE_SHARE
                    MapSheetDetent.Peek at height - peek
                }
            }
        SideEffect { if (draggable.anchors != anchors) draggable.updateAnchors(anchors, draggable.targetValue) }
        // The resting sheet's top, not the dragged one's, so the map frames once the sheet settles, not on every frame of a drag.
        val restingTop = anchors.positionOf(draggable.settledValue)
        map(with(density) { (height - restingTop).toDp() })
        val fling = AnchoredDraggableDefaults.flingBehavior(draggable)
        val connection = remember(draggable, fling) { SheetScrollConnection(draggable, fling) }
        Surface(
            Modifier.fillMaxSize()
                .offset {
                    IntOffset(
                        0,
                        draggable.offset.takeUnless { it.isNaN() }?.roundToInt() ?: anchors.positionOf(draggable.targetValue).roundToInt(),
                    )
                }
                .nestedScroll(connection)
                .anchoredDraggable(draggable, Orientation.Vertical, flingBehavior = fling),
            shape = BottomSheetDefaults.ExpandedShape,
            color = BottomSheetDefaults.ContainerColor,
            shadowElevation = BottomSheetDefaults.Elevation,
        ) {
            Column(Modifier.fillMaxSize()) {
                val handle = stringResource(Res.string.map_sheet_handle)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = GRABBER_PADDING).semantics {
                        contentDescription = handle
                        // A tap target for a screen reader: each use moves the sheet to its next height, from low up to full and back.
                        customActions =
                            listOf(
                                CustomAccessibilityAction(handle) {
                                    scope.launch { state.moveTo(next(draggable.settledValue)) }
                                    true
                                },
                            )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(GRABBER_WIDTH, GRABBER_HEIGHT)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.extraLarge),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { sheet() }
            }
        }
    }
}

/** The height after [detent], going up and then wrapping back to the lowest. */
private fun next(detent: MapSheetDetent): MapSheetDetent =
    when (detent) {
        MapSheetDetent.Peek -> MapSheetDetent.Middle
        MapSheetDetent.Middle -> MapSheetDetent.Full
        MapSheetDetent.Full -> MapSheetDetent.Peek
    }

/**
 * Shares a drag between the sheet and a list in it, as Material's bottom sheet does: a drag up moves the sheet until it's at full height
 * before the list scrolls, and what a drag down leaves over once the list is at its top moves the sheet down. A fling does the same.
 */
private class SheetScrollConnection(private val sheet: AnchoredDraggableState<MapSheetDetent>, private val fling: FlingBehavior) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput) Offset(0f, sheet.dispatchRawDelta(delta)) else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, sheet.dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        val up = available.y
        val top = sheet.anchors.minPosition()
        return if (up < 0 && sheet.requireOffset() > top) {
            fling(up)
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        fling(available.y)
        return available
    }

    /** Flings the sheet at [velocity] with its own fling behaviour, which lands it at the height the fling carries it to. */
    private suspend fun fling(velocity: Float) {
        sheet.anchoredDrag {
            val scroll =
                object : ScrollScope {
                    override fun scrollBy(pixels: Float): Float {
                        val from = sheet.requireOffset()
                        val to = (from + pixels).coerceIn(sheet.anchors.minPosition(), sheet.anchors.maxPosition())
                        dragTo(to)
                        return to - from
                    }
                }
            with(fling) { scroll.performFling(velocity) }
        }
    }
}

/** The lowest sheet's height showing: the grabber and a row's worth of what's under it. */
private val PEEK = 112.dp

/** How far down the map the sheet's middle height comes, as a share of the space: half, the map above and the list below. */
private const val MIDDLE_SHARE = 0.5f

// Material's bottom sheet drag handle (BottomSheetDefaults.DragHandle), drawn here so the handle keeps this sheet's own description and
// action rather than Material's.
private val GRABBER_WIDTH = 32.dp
private val GRABBER_HEIGHT = 4.dp
private val GRABBER_PADDING = 22.dp
