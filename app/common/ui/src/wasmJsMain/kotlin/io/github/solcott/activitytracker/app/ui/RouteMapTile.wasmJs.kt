package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList

/** The line preview: MapLibre has no web target to draw the image with. */
@Composable
actual fun RouteMapTile(polyline: String, height: Dp, gaps: ImmutableList<Int>?, modifier: Modifier) =
    RoutePreview(polyline, modifier, height, gaps)
