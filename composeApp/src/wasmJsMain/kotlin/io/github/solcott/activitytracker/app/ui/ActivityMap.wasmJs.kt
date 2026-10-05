package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable actual fun ActivityMap(polyline: String, modifier: Modifier, height: Dp) = RoutePreview(polyline, modifier, height)
