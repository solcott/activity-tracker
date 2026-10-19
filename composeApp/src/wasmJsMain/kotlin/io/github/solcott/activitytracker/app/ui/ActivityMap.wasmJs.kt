package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.solcott.activitytracker.app.RouteEnds
import kotlinx.collections.immutable.ImmutableList

@Composable
actual fun ActivityMap(polyline: String, modifier: Modifier, height: Dp, gaps: ImmutableList<Int>?, ends: RouteEnds?) =
    RoutePreview(polyline, modifier, height, gaps, ends)
