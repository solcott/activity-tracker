package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.solcott.activitytracker.app.RouteEnds
import kotlinx.collections.immutable.ImmutableList

/** The canvas preview, static: there's no enlarged map on web until it can draw a real one (`feed/route-map-viewer`). */
@Composable
actual fun ActivityMap(
    polyline: String,
    modifier: Modifier,
    height: Dp,
    gaps: ImmutableList<Int>?,
    ends: RouteEnds?,
    onExpand: (() -> Unit)?,
    interactive: Boolean,
) = RoutePreview(polyline, modifier, height, gaps, ends)
